package com.Campeol.ui;

/**
 * Relógio estilo xadrez: acumula tempo de cada peça.
 * Apenas informativo (não limita a partida).
 */
public class GameClock {
  private long accX;
  private long accO;
  private long matchStart = System.currentTimeMillis();
  private long turnStart = matchStart;
  private Character turnPiece; // 'X' / 'O' / null

  public void startMatch() {
    accX = 0;
    accO = 0;
    matchStart = System.currentTimeMillis();
    turnStart = matchStart;
    turnPiece = null;
  }

  public void startTurn(char piece) {
    stopTurn();
    turnPiece = piece;
    turnStart = System.currentTimeMillis();
  }

  public void stopTurn() {
    if (turnPiece == null) {
      turnStart = System.currentTimeMillis();
      return;
    }
    long now = System.currentTimeMillis();
    long delta = now - turnStart;
    if (turnPiece == 'X') accX += delta;
    else if (turnPiece == 'O') accO += delta;
    turnStart = now;
  }

  public void stop() {
    stopTurn();
    turnPiece = null;
  }

  public long currentTurnMillis() {
    if (turnPiece == null) return 0;
    return System.currentTimeMillis() - turnStart;
  }

  public long totalFor(char piece) {
    long base = (piece == 'X') ? accX : accO;
    if (turnPiece != null && turnPiece == piece) {
      base += System.currentTimeMillis() - turnStart;
    }
    return base;
  }

  public long matchMillis() {
    return System.currentTimeMillis() - matchStart;
  }
}
