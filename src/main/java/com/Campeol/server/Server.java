package com.Campeol.server;

import java.io.IOException;
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
import com.Campeol.net.NetPoll;
import com.Campeol.net.SslUtil;
import com.Campeol.subgame.Match;

public class Server {
  private ObjectOutputStream writer;
  private ObjectInputStream reader;
  private char serverPiece;
  /** Última escrita bem-sucedida (lida pela thread de heartbeat). */
  private volatile long lastWriteMillis = System.currentTimeMillis();

  public Boolean start(int portNumber, String password) {
    try {
      SSLServerSocketFactory factory = SslUtil.getServerSocketFactory();
      SSLServerSocket server = (SSLServerSocket) factory.createServerSocket(portNumber);
      server.setNeedClientAuth(false);
      Thread broadcastThread = new Thread(this::sendUPDPacket);
      broadcastThread.setDaemon(true);
      broadcastThread.start();
      SSLSocket socket = (SSLSocket) server.accept();
      socket.setSoTimeout(500);
      writer = new ObjectOutputStream(socket.getOutputStream());
      writer.flush();
      reader = new ObjectInputStream(socket.getInputStream());

      try {
        Random random = new Random();
        Boolean sorteio = random.nextBoolean();
        if (!validatePassword(writer, reader, password)) {
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
        e.printStackTrace();
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
    return false;
  }

  public char getServerPiece() {
    return serverPiece;
  }

  public void sendUPDPacket() {
    int portServer = 5000;
    try (DatagramSocket socket = new DatagramSocket(portServer);) {
      byte[] buffer = new byte[1024];
      try {
        long endTime = System.currentTimeMillis() + 60000;
        while (System.currentTimeMillis() < endTime) {
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
        e.printStackTrace();
      }
    } catch (SocketException e) {
      e.printStackTrace();
    }
  }

  public boolean validatePassword(ObjectOutputStream writer, ObjectInputStream reader, String password)
      throws IOException, ClassNotFoundException {
    String check = (String) reader.readObject();
    if (check.equals(password)) {
      writer.writeObject("pass");
      return true;
    } else {
      writer.writeObject("fail");
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
    // se chegou NetMessage fora de hora, ignora e retorna null (caller faz poll de novo)
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
