package com.Campeol.game;

import java.io.IOException;

import com.Campeol.MatchStatus;
import com.Campeol.subgame.Match;
import com.Campeol.subgame.Position;
import com.googlecode.lanterna.TerminalPosition;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.Terminal;

public class GameUI implements AutoCloseable {

  private Screen screen;
  private Terminal terminal;
  private TextGraphics txt;
  GameBoard gb;

  public GameUI() throws IOException {
    this.terminal = new DefaultTerminalFactory().createTerminal();
    this.screen = new TerminalScreen(terminal);
    this.txt = screen.newTextGraphics();
    gb = new GameBoard();
    screen.startScreen();
  }

  public void render() throws IOException {
    gb.renderAllGames(txt);
    screen.refresh();
  }

  public char startGame() throws IOException {
    screen.clear();
    char kp;
    do {
      txt.putString(0, 0, "Chose a piece to play[X/O]: ");
      screen.setCursorPosition(new TerminalPosition(0, 1));
      screen.refresh();
      KeyStroke keyPressed = screen.readInput();
      Character c = keyPressed.getCharacter();
      kp = c == null ? ' ' : Character.toUpperCase(c);
    } while (kp != 'X' && kp != 'O');
    return kp;

  }

  public Position readInput(Match match) throws IOException, InterruptedException {
    int row = 0;
    int column = 0;
    Position pos = new Position(row, column);

    screen.clear();
    render();
    gb.getGamePlaces(row, column).render(txt, gb.getCurrentPlayer(), TextColor.ANSI.BLACK_BRIGHT);
    KeyStroke keyPressed = null;
    screen.setCursorPosition(new TerminalPosition(match.getIntColumnPosition() + (column * 4 + 1),
        match.getIntRowPosition() + (row * 2)));
    screen.refresh();
    while (keyPressed == null || keyPressed.getKeyType() != KeyType.Enter
        && keyPressed.getKeyType() != KeyType.Escape) {
      keyPressed = screen.readInput();
      switch (keyPressed.getKeyType()) {
        case ArrowRight:
          gb.getGamePlaces(row, column).render(txt, gb.getCurrentPlayer(), null);
          screen.refresh();
          column++;
          if (column > 2) {
            column--;
          }
          screen.setCursorPosition(new TerminalPosition(match.getIntColumnPosition() + (column * 4 + 1),
              match.getIntRowPosition() + (row * 2)));
          pos.setPosition(row, column);
          gb.getGamePlaces(row, column).render(txt, gb.getCurrentPlayer(), TextColor.ANSI.BLACK_BRIGHT);
          screen.refresh();
          break;
        case ArrowLeft:
          gb.getGamePlaces(row, column).render(txt, gb.getCurrentPlayer(), null);
          screen.refresh();
          column--;
          if (column < 0) {
            column++;
          }
          screen.setCursorPosition(new TerminalPosition(match.getIntColumnPosition() + (column * 4 + 1),
              match.getIntRowPosition() + (row * 2)));
          pos.setPosition(row, column);
          gb.getGamePlaces(row, column).render(txt, gb.getCurrentPlayer(), TextColor.ANSI.BLACK_BRIGHT);
          screen.refresh();
          break;
        case ArrowUp:
          gb.getGamePlaces(row, column).render(txt, gb.getCurrentPlayer(), null);
          screen.refresh();
          row--;
          if (row < 0) {
            row++;
          }
          screen.setCursorPosition(new TerminalPosition(match.getIntColumnPosition() + (column * 4 + 1),
              match.getIntRowPosition() + (row * 2)));
          pos.setPosition(row, column);
          gb.getGamePlaces(row, column).render(txt, gb.getCurrentPlayer(), TextColor.ANSI.BLACK_BRIGHT);
          screen.refresh();
          break;
        case ArrowDown:
          gb.getGamePlaces(row, column).render(txt, gb.getCurrentPlayer(), null);
          screen.refresh();
          row++;
          if (row > 2) {
            row--;
          }
          screen.setCursorPosition(new TerminalPosition(match.getIntColumnPosition() + (column * 4 + 1),
              match.getIntRowPosition() + (row * 2)));
          pos.setPosition(row, column);
          gb.getGamePlaces(row, column).render(txt, gb.getCurrentPlayer(), TextColor.ANSI.BLACK_BRIGHT);
          screen.refresh();
          break;
        case Escape:
          screen.clear();
          match.setMatchStatus(MatchStatus.INTERRUPTED);
          break;
        default:
          if (keyPressed != null
              && (keyPressed.getKeyType() != KeyType.Enter && keyPressed.getKeyType() != KeyType.Escape)) {
            txt.putString(15, 19, "Invalid Input!");
            screen.refresh();
            long endTime = System.currentTimeMillis() + 2000;
            while (System.currentTimeMillis() < endTime) {
              if (screen.pollInput() != null) {
                break;
              }
              Thread.sleep(50);
            }
            txt.putString(15, 19, "               ");
            screen.refresh();
            keyPressed = null;
          }

      }
    }
    return pos;
  }

  public void showErro(String msg) throws IOException, InterruptedException {
    screen.clear();
    txt.putString(4, 4, msg);
    screen.refresh();
    long endTime = System.currentTimeMillis() + 2000;
    KeyStroke key = null;
    while (System.currentTimeMillis() < endTime) {
      key = screen.pollInput();
      if (key != null) {
        break;
      }
      Thread.sleep(50);
    }
  }

  // TODO metodo para printar o fim do jogo
  /*
   * public void endGame(Match match) throws IOException, InterruptedException {
   * match.endMatch(txt, gb.getCurrentPlayer());
   * screen.refresh();
   * long endTime = System.currentTimeMillis() + 2000;
   * KeyStroke key = null;
   * while (System.currentTimeMillis() < endTime) {
   * key = screen.pollInput();
   * if (key != null) {
   * break;
   * }
   * Thread.sleep(50);
   * }
   * }
   */

  public void endMatch(Match match) throws IOException, InterruptedException {
    match.render(txt, gb.getCurrentPlayer(), null);
    screen.refresh();
  }

  public void close() throws IOException {
    screen.stopScreen();
  }

  public Match getMatch(int i, int j) {
    return gb.getGamePlaces(i, j);
  }

  public void startPlayer(char XorO) {
    gb.startPlayer(XorO);
  }

  public void makeMove(Match match, Position position) throws IOException, InterruptedException {
    gb.makeMove(match, position);
    if (gb.getMatchFinished()) {
      endMatch(match);
    }
  }

  public Match bigMove() throws IOException, InterruptedException {
    int row = 1;
    int column = 1;
    Match match = gb.getGamePlaces(row, column);
    screen.clear();
    render();
    KeyStroke keyPressed = null;
    screen.setCursorPosition(new TerminalPosition(21,
        9));
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
          screen.setCursorPosition(new TerminalPosition(column * 14 + 7,
              row * 6 + 3));
          match = gb.getGamePlaces(row, column);
          screen.refresh();
          break;
        case ArrowLeft:
          column--;
          if (column < 0) {
            column++;
          }
          screen.setCursorPosition(new TerminalPosition(column * 14 + 7,
              row * 6 + 3));
          match = gb.getGamePlaces(row, column);
          screen.refresh();
          break;
        case ArrowUp:
          row--;
          if (row < 0) {
            row++;
          }
          screen.setCursorPosition(new TerminalPosition(column * 14 + 7,
              row * 6 + 3));
          match = gb.getGamePlaces(row, column);
          screen.refresh();
          break;
        case ArrowDown:
          row++;
          if (row > 2) {
            row--;
          }
          screen.setCursorPosition(new TerminalPosition(column * 14 + 7,
              row * 6 + 3));
          match = gb.getGamePlaces(row, column);
          screen.refresh();
          break;
        case Escape:
          screen.clear();
          match.setMatchStatus(MatchStatus.INTERRUPTED);
          break;
        default:
          if (keyPressed != null
              && (keyPressed.getKeyType() != KeyType.Enter && keyPressed.getKeyType() != KeyType.Escape)) {
            txt.putString(15, 19, "Invalid Input!");
            screen.refresh();
            long endTime = System.currentTimeMillis() + 2000;
            while (System.currentTimeMillis() < endTime) {
              if (screen.pollInput() != null) {
                break;
              }
              Thread.sleep(50);
            }
            txt.putString(15, 19, "               ");
            screen.refresh();
            keyPressed = null;
          }
      }
      if (keyPressed != null && keyPressed.getKeyType() == KeyType.Enter) {
        if (match.getMatchStatus() != MatchStatus.IN_PROGRESS) {
          keyPressed = null;
        }
      }
    }
    return match;
  }

  public Match changeMatch(Position pos) throws IOException, InterruptedException {
    if (gb.getGamePlaces(pos.getRow(), pos.getColumn()).getMatchStatus() != MatchStatus.IN_PROGRESS) {
      return bigMove();
    } else {
      return gb.getGamePlaces(pos.getRow(), pos.getColumn());

    }
  }

  public MatchStatus getStatus() {
    return gb.getStatus();
  }
}
