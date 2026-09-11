package com.Campeol.subgame;

import java.io.Serializable;

import com.Campeol.MatchStatus;
import com.Campeol.ui.I18n;
import com.Campeol.ui.Theme;
import com.googlecode.lanterna.SGR;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;

public class Match implements Serializable {

  private Board board;
  private Player winner;
  private MatchStatus status;
  private int rowPosition;
  private int columnPosition;
  private int gridRow;
  private int gridCol;
  private Position lastMove;

  public Match(int row, int column, int gridRow, int gridCol) {
    board = new Board(3, 3);
    status = MatchStatus.IN_PROGRESS;
    this.rowPosition = row;
    this.columnPosition = column;
    this.gridRow = gridRow;
    this.gridCol = gridCol;
  }

  public void makeMove(Player player, Position position) {
    board.placePiece(player, position);
    this.lastMove = position;
    if (board.testEndGame()) {
      status = MatchStatus.VICTORY;
      winner = player;
    } else if (board.isFull()) {
      status = MatchStatus.DRAW;
    }
  }

  // Arte clássica em letras (vãos ímpares → centro 5.0 exato em todas as linhas).
  private static final String[] BIG_X = {
      "X         X",
      "  X     X  ",
      "    X X    ",
      "  X     X  ",
      "X         X"
  };

  private static final String[] BIG_O = {
      "   OOOOO   ",
      "  O     O  ",
      " O       O ",
      "  O     O  ",
      "   OOOOO   "
  };

  public void render(TextGraphics txt, Player currentPlayer, TextColor highlight) {
    render(txt, currentPlayer, highlight, -1, -1, 0, 0);
  }

  /**
   * Render com origem (centralização) + seleção de célula.
   * ox/oy = origem do tabuleiro lógico no terminal.
   */
  public void render(TextGraphics txt, Player currentPlayer, TextColor highlight, int selRow, int selCol,
      int ox, int oy) {
    int absRow = rowPosition + oy;
    int absCol = columnPosition + ox;
    if (status != MatchStatus.IN_PROGRESS) {
      board.clearBoard(txt, absRow, absCol);
      txt.clearModifiers();
      txt.setBackgroundColor(null);
      // fundo sutil monocromático para vencido
      TextColor bg = Theme.WON_BG;
      if (status == MatchStatus.VICTORY && winner != null) {
        txt.setForegroundColor(winner.getPiece().getXorO() == 'X' ? Theme.X_FG : Theme.O_FG);
        txt.enableModifiers(SGR.BOLD);
        String[] art = winner.getPiece().getXorO() == 'X' ? BIG_X : BIG_O;
        // A área tem sempre 5 linhas; linhas além de art.length viram branco
        // (evita IndexOutOfBounds se a arte for editada com menos linhas).
        for (int i = 0; i < 5; i++) {
          txt.setBackgroundColor(bg);
          txt.putString(absCol, absRow + i, "           ");
          txt.putString(absCol, absRow + i, i < art.length ? art[i] : "           ");
        }
      } else {
        // DRAW centralizado
        String label = I18n.t("draw.label");
        int lx = absCol + Math.max(0, (11 - label.length()) / 2);
        txt.setForegroundColor(Theme.DIM_FG);
        txt.enableModifiers(SGR.BOLD);
        for (int i = 0; i < 5; i++) {
          txt.setBackgroundColor(null);
          txt.putString(absCol, absRow + i, "           ");
        }
        txt.setBackgroundColor(bg);
        txt.putString(absCol, absRow + 2, "           ");
        txt.putString(lx, absRow + 2, label);
      }
      txt.clearModifiers();
      txt.setBackgroundColor(null);
      txt.setForegroundColor(null);
    } else {
      board.render(txt, absRow, absCol, selRow, selCol, highlight);
    }
  }

  public MatchStatus getMatchStatus() {
    return status;
  }

  public void setMatchStatus(MatchStatus status) {
    this.status = status;
  }

  public int getIntRowPosition() {
    return rowPosition;
  }

  public int getIntColumnPosition() {
    return columnPosition;
  }

  public int getGridRow() {
    return gridRow;
  }

  public int getGridCol() {
    return gridCol;
  }

  public Player getWinner() {
    return winner;
  }

  public Position getLastMove() {
    return lastMove;
  }

  public void setLastMove(Position lastMove) {
    this.lastMove = lastMove;
  }

}
