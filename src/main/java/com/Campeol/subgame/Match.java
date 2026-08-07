package com.Campeol.subgame;

import com.Campeol.subgame.exception.subGameException;

public class Match {

  private Board board;
  private Player p1, p2, currentPlayer;
  private Integer turn;

  public Match() {
    board = new Board(3, 3);
    turn = 1;
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

  private void changeTurn() {
    currentPlayer = null;
    if (turn % 2 != 0) {
      currentPlayer = p1;
    } else {
      currentPlayer = p2;
    }
    turn++;
  }

  public void makeMove(Position position) {
    if (turn % 2 != 0) {
      getBoard().placePiece(p1, position);
    } else {
      getBoard().placePiece(p2, position);
    }
    changeTurn();
  }

  // temporary===============
  public Player getP1() {
    return p1;
  }

  public Player getP2() {
    return p2;
  }

  public Board getBoard() {
    return board;
  }

  public Player getCurrentPlayer() {
    return currentPlayer;
  }
  // =========================

}
