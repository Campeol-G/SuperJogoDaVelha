package com.Campeol.subgame;

import java.io.IOException;

import com.googlecode.lanterna.graphics.TextGraphics;
import java.util.concurrent.TimeUnit;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.Terminal;

public class subUI {
  public static void printBoard(Match match) {
    try (Terminal terminal = new DefaultTerminalFactory().createTerminal()) {
      try (Screen screen = new TerminalScreen(terminal)) {
        TextGraphics txt = screen.newTextGraphics();
        screen.startScreen();
        screen.clear();
        match.render(txt);
        screen.refresh();
        try {
          Thread.sleep(TimeUnit.SECONDS.toMillis(2));
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
        screen.stopScreen();
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public static void endGame(Match match) {
    try (Terminal terminal = new DefaultTerminalFactory().createTerminal()) {
      try (Screen screen = new TerminalScreen(terminal)) {
        TextGraphics txt = screen.newTextGraphics();
        screen.startScreen();
        screen.clear();
        match.endGame(txt);
        screen.refresh();
        try {
          Thread.sleep(TimeUnit.SECONDS.toMillis(2));
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
        screen.stopScreen();
      }
    } catch (IOException e) {
      e.printStackTrace();
    }

  }
}
