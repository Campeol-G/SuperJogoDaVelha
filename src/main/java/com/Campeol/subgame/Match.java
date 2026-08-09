package com.Campeol.subgame;

import com.Campeol.MatchStatus;
import com.Campeol.subgame.exception.subGameException;
import com.googlecode.lanterna.graphics.TextGraphics;

public class Match {

  private Board board;
  private Player p1, p2, currentPlayer;
  private Integer turn;
  private MatchStatus status;

  public Match() {
    board = new Board(3, 3);
    turn = 1;
    status = MatchStatus.IN_PROGRESS;
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
      board.placePiece(p1, position);
    } else {
      board.placePiece(p2, position);
    }
    if (turn == 9) {
      status = MatchStatus.DRAW;
    }
    if (board.checkColumns() || board.checkRows() || board.checkDiagnoal()) {
      status = MatchStatus.VICTORY;
    }
    changeTurn();
  }

  public void render(TextGraphics txt) {
    txt.putString(0, 0, "Vez de " + (turn % 2 != 0 ? p1 : p2).getPiece());
    board.render(txt);
  }

  public void endGame(TextGraphics txt) {
    txt.putString(2, 2, status.toString());
  }

  public MatchStatus getMatchStatus() {
    return status;
  }
  // temporary===============
  // =========================

}
