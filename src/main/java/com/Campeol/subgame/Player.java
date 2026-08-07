package com.Campeol.subgame;

public class Player {

  private Piece piece;

  public Player(Piece piece, char XorO) {
    this.piece = piece;
    piece.setChar(XorO);
  }

  public Piece getPiece() {
    return piece;
  }

  public void setPiece(Piece piece) {
    this.piece = piece;
  }

}
