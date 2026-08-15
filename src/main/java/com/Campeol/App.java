package com.Campeol;

import java.io.IOException;

import com.Campeol.game.GameUI;
import com.Campeol.game.exception.GameException;
import com.Campeol.subgame.Match;
import com.Campeol.subgame.Position;
import com.Campeol.subgame.exception.subGameException;

public class App {
  public static void main(String[] args) {
    // just test
    try (GameUI ui = new GameUI()) {
      ui.startPlayer(ui.startGame());
      if (ui.getStatus() != MatchStatus.IN_PROGRESS) {
        return;
      }
      Match match = ui.bigMove();
      while (ui.getStatus() == MatchStatus.IN_PROGRESS) {
        ui.render();
        try {
          Position pos = ui.readInput(match);
          if (match.getMatchStatus() != MatchStatus.INTERRUPTED) {
            ui.makeMove(match, pos);
            match = ui.changeMatch(pos);
          }
        } catch (subGameException e) {
          try {
            ui.showErro(e.getMessage());
          } catch (InterruptedException x) {
            x.printStackTrace();
          }
        } catch (GameException y) {
          try {
            ui.showErro(y.getMessage());
          } catch (InterruptedException x) {
            x.printStackTrace();
          }
        }

      }
    } catch (IOException e) {
      e.printStackTrace();
    } catch (InterruptedException y) {
      y.printStackTrace();
    }
  }

}
