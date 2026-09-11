package com.Campeol.ui;

import com.googlecode.lanterna.SGR;
import com.googlecode.lanterna.TextColor;

/**
 * Paleta monocromática clean.
 * Tudo em tons de branco/cinza para não poluir o terminal.
 * X em destaque (branco brilhante + negrito), O mais suave (branco normal).
 */
public final class Theme {
  private Theme() {}

  public static final TextColor X_FG = TextColor.ANSI.WHITE_BRIGHT;
  public static final TextColor O_FG = TextColor.ANSI.WHITE;
  public static final TextColor DIM_FG = TextColor.ANSI.BLACK_BRIGHT;
  public static final TextColor DEFAULT_FG = TextColor.ANSI.DEFAULT;

  // Cursor da casa: neutro (branco) porém brilhante — sem REVERSE,
  // que invertia para fundo escuro e sumia em terminais escuros.
  public static final TextColor HOVER_BG = TextColor.ANSI.WHITE_BRIGHT;
  public static final TextColor HOVER_FG = TextColor.ANSI.BLACK;
  public static final TextColor ACTIVE_MACRO_BG = TextColor.ANSI.BLACK_BRIGHT;
  public static final TextColor WON_BG = TextColor.ANSI.BLACK_BRIGHT;

  public static final SGR[] X_MODIFIERS = new SGR[] { SGR.BOLD };
  public static final SGR[] O_MODIFIERS = new SGR[] {};
}
