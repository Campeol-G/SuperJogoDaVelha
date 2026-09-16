package com.Campeol.game;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.Campeol.MatchStatus;
import com.Campeol.subgame.Board;
import com.Campeol.subgame.Match;
import com.Campeol.subgame.Position;

/**
 * Bot do modo Rankeado local — 10 níveis híbridos.
 * L1-L3: aleatório + oportunista. L4-L6: heurística + erro decrescente.
 * L7-L10: minimax no mini + peso macro. L10 quase perfeito.
 */
public final class BotPlayer {
  private BotPlayer() {}

  public static double errorRate(int level) {
    switch (Math.min(10, Math.max(1, level))) {
      case 1: return 1.0;
      case 2: return 0.8;
      case 3: return 0.4;
      case 4: return 0.25;
      case 5: return 0.15;
      case 6: return 0.10;
      case 7: return 0.05;
      case 8: return 0.02;
      default: return 0.0;
    }
  }

  private static char opponent(char bot) {
    return bot == 'X' ? 'O' : 'X';
  }

  /** Escolha da célula dentro do mini forçado. */
  public static Position chooseCell(GameBoard gb, Match active, char botPiece, int level, Random rng) {
    if (active == null || active.getMatchStatus() != MatchStatus.IN_PROGRESS) {
      throw new com.Campeol.subgame.exception.SubGameException("finished.game");
    }
    Board b = active.getBoard();
    List<Position> free = b.freeCells();
    if (free.isEmpty()) {
      throw new com.Campeol.subgame.exception.SubGameException("finished.game");
    }
    if (free.size() == 1) return free.get(0);

    if (rng.nextDouble() < errorRate(level)) {
      return free.get(rng.nextInt(free.size()));
    }

    int lv = Math.min(10, Math.max(1, level));
    char opp = opponent(botPiece);

    if (lv <= 3) {
      Position win = findWinning(b, botPiece);
      if (win != null && lv >= 2) return win;
      if (lv == 1) return free.get(rng.nextInt(free.size()));
      return win != null ? win : free.get(rng.nextInt(free.size()));
    }
    if (lv == 4) {
      Position win = findWinning(b, botPiece);
      if (win != null) return win;
      Position block = findWinning(b, opp);
      if (block != null) return block;
      return free.get(rng.nextInt(free.size()));
    }
    if (lv <= 6) {
      Position win = findWinning(b, botPiece);
      if (win != null) return win;
      Position block = findWinning(b, opp);
      if (block != null) return block;
      Position pref = preferCenterCorner(free);
      if (pref != null && lv >= 5) {
        if (lv == 6) {
          Position safe = avoidBadDestination(gb, active, free, botPiece, pref);
          if (safe != null) return safe;
        }
        return pref;
      }
      if (lv == 6) {
        return bestByMacro(gb, active, free, b, botPiece, rng);
      }
      return free.get(rng.nextInt(free.size()));
    }
    // L7-L10: minimax + macro
    int depth = lv == 7 ? 1 : (lv == 8 ? 2 : 3);
    if (lv == 10 && free.size() <= 6) depth = 4;
    return bestByMinimax(gb, active, b, botPiece, depth, rng);
  }

  /** Escolha do macro quando a jogada é livre. Retorna {row, col}. */
  public static int[] chooseMacro(GameBoard gb, char botPiece, int level, Random rng) {
    List<int[]> free = new ArrayList<>();
    for (int i = 0; i < 3; i++) {
      for (int j = 0; j < 3; j++) {
        if (gb.getGamePlaces(i, j).getMatchStatus() == MatchStatus.IN_PROGRESS) {
          free.add(new int[]{i, j});
        }
      }
    }
    if (free.isEmpty()) return new int[]{1, 1};
    if (free.size() == 1) return free.get(0);
    int lv = Math.min(10, Math.max(1, level));
    if (lv <= 3 || rng.nextDouble() < errorRate(lv)) {
      return free.get(rng.nextInt(free.size()));
    }
    // prefere macro onde vencer fecha linha macro, bloqueia derrota macro,
    // centro, e minis com mais peças do bot
    int best = 0;
    int bestScore = Integer.MIN_VALUE;
    for (int k = 0; k < free.size(); k++) {
      int[] mc = free.get(k);
      int s = scoreMacro(gb, mc[0], mc[1], botPiece);
      if (lv >= 7) {
        // bônus: mini com ameaça imediata do bot
        Match m = gb.getGamePlaces(mc[0], mc[1]);
        if (findWinning(m.getBoard(), botPiece) != null) s += 3;
        if (findWinning(m.getBoard(), opponent(botPiece)) != null) s += 2;
      }
      s += rng.nextInt(2); // desempate
      if (s > bestScore) {
        bestScore = s;
        best = k;
      }
    }
    return free.get(best);
  }

  // ---------- helpers ----------

  static Position findWinning(Board b, char piece) {
    for (Position p : b.freeCells()) {
      Board c = b.copy();
      c.setPiece(p.getRow(), p.getColumn(), piece);
      if (c.testEndGame()) return p;
    }
    return null;
  }

  private static Position preferCenterCorner(List<Position> free) {
    for (Position p : free) {
      if (p.getRow() == 1 && p.getColumn() == 1) return p;
    }
    int[][] corners = {{0, 0}, {0, 2}, {2, 0}, {2, 2}};
    for (int[] co : corners) {
      for (Position p : free) {
        if (p.getRow() == co[0] && p.getColumn() == co[1]) return p;
      }
    }
    return null;
  }

  /** L6: evita mandar o oponente para mini onde ele vence na hora. */
  private static Position avoidBadDestination(GameBoard gb, Match active, List<Position> free,
      char bot, Position preferred) {
    char opp = opponent(bot);
    List<Position> safe = new ArrayList<>();
    for (Position p : free) {
      Match target = gb.getGamePlaces(p.getRow(), p.getColumn());
      if (target.getMatchStatus() != MatchStatus.IN_PROGRESS) continue; // dá liberdade: ruim
      if (findWinning(target.getBoard(), opp) != null) continue; // oponente vence lá: ruim
      safe.add(p);
    }
    if (safe.isEmpty()) return preferred;
    if (safe.contains(preferred)) return preferred;
    // prefere centro/canto entre os seguros
    Position c = preferCenterCorner(safe);
    return c != null ? c : safe.get(0);
  }

  private static Position bestByMacro(GameBoard gb, Match active, List<Position> free,
      Board b, char bot, Random rng) {
    Position best = free.get(0);
    int bestScore = Integer.MIN_VALUE;
    for (Position p : free) {
      Match target = gb.getGamePlaces(p.getRow(), p.getColumn());
      int s = 0;
      if (target.getMatchStatus() != MatchStatus.IN_PROGRESS) {
        s -= 5;
      } else {
        if (findWinning(target.getBoard(), opponent(bot)) != null) s -= 8;
        if (p.getRow() == 1 && p.getColumn() == 1) s += 1;
      }
      // vencer este mini fecha macro?
      if (wouldWinMacro(gb, active.getGridRow(), active.getGridCol(), bot)) s += 10;
      s += rng.nextInt(2);
      if (s > bestScore) {
        bestScore = s;
        best = p;
      }
    }
    return best;
  }

  private static Position bestByMinimax(GameBoard gb, Match active, Board b,
      char bot, int depth, Random rng) {
    List<Position> free = b.freeCells();
    Position best = free.get(0);
    int bestScore = Integer.MIN_VALUE;
    List<Position> shuffled = new ArrayList<>(free);
    java.util.Collections.shuffle(shuffled, rng);
    for (Position p : shuffled) {
      Board c = b.copy();
      c.setPiece(p.getRow(), p.getColumn(), bot);
      int s;
      if (c.testEndGame()) {
        s = 100 + (wouldWinMacro(gb, active.getGridRow(), active.getGridCol(), bot) ? 50 : 0);
      } else if (c.isFull()) {
        s = 0;
      } else {
        s = minimax(c, bot, opponent(bot), depth - 1, Integer.MIN_VALUE, Integer.MAX_VALUE);
      }
      // peso macro: destino ruim penaliza
      Match target = gb.getGamePlaces(p.getRow(), p.getColumn());
      if (target.getMatchStatus() != MatchStatus.IN_PROGRESS) {
        s -= 5;
      } else if (findWinning(target.getBoard(), opponent(bot)) != null) {
        s -= 8;
      }
      if (s > bestScore) {
        bestScore = s;
        best = p;
      }
    }
    return best;
  }

  static int minimax(Board b, char bot, char turn, int depth, int alpha, int beta) {
    List<Position> free = b.freeCells();
    if (free.isEmpty()) return 0;
    if (depth <= 0) return heuristic(b, bot);
    boolean maximizing = (turn == bot);
    if (maximizing) {
      int best = Integer.MIN_VALUE;
      for (Position p : free) {
        Board c = b.copy();
        c.setPiece(p.getRow(), p.getColumn(), turn);
        int s;
        if (c.testEndGame()) s = 10 + depth;
        else if (c.isFull()) s = 0;
        else s = minimax(c, bot, opponent(turn), depth - 1, alpha, beta);
        best = Math.max(best, s);
        alpha = Math.max(alpha, best);
        if (beta <= alpha) break;
      }
      return best;
    } else {
      int best = Integer.MAX_VALUE;
      for (Position p : free) {
        Board c = b.copy();
        c.setPiece(p.getRow(), p.getColumn(), turn);
        int s;
        if (c.testEndGame()) s = -10 - depth;
        else if (c.isFull()) s = 0;
        else s = minimax(c, bot, opponent(turn), depth - 1, alpha, beta);
        best = Math.min(best, s);
        beta = Math.min(beta, best);
        if (beta <= alpha) break;
      }
      return best;
    }
  }

  /** Heurística: linhas abertas com peças do bot (+) vs oponente (-). */
  static int heuristic(Board b, char bot) {
    char opp = opponent(bot);
    int[][][] lines = {
        {{0, 0}, {0, 1}, {0, 2}}, {{1, 0}, {1, 1}, {1, 2}}, {{2, 0}, {2, 1}, {2, 2}},
        {{0, 0}, {1, 0}, {2, 0}}, {{0, 1}, {1, 1}, {2, 1}}, {{0, 2}, {1, 2}, {2, 2}},
        {{0, 0}, {1, 1}, {2, 2}}, {{0, 2}, {1, 1}, {2, 0}}
    };
    int s = 0;
    for (int[][] ln : lines) {
      int nb = 0, no = 0;
      for (int[] cell : ln) {
        com.Campeol.subgame.Piece pc = b.getPiece(cell[0], cell[1]);
        if (pc == null) continue;
        if (pc.getXorO() == bot) nb++;
        else if (pc.getXorO() == opp) no++;
      }
      if (nb > 0 && no > 0) continue;
      if (nb == 2) s += 5;
      else if (nb == 1) s += 1;
      if (no == 2) s -= 5;
      else if (no == 1) s -= 1;
    }
    return s;
  }

  private static boolean wouldWinMacro(GameBoard gb, int mr, int mc, char bot) {
    // simula: se o bot vencesse este mini, fecharia linha macro?
    int botCount;
    // linhas
    botCount = countMacroOwned(gb, mr, 0, mr, 1, mr, 2, bot);
    if (botCount == 2) return true;
    botCount = countMacroOwned(gb, 0, mc, 1, mc, 2, mc, bot);
    if (botCount == 2) return true;
    if (mr == mc && countMacroOwned(gb, 0, 0, 1, 1, 2, 2, bot) == 2) return true;
    if (mr + mc == 2 && countMacroOwned(gb, 0, 2, 1, 1, 2, 0, bot) == 2) return true;
    return false;
  }

  private static int countMacroOwned(GameBoard gb, int r1, int c1, int r2, int c2, int r3, int c3, char bot) {
    int n = 0;
    int[][] cells = {{r1, c1}, {r2, c2}, {r3, c3}};
    for (int[] cell : cells) {
      Match m = gb.getGamePlaces(cell[0], cell[1]);
      if (m.getMatchStatus() == MatchStatus.VICTORY && m.getWinner() != null
          && m.getWinner().getPiece().getXorO() == bot) {
        n++;
      }
    }
    return n;
  }

  private static int scoreMacro(GameBoard gb, int mr, int mc, char bot) {
    char opp = opponent(bot);
    int s = 0;
    if (mr == 1 && mc == 1) s += 3;
    else if ((mr + mc) % 2 == 0) s += 1;
    // completar / bloquear linha macro
    int[][] lines = {{0, 0, 0, 1, 0, 2}, {1, 0, 1, 1, 1, 2}, {2, 0, 2, 1, 2, 2},
        {0, 0, 1, 0, 2, 0}, {0, 1, 1, 1, 2, 1}, {0, 2, 1, 2, 2, 2},
        {0, 0, 1, 1, 2, 2}, {0, 2, 1, 1, 2, 0}};
    for (int[] ln : lines) {
      boolean inLine = false;
      for (int k = 0; k < 3; k++) {
        if (ln[k * 2] == mr && ln[k * 2 + 1] == mc) { inLine = true; break; }
      }
      if (!inLine) continue;
      int nb = 0, no = 0;
      for (int k = 0; k < 3; k++) {
        Match m = gb.getGamePlaces(ln[k * 2], ln[k * 2 + 1]);
        if (m.getMatchStatus() == MatchStatus.VICTORY && m.getWinner() != null) {
          if (m.getWinner().getPiece().getXorO() == bot) nb++;
          else no++;
        }
      }
      if (nb == 2) s += 8;
      if (no == 2) s -= 2; // macro do oponente ameaçado: jogar lá pode bloquear? pequeno
      s += nb;
    }
    // evita mini cheio de peças do oponente
    Match self = gb.getGamePlaces(mr, mc);
    int nb = 0, no = 0;
    for (Position p : self.getBoard().freeCells()) { /* só conta livres, ignora */ }
    for (int i = 0; i < 3; i++) {
      for (int j = 0; j < 3; j++) {
        com.Campeol.subgame.Piece pc = self.getBoard().getPiece(i, j);
        if (pc == null) continue;
        if (pc.getXorO() == bot) nb++;
        else no++;
      }
    }
    s += nb - no;
    return s;
  }
}
