package com.Campeol.ui;

import com.Campeol.MatchStatus;
import com.Campeol.game.GameBoard;
import com.Campeol.subgame.Position;
import com.googlecode.lanterna.SGR;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;

/**
 * HUD lateral estilo game: vez, destino, timers xadrez, placar, ajuda.
 */
public final class HudView {
  private HudView() {}

  public static void render(TextGraphics txt, Viewport vp, GameBoard gb, SessionScore score,
      Position destOrNull, boolean destIsFree, String turnLabel) {
    int hx = vp.hudX();
    int hy = vp.getOriginY();
    int w = Viewport.HUD_W;
    int h = Viewport.BOARD_H; // 19

    txt.clearModifiers();
    txt.setBackgroundColor(null);
    txt.setForegroundColor(Theme.DIM_FG);

    // moldura
    txt.putString(hx, hy, "┌" + "─".repeat(w - 2) + "┐");
    for (int i = 1; i < h - 1; i++) {
      txt.putString(hx, hy + i, "│");
      txt.putString(hx + w - 1, hy + i, "│");
    }
    txt.putString(hx, hy + h - 1, "└" + "─".repeat(w - 2) + "┘");

    int lx = hx + 2;
    int maxW = w - 4;
    int y = hy + 1;

    // turno
    String turnStr = I18n.t("turn") + ": " + (gb.getTurn() != null ? gb.getTurn() : 1);
    putLine(txt, lx, y++, trunc(turnStr, maxW), Theme.DIM_FG, false);

    // vez
    String vez = turnLabel != null ? turnLabel : "";
    putLine(txt, lx, y++, trunc(I18n.t("turn.of") + ": " + vez, maxW), TextColor.ANSI.WHITE_BRIGHT, true);

    // destino
    String dest;
    if (destIsFree) {
      dest = I18n.t("dest") + ": " + I18n.t("dest.free");
    } else if (destOrNull != null) {
      dest = String.format("%s: [%d,%d]", I18n.t("dest"), destOrNull.getRow(), destOrNull.getColumn());
    } else {
      dest = I18n.t("dest") + ": --";
    }
    putLine(txt, lx, y++, trunc(dest, maxW), TextColor.ANSI.WHITE, false);

    putSep(txt, hx, y++, w);

    // timers xadrez
    long tx = gb.getClock().totalFor('X');
    long to = gb.getClock().totalFor('O');
    putLine(txt, lx, y++, trunc(I18n.t("timer.x") + " " + UiUtils.formatDuration(tx), maxW), Theme.X_FG, true);
    putLine(txt, lx, y++, trunc(I18n.t("timer.o") + " " + UiUtils.formatDuration(to), maxW), Theme.O_FG, false);
    putLine(txt, lx, y++, trunc(I18n.t("timer.match") + " " + UiUtils.formatDuration(gb.getClock().matchMillis()), maxW),
        Theme.DIM_FG, false);
    putLine(txt, lx, y++, trunc(I18n.t("timer.turn") + " " + UiUtils.formatDuration(gb.getClock().currentTurnMillis()), maxW),
        Theme.DIM_FG, false);

    putSep(txt, hx, y++, w);

    // placar sessão — 3 linhas (pedido do usuário)
    putLine(txt, lx, y++, trunc(I18n.t("score"), maxW), TextColor.ANSI.WHITE_BRIGHT, true);
    int wx = score != null ? score.getWinsX() : 0;
    int wo = score != null ? score.getWinsO() : 0;
    int dr = score != null ? score.getDraws() : 0;
    boolean xLead = wx > wo;
    boolean oLead = wo > wx;
    putLine(txt, lx, y++, trunc("  " + I18n.t("score.x") + "  ·  " + wx, maxW), Theme.X_FG, xLead);
    putLine(txt, lx, y++, trunc("  " + I18n.t("score.o") + "  ·  " + wo, maxW), Theme.O_FG, oLead);
    putLine(txt, lx, y++, trunc("  " + I18n.t("score.draw") + "  ·  " + dr, maxW), Theme.DIM_FG, false);

    // status global sutil
    if (gb.getStatus() != MatchStatus.IN_PROGRESS) {
      putLine(txt, lx, y++, trunc(gb.getStatus().toString(), maxW), TextColor.ANSI.WHITE_BRIGHT, true);
    }

    // preenche resto e dica no rodapé do HUD
    String hint = I18n.t("help.hint");
    // limpa linhas restantes
    for (int i = y; i < hy + h - 2; i++) {
      txt.setBackgroundColor(null);
      txt.putString(lx, i, " ".repeat(maxW));
    }
    putLine(txt, lx, hy + h - 2, trunc(hint, maxW), Theme.DIM_FG, false);

    txt.clearModifiers();
    txt.setBackgroundColor(null);
    txt.setForegroundColor(null);
  }

  private static void putSep(TextGraphics txt, int hx, int y, int w) {
    txt.setForegroundColor(Theme.DIM_FG);
    txt.setBackgroundColor(null);
    txt.clearModifiers();
    txt.putString(hx, y, "├" + "─".repeat(w - 2) + "┤");
  }

  private static void putLine(TextGraphics txt, int x, int y, String s, TextColor fg, boolean bold) {
    txt.setBackgroundColor(null);
    txt.setForegroundColor(fg);
    txt.clearModifiers();
    if (bold) txt.enableModifiers(SGR.BOLD);
    // limpa antes para não deixar rastro
    int maxClear = Viewport.HUD_W - 4;
    txt.putString(x, y, " ".repeat(maxClear));
    txt.putString(x, y, s);
    txt.clearModifiers();
    txt.setBackgroundColor(null);
    txt.setForegroundColor(null);
  }

  private static String trunc(String s, int max) {
    if (s == null) return "";
    if (s.length() <= max) return s;
    if (max <= 1) return s.substring(0, max);
    return s.substring(0, max - 1) + "…";
  }
}
