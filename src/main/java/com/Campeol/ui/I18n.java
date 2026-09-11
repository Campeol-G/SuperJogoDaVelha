package com.Campeol.ui;

import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * Internacionalização PT/EN via ResourceBundle.
 * Arquivos: messages_pt.properties / messages_en.properties
 */
public final class I18n {
  private static Locale locale = new Locale("pt");
  private static ResourceBundle bundle = null;

  private I18n() {}

  public static void setLocale(String lang) {
    if (lang == null) lang = "pt";
    lang = lang.toLowerCase().startsWith("en") ? "en" : "pt";
    locale = new Locale(lang);
    bundle = null;
  }

  public static String getLang() {
    return locale.getLanguage();
  }

  public static Locale getLocale() {
    return locale;
  }

  private static ResourceBundle bundle() {
    if (bundle == null) {
      try {
        bundle = ResourceBundle.getBundle("messages", locale);
      } catch (MissingResourceException e) {
        bundle = ResourceBundle.getBundle("messages", new Locale("pt"));
      }
    }
    return bundle;
  }

  public static void reset() {
    bundle = null;
  }

  public static String t(String key) {
    try {
      return bundle().getString(key);
    } catch (MissingResourceException e) {
      return key;
    }
  }

  public static String t(String key, Object... args) {
    String pattern = t(key);
    try {
      return String.format(pattern, args);
    } catch (Exception e) {
      return pattern;
    }
  }
}
