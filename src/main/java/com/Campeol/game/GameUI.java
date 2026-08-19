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

  private static final int MIN_COLS = 50;
  private static final int MIN_ROWS = 24;
  private Screen screen;
  private Terminal terminal;
  private TextGraphics txt;
  GameBoard gb;

  public GameUI() throws IOException, InterruptedException {
    this.terminal = new DefaultTerminalFactory().createTerminal();
    this.screen = new TerminalScreen(terminal);
    screen.startScreen();
    this.txt = screen.newTextGraphics();
    gb = new GameBoard();
    waitForEnoughSize();

  }

  public char startGame() throws IOException, InterruptedException {
    screen.clear();
    char kp;
    do {
      waitForEnoughSize();
      txt.putString(0, 0, "Chose a piece to play[X/O]: ");
      screen.setCursorPosition(new TerminalPosition(0, 1));
      screen.refresh();
      KeyStroke keyPressed = screen.readInput();
      if (keyPressed.getKeyType() == KeyType.Escape) {
        gb.setGameStatus(MatchStatus.INTERRUPTED);
        endGame();
        return ' ';
      }
      Character c = keyPressed.getCharacter();
      kp = c == null ? ' ' : Character.toUpperCase(c);
    } while (kp != 'X' && kp != 'O');
    return kp;

  }

  public void startPlayer(char XorO) {
    gb.startPlayer(XorO);
  }

  public void render() throws IOException, InterruptedException {
    waitForEnoughSize();
    gb.renderAllGames(txt);
    screen.refresh();
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
          gb.setGameStatus(MatchStatus.INTERRUPTED);
          endGame();
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
          txt.putString(2, 19, "You can't chose a already finished game");
          screen.refresh();
          long endTime = System.currentTimeMillis() + 2000;
          while (System.currentTimeMillis() < endTime) {
            if (screen.pollInput() != null) {
              break;
            }
            Thread.sleep(50);
          }
          txt.putString(2, 19, "                                       ");
          screen.refresh();
          keyPressed = null;
        }
      }
    }
    return match;
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
          gb.setGameStatus(MatchStatus.INTERRUPTED);
          endGame();
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

  public void makeMove(Match match, Position position) throws IOException, InterruptedException {
    gb.makeMove(match, position);
    if (gb.getMatchFinished()) {
      endMatch(match);
    }
  }

  public Match changeMatch(Position pos) throws IOException, InterruptedException {
    if (gb.getGamePlaces(pos.getRow(), pos.getColumn()).getMatchStatus() != MatchStatus.IN_PROGRESS) {
      return bigMove();
    } else {
      return gb.getGamePlaces(pos.getRow(), pos.getColumn());

    }
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

  public void endMatch(Match match) throws IOException, InterruptedException {
    match.render(txt, gb.getCurrentPlayer(), null);
    screen.refresh();
  }

  public void endGame() throws IOException, InterruptedException {
    screen.clear();
    if (gb.getStatus() == MatchStatus.INTERRUPTED) {
      gb.setGameStatus(MatchStatus.INTERRUPTED);
      txt.putString(18, 6, gb.getStatus().toString());
      if (gb.getCurrentPlayer() == null) {
        txt.putString(18, 7, "BY: Player 1");
      } else {
        txt.putString(21, 7, "BY: " + gb.getCurrentPlayer().getPiece());
      }
    }
    if (gb.getStatus() == MatchStatus.VICTORY) {
      txt.putString(18, 6, gb.getStatus().toString());
      txt.putString(18, 7, "BY:" + gb.getWinner().getPiece());
    } else if (gb.getStatus() == MatchStatus.DRAW) {
      txt.putString(18, 6, gb.getStatus().toString());
    }
    screen.refresh();
    terminal.setCursorVisible(false);
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

  public void close() throws IOException {
    screen.stopScreen();
  }

  public Match getMatch(int i, int j) {
    return gb.getGamePlaces(i, j);
  }

  public MatchStatus getStatus() {
    return gb.getStatus();
  }

  private boolean isSizeOk() throws IOException {
    return terminal.getTerminalSize().getRows() >= MIN_ROWS && terminal.getTerminalSize().getColumns() >= MIN_COLS;
  }

  private void waitForEnoughSize() throws IOException, InterruptedException {
    while (!isSizeOk()) {
      screen.clear();
      txt.putString(0, 0, "Redimensione a janela");
      txt.putString(0, 1, "Minimo: 50x24");
      screen.refresh();
      long endTime = System.currentTimeMillis() + 50;
      KeyStroke key = null;
      while (System.currentTimeMillis() < endTime) {
        key = screen.pollInput();
        if (key != null) {
          break;
        }
        Thread.sleep(50);
      }

    }
  }

}
