package com.Campeol.client;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Scanner;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import com.Campeol.net.SslUtil;

/**
 * Client
 */
public class Client {
  private ObjectOutputStream write;
  private ObjectInputStream reader;

  public void start(int portNumber, Scanner sc) {
    try {
      SSLSocketFactory factory = SslUtil.getSocketFactory();
      SSLSocket socket = (SSLSocket) factory.createSocket(findIP(), portNumber);
      write = new ObjectOutputStream(socket.getOutputStream());
      reader = new ObjectInputStream(socket.getInputStream());
      try {
        String password = (String) reader.readObject();
        System.out.println(password);
      } catch (ClassNotFoundException e) {
        e.printStackTrace();
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
}
