package com.Campeol.ui;

import java.io.Serializable;

/**
 * Placar agregado da sessão (várias partidas / revanches).
 */
public class SessionScore implements Serializable {
  private static final long serialVersionUID = 1L;

  private int winsX;
  private int winsO;
  private int draws;

  public void registerWin(char piece) {
    if (piece == 'X') winsX++;
    else if (piece == 'O') winsO++;
  }

  public void registerDraw() {
    draws++;
  }

  public int getWinsX() { return winsX; }
  public int getWinsO() { return winsO; }
  public int getDraws() { return draws; }

  public void clear() {
    winsX = 0;
    winsO = 0;
    draws = 0;
  }

  public void restoreFrom(SessionScore other) {
    if (other == null) return;
    this.winsX = other.getWinsX();
    this.winsO = other.getWinsO();
    this.draws = other.getDraws();
  }

  public String summary() {
    return I18n.t("end.score", winsX, winsO, draws);
  }
}
