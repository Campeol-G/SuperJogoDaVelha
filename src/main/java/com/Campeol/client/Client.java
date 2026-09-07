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
import com.Campeol.net.SslUtil;
import com.Campeol.subgame.Match;

/**
 * Client
 */
public class Client {
  private ObjectOutputStream writer;
  private ObjectInputStream reader;
  private char clientPiece;

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
    try (DatagramSocket socket = new DatagramSocket();) {
      socket.setSoTimeout(5000);
      byte[] buffer = new byte[1024];
      try {
        byte[] mensagem = "DISCOVER_SERVER".getBytes();
        InetAddress broadcastAddress = InetAddress.getByName("255.255.255.255");
        DatagramPacket conection = new DatagramPacket(mensagem, mensagem.length, broadcastAddress, portServer);
        while (attempts < maxAttempts) {
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
            System.out.println("Sem resposta do servidor, tentando novamente... (" + (attempts + 1) + "/" + maxAttempts + ")");
          }
          attempts++;
        }
        System.out.println("Servidor nao encontrado apos " + maxAttempts + " tentativas.");
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

  public void send(Match match) {
    try {
      writer.writeObject(match);
      writer.reset();
      writer.flush();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public Match receive() {
    try {
      return (Match) reader.readObject();
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
