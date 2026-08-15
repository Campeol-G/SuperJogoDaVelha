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
  private Player p1, p2, currentPlayer, winner;
  private Integer turn;
  private MatchStatus status;
  private boolean matchFinished;

  public GameBoard() {
    startAllGames();
    status = MatchStatus.IN_PROGRESS;
  }

  public void startPlayer(char XorO) {
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
    if (gameOver()) {
      winner = currentPlayer;
      status = MatchStatus.VICTORY;
    } else if (draw()) {
      status = MatchStatus.DRAW;
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

  public boolean gameOver() {
    return testColumn() || testDiagnoal() || testRow();
  }

  public boolean draw() {
    for (int i = 0; i < gamePlaces.length; i++) {
      for (int j = 0; j < gamePlaces.length; j++) {
        if (gamePlaces[i][j].getMatchStatus() == MatchStatus.IN_PROGRESS) {
          return false;
        }
      }
    }
    return true;
  }

  private boolean samePlayer(int r1, int c1, int r2, int c2, int r3, int c3) {
    Match m1 = gamePlaces[r1][c1];
    Match m2 = gamePlaces[r2][c2];
    Match m3 = gamePlaces[r3][c3];

    if (m1.getMatchStatus() == MatchStatus.VICTORY &&
        m2.getMatchStatus() == MatchStatus.VICTORY &&
        m3.getMatchStatus() == MatchStatus.VICTORY) {
      Piece p1 = m1.getWinner().getPiece();
      return p1 == m2.getWinner().getPiece() && p1 == m3.getWinner().getPiece();
    }
    return false;
  }

  private boolean testColumn() {
    return samePlayer(0, 0, 1, 0, 2, 0) ||
        samePlayer(0, 1, 1, 1, 2, 1) ||
        samePlayer(0, 2, 1, 2, 2, 2);
  }

  private boolean testRow() {
    return samePlayer(0, 0, 0, 1, 0, 2) ||
        samePlayer(1, 0, 1, 1, 1, 2) ||
        samePlayer(2, 0, 2, 1, 2, 2);
  }

  private boolean testDiagnoal() {
    return samePlayer(0, 0, 1, 1, 2, 2) ||
        samePlayer(0, 2, 1, 1, 2, 0);
  }

  public Match getGamePlaces(int i, int j) {
    return gamePlaces[i][j];
  }

  public void setGameStatus(MatchStatus status) {
    this.status = status;
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

  public Player getWinner() {
    return winner;
  }

}
