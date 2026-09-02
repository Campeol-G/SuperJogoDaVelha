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
import com.Campeol.net.SslUtil;
import com.Campeol.subgame.Match;

public class Server {
  private ObjectOutputStream writer;
  private ObjectInputStream reader;

  public Boolean start(int portNumber, String password) {
    try {
      SSLServerSocketFactory factory = SslUtil.getServerSocketFactory();
      SSLServerSocket server = (SSLServerSocket) factory.createServerSocket(portNumber);
      server.setNeedClientAuth(false);
      sendUPDPacket();
      SSLSocket socket = (SSLSocket) server.accept();
      writer = new ObjectOutputStream(socket.getOutputStream());
      reader = new ObjectInputStream(socket.getInputStream());

      try {
        Random random = new Random();
        Boolean sorteio = random.nextBoolean();
        if (!validatePassword(writer, reader, password)) {
          throw new NetException("Wrong password");
        }
        if (sorteio) {
          writer.writeObject(sorteio);
          return true;
        } else {
          writer.writeObject(sorteio);
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

  public void sendUPDPacket() {
    int portServer = 5000;
    try (DatagramSocket socket = new DatagramSocket(portServer);) {
      byte[] buffer = new byte[1024];
      try {
        while (true) {
          DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

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
            break;
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
    try {
      writer.writeObject(match);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public Match receive() {
    try {
      return (Match) reader.readObject();
    } catch (IOException e) {
      e.printStackTrace();
    } catch (ClassNotFoundException ex) {
      ex.printStackTrace();
    }
    return null;
  }

}
