package com.Campeol.subgame;

import com.Campeol.MatchStatus;
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
    if (board.checkColumns() || board.checkRows() || board.checkDiagnoal()) {
      status = MatchStatus.VICTORY;
      winner = player;
    } else if (board.isFull()) {
      status = MatchStatus.DRAW;
    }
  }

  public void render(TextGraphics txt) {
    board.render(txt, rowPosition, columnPosition);
  }

  public void endGame(TextGraphics txt, Player currentPlayer) {
    if (status == MatchStatus.INTERRUPTED) {
      txt.putString(16, 7, status.toString());
      txt.putString(16, 7 + 1, "BY:" + currentPlayer.getPiece());
    } else {
      txt.putString(columnPosition, rowPosition, status.toString());
      if (status != MatchStatus.DRAW) {
        txt.putString(columnPosition, rowPosition + 1, "BY:" + winner.getPiece());
      }
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