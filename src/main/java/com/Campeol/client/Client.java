package com.Campeol.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;

/**
 * Client
 */
public class Client {
  public static void start(int portNumber, Scanner sc) {
    try (var socket = new Socket("localhost", portNumber);
        var writer = new PrintWriter(socket.getOutputStream(), true);
        var reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
      String test = sc.nextLine();
      writer.println(test);
      System.out.println(reader.readLine());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
