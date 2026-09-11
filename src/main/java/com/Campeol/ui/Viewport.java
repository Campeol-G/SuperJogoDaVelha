package com.Campeol.ui;

/**
 * Calcula a origem para centralizar o jogo no terminal.
 * O tabuleiro lógico tem BOARD_W x BOARD_H. O HUD fica à direita.
 */
public final class Viewport {
  public static final int BOARD_W = 43; // 0..42
  public static final int BOARD_H = 19; // 0..18
  public static final int HUD_W = 26;
  public static final int GAP = 2;
  public static final int TOTAL_W = BOARD_W + GAP + HUD_W; // 71
  public static final int TOTAL_H = 21; // board (19) + 1 linha de mensagem

  private final int originX;
  private final int originY;

  public Viewport(int originX, int originY) {
    this.originX = Math.max(0, originX);
    this.originY = Math.max(0, originY);
  }

  public static Viewport centered(int termCols, int termRows) {
    int ox = (termCols - TOTAL_W) / 2;
    int oy = (termRows - TOTAL_H) / 2;
    if (ox < 0) ox = 0;
    if (oy < 0) oy = 0;
    // reserva 1 linha/col de margem quando dá
    if (ox > 0) ox = Math.max(0, ox);
    return new Viewport(ox, oy);
  }

  public int ox(int logicalX) {
    return originX + logicalX;
  }

  public int oy(int logicalY) {
    return originY + logicalY;
  }

  public int getOriginX() {
    return originX;
  }

  public int getOriginY() {
    return originY;
  }

  public int hudX() {
    return originX + BOARD_W + GAP;
  }

  public int messageY() {
    return originY + BOARD_H + 1;
  }

  public int footerY() {
    return originY + BOARD_H + 2;
  }
}
