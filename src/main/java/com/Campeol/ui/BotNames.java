package com.Campeol.ui;

/**
 * Nomes dos bots por nível (PT default, EN via properties).
 */
public final class BotNames {
  private BotNames() {}

  public static String name(int level) {
    String key = "bot.name." + Math.min(10, Math.max(1, level));
    String v = I18n.t(key);
    if (v == null || v.equals(key)) {
      return fallback(level);
    }
    return v;
  }

  private static String fallback(int level) {
    switch (level) {
      case 1: return "Recruta";
      case 2: return "Aprendiz";
      case 3: return "Escudeiro";
      case 4: return "Soldado";
      case 5: return "Veterano";
      case 6: return "Tático";
      case 7: return "Estrategista";
      case 8: return "Mestre";
      case 9: return "Grão-Mestre";
      case 10: return "Lenda";
      default: return "Bot";
    }
  }
}
