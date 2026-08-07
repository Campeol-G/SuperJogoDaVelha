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

  public Piece(char xorO, Position position) {
    XorO = xorO;
    this.position = null;
  }
}
