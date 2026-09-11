package com.Campeol.ui;

import com.Campeol.MatchStatus;
import com.googlecode.lanterna.SGR;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.Screen;

/**
 * Tela de finalização trabalhada + revanche.
 */
public final class EndScreen {
  public enum Choice {
    REMATCH,
    QUIT
  }

  private EndScreen() {}

  public static class Result {
    public final Choice choice;
    public final boolean wantsRematch;

    public Result(Choice c) {
      this.choice = c;
      this.wantsRematch = c == Choice.REMATCH;
    }
  }

  /**
   * Mostra tela final local (bloqueante até R/Esc/Enter).
   * Retorna REMATCH se R, QUIT caso contrário.
   */
  public static Result showLocal(Screen screen, TextGraphics txt, MatchStatus status, String winnerLabel,
      SessionScore score, long matchMillis) throws java.io.IOException, InterruptedException {
    draw(screen, txt, status, winnerLabel, score, matchMillis, null);
    while (true) {
      KeyStroke k = screen.readInput();
      if (k == null) continue;
      if (k.getKeyType() == KeyType.Escape) return new Result(Choice.QUIT);
      Character c = k.getCharacter();
      if (c != null) {
        char u = Character.toUpperCase(c);
        if (u == 'R') return new Result(Choice.REMATCH);
        if (u == 'N' || u == 'Q') return new Result(Choice.QUIT);
      }
      if (k.getKeyType() == KeyType.Enter) {
        // Enter = sair (evita revanche acidental)
        return new Result(Choice.QUIT);
      }
    }
  }

  public static void draw(Screen screen, TextGraphics txt, MatchStatus status, String winnerLabel,
      SessionScore score, long matchMillis, String extraLine) throws java.io.IOException {
    screen.clear();
    int cols = screen.getTerminalSize().getColumns();
    int rows = screen.getTerminalSize().getRows();
    int w = 46;
    int h = 13;
    int x0 = Math.max(0, (cols - w) / 2);
    int y0 = Math.max(0, (rows - h) / 2);

    txt.setBackgroundColor(TextColor.ANSI.BLACK);
    txt.setForegroundColor(TextColor.ANSI.WHITE);
    for (int y = 0; y < h; y++) {
      for (int x = 0; x < w; x++) {
        txt.putString(x0 + x, y0 + y, " ");
      }
    }

    txt.setForegroundColor(TextColor.ANSI.WHITE_BRIGHT);
    txt.enableModifiers(SGR.BOLD);
    txt.putString(x0, y0, "╔" + "═".repeat(w - 2) + "╗");
    for (int i = 1; i < h - 1; i++) {
      txt.putString(x0, y0 + i, "║");
      txt.putString(x0 + w - 1, y0 + i, "║");
    }
    txt.putString(x0, y0 + h - 1, "╚" + "═".repeat(w - 2) + "╝");
    txt.clearModifiers();

    String title;
    if (status == MatchStatus.VICTORY) title = I18n.t("end.victory");
    else if (status == MatchStatus.DRAW) title = I18n.t("end.draw");
    else title = I18n.t("end.interrupted");

    txt.setForegroundColor(TextColor.ANSI.WHITE_BRIGHT);
    txt.enableModifiers(SGR.BOLD);
    txt.putString(x0 + (w - title.length()) / 2, y0 + 2, title);
    txt.clearModifiers();

    txt.setForegroundColor(TextColor.ANSI.WHITE);
    txt.setBackgroundColor(TextColor.ANSI.BLACK);
    int y = y0 + 4;
    if (winnerLabel != null && !winnerLabel.isEmpty() && status == MatchStatus.VICTORY) {
      String by = I18n.t("end.by", winnerLabel);
      txt.putString(x0 + (w - by.length()) / 2, y++, by);
    } else {
      y++;
    }
    String time = I18n.t("timer.match") + " " + UiUtils.formatDuration(matchMillis);
    txt.setForegroundColor(Theme.DIM_FG);
    txt.putString(x0 + (w - time.length()) / 2, y++, time);

    txt.setForegroundColor(TextColor.ANSI.WHITE);
    String sc = score != null ? score.summary() : "";
    if (!sc.isEmpty()) {
      txt.putString(x0 + (w - Math.min(w - 4, sc.length())) / 2, y++, sc.length() > w - 4 ? sc.substring(0, w - 4) : sc);
    } else {
      y++;
    }
    y++;
    if (extraLine != null && !extraLine.isEmpty()) {
      txt.setForegroundColor(TextColor.ANSI.WHITE_BRIGHT);
      txt.enableModifiers(SGR.BOLD);
      String el = extraLine.length() > w - 4 ? extraLine.substring(0, w - 4) : extraLine;
      txt.putString(x0 + (w - el.length()) / 2, y++, el);
      txt.clearModifiers();
      txt.setForegroundColor(TextColor.ANSI.WHITE);
    }
    String opt = I18n.t("end.rematch");
    txt.putString(x0 + (w - opt.length()) / 2, y0 + h - 3, opt);

    txt.clearModifiers();
    txt.setBackgroundColor(null);
    txt.setForegroundColor(null);
    try {
      screen.setCursorPosition(null);
    } catch (Exception ignored) {
    }
    screen.refresh();
  }
}
