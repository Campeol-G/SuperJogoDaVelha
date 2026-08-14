package com.Campeol.game;

import com.Campeol.MatchStatus;
import com.Campeol.subgame.Match;
import com.Campeol.subgame.Piece;
import com.Campeol.subgame.Player;
import com.Campeol.subgame.Position;
import com.Campeol.subgame.exception.subGameException;
import com.googlecode.lanterna.graphics.TextGraphics;

public class GameBoard {
  private Match[][] gamePlaces;
  private Player p1, p2, currentPlayer;
  private Integer turn;
  private MatchStatus status;
  private boolean matchFinished;

  public GameBoard() {
    startAllGames();
    status = MatchStatus.IN_PROGRESS;
  }

  public void startPlayer(char XorO) {
    if (XorO != 'O' && XorO != 'X') {
      throw new subGameException("Invalid piece choice");
    }
    p1 = new Player(new Piece(XorO));
    p2 = new Player(new Piece(XorO == 'X' ? 'O' : 'X'));
    currentPlayer = p1;
    turn = 1;
  }

  public void makeMove(Match match, Position position) {
    matchFinished = false;
    match.makeMove(currentPlayer, position);
    if (match.getMatchStatus() == MatchStatus.VICTORY || match.getMatchStatus() == MatchStatus.DRAW) {
      matchFinished = true;
    }
    changeTurn();
  }

  public boolean getMatchFinished() {
    return matchFinished;
  }

  private void changeTurn() {
    currentPlayer = currentPlayer == p1 ? p2 : p1;
    turn++;
  }

  public Player getCurrentPlayer() {
    return currentPlayer;
  }

  public Integer getTurn() {
    return turn;
  }

  public MatchStatus getStatus() {
    return status;
  }

  public void startAllGames() {
    gamePlaces = new Match[3][3];
    for (int i = 0; i < gamePlaces.length; i++) {
      for (int j = 0; j < gamePlaces[i].length; j++) {
        gamePlaces[i][j] = new Match(i * 6 + 1, j * 14 + 2);

      }
    }
  }

  // TODO refector this hardcoded position;
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
        gamePlaces[i][j].render(txt, currentPlayer, null);
      }
    }
    divisors(txt);
  }

  public Match getGamePlaces(int i, int j) {
    return gamePlaces[i][j];
  }
}
