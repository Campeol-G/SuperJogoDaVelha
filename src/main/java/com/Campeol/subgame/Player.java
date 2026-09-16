package com.Campeol.subgame;

import java.io.Serializable;

public class Player implements Serializable {

  private static final long serialVersionUID = 1L;

  private Piece piece;

  public Player(Piece piece) {
    this.piece = piece;
  }

  public Piece getPiece() {
    return piece;
  }

  public void setPiece(Piece piece) {
    this.piece = piece;
  }

}
