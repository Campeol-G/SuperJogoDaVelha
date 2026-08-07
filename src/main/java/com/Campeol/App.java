package com.Campeol;

import com.Campeol.subgame.Match;
import com.Campeol.subgame.Position;

public class App {
  public static void main(String[] args) {
    // just test
    Match match = new Match();
    match.startPlayer('X');
    System.out.println(match.getP1().getPiece());
    System.out.println(match.getP2().getPiece());
    match.getBoard().placePiece(match.getP1(), new Position(1, 1));
    match.getBoard().placePiece(match.getP2(), new Position(1, 1));
    for (int i = 0; i < match.getBoard().getBoardPlace().length; i++) {
      for (int j = 0; j < match.getBoard().getBoardPlace()[i].length; j++) {
        System.out.print(match.getBoard().getBoardPlace()[i][j]);
      }
      System.out.println();
    }
  }
}
