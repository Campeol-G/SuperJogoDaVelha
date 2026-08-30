package com.Campeol;

import java.io.IOException;

import com.Campeol.game.GameUI;
import com.Campeol.game.OnlineGame;
import com.Campeol.subgame.Match;
import com.Campeol.subgame.Position;
import com.Campeol.subgame.exception.SubGameException;

public class App {
  public static void main(String[] args) {
    try (GameUI ui = new GameUI()) {
      if (ui.Menu() == 1) {
        OnlineGame og = new OnlineGame();
        if (ui.OnlineMenu() == 1) {
          Match match;
          if (og.createGame()) {
            match = ui.bigMove();
            Position pos = ui.readInput(match);
            ui.makeMove(match, pos);
            match = ui.changeMatch(pos);
            og.getSendServer(match, pos);
          }

        } else {
          Match match;
          if (og.getInTheGame()) {
            match = ui.bigMove();
            Position pos = ui.readInput(match);
            ui.makeMove(match, pos);
            match = ui.changeMatch(pos);
            og.getSendClient(match, pos);
          }
        }
      } else {

        ui.startPlayer(ui.startLocalCustomGame());
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
              ui.changeTurn();
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
      }
    } catch (IOException e) {
      e.printStackTrace();
    } catch (InterruptedException y) {
      y.printStackTrace();
    }
  }

}
