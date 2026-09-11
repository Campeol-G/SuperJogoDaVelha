package com.Campeol.ui;

import com.googlecode.lanterna.TerminalPosition;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.graphics.TextGraphics;

/**
 * Substitui o antigo padrão putString("           ") para apagar texto.
 * Usa fillRectangle (limpeza real da área) + re-render para restaurar.
 */
public final class UiUtils {
  private UiUtils() {}

  public static void clearRect(TextGraphics txt, int col, int row, int w, int h) {
    txt.setBackgroundColor(null);
    txt.setForegroundColor(null);
    txt.clearModifiers();
    txt.fillRectangle(new TerminalPosition(col, row), new TerminalSize(w, h), ' ');
  }

  public static void clearBoardArea(TextGraphics txt, Viewport vp) {
    clearRect(txt, vp.getOriginX(), vp.getOriginY(), Viewport.TOTAL_W, Viewport.TOTAL_H);
  }

  public static int centerX(int containerX, int containerW, String text) {
    return containerX + Math.max(0, (containerW - text.length()) / 2);
  }

  public static String formatDuration(long millis) {
    if (millis < 0) millis = 0;
    long totalSec = millis / 1000;
    long mm = totalSec / 60;
    long ss = totalSec % 60;
    return String.format("%02d:%02d", mm, ss);
  }

  public static String clampNick(String s, int max) {
    if (s == null) return "";
    s = s.trim();
    if (s.length() > max) return s.substring(0, max);
    return s;
  }
}
