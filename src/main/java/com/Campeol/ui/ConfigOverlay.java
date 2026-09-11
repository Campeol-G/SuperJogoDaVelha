package com.Campeol.ui;

import com.googlecode.lanterna.SGR;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.screen.Screen;

/**
 * Overlay de configuração: trocar nick / idioma sem sair do jogo.
 */
public final class ConfigOverlay {
  private ConfigOverlay() {}

  public static void show(Screen screen, TextGraphics txt) throws java.io.IOException {
    int w = 38;
    int h = 10;
    int termCols = screen.getTerminalSize().getColumns();
    int termRows = screen.getTerminalSize().getRows();
    int x0 = Math.max(0, (termCols - w) / 2);
    int y0 = Math.max(0, (termRows - h) / 2);

    txt.setBackgroundColor(TextColor.ANSI.BLACK);
    txt.setForegroundColor(TextColor.ANSI.WHITE);
    for (int y = 0; y < h; y++) {
      for (int x = 0; x < w; x++) {
        txt.putString(x0 + x, y0 + y, " ");
      }
    }
    txt.setForegroundColor(TextColor.ANSI.WHITE_BRIGHT);
    txt.enableModifiers(SGR.BOLD);
    txt.putString(x0, y0, "┌" + "─".repeat(w - 2) + "┐");
    for (int i = 1; i < h - 1; i++) {
      txt.putString(x0, y0 + i, "│");
      txt.putString(x0 + w - 1, y0 + i, "│");
    }
    txt.putString(x0, y0 + h - 1, "└" + "─".repeat(w - 2) + "┘");
    String title = I18n.t("config.title");
    txt.putString(x0 + (w - title.length()) / 2, y0, " " + title + " ");
    txt.clearModifiers();

    txt.setForegroundColor(TextColor.ANSI.WHITE);
    txt.setBackgroundColor(TextColor.ANSI.BLACK);
    String curNick = ProfileStore.getNick();
    if (curNick == null || curNick.isEmpty()) curNick = I18n.t("nick.you");
    String curLang = "pt".equals(I18n.getLang()) ? "PT" : "EN";
    String[] rows = {
        I18n.t("config.nick") + "  (" + curNick + ")",
        I18n.t("config.lang") + "  [" + curLang + "]",
        "",
        I18n.t("config.hint"),
        I18n.t("config.close")
    };
    for (int i = 0; i < rows.length && i < h - 2; i++) {
      String r = rows[i].length() > w - 4 ? rows[i].substring(0, w - 4) : rows[i];
      txt.putString(x0 + 2, y0 + 2 + i, r);
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
