package com.Campeol.subgame;

import java.io.Serializable;

import com.Campeol.MatchStatus;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;

public class Match implements Serializable {

  private Board board;
  private Player winner;
  private MatchStatus status;
  private int rowPosition;
  private int columnPosition;
  private int gridRow;
  private int gridCol;
  private Position lastMove;

  public Match(int row, int column, int gridRow, int gridCol) {
    board = new Board(3, 3);
    status = MatchStatus.IN_PROGRESS;
    this.rowPosition = row;
    this.columnPosition = column;
    this.gridRow = gridRow;
    this.gridCol = gridCol;
  }

  public void makeMove(Player player, Position position) {
    board.placePiece(player, position);
    this.lastMove = position;
    if (board.testEndGame()) {
      status = MatchStatus.VICTORY;
      winner = player;
    } else if (board.isFull()) {
      status = MatchStatus.DRAW;
    }
  }

  public void render(TextGraphics txt, Player currentPlayer, TextColor highlight) {
    if (status != MatchStatus.IN_PROGRESS) {
      board.clearBoard(txt, rowPosition, columnPosition);
      txt.setBackgroundColor(highlight);
      for (int i = 0; i < 5; i++) {
        txt.putString(columnPosition, rowPosition + i, " ".repeat(11));
      }
      txt.putString(columnPosition + 2, rowPosition + 1, status.toString());
      if (status != MatchStatus.DRAW) {
        txt.putString(columnPosition + 3, rowPosition + 2, "BY:" + winner.getPiece());
      }
      txt.setBackgroundColor(null);
    } else {
      board.render(txt, rowPosition, columnPosition, highlight);
    }
  }

  public MatchStatus getMatchStatus() {
    return status;
  }

  public void setMatchStatus(MatchStatus status) {
    this.status = status;
  }

  public int getIntRowPosition() {
    return rowPosition;
  }

  public int getIntColumnPosition() {
    return columnPosition;
  }

  public int getGridRow() {
    return gridRow;
  }

  public int getGridCol() {
    return gridCol;
  }

  public Player getWinner() {
    return winner;
  }

  public Position getLastMove() {
    return lastMove;
  }

  public void setLastMove(Position lastMove) {
    this.lastMove = lastMove;
  }

}
