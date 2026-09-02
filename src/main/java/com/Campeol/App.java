package com.Campeol;

import java.io.IOException;
import java.util.Scanner;

import com.Campeol.game.GameUI;
import com.Campeol.game.OnlineGame;
import com.Campeol.net.NetException;
import com.Campeol.subgame.Match;
import com.Campeol.subgame.Position;
import com.Campeol.subgame.exception.SubGameException;

public class App {
  public static void main(String[] args) {
    Scanner sc = new Scanner(System.in);
    try (GameUI ui = new GameUI()) {
      if (ui.Menu(sc) == 1) { // MULTIPLAYER
        OnlineGame og = new OnlineGame();
        ui.startPlayer();
        if (ui.OnlineMenu(sc) == 1) { // CREATE GAME
          Match match;
          if (og.createGame()) { // firstMove
            match = ui.bigMove();
            Position pos = ui.readInput(match);
            ui.makeMove(match, pos);
            og.getSendServer(match);
            match = ui.changeMatch(pos);
          } // game flow
          while (ui.getStatus() == MatchStatus.IN_PROGRESS) {
            ui.render();
            match = og.getReceiveServer();
            ui.receiveOpponentMove(match);
            match = ui.changeMatch(match.getLastMove());
            if (match == null || ui.getStatus() != MatchStatus.IN_PROGRESS)
              break;
            try {
              Position pos = ui.readInput(match);
              if (ui.getStatus() == MatchStatus.IN_PROGRESS) {
                ui.makeMove(match, pos);
                og.getSendServer(match);
                match = ui.changeMatch(pos);
              }
              ui.render();
            } catch (SubGameException e) {
              try {
                ui.showErro(e.getMessage());
              } catch (InterruptedException x) {
                x.printStackTrace();
              }
            } catch (NetException ex) {
              try {
                ui.showErro(ex.getMessage());
              } catch (InterruptedException y) {
                y.printStackTrace();
              }
            }
          }
          ui.endGame();
        } else { // GET IN THE GAME
          Match match;
          if (!og.getInTheGame()) { // firstMove
            match = ui.bigMove();
            Position pos = ui.readInput(match);
            ui.makeMove(match, pos);
            og.getSendClient(match);
            match = ui.changeMatch(pos);
          } // game flow
          while (ui.getStatus() == MatchStatus.IN_PROGRESS) {
            ui.render();

            match = og.getReceiveClient();
            ui.receiveOpponentMove(match);
            match = ui.changeMatch(match.getLastMove());
            if (match == null || ui.getStatus() != MatchStatus.IN_PROGRESS)
              break;
            try {
              Position pos = ui.readInput(match);
              if (ui.getStatus() == MatchStatus.IN_PROGRESS) {
                ui.makeMove(match, pos);
                og.getSendClient(match);
                match = ui.changeMatch(pos);
              }
              ui.render();
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
