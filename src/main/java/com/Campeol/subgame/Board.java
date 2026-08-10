package com.Campeol.subgame;

import com.Campeol.subgame.exception.subGameException;
import com.googlecode.lanterna.graphics.TextGraphics;

public class Board {

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

  // TODO
  // precisa ser alterado por conta do terminalPosition para poder ser usado com o
  // 3x3 de subgame;
  public void render(TextGraphics txt) {
    for (int i = 0; i < row; i++) {
      for (int j = 0; j < column; j++) {
        String sep = (j < column - 1) ? "|" : " ";
        Piece piece = boardPlace[i][j];
        String content = piece != null ? piece.toString() : " ";
        txt.putString(j * 4, i * 2, " " + content + " " + sep);
      }
      String sep = (i < row - 1) ? "---" + "+---".repeat(column - 1) : "";
      txt.putString(0, i * 2 + 1, sep);
    }
  }

  public Piece getPiece(Position position) {
    if (thereIsAPiece(position)) {
      throw new subGameException("There's already a piece there");
    }
    return boardPlace[position.getRow()][position.getColumn()];
  }

  public void placePiece(Player player, Position position) {
    if (thereIsAPiece(position)) {
      throw new subGameException("There's already a piece there");
    }
    boardPlace[position.getRow()][position.getColumn()] = player.getPiece();
  }

  public boolean thereIsAPiece(Position position) {
    if (!positionExist(position)) {
      throw new subGameException("this is not a possible position");
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
    return a != null && a == b && b == c;
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
}
