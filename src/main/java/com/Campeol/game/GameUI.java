package com.Campeol.game;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import com.Campeol.MatchStatus;
import com.Campeol.subgame.Match;
import com.Campeol.subgame.Position;
import com.Campeol.subgame.exception.subGameException;
import com.googlecode.lanterna.TerminalPosition;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;

public class GameUI implements AutoCloseable {

  private Screen screen;
  private Match match;
  private TextGraphics txt;

  public GameUI(Match match) throws IOException {
    this.match = match;
    this.screen = new TerminalScreen(new DefaultTerminalFactory().createTerminal());
    this.txt = screen.newTextGraphics();
    screen.startScreen();
  }

  public void render() throws IOException {
    match.render(txt);
    screen.refresh();
  }

  public Position readPosition() throws IOException {
    int row = 0;
    int column = 0;
    Position pos = new Position(row, column);

    screen.clear();
    render();
    KeyStroke keyPressed = null;
    screen.setCursorPosition(new TerminalPosition(column * 4 + 1, row * 2));
    screen.refresh();
    while (keyPressed == null || keyPressed.getKeyType() != KeyType.Enter
        && keyPressed.getKeyType() != KeyType.Escape) {
      keyPressed = screen.readInput();
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
          screen.clear();
          match.setMatchStatus(MatchStatus.INTERRUPTED);
          break;
        default:
          if (keyPressed != null
              && (keyPressed.getKeyType() != KeyType.Enter && keyPressed.getKeyType() != KeyType.Escape)) {
            throw new subGameException("Invalid input!");
          }
      }
    }
    return pos;
  }

  public void showErro(String msg) throws IOException {
    screen.clear();
    txt.putString(4, 4, msg);
    screen.refresh();
    try {
      Thread.sleep(TimeUnit.SECONDS.toMillis(2));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }

  }

  public void endGame() throws IOException {
    screen.clear();
    match.endGame(txt);
    screen.refresh();
    try {
      Thread.sleep(TimeUnit.SECONDS.toMillis(2));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }

  }

  public void close() throws IOException {
    screen.stopScreen();
  }
}
