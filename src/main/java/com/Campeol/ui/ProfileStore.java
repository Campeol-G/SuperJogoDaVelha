package com.Campeol.ui;

import java.util.prefs.Preferences;

/**
 * Persiste nick + idioma no computador (Preferences API).
 * Linux: ~/.java, Windows: registry, macOS: plist. Sem arquivo manual.
 */
public final class ProfileStore {
  private static final String KEY_LANG = "lang";
  private static final String KEY_NICK = "nick";

  private ProfileStore() {}

  private static Preferences prefs() {
    return Preferences.userNodeForPackage(ProfileStore.class);
  }

  public static String getLang() {
    return prefs().get(KEY_LANG, null);
  }

  public static void setLang(String lang) {
    if (lang == null) return;
    prefs().put(KEY_LANG, lang.toLowerCase().startsWith("en") ? "en" : "pt");
    try { prefs().flush(); } catch (Exception ignored) {}
    I18n.setLocale(lang);
  }

  public static String getNick() {
    return prefs().get(KEY_NICK, null);
  }

  public static void setNick(String nick) {
    if (nick == null) nick = "";
    nick = UiUtils.clampNick(nick, 12);
    prefs().put(KEY_NICK, nick);
    try { prefs().flush(); } catch (Exception ignored) {}
  }
}
