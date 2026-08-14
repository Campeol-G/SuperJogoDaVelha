package com.Campeol.subgame;

import com.Campeol.MatchStatus;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;

public class Match {

  private Board board;
  private Player winner;
  private MatchStatus status;
  private int rowPosition;
  private int columnPosition;

  public Match(int row, int column) {
    board = new Board(3, 3);
    status = MatchStatus.IN_PROGRESS;
    this.rowPosition = row;
    this.columnPosition = column;
  }

  public void makeMove(Player player, Position position) {
    board.placePiece(player, position);
    if (board.testEndGame()) {
      status = MatchStatus.VICTORY;
      winner = player;
    } else if (board.isFull()) {
      status = MatchStatus.DRAW;
    }
  }

  public void render(TextGraphics txt, Player currentPlayer, TextColor highlight) {
    if (status == MatchStatus.INTERRUPTED) {
      txt.setBackgroundColor(highlight);
      txt.putString(16, 7, status.toString());
      txt.putString(17, 7 + 1, "BY:" + currentPlayer.getPiece());
      txt.setBackgroundColor(null);
    } else if (status != MatchStatus.IN_PROGRESS) {
      board.clearBoard(txt, rowPosition, columnPosition);
      txt.setBackgroundColor(highlight);
      for (int i = 0; i < 5; i++) {
        txt.putString(columnPosition, rowPosition + i, " ".repeat(12));
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

}
