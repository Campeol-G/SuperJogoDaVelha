package com.Campeol.subgame;

public class Position {

  private Integer row;
  private Integer column;

  public Integer getRow() {
    return row;
  }

  public Integer getColumn() {
    return column;
  }

  public Position(Integer row, Integer column) {
    this.row = row;
    this.column = column;
  }

  public void setPosition(Integer row, Integer column) {
    this.row = row;
    this.column = column;
  }

  @Override
  public String toString() {
    return "row=" + row + ", column=" + column;
  }

}
