package com.Campeol.game;

import java.util.Random;
import java.util.Scanner;

import com.Campeol.client.Client;
import com.Campeol.net.Pack;
import com.Campeol.server.Server;
import com.Campeol.subgame.Match;
import com.Campeol.subgame.Piece;
import com.Campeol.subgame.Player;
import com.Campeol.subgame.Position;
import com.googlecode.lanterna.TerminalPosition;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.screen.Screen;

/**
 * OnlineGame
 */
public class OnlineGame extends GameBoard {

  private Server server;
  private Client client;
  private final static int portNumber = 8080;
  private final static Scanner sc = new Scanner(System.in);
  private final static Random random = new Random();

  public void startPlayer() {
    boolean sorteio = random.nextBoolean();

    if (sorteio) {
      p1 = new Player(new Piece('X'));
      p2 = new Player(new Piece('O'));
    } else {
      p1 = new Player(new Piece('O'));
      p2 = new Player(new Piece('X'));
    }
  }

  public Boolean createGame() {
    System.out.print("Chose a password: ");
    String password = sc.nextLine();

    server = new Server();
    startPlayer();
    return server.start(portNumber, password);
  }

  public void getSendServer(Match match, Position pos) {
    server.send(match, pos);
  }

  public Pack getReceiveServer() {
    return server.receive();
  }

  public Boolean getInTheGame() {
    System.out.print("Enter the password: ");
    String password = sc.nextLine();
    client = new Client();
    return client.start(portNumber, password);
  }

  public void getSendClient(Match match, Position pos) {
    client.send(match, pos);
  }

  public Pack getReceiveClient() {
    return client.receive();
  }

}
