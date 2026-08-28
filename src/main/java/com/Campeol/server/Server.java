package com.Campeol.server;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;

import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;

import com.Campeol.net.SslUtil;

public class Server {

  public void start(int portNumber, String password) {
    try {
      SSLServerSocketFactory factory = SslUtil.getServerSocketFactory();
      SSLServerSocket server = (SSLServerSocket) factory.createServerSocket(portNumber);
      server.setNeedClientAuth(false);
      sendUPDPacket();
      SSLSocket socket = (SSLSocket) server.accept();
      System.out.println("Connected");
      try (var writer = new ObjectOutputStream(socket.getOutputStream())) {
        writer.writeObject(password);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public void sendUPDPacket() {
    int portServer = 5000;
    try (DatagramSocket socket = new DatagramSocket(5000);) {
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

}
