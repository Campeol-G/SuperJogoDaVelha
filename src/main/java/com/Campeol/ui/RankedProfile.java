package com.Campeol.ui;

import java.util.prefs.Preferences;

/**
 * Progressão do modo Rankeado local (vs Bot).
 * L1-L5: 2 vitórias para subir. L6-L9: 3 vitórias. L10: winstreak + 3 vidas.
 * Derrota: -1 vitória (piso 0). 0v + derrota = cai 1 nível (com 1v no anterior).
 * L1 0v + derrota = nada. Empate = nada.
 * L10: vitória = streak+1 e mantém vidas; derrota = streak 0 e vidas-1.
 * Vidas zeradas = cai para L9 com 1v. Vidas recarregam só ao CHEGAR no L10.
 */
public final class RankedProfile {
  public static final int MIN_LEVEL = 1;
  public static final int MAX_LEVEL = 10;
  public static final int LIVES_FULL = 3;

  public enum Result {
    NONE,
    PROGRESS,
    PROMOTED,
    DEMOTED,
    STAY,
    STREAK,
    LIVES_LOST,
    FELL_FROM_10
  }

  private int level;
  private int wins;
  private int streak;
  private int lives;

  public RankedProfile() {
    this(1, 0, 0, LIVES_FULL);
  }

  public RankedProfile(int level, int wins, int streak, int lives) {
    this.level = Math.min(MAX_LEVEL, Math.max(MIN_LEVEL, level));
    this.wins = Math.max(0, wins);
    this.streak = Math.max(0, streak);
    this.lives = Math.max(0, Math.min(LIVES_FULL, lives));
    if (this.level < MAX_LEVEL) {
      this.streak = 0;
    }
    if (this.level < MAX_LEVEL) {
      this.lives = LIVES_FULL;
    }
  }

  public static int winsNeeded(int level) {
    if (level >= MAX_LEVEL) return Integer.MAX_VALUE;
    if (level >= 6) return 3;
    return 2;
  }

  public int winsNeeded() {
    return winsNeeded(this.level);
  }

  public int getLevel() { return level; }
  public int getWins() { return wins; }
  public int getStreak() { return streak; }
  public int getLives() { return lives; }

  /** Vitória do humano. Retorna o que aconteceu para a UI anunciar. */
  public Result registerWin() {
    if (level >= MAX_LEVEL) {
      streak++;
      return Result.STREAK;
    }
    wins++;
    if (wins >= winsNeeded(level)) {
      level++;
      wins = 0;
      if (level >= MAX_LEVEL) {
        level = MAX_LEVEL;
        streak = 0;
        lives = LIVES_FULL;
      }
      return Result.PROMOTED;
    }
    return Result.PROGRESS;
  }

  /** Derrota do humano. */
  public Result registerLoss() {
    if (level >= MAX_LEVEL) {
      streak = 0;
      lives--;
      if (lives <= 0) {
        level = MAX_LEVEL - 1;
        wins = 1;
        streak = 0;
        lives = LIVES_FULL;
        return Result.FELL_FROM_10;
      }
      return Result.LIVES_LOST;
    }
    if (wins > 0) {
      wins--;
      return Result.STAY;
    }
    if (level > MIN_LEVEL) {
      level--;
      wins = 1;
      return Result.DEMOTED;
    }
    return Result.NONE;
  }

  public Result registerDraw() {
    return Result.NONE;
  }

  public void reset() {
    level = MIN_LEVEL;
    wins = 0;
    streak = 0;
    lives = LIVES_FULL;
  }

  /** Tutorial já foi exibido ao menos uma vez? (só mostra na 1ª entrada). */
  public static boolean isHelpSeen() {
    try {
      return prefs().getBoolean("rank.help.seen", false);
    } catch (Exception e) {
      return false;
    }
  }

  public static void setHelpSeen() {
    try {
      Preferences p = prefs();
      p.putBoolean("rank.help.seen", true);
      p.flush();
    } catch (Exception ignored) {
    }
  }

  // ---------- persistência (Preferences, mesma base do ProfileStore) ----------
  private static Preferences prefs() {
    return Preferences.userNodeForPackage(RankedProfile.class);
  }

  public void save() {
    try {
      Preferences p = prefs();
      p.putInt("rank.level", level);
      p.putInt("rank.wins", wins);
      p.putInt("rank.streak", streak);
      p.putInt("rank.lives", lives);
      p.flush();
    } catch (Exception ignored) {
    }
  }

  public static RankedProfile load() {
    try {
      Preferences p = prefs();
      int level = p.getInt("rank.level", 1);
      int wins = p.getInt("rank.wins", 0);
      int streak = p.getInt("rank.streak", 0);
      int lives = p.getInt("rank.lives", LIVES_FULL);
      RankedProfile r = new RankedProfile(level, wins, streak, lives);
      // clamp de segurança: wins nunca acima do necessário
      if (r.level < MAX_LEVEL && r.wins >= winsNeeded(r.level)) {
        r.wins = winsNeeded(r.level) - 1;
      }
      return r;
    } catch (Exception e) {
      return new RankedProfile();
    }
  }

  /** Linha da barrinha: ex. "Nv4 Veterano [██░] 1/2 p/ Nv5" ou L10 streak/vidas. */
  public String progressBar() {
    if (level >= MAX_LEVEL) {
      return I18n.t("ranked.bar10", streak, lives);
    }
    int need = winsNeeded(level);
    StringBuilder bar = new StringBuilder("[");
    for (int i = 0; i < need; i++) {
      bar.append(i < wins ? "█" : "░");
    }
    bar.append("]");
    return I18n.t("ranked.bar", level, BotNames.name(level), bar.toString(), wins, need, level + 1);
  }

  public String hudLine() {
    if (level >= MAX_LEVEL) {
      return I18n.t("ranked.hud10", level, BotNames.name(level), streak, lives);
    }
    return I18n.t("ranked.hud", level, BotNames.name(level));
  }
}
