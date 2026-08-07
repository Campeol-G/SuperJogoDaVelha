package com.Campeol.subgame;

public class Piece {

  private Position position;
  private char XorO;

  public Position getPosition() {
    return position;
  }

  public char getXorO() {
    return XorO;
  }

  public Piece(char xorO) {
    XorO = xorO;
    this.position = null;
  }

  public void setChar(char XorO) {
    this.XorO = XorO;
  }
}
