package com.Campeol.subgame;

import java.io.Serializable;

import com.Campeol.subgame.exception.SubGameException;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;

public class Board implements Serializable {

  private Integer row;
  private Integer column;
  private Piece[][] boardPlace;

  public Board(Integer row, Integer column) {
    this.row = row;
    this.column = column;
    this.boardPlace = new Piece[row][column];
  }

  public Integer getRow() {
    return row;
  }

  public Integer getColumn() {
    return column;
  }

  public void render(TextGraphics txt, int offsetRow, int offsetColumn, TextColor highlight) {
    txt.setBackgroundColor(highlight);
    for (int i = 0; i < row; i++) {
      for (int j = 0; j < column; j++) {
        String sep = (j < column - 1) ? "|" : "";
        Piece piece = boardPlace[i][j];
        String content = piece != null ? piece.toString() : " ";
        txt.putString(j * 4 + offsetColumn, i * 2 + offsetRow, " " + content + " " + sep);
      }
      String sep = (i < row - 1) ? "---" + "+---".repeat(column - 2) + "+---" : "";
      txt.putString(0 + offsetColumn, i * 2 + 1 + offsetRow, sep);
    }
    txt.setBackgroundColor(null);
  }

  public void clearBoard(TextGraphics txt, int offsetRow, int offsetColumn) {
    txt.setBackgroundColor(null);
    for (int i = 0; i < row; i++) {
      for (int j = 0; j < column; j++) {
        txt.putString(j * 4 + offsetColumn, i * 2 + offsetRow, "    ");
      }
      String sep = (i < row - 1) ? "   " + "    ".repeat(column - 2) + "     " : "";
      txt.putString(0 + offsetColumn, i * 2 + 1 + offsetRow, sep);
    }
  }

  public void placePiece(Player player, Position position) {
    if (thereIsAPiece(position)) {
      throw new SubGameException("There's already a piece there");
    }
    boardPlace[position.getRow()][position.getColumn()] = player.getPiece();
  }

  public boolean isFull() {
    for (int i = 0; i < row; i++) {
      for (int j = 0; j < column; j++) {
        if (boardPlace[i][j] == null) {
          return false;
        }
      }
    }
    return true;
  }

  public boolean thereIsAPiece(Position position) {
    if (!positionExist(position)) {
      throw new SubGameException("this is not a possible position");
    }
    return boardPlace[position.getRow()][position.getColumn()] != null;
  }

  public boolean positionExist(Position position) {
    return position.getRow() >= 0 && position.getRow() < row && position.getColumn() >= 0
        && position.getColumn() < column;
  }

  private boolean samePiece(int r1, int c1, int r2, int c2, int r3, int c3) {
    Piece a = boardPlace[r1][c1];
    Piece b = boardPlace[r2][c2];
    Piece c = boardPlace[r3][c3];
    return a != null && a.equals(b) && b.equals(c);
  }

  public boolean checkDiagnoal() {
    return samePiece(0, 0, 1, 1, 2, 2)
        || samePiece(2, 0, 1, 1, 0, 2);
  }

  public boolean checkRows() {
    return samePiece(0, 0, 0, 1, 0, 2)
        || samePiece(1, 0, 1, 1, 1, 2)
        || samePiece(2, 0, 2, 1, 2, 2);
  }

  public boolean checkColumns() {
    return samePiece(0, 0, 1, 0, 2, 0)
        || samePiece(0, 1, 1, 1, 2, 1)
        || samePiece(0, 2, 1, 2, 2, 2);
  }

  public boolean testEndGame() {
    if (checkColumns() || checkRows() || checkDiagnoal()) {
      return true;
    } else {
      return false;
    }
  }
}
