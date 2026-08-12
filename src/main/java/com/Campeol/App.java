package com.Campeol;

import java.io.IOException;
import java.lang.InterruptedException;

import com.Campeol.game.GameUI;
import com.Campeol.subgame.Match;
import com.Campeol.subgame.Position;
import com.Campeol.subgame.exception.subGameException;

public class App {
  public static void main(String[] args) {
    // just test
    try (GameUI ui = new GameUI()) {
      Match match = ui.getMatch(1, 1);
      match.startPlayer(ui.startGame());
      while (match.getMatchStatus() == MatchStatus.IN_PROGRESS) {
        ui.render();
        try {
          Position pos = ui.readInput(match);
          if (match.getMatchStatus() != MatchStatus.INTERRUPTED) {
            match.makeMove(pos);

          }
        } catch (subGameException e) {
          try {
            ui.showErro(e.getMessage());
          } catch (InterruptedException x) {
            x.printStackTrace();
          }
        }
      }
      try {
        ui.endGame(match);
      } catch (InterruptedException x) {
        x.printStackTrace();
      }

    } catch (IOException e) {
      e.printStackTrace();
    }
  }

}
