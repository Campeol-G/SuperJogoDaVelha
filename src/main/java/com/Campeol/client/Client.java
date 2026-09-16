package com.Campeol.client;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;

import java.io.ObjectInputFilter;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import com.Campeol.net.NetException;
import com.Campeol.net.NetLog;
import com.Campeol.net.NetPoll;
import com.Campeol.net.SslUtil;
import com.Campeol.subgame.Match;

/**
 * Client
 */
public class Client {
  private ObjectOutputStream writer;
  private ObjectInputStream reader;
  private volatile SSLSocket tcpSocket = null;
  private char clientPiece;
  /** Última escrita bem-sucedida (lida pela thread de heartbeat). */
  private volatile long lastWriteMillis = System.currentTimeMillis();
  /** Socket da descoberta UDP (guardado para o cancel soltar o receive). */
  private volatile DatagramSocket discoverySocket = null;
  /** Cancelamento pedido pela UI (Esc na espera). */
  private volatile boolean cancelDiscovery = false;

  /**
   * Cancela uma busca em andamento: solta o receive() bloqueado.
   * Idempotente.
   */
  public void cancelDiscovery() {
    cancelDiscovery = true;
    DatagramSocket s = discoverySocket;
    if (s != null) {
      try {
        s.close();
      } catch (Exception ignored) {
      }
    }
    SSLSocket t = tcpSocket;
    if (t != null && !t.isClosed()) {
      try {
        t.close();
      } catch (Exception ignored) {
      }
    }
  }

  public Boolean start(int portNumber, String password) {
    SSLSocket socket = null;
    try {
      InetAddress serverAddress = findIP();
      if (cancelDiscovery) {
        return false;
      }
      if (serverAddress == null) {
        throw new NetException("Servidor nao encontrado");
      }
      SSLSocketFactory factory = SslUtil.getSocketFactory();
      socket = (SSLSocket) factory.createSocket(serverAddress, portNumber);
      tcpSocket = socket;
      socket.setSoTimeout(500);
      writer = new ObjectOutputStream(socket.getOutputStream());
      writer.flush();
      reader = new ObjectInputStream(socket.getInputStream());
      reader.setObjectInputFilter(buildFilter());

      try {
        if (!validatePassword(writer, reader, password)) {
          throw new NetException("Invalid Password");
        }
        Object o1 = reader.readObject();
        Object o2 = reader.readObject();
        if (!(o1 instanceof Boolean) || !(o2 instanceof Character)) {
          throw new NetException("Handshake falhou: resposta invalida");
        }
        Boolean sorteio = (Boolean) o1;
        clientPiece = (Character) o2;
        if (clientPiece != 'X' && clientPiece != 'O') {
          throw new NetException("Handshake falhou: peca invalida");
        }
        return sorteio;
      } catch (ClassNotFoundException e) {
        throw new RuntimeException(e);
      }
    } catch (IOException e) {
      close();
      throw new RuntimeException(e);
    } catch (RuntimeException e) {
      close();
      throw e;
    }
  }

  /** Permite só classes do protocolo; bloqueia gadget-chain via desserialização. */
  private static ObjectInputFilter buildFilter() {
    return info -> {
      Class<?> c = info.serialClass();
      if (c == null) {
        // arrays primitivos / null: decide pelo tamanho/profundidade
        if (info.arrayLength() >= 0 && info.arrayLength() > 10000) {
          return ObjectInputFilter.Status.REJECTED;
        }
        return ObjectInputFilter.Status.UNDECIDED;
      }
      String n = c.getName();
      // Arrays (Match[][], Piece[][]): permite o array, elementos checados depois.
      if (n.startsWith("[")) {
        if (info.arrayLength() >= 0 && info.arrayLength() > 10000) {
          return ObjectInputFilter.Status.REJECTED;
        }
        return ObjectInputFilter.Status.ALLOWED;
      }
      if (n.startsWith("com.Campeol.subgame.")
          || n.startsWith("com.Campeol.ui.NetMessage")
          || n.equals("java.lang.String")
          || n.equals("java.lang.Boolean")
          || n.equals("java.lang.Character")
          || n.equals("java.lang.Integer")
          || n.equals("java.lang.Number")
          || n.equals("java.util.ArrayList")
          || n.equals("java.lang.Object")
          || n.startsWith("com.Campeol.MatchStatus")) {
        return ObjectInputFilter.Status.ALLOWED;
      }
      if (n.startsWith("java.lang.") || n.startsWith("java.util.")) {
        return ObjectInputFilter.Status.UNDECIDED;
      }
      return ObjectInputFilter.Status.REJECTED;
    };
  }

  public char getClientPiece() {
    return clientPiece;
  }

  private InetAddress findIP() {
    int portServer = 5000;
    int maxAttempts = 12;
    int attempts = 0;
    // Sem reset do flag: connectAsync sempre usa instância nova (nasce
    // false); resetar abriria corrida com um cancel anterior.
    DatagramSocket socket = null;
    try {
      socket = new DatagramSocket();
      discoverySocket = socket;
      socket.setSoTimeout(5000);
      byte[] buffer = new byte[1024];
      try {
        byte[] mensagem = "DISCOVER_SERVER".getBytes();
        InetAddress broadcastAddress = InetAddress.getByName("255.255.255.255");
        DatagramPacket conection = new DatagramPacket(mensagem, mensagem.length, broadcastAddress, portServer);
        while (attempts < maxAttempts && !cancelDiscovery) {
          socket.send(conection);

          DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
          try {
            socket.receive(packet);
            String response = new String(
                packet.getData(),
                0,
                packet.getLength());
            if (response.equals("SERVER_HERE")) {
              return packet.getAddress();
            }
          } catch (SocketTimeoutException exp) {
            // silencioso: a tela de espera já está visível (sem System.out
            // sob a tela Lanterna, que corromperia o terminal)
          } catch (SocketException exp) {
            // socket fechado pelo cancel: sai sem barulho
            break;
          }
          attempts++;
        }
      } catch (IOException e) {
        if (!cancelDiscovery) {
          NetLog.log("client discovery", e);
        }
      } finally {
        discoverySocket = null;
        if (socket != null) {
          try {
            socket.close();
          } catch (Exception ignored) {
          }
        }
      }
    } catch (SocketException ex) {
      if (!cancelDiscovery) {
        NetLog.log("client discovery socket", ex);
      }
    }
    return null;
  }

  private boolean validatePassword(ObjectOutputStream writer, ObjectInputStream reader, String password)
      throws IOException, ClassNotFoundException {
    writer.writeObject(password);
    writer.flush();
    Object reply = reader.readObject();
    if (!(reply instanceof String)) {
      return false;
    }
    String check = (String) reply;
    String pass = "pass";
    return check != null && check.equals(pass);
  }

  public boolean send(Match match) {
    return sendObject(match);
  }

  /**
   * Envia um objeto. Sincronizado: a thread de heartbeat pode enviar PING
   * enquanto a thread do jogo envia Match (ObjectOutputStream não é thread-safe).
   *
   * @return true se escreveu com sucesso.
   */
  public synchronized boolean sendObject(Object o) {
    if (writer == null) {
      return false;
    }
    try {
      writer.writeObject(o);
      writer.reset();
      writer.flush();
      lastWriteMillis = System.currentTimeMillis();
      return true;
    } catch (IOException e) {
      NetLog.log("client send", e);
      return false;
    }
  }

  public long lastWriteMillis() {
    return lastWriteMillis;
  }

  /**
   * Leitura que distingue timeout (peer vivo, sem dados) de desconexão
   * (EOF/reset: peer saiu ou caiu).
   */
  public NetPoll pollObject() {
    if (reader == null) {
      return NetPoll.timeout();
    }
    try {
      return NetPoll.ok(reader.readObject());
    } catch (java.net.SocketTimeoutException e) {
      return NetPoll.timeout();
    } catch (java.io.EOFException e) {
      return NetPoll.disconnected();
    } catch (java.net.SocketException e) {
      return NetPoll.disconnected();
    } catch (IOException e) {
      NetLog.log("client poll io", e);
      return NetPoll.disconnected();
    } catch (ClassNotFoundException ex) {
      NetLog.log("client poll class", ex);
      return NetPoll.timeout();
    }
  }

  public NetPoll pollObjectShort() {
    SSLSocket s = tcpSocket;
    if (reader == null || s == null || s.isClosed()) {
      return NetPoll.timeout();
    }
    try {
      s.setSoTimeout(20);
    } catch (Exception ignored) {
    }
    try {
      return pollObject();
    } finally {
      try {
        s.setSoTimeout(500);
      } catch (Exception ignored) {
      }
    }
  }

  public Object receiveObject() {
    if (reader == null) {
      return null;
    }
    try {
      return reader.readObject();
    } catch (java.net.SocketTimeoutException e) {
      return null;
    } catch (java.io.EOFException e) {
      return null;
    } catch (IOException e) {
      NetLog.log("client receive", e);
    } catch (ClassNotFoundException ex) {
      NetLog.log("client receive class", ex);
    }
    return null;
  }

  public Match receive() {
    Object o = receiveObject();
    if (o instanceof Match) return (Match) o;
    return null;
  }

  public void close() {
    try {
      if (reader != null)
        reader.close();
    } catch (IOException e) {
    }
    try {
      if (writer != null)
        writer.close();
    } catch (IOException e) {
    }
    reader = null;
    writer = null;
    SSLSocket t = tcpSocket;
    tcpSocket = null;
    if (t != null && !t.isClosed()) {
      try {
        t.close();
      } catch (IOException ignored) {
      }
    }
  }
}
