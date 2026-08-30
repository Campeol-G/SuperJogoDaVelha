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

import com.Campeol.net.Pack;
import com.Campeol.net.SslUtil;
import com.Campeol.subgame.Match;
import com.Campeol.subgame.Position;

/**
 * Client
 */
public class Client {
  private ObjectOutputStream writer;
  private ObjectInputStream reader;

  public Boolean start(int portNumber, String password) {
    try {
      SSLSocketFactory factory = SslUtil.getSocketFactory();
      SSLSocket socket = (SSLSocket) factory.createSocket(findIP(), portNumber);
      writer = new ObjectOutputStream(socket.getOutputStream());
      reader = new ObjectInputStream(socket.getInputStream());
      try {
        if (validatePassword(writer, reader, password)) {
          System.out.println("true");
        }
        return (Boolean) reader.readObject();
      } catch (ClassNotFoundException e) {
        throw new RuntimeException(e);
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private InetAddress findIP() {
    int portServer = 5000;
    try (DatagramSocket socket = new DatagramSocket();) {
      socket.setSoTimeout(5000);
      byte[] buffer = new byte[1024];
      try {
        byte[] mensagem = "DISCOVER_SERVER".getBytes();
        InetAddress broadcastAddress = InetAddress.getByName("255.255.255.255");
        DatagramPacket conection = new DatagramPacket(mensagem, mensagem.length, broadcastAddress, portServer);
        while (true) {
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
            System.out.println("Sem resposta do servidor, tentando novamente...");
          }
        }
      } catch (IOException e) {
        e.printStackTrace();
      }
    } catch (SocketException ex) {
      ex.printStackTrace();
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

  public void send(Match match, Position pos) {
    try {
      writer.writeObject(match);
      writer.writeObject(pos);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public Pack receive() {
    try {
      Match match = (Match) reader.readObject();
      Position pos = (Position) reader.readObject();
      Pack pack = new Pack(match, pos);
      return pack;
    } catch (IOException e) {
      e.printStackTrace();
    } catch (ClassNotFoundException ex) {
      ex.printStackTrace();
    }
    return null;
  }
}
