package com.Campeol.subgame;

import com.Campeol.MatchStatus;
import com.Campeol.subgame.exception.subGameException;
import com.googlecode.lanterna.graphics.TextGraphics;

public class Match {

  private Board board;
  private Player p1, p2, currentPlayer;
  private Integer turn;
  private MatchStatus status;
  private int rowPosition;
  private int columnPosition;

  public Match(int row, int column) {
    board = new Board(3, 3);
    turn = 1;
    status = MatchStatus.IN_PROGRESS;
    this.rowPosition = row;
    this.columnPosition = column;
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
    currentPlayer = p1;
  }

  private void changeTurn() {
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
    board.render(txt, rowPosition, columnPosition);
  }

  public void endGame(TextGraphics txt) {
    txt.putString(2, 2, status.toString());
    if (status != MatchStatus.DRAW) {
      txt.putString(2, 3, "BY:" + currentPlayer.getPiece());
    }
  }

  public MatchStatus getMatchStatus() {
    return status;
  }

  // temporary===============
  public int getTurn() {
    return turn;
  }

  public Player getCurrentPlayer() {
    return currentPlayer;
  }

  public void setMatchStatus(MatchStatus status) {
    this.status = status;
  }
  // =========================

}
