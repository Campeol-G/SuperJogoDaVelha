package com.Campeol;

import java.io.IOException;

import com.Campeol.game.GameUI;
import com.Campeol.subgame.Match;
import com.Campeol.subgame.Position;
import com.Campeol.subgame.exception.subGameException;

public class App {
  public static void main(String[] args) {
    // just test
    Match match = new Match(0, 26);
    try (GameUI ui = new GameUI(match)) {
      match.startPlayer(ui.startGame());
      while (match.getMatchStatus() == MatchStatus.IN_PROGRESS) {
        ui.render();
        try {
          Position pos = ui.readInput();
          if (match.getMatchStatus() != MatchStatus.INTERRUPTED) {
            match.makeMove(pos);

          }
        } catch (subGameException e) {
          ui.showErro(e.getMessage());
        }
      }
      ui.endGame();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

}
