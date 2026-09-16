package com.Campeol.subgame;

import java.io.Serializable;
import java.util.Objects;

public class Piece implements Serializable {

  private static final long serialVersionUID = 1L;

  private Position position;
  private char XorO;

  public Position getPosition() {
    return position;
  }

  public char getXorO() {
    return XorO;
  }

  public Piece(char xorO) {
    if (xorO != 'X' && xorO != 'O') {
      throw new IllegalArgumentException("Piece must be X or O");
    }
    XorO = xorO;
    this.position = null;
  }

  public void setChar(char XorO) {
    if (XorO != 'X' && XorO != 'O') {
      throw new IllegalArgumentException("Piece must be X or O");
    }
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
