package com.Campeol.game;

import java.util.Scanner;

import com.Campeol.server.Server;
import com.Campeol.subgame.Match;
import com.Campeol.subgame.Position;

/**
 * OnlineGame
 */
public class OnlineGame extends GameBoard {

  Scanner sc = new Scanner(System.in);

  @Override
  public void makeMove(Match match, Position position) {
    // TODO Auto-generated method stub
    super.makeMove(match, position);
  }

  @Override
  public void startPlayer(char XorO) {
    // TODO Auto-generated method stub
    super.startPlayer(XorO);
  }

  public void createGame() {
    System.out.print("Choose a password: ");
    String password = sc.nextLine();

    new Server().start(8080, password);
  }
}
