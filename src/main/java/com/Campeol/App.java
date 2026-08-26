package com.Campeol;

import java.io.IOException;
import java.util.Scanner;

import com.Campeol.client.Client;
import com.Campeol.game.GameUI;
import com.Campeol.server.Server;
import com.Campeol.subgame.Match;
import com.Campeol.subgame.Position;
import com.Campeol.subgame.exception.SubGameException;

public class App {
  public static void main(String[] args) {
    // just test
    Scanner sc = new Scanner(System.in);
    String test = sc.nextLine();
    if (test.charAt(0) == '1') {
      Server.start(12345);
    } else {
      Client.start(12345, sc);
    }
    sc.nextLine();
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
          if (ui.getStatus() == MatchStatus.IN_PROGRESS) {
            ui.makeMove(match, pos);
            match = ui.changeMatch(pos);
          }
        } catch (SubGameException e) {
          try {
            ui.showErro(e.getMessage());
          } catch (InterruptedException x) {
            x.printStackTrace();
          }
        }
      }
      ui.endGame();
    } catch (IOException e) {
      e.printStackTrace();
    } catch (InterruptedException y) {
      y.printStackTrace();
    }
  }

}
