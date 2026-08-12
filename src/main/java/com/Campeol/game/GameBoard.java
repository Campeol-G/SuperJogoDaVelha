package com.Campeol.game;

import com.Campeol.subgame.Match;
import com.googlecode.lanterna.graphics.TextGraphics;

public class GameBoard {
  Match[][] gamePlaces;

  public GameBoard() {
    startAllGames();
  }

  public void startAllGames() {
    gamePlaces = new Match[3][3];
    for (int i = 0; i < gamePlaces.length; i++) {
      for (int j = 0; j < gamePlaces[i].length; j++) {
        gamePlaces[i][j] = new Match(i * 6 + 1, j * 14 + 2);

      }
    }
  }

  // TODO refector the hardcoded position;
  public void divisors(TextGraphics txt) {
    // columns
    for (int i = 0; i < 19; i++) {
      txt.putString(14, i, "║");
      txt.putString(28, i, "║");
    }

    // rows
    for (int i = 0; i < 43; i++) {
      txt.putString(i, 6, "═");
      txt.putString(i, 12, "═");
    }

    // connectors
    txt.putString(14, 6, "╬");
    txt.putString(28, 6, "╬");
    txt.putString(14, 12, "╬");
    txt.putString(28, 12, "╬");

    // external contour
    // columns
    for (int i = 0; i < 19; i++) {
      txt.putString(0, i, "║");
      txt.putString(42, i, "║");
    }

    // rows
    for (int i = 0; i < 43; i++) {
      txt.putString(i, 18, "═");
      txt.putString(i, 0, "═");
      txt.putString(12, 0, " SUPER TIC-TAC-TOE ");
    }

    // connectors
    txt.putString(0, 0, "╔");
    txt.putString(42, 0, "╗");
    txt.putString(0, 12, "╠");
    txt.putString(42, 12, "╣");
    txt.putString(0, 6, "╠");
    txt.putString(42, 6, "╣");
    txt.putString(0, 18, "╚");
    txt.putString(42, 18, "╝");
    txt.putString(14, 18, "╩");
    txt.putString(28, 18, "╩");

  }

  public void renderAllGames(TextGraphics txt) {
    for (int i = 0; i < gamePlaces.length; i++) {
      for (int j = 0; j < gamePlaces.length; j++) {
        gamePlaces[i][j].render(txt);
      }
    }
    divisors(txt);

  }
}
