package com.Campeol.ui;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import com.Campeol.MatchStatus;
import com.Campeol.game.GameBoard;
import com.Campeol.net.NetLog;
import com.Campeol.subgame.Match;
import com.Campeol.subgame.Player;
import com.Campeol.subgame.Position;

/**
 * Save da partida local/rankeada (ESC &gt; Sair e salvar).
 * Arquivo em ~/.superjogo/save-local.dat e save-ranked.dat.
 * Guarda tabuleiro + turno + placar da sessão para continuar depois.
 */
public final class LocalSave implements Serializable {
  private static final long serialVersionUID = 1L;

  private Match[][] gamePlaces;
  private Player p1;
  private Player p2;
  private Player currentPlayer;
  private Player winner;
  private Integer turn;
  private MatchStatus status;
  private boolean matchFinished;
  private boolean starterIsP1;
  private SessionScore sessionScore;
  private Position lastDest;
  private boolean lastDestFree;
  private Character rankedHumanPiece;
  private Character noClockPiece;

  private LocalSave() {}

  public static File fileFor(String mode) {
    String home = System.getProperty("user.home");
    File dir = new File(home != null ? home : ".", ".superjogo");
    String name = "RANKED".equals(mode) ? "save-ranked.dat" : "save-local.dat";
    return new File(dir, name);
  }

  public static boolean exists(String mode) {
    try {
      File f = fileFor(mode);
      return f != null && f.isFile() && f.length() > 0;
    } catch (Exception e) {
      return false;
    }
  }

  public static void delete(String mode) {
    try {
      File f = fileFor(mode);
      if (f != null && f.isFile()) {
        f.delete();
      }
    } catch (Exception e) {
      NetLog.log("save delete", e);
    }
  }

  public static void save(String mode, GameBoard gb, SessionScore score,
      Position lastDest, boolean lastDestFree, Character rankedHumanPiece) {
    LocalSave s = new LocalSave();
    try {
      s.gamePlaces = new Match[3][3];
      for (int i = 0; i < 3; i++) {
        for (int j = 0; j < 3; j++) {
          s.gamePlaces[i][j] = gb.getGamePlaces(i, j);
        }
      }
      s.p1 = gb.getP1();
      s.p2 = gb.getP2();
      s.currentPlayer = gb.getCurrentPlayer();
      s.winner = gb.getWinner();
      s.turn = gb.getTurn();
      s.status = gb.getStatus();
      s.matchFinished = gb.getMatchFinished();
      s.starterIsP1 = gb.isStarterP1();
      s.sessionScore = score;
      s.lastDest = lastDest;
      s.lastDestFree = lastDestFree;
      s.rankedHumanPiece = rankedHumanPiece;
      s.noClockPiece = gb.getNoClockPiece();
    } catch (Exception e) {
      NetLog.log("save snapshot", e);
      return;
    }
    try {
      File f = fileFor(mode);
      f.getParentFile().mkdirs();
      try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(f))) {
        oos.writeObject(s);
      }
    } catch (Exception e) {
      NetLog.log("save write", e);
    }
  }

  public static LocalSave load(String mode) {
    try {
      File f = fileFor(mode);
      if (f == null || !f.isFile()) return null;
      try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(f))) {
        ois.setObjectInputFilter(buildFilter());
        Object o = ois.readObject();
        if (o instanceof LocalSave) return (LocalSave) o;
        return null;
      }
    } catch (Exception e) {
      NetLog.log("save load", e);
      return null;
    }
  }

  private static ObjectInputFilter buildFilter() {
    return info -> {
      Class<?> c = info.serialClass();
      if (c == null) {
        if (info.arrayLength() >= 0 && info.arrayLength() > 10000) {
          return ObjectInputFilter.Status.REJECTED;
        }
        return ObjectInputFilter.Status.UNDECIDED;
      }
      String n = c.getName();
      // Arrays (ex. Match[][], Piece[][]): permite, os elementos são checados em seguida.
      if (n.startsWith("[")) {
        if (info.arrayLength() >= 0 && info.arrayLength() > 10000) {
          return ObjectInputFilter.Status.REJECTED;
        }
        return ObjectInputFilter.Status.ALLOWED;
      }
      if (n.equals("com.Campeol.ui.LocalSave")
          || n.startsWith("com.Campeol.subgame.")
          || n.startsWith("com.Campeol.ui.SessionScore")
          || n.startsWith("com.Campeol.MatchStatus")
          || n.equals("java.lang.String")
          || n.equals("java.lang.Boolean")
          || n.equals("java.lang.Character")
          || n.equals("java.lang.Integer")) {
        return ObjectInputFilter.Status.ALLOWED;
      }
      if (n.startsWith("java.lang.") || n.startsWith("java.util.")) {
        return ObjectInputFilter.Status.UNDECIDED;
      }
      return ObjectInputFilter.Status.REJECTED;
    };
  }

  /**
   * Aplica o save no board atual. Retorna false se o save está corrompido.
   * O relógio é reiniciado para o turno atual (tempo anterior descartado).
   */
  public boolean applyTo(GameBoard gb) {
    try {
      if (gamePlaces == null || p1 == null || p2 == null || currentPlayer == null
          || turn == null || status == null) {
        return false;
      }
      if (status != MatchStatus.IN_PROGRESS) {
        return false;
      }
      gb.startAllGames();
      gb.clearResult();
      for (int i = 0; i < 3; i++) {
        for (int j = 0; j < 3; j++) {
          Match m = gamePlaces[i][j];
          if (m == null) return false;
          if (m.getGridRow() != i || m.getGridCol() != j) return false;
          gb.setGamePlaces(i, j, m);
        }
      }
      gb.getPlayers(p1, p2);
      // currentPlayer deve ser p1 ou p2 pela peça (evita referência fantasma)
      char cur = currentPlayer.getPiece() != null ? currentPlayer.getPiece().getXorO() : '?';
      if (p1.getPiece() != null && p1.getPiece().getXorO() == cur) {
        gb.setCurrentPlayer(p1);
      } else if (p2.getPiece() != null && p2.getPiece().getXorO() == cur) {
        gb.setCurrentPlayer(p2);
      } else {
        return false;
      }
      gb.setTurn(turn);
      gb.setWinner(winner);
      gb.setMatchFinished(matchFinished);
      gb.setStarterIsP1(starterIsP1);
      gb.setGameStatus(MatchStatus.IN_PROGRESS);
      if (noClockPiece != null) {
        gb.setNoClockPiece(noClockPiece);
      } else {
        gb.clearNoClockPiece();
      }
      gb.getClock().startMatch();
      try {
        gb.getClock().startTurn(gb.getCurrentPlayer().getPiece().getXorO());
      } catch (Exception ignored) {
      }
      return true;
    } catch (Exception e) {
      NetLog.log("save apply", e);
      return false;
    }
  }

  public SessionScore getSessionScore() {
    return sessionScore;
  }

  public Position getLastDest() {
    return lastDest;
  }

  public boolean isLastDestFree() {
    return lastDestFree;
  }

  public Character getRankedHumanPiece() {
    return rankedHumanPiece;
  }
}
