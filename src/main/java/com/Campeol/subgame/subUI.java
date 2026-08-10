package com.Campeol.subgame;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import com.Campeol.subgame.exception.subGameException;
import com.googlecode.lanterna.TerminalPosition;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
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

  public static void exceptions(String msg) {
    try (Terminal terminal = new DefaultTerminalFactory().createTerminal()) {
      try (Screen screen = new TerminalScreen(terminal)) {
        TextGraphics txt = screen.newTextGraphics();
        screen.startScreen();
        screen.clear();
        txt.putString(4, 4, msg);
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

  public static Position readPosition(Match match) {
    int row = 0;
    int column = 0;
    Position pos = new Position(row, column);
    try (Terminal terminal = new DefaultTerminalFactory().createTerminal()) {
      try (Screen screen = new TerminalScreen(terminal)) {
        screen.startScreen();
        screen.clear();
        TextGraphics txt = screen.newTextGraphics();
        match.render(txt);
        KeyStroke keyPressed = null;
        screen.setCursorPosition(new TerminalPosition(1, 0));
        screen.refresh();
        while (keyPressed == null || keyPressed.getKeyType() != KeyType.Enter) {
          keyPressed = terminal.readInput();
          switch (keyPressed.getKeyType()) {
            case ArrowRight:
              column++;
              if (column > 2) {
                column--;
              }
              screen.setCursorPosition(new TerminalPosition(column * 4 + 1, row * 2));
              pos.setPosition(row, column);
              screen.refresh();
              break;
            case ArrowLeft:
              column--;
              if (column < 0) {
                column++;
              }
              screen.setCursorPosition(new TerminalPosition(column * 4 + 1, row * 2));
              pos.setPosition(row, column);
              screen.refresh();
              break;
            case ArrowUp:
              row--;
              if (row < 0) {
                row++;
              }
              screen.setCursorPosition(new TerminalPosition(column * 4 + 1, row * 2));
              pos.setPosition(row, column);
              screen.refresh();
              break;
            case ArrowDown:
              row++;
              if (row > 2) {
                row--;
              }
              screen.setCursorPosition(new TerminalPosition(column * 4 + 1, row * 2));
              pos.setPosition(row, column);
              screen.refresh();
              break;
            case Escape:
              throw new subGameException("Game interrupted by player: " + match.getCurrentPlayer().getPiece());
            default:
              if (keyPressed != null && keyPressed.getKeyType() != KeyType.Enter) {
                throw new subGameException("Invalid input!");
              }
          }
        }
        screen.stopScreen();
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
    return pos;
  }

}
