package com.Campeol.ui;

import com.googlecode.lanterna.SGR;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.screen.Screen;

/**
 * Tutorial do modo Rankeado local: só funcionalidades, sem tecnicês.
 */
public final class RankedHelpOverlay {
  private RankedHelpOverlay() {}

  public static void show(Screen screen, TextGraphics txt) throws java.io.IOException {
    int w = 46;
    int h = 18;
    int termCols = screen.getTerminalSize().getColumns();
    int termRows = screen.getTerminalSize().getRows();
    int x0 = Math.max(0, (termCols - w) / 2);
    int y0 = Math.max(0, (termRows - h) / 2);

    txt.setBackgroundColor(TextColor.ANSI.BLACK);
    txt.setForegroundColor(TextColor.ANSI.WHITE_BRIGHT);
    for (int y = 0; y < h; y++) {
      for (int x = 0; x < w; x++) {
        txt.putString(x0 + x, y0 + y, " ");
      }
    }
    txt.setBackgroundColor(TextColor.ANSI.BLACK);
    txt.setForegroundColor(TextColor.ANSI.WHITE_BRIGHT);
    txt.enableModifiers(SGR.BOLD);
    txt.putString(x0, y0, "┌" + "─".repeat(w - 2) + "┐");
    for (int i = 1; i < h - 1; i++) {
      txt.putString(x0, y0 + i, "│");
      txt.putString(x0 + w - 1, y0 + i, "│");
    }
    txt.putString(x0, y0 + h - 1, "└" + "─".repeat(w - 2) + "┘");
    String title = I18n.t("ranked.help.title");
    txt.putString(x0 + Math.max(0, (w - title.length() - 2) / 2), y0, " " + title + " ");
    txt.clearModifiers();

    txt.setForegroundColor(TextColor.ANSI.WHITE);
    txt.setBackgroundColor(TextColor.ANSI.BLACK);
    String[] rows = {
        I18n.t("ranked.help.row1"),
        I18n.t("ranked.help.row2"),
        I18n.t("ranked.help.row3"),
        I18n.t("ranked.help.row4"),
        I18n.t("ranked.help.row5"),
        I18n.t("ranked.help.row6"),
        I18n.t("ranked.help.row7"),
        I18n.t("ranked.help.row8"),
        I18n.t("ranked.help.row9"),
        I18n.t("ranked.help.row10"),
        "",
        I18n.t("ranked.help.close")
    };
    for (int i = 0; i < rows.length && i < h - 2; i++) {
      String r = rows[i] == null ? "" : rows[i];
      txt.setForegroundColor(i >= rows.length - 1 ? Theme.DIM_FG : TextColor.ANSI.WHITE);
      txt.putString(x0 + 2, y0 + 2 + i, r.length() > w - 4 ? r.substring(0, w - 4) : r);
    }
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
