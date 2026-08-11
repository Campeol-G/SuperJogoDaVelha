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
        gamePlaces[i][j] = new Match(i * 6, j * 13);
      }
    }
  }

  public void renderAllGames(TextGraphics txt) {
    for (int i = 0; i < gamePlaces.length; i++) {
      for (int j = 0; j < gamePlaces.length; j++) {
        gamePlaces[i][j].render(txt);
      }
    }
  }
}
