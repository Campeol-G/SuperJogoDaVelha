package com.Campeol;

import java.io.IOException;
import java.util.Scanner;

import com.Campeol.game.GameUI;
import com.Campeol.game.OnlineGame;
import com.Campeol.net.NetException;
import com.Campeol.subgame.Match;
import com.Campeol.subgame.Position;
import com.Campeol.subgame.exception.SubGameException;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;

public class App {
  public static void main(String[] args) {
    Scanner sc = new Scanner(System.in);
    try (GameUI ui = new GameUI()) {
      if (ui.Menu(sc) == 1) { // MULTIPLAYER
        OnlineGame og = new OnlineGame();
        if (ui.OnlineMenu(sc) == 1) { // CREATE GAME
          Match match;
          Boolean firstMove = og.createGame();
          ui.startPlayer(og.getServerPiece());
          ui.render();
          if (firstMove) { // firstMove
            match = ui.bigMove();
            if (ui.getStatus() != MatchStatus.IN_PROGRESS) {
              og.close();
              ui.endGame();
              System.exit(0);
            }
            Position pos = ui.readInput(match);
            if (ui.getStatus() != MatchStatus.IN_PROGRESS) {
              og.close();
              ui.endGame();
              System.exit(0);
            }
            ui.makeMove(match, pos);
            ui.changeTurn();
            og.getSendServer(match);
            ui.render();
          } // game flow
          while (ui.getStatus() == MatchStatus.IN_PROGRESS) {
            match = receiveWithEscCheck(og::getReceiveServer, ui, og);
            if (match == null) {
              og.close();
              break;
            }
            ui.receiveOpponentMove(match);
            match = ui.changeMatch(match.getLastMove());
            if (match == null || ui.getStatus() != MatchStatus.IN_PROGRESS) {
              if (match != null) {
                og.getSendServer(match);
              }
              og.close();
              break;
            }
            ui.render();
            try {
              Position pos = ui.readInput(match);
              if (ui.getStatus() != MatchStatus.IN_PROGRESS) {
                og.close();
                break;
              }
              if (ui.getStatus() == MatchStatus.IN_PROGRESS) {
                ui.makeMove(match, pos);
                ui.changeTurn();
                og.getSendServer(match);
                ui.render();
              }
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
          System.exit(0);
        } else { // GET IN THE GAME
          Match match;
          Boolean firstMove = og.getInTheGame();
          ui.startPlayer(og.getClientPiece());
          ui.render();
          if (!firstMove) { // firstMove
            match = ui.bigMove();
            if (ui.getStatus() != MatchStatus.IN_PROGRESS) {
              og.close();
              ui.endGame();
              System.exit(0);
            }
            Position pos = ui.readInput(match);
            if (ui.getStatus() != MatchStatus.IN_PROGRESS) {
              og.close();
              ui.endGame();
              System.exit(0);
            }
            ui.makeMove(match, pos);
            ui.changeTurn();
            og.getSendClient(match);
            ui.render();
          } // game flow
          while (ui.getStatus() == MatchStatus.IN_PROGRESS) {
            match = receiveWithEscCheck(og::getReceiveClient, ui, og);
            if (match == null) {
              og.close();
              break;
            }
            ui.receiveOpponentMove(match);
            match = ui.changeMatch(match.getLastMove());
            if (match == null || ui.getStatus() != MatchStatus.IN_PROGRESS) {
              if (match != null) {
                og.getSendClient(match);
              }
              og.close();
              break;
            }
            ui.render();
            try {
              Position pos = ui.readInput(match);
              if (ui.getStatus() != MatchStatus.IN_PROGRESS) {
                og.close();
                break;
              }
              if (ui.getStatus() == MatchStatus.IN_PROGRESS) {
                ui.makeMove(match, pos);
                ui.changeTurn();
                og.getSendClient(match);
                ui.render();
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
          System.exit(0);
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

  private static Match receiveWithEscCheck(java.util.function.Supplier<Match> receive, GameUI ui, OnlineGame og)
      throws IOException, InterruptedException {
    while (true) {
      KeyStroke key = ui.pollInput();
      if (key != null && key.getKeyType() == KeyType.Escape) {
        ui.setGameStatus(MatchStatus.INTERRUPTED);
        return null;
      }
      Match m = receive.get();
      if (m != null) {
        return m;
      }
      if (ui.getStatus() != MatchStatus.IN_PROGRESS) {
        return null;
      }
    }
  }

}
