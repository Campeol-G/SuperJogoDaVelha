package com.Campeol.server;

import java.io.IOException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.Random;

import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;

import com.Campeol.net.NetException;
import com.Campeol.net.NetLog;
import com.Campeol.net.NetPoll;
import com.Campeol.net.SslUtil;
import com.Campeol.subgame.Match;

public class Server {
  private ObjectOutputStream writer;
  private ObjectInputStream reader;
  private volatile SSLSocket acceptedSocket = null;
  private volatile DatagramSocket udpSocket = null;
  private char serverPiece;
  /** Última escrita bem-sucedida (lida pela thread de heartbeat). */
  private volatile long lastWriteMillis = System.currentTimeMillis();
  /** Socket de escuta (guardado para o cancel poder fechar e soltar o accept). */
  private volatile SSLServerSocket listenSocket = null;
  /** Cancelamento pedido pela UI (Esc na espera). Silencia o stacktrace. */
  private volatile boolean cancelled = false;
  /** Anúncio UDP de descoberta ligado? (desliga no cancel/close). */
  private volatile boolean broadcasting = true;

  /**
   * Cancela uma espera em andamento: solta o accept() bloqueado e para
   * de anunciar este host (evita join num fantasma). Idempotente.
   */
  public void cancel() {
    cancelled = true;
    broadcasting = false;
    closeListener();
    closeUdp();
    SSLSocket s = acceptedSocket;
    if (s != null) {
      try {
        s.close();
      } catch (IOException ignored) {
      }
    }
  }

  private void closeUdp() {
    DatagramSocket s = udpSocket;
    udpSocket = null;
    if (s != null && !s.isClosed()) {
      try {
        s.close();
      } catch (Exception ignored) {
      }
    }
  }

  private void closeListener() {
    SSLServerSocket s = listenSocket;
    listenSocket = null;
    if (s != null) {
      try {
        s.close();
      } catch (IOException ignored) {
      }
    }
  }

  public Boolean start(int portNumber, String password) {
    // Sem reset de cancelled aqui: connectAsync sempre usa instância nova
    // (flag nasce false); resetar abriria corrida com um cancel anterior.
    broadcasting = true;
    try {
      SSLServerSocketFactory factory = SslUtil.getServerSocketFactory();
      SSLServerSocket server = (SSLServerSocket) factory.createServerSocket(portNumber);
      listenSocket = server;
      if (cancelled) {
        // cancel chegou antes do accept: não bloqueia
        closeListener();
        return false;
      }
      server.setNeedClientAuth(false);
      Thread broadcastThread = new Thread(this::sendUPDPacket);
      broadcastThread.setDaemon(true);
      broadcastThread.start();
      SSLSocket socket;
      try {
        socket = (SSLSocket) server.accept();
        acceptedSocket = socket;
      } finally {
        // só um cliente por partida: fecha a escuta assim que aceitar
        closeListener();
      }
      socket.setSoTimeout(500);
      writer = new ObjectOutputStream(socket.getOutputStream());
      writer.flush();
      reader = new ObjectInputStream(socket.getInputStream());
      reader.setObjectInputFilter(buildFilter());

      try {
        Random random = new Random();
        Boolean sorteio = random.nextBoolean();
        if (!validatePassword(writer, reader, password)) {
          try {
            writer.flush();
          } catch (Exception ignored) {
          }
          close();
          throw new NetException("Wrong password");
        }
        if (sorteio) {
          serverPiece = 'X';
          writer.writeObject(sorteio);
          writer.writeObject('X');
          return true;
        } else {
          serverPiece = 'O';
          writer.writeObject(sorteio);
          writer.writeObject('O');
          return false;
        }
      } catch (ClassNotFoundException e) {
        throw new NetException("Handshake falhou: " + e.getMessage());
      }
    } catch (IOException e) {
      if (cancelled) {
        return false;
      }
      throw new NetException(e.getMessage() != null ? e.getMessage() : e.toString());
    }
  }

  public char getServerPiece() {
    return serverPiece;
  }

  /** Permite só classes do protocolo; bloqueia gadget-chain via desserialização. */
  private static ObjectInputFilter buildFilter() {
    return info -> {
      Class<?> c = info.serialClass();
      if (c == null) {
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

  public void stopBroadcast() {
    broadcasting = false;
  }

  public void sendUPDPacket() {
    int portServer = 5000;
    DatagramSocket socket;
    try {
      socket = new DatagramSocket(portServer);
      udpSocket = socket;
    } catch (SocketException e) {
      NetLog.log("UDP bind 5000 falhou", e);
      return;
    }
    try {
      byte[] buffer = new byte[1024];
      try {
        long endTime = System.currentTimeMillis() + 60000;
        while (broadcasting && System.currentTimeMillis() < endTime) {
          DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
          socket.setSoTimeout(1000);
          try {
            socket.receive(packet);
            String mensagem = new String(
                packet.getData(),
                0,
                packet.getLength());
            if (mensagem.equals("DISCOVER_SERVER")) {
              byte[] resposta = "SERVER_HERE".getBytes();
              DatagramPacket response = new DatagramPacket(
                  resposta,
                  resposta.length,
                  packet.getAddress(),
                  packet.getPort());
              socket.send(response);
            }
          } catch (java.net.SocketTimeoutException timeout) {
            // continua tentando ate o tempo acabar
          }
        }
      } catch (IOException e) {
        if (broadcasting) {
          NetLog.log("UDP broadcast", e);
        }
      } finally {
        closeUdp();
      }
    } catch (Exception e) {
      NetLog.log("UDP loop", e);
      closeUdp();
    }
  }

  public boolean validatePassword(ObjectOutputStream writer, ObjectInputStream reader, String password)
      throws IOException, ClassNotFoundException {
    Object got = reader.readObject();
    if (!(got instanceof String)) {
      try {
        writer.writeObject("fail");
        writer.flush();
      } catch (Exception ignored) {
      }
      return false;
    }
    String check = (String) got;
    if (password != null && password.equals(check)) {
      writer.writeObject("pass");
      writer.flush();
      return true;
    } else {
      writer.writeObject("fail");
      writer.flush();
      return false;
    }
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
      NetLog.log("server send", e);
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
      NetLog.log("server poll io", e);
      return NetPoll.disconnected();
    } catch (ClassNotFoundException ex) {
      NetLog.log("server poll class", ex);
      return NetPoll.timeout();
    }
  }

  /**
   * Poll curto (20ms) para checar QUIT durante input local sem travar a UI.
   * Match chegado aqui deve ir para stash no chamador.
   */
  public NetPoll pollObjectShort() {
    SSLSocket s = acceptedSocket;
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
      NetLog.log("server receive", e);
    } catch (ClassNotFoundException ex) {
      NetLog.log("server receive class", ex);
    }
    return null;
  }

  public Match receive() {
    Object o = receiveObject();
    if (o instanceof Match) return (Match) o;
    // se chegou NetMessage fora de hora, ignora e retorna null (caller faz poll de novo)
    return null;
  }

  public void close() {
    stopBroadcast();
    closeListener();
    closeUdp();
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
    SSLSocket s = acceptedSocket;
    acceptedSocket = null;
    if (s != null && !s.isClosed()) {
      try {
        s.close();
      } catch (IOException ignored) {
      }
    }
  }

}
