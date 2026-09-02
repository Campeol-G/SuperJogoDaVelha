package com.Campeol.game;

import java.util.Random;
import java.util.Scanner;

import com.Campeol.client.Client;
import com.Campeol.server.Server;
import com.Campeol.subgame.Match;

/**
 * OnlineGame
 */
public class OnlineGame {

  private Server server;
  private Client client;
  private final static int portNumber = 8080;
  private final static Scanner sc = new Scanner(System.in);

  public Boolean createGame() {
    System.out.print("Chose a password: ");
    String password = sc.nextLine();

    server = new Server();
    return server.start(portNumber, password);
  }

  public void getSendServer(Match match) {
    server.send(match);
  }

  public Match getReceiveServer() {
    return server.receive();
  }

  public Boolean getInTheGame() {
    System.out.print("Enter the password: ");
    String password = sc.nextLine();
    client = new Client();
    return client.start(portNumber, password);
  }

  public void getSendClient(Match match) {
    client.send(match);
  }

  public Match getReceiveClient() {
    return client.receive();
  }

}
