package com.Campeol.subgame;

import java.io.Serializable;

public class Position implements Serializable {

  private Integer row;
  private Integer column;

  public Position(Integer row, Integer column) {
    this.row = row;
    this.column = column;
  }

  public Integer getRow() {
    return row;
  }

  public Integer getColumn() {
    return column;
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
