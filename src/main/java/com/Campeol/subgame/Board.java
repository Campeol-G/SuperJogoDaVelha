package com.Campeol.subgame;

import java.io.Serializable;

import com.Campeol.subgame.exception.SubGameException;
import com.Campeol.ui.Theme;
import com.googlecode.lanterna.SGR;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;

public class Board implements Serializable {

  private Integer row;
  private Integer column;
  private Piece[][] boardPlace;

  public Board(Integer row, Integer column) {
    this.row = row;
    this.column = column;
    this.boardPlace = new Piece[row][column];
  }

  public Integer getRow() {
    return row;
  }

  public Integer getColumn() {
    return column;
  }

  public void render(TextGraphics txt, int offsetRow, int offsetColumn, TextColor highlight) {
    render(txt, offsetRow, offsetColumn, -1, -1, highlight);
  }

  /**
   * Render com highlight SOMENTE na casa (3x1), sem pintar o subgame.
   * selRow/selCol = -1 significa sem seleção. Divisórias │───┼ preservadas.
   */
  public void render(TextGraphics txt, int offsetRow, int offsetColumn, int selRow, int selCol,
      TextColor macroBg) {
    for (int i = 0; i < row; i++) {
      for (int j = 0; j < column; j++) {
        Piece piece = boardPlace[i][j];
        String content = piece != null ? piece.toString() : " ";
        boolean selected = (i == selRow && j == selCol);
        int x = j * 4 + offsetColumn;
        int y = i * 2 + offsetRow;

        // conteúdo da casa (3 cols: " X ")
        txt.clearModifiers();
        txt.setBackgroundColor(null);
        txt.setForegroundColor(null);
        if (selected) {
          txt.setBackgroundColor(Theme.HOVER_BG);
          txt.setForegroundColor(Theme.HOVER_FG);
          txt.enableModifiers(SGR.BOLD);
        } else if (piece != null) {
          if (piece.getXorO() == 'X') {
            txt.setForegroundColor(Theme.X_FG);
            txt.enableModifiers(SGR.BOLD);
          } else {
            txt.setForegroundColor(Theme.O_FG);
          }
        } else {
          txt.setForegroundColor(Theme.DIM_FG);
        }
        txt.putString(x, y, " " + content + " ");
        txt.clearModifiers();
        txt.setBackgroundColor(null);
        txt.setForegroundColor(null);
        // separador vertical SEMPRE dim, nunca com highlight (preserva divisória)
        txt.setForegroundColor(Theme.DIM_FG);
        String sep = (j < column - 1) ? "│" : "";
        if (!sep.isEmpty()) {
          txt.putString(x + 3, y, sep);
        }
        txt.clearModifiers();
        txt.setBackgroundColor(null);
        txt.setForegroundColor(null);
      }
      // separador horizontal SEMPRE dim
      txt.setForegroundColor(Theme.DIM_FG);
      txt.setBackgroundColor(null);
      String sep = (i < row - 1) ? "───┼───┼───" : "";
      if (!sep.isEmpty()) {
        txt.putString(0 + offsetColumn, i * 2 + 1 + offsetRow, sep);
      }
      txt.clearModifiers();
      txt.setBackgroundColor(null);
      txt.setForegroundColor(null);
    }
    txt.clearModifiers();
    txt.setBackgroundColor(null);
    txt.setForegroundColor(null);
  }

  public void clearBoard(TextGraphics txt, int offsetRow, int offsetColumn) {
    txt.setBackgroundColor(null);
    for (int i = 0; i < row; i++) {
      for (int j = 0; j < column; j++) {
        txt.putString(j * 4 + offsetColumn, i * 2 + offsetRow, "    ");
      }
      String sep = (i < row - 1) ? "   " + "    ".repeat(column - 2) + "     " : "";
      txt.putString(0 + offsetColumn, i * 2 + 1 + offsetRow, sep);
    }
  }

  public void placePiece(Player player, Position position) {
    if (thereIsAPiece(position)) {
      throw new SubGameException(com.Campeol.ui.I18n.t("occupied"));
    }
    boardPlace[position.getRow()][position.getColumn()] = player.getPiece();
  }

  public boolean isFull() {
    for (int i = 0; i < row; i++) {
      for (int j = 0; j < column; j++) {
        if (boardPlace[i][j] == null) {
          return false;
        }
      }
    }
    return true;
  }

  public boolean thereIsAPiece(Position position) {
    if (!positionExist(position)) {
      throw new SubGameException(com.Campeol.ui.I18n.t("invalid.pos"));
    }
    return boardPlace[position.getRow()][position.getColumn()] != null;
  }

  public boolean positionExist(Position position) {
    return position.getRow() >= 0 && position.getRow() < row && position.getColumn() >= 0
        && position.getColumn() < column;
  }

  private boolean samePiece(int r1, int c1, int r2, int c2, int r3, int c3) {
    Piece a = boardPlace[r1][c1];
    Piece b = boardPlace[r2][c2];
    Piece c = boardPlace[r3][c3];
    return a != null && a.equals(b) && b.equals(c);
  }

  public boolean checkDiagnoal() {
    return samePiece(0, 0, 1, 1, 2, 2)
        || samePiece(2, 0, 1, 1, 0, 2);
  }

  public boolean checkRows() {
    return samePiece(0, 0, 0, 1, 0, 2)
        || samePiece(1, 0, 1, 1, 1, 2)
        || samePiece(2, 0, 2, 1, 2, 2);
  }

  public boolean checkColumns() {
    return samePiece(0, 0, 1, 0, 2, 0)
        || samePiece(0, 1, 1, 1, 2, 1)
        || samePiece(0, 2, 1, 2, 2, 2);
  }

  public boolean testEndGame() {
    if (checkColumns() || checkRows() || checkDiagnoal()) {
      return true;
    } else {
      return false;
    }
  }
}
