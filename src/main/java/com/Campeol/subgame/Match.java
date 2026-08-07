package com.Campeol.subgame;

import com.Campeol.subgame.exception.subGameException;

public class Match {

  private Board board;
  private Player p1, p2;

  public Match() {
    board = new Board(3, 3);
  }

  public void startPlayer(char XorO) {
    if (XorO != 'O' && XorO != 'X') {
      throw new subGameException("Invalid piece choice");
    }
    if (XorO == 'X') {
      p1 = new Player(new Piece(XorO));
      p2 = new Player(new Piece('O'));
    } else if (XorO == 'O') {
      p1 = new Player(new Piece(XorO));
      p2 = new Player(new Piece('X'));
    }
  }

  // temporary
  public Player getP1() {
    return p1;
  }

  public Player getP2() {
    return p2;
  }

  public Board getBoard() {
    return board;
  }

}
