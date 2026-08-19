package com.Campeol.subgame;

import java.util.Objects;

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

  public String toString() {
    if (XorO == 'O') {
      return "O";
    } else {
      return "X";
    }

  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Piece piece = (Piece) o;
    return XorO == piece.XorO;
  }

  @Override
  public int hashCode() {
    return Objects.hash(XorO);
  }
}
