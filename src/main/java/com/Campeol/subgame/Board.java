package com.Campeol.subgame;

import com.Campeol.subgame.exception.subGameException;

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

  public Piece[][] getBoardPlace() {
    return boardPlace;
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

}
