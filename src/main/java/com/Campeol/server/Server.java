package com.Campeol.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;

public class Server {

  public static void start(int portNumber) {
    try (var serverSocket = new ServerSocket(portNumber)) {
      var clientSocket = serverSocket.accept();
      System.out.println("Connected");
      try (var clientInput = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
          var writer = new PrintWriter(clientSocket.getOutputStream(), true)) {
        for (String inputLine; (inputLine = clientInput.readLine()) != null;) {
          System.out.println(
              clientSocket.getPort() + " | " + clientSocket.getInetAddress().getHostAddress() + " | " + inputLine);
          writer.println(new StringBuilder(inputLine).reverse());
        }
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
