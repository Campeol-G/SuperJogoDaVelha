package com.Campeol.net;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Log em arquivo para erros de rede/jogo.
 * Nunca imprime no stdout — stdout corrompe a tela alternativa do Lanterna.
 */
public final class NetLog {
  private static final Object LOCK = new Object();
  private static File logFile;

  private NetLog() {}

  private static File file() {
    if (logFile != null) return logFile;
    synchronized (LOCK) {
      if (logFile != null) return logFile;
      try {
        String home = System.getProperty("user.home");
        File dir = new File(home != null ? home : ".", ".superjogo");
        dir.mkdirs();
        logFile = new File(dir, "net.log");
      } catch (Exception e) {
        try {
          logFile = File.createTempFile("superjogo-net", ".log");
        } catch (Exception ignored) {
          return null;
        }
      }
      return logFile;
    }
  }

  public static void log(String msg) {
    File f = file();
    if (f == null) return;
    synchronized (LOCK) {
      try (PrintWriter w = new PrintWriter(new FileWriter(f, true))) {
        String ts = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        w.println("[" + ts + "] " + msg);
      } catch (Exception ignored) {
      }
    }
  }

  public static void log(String ctx, Throwable t) {
    File f = file();
    if (f == null) return;
    synchronized (LOCK) {
      try (PrintWriter w = new PrintWriter(new FileWriter(f, true))) {
        String ts = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        w.println("[" + ts + "] " + ctx + ": " + t);
        t.printStackTrace(w);
      } catch (Exception ignored) {
      }
    }
  }
}
