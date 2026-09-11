package com.Campeol.client;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import com.Campeol.net.NetException;
import com.Campeol.net.NetPoll;
import com.Campeol.net.SslUtil;
import com.Campeol.subgame.Match;

/**
 * Client
 */
public class Client {
  private ObjectOutputStream writer;
  private ObjectInputStream reader;
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
  }

  public Boolean start(int portNumber, String password) {
    try {
      InetAddress serverAddress = findIP();
      if (serverAddress == null) {
        throw new NetException("Servidor nao encontrado");
      }
      SSLSocketFactory factory = SslUtil.getSocketFactory();
      SSLSocket socket = (SSLSocket) factory.createSocket(serverAddress, portNumber);
      socket.setSoTimeout(500);
      writer = new ObjectOutputStream(socket.getOutputStream());
      writer.flush();
      reader = new ObjectInputStream(socket.getInputStream());

      try {
        if (!validatePassword(writer, reader, password)) {
          throw new NetException("Invalid Password");
        }
        Boolean sorteio = (Boolean) reader.readObject();
        clientPiece = (Character) reader.readObject();
        return sorteio;
      } catch (ClassNotFoundException e) {
        throw new RuntimeException(e);
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
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
          e.printStackTrace();
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
        ex.printStackTrace();
      }
    }
    return null;
  }

  private boolean validatePassword(ObjectOutputStream writer, ObjectInputStream reader, String password)
      throws IOException, ClassNotFoundException {
    writer.writeObject(password);
    String check = (String) reader.readObject();
    String pass = "pass";
    if (check != null && check.equals(pass)) {
      return true;
    } else {
      return false;
    }
  }

  public void send(Match match) {
    sendObject(match);
  }

  /**
   * Envia um objeto. Sincronizado: a thread de heartbeat pode enviar PING
   * enquanto a thread do jogo envia Match (ObjectOutputStream não é thread-safe).
   *
   * @return true se escreveu com sucesso.
   */
  public synchronized boolean sendObject(Object o) {
    try {
      writer.writeObject(o);
      writer.reset();
      writer.flush();
      lastWriteMillis = System.currentTimeMillis();
      return true;
    } catch (IOException e) {
      e.printStackTrace();
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
    try {
      return NetPoll.ok(reader.readObject());
    } catch (java.net.SocketTimeoutException e) {
      return NetPoll.timeout();
    } catch (java.io.EOFException e) {
      return NetPoll.disconnected();
    } catch (java.net.SocketException e) {
      return NetPoll.disconnected();
    } catch (IOException e) {
      e.printStackTrace();
      return NetPoll.disconnected();
    } catch (ClassNotFoundException ex) {
      ex.printStackTrace();
      return NetPoll.timeout();
    }
  }

  public Object receiveObject() {
    try {
      return reader.readObject();
    } catch (java.net.SocketTimeoutException e) {
      return null;
    } catch (java.io.EOFException e) {
      return null;
    } catch (IOException e) {
      e.printStackTrace();
    } catch (ClassNotFoundException ex) {
      ex.printStackTrace();
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
  }
}
