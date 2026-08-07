package com.Campeol.subgame;

public class Board {

  private Integer row;
  private Integer column;
  private Piece[][] boardPlace;

  public Board(Integer row, Integer column) {
    this.row = null;
    this.column = null;
    this.boardPlace = new Piece[row][column];
  }

  public Integer getRow() {
    return row;
  }

  public Integer getColumn() {
    return column;
  }

  public Piece[][] getBoardPlace() {
    return boardPlace;
  }

}
