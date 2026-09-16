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
    long delta = Math.max(0, now - turnStart);
    if (turnPiece == 'X') accX += delta;
    else if (turnPiece == 'O') accO += delta;
    turnStart = now;
  }

  public void stop() {
    stopTurn();
    turnPiece = null;
  }

  /** Congela sem acumular: usado para não contar o tempo do bot no rankeado. */
  public void pause() {
    turnPiece = null;
    turnStart = System.currentTimeMillis();
  }

  public long currentTurnMillis() {
    if (turnPiece == null) return 0;
    return Math.max(0, System.currentTimeMillis() - turnStart);
  }

  public long totalFor(char piece) {
    long base = (piece == 'X') ? accX : accO;
    if (turnPiece != null && turnPiece == piece) {
      base += Math.max(0, System.currentTimeMillis() - turnStart);
    }
    return Math.max(0, base);
  }

  public long matchMillis() {
    return Math.max(0, System.currentTimeMillis() - matchStart);
  }
}
