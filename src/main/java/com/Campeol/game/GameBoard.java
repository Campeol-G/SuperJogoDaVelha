package com.Campeol.game;

import java.util.Random;

import com.Campeol.MatchStatus;
import com.Campeol.subgame.Match;
import com.Campeol.subgame.Piece;
import com.Campeol.subgame.Player;
import com.Campeol.subgame.Position;
import com.Campeol.ui.GameClock;
import com.Campeol.ui.I18n;
import com.Campeol.ui.Theme;
import com.googlecode.lanterna.SGR;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;

public class GameBoard {
  private static final int BOARD_COUNT = 3;
  private static final int COL_SPACING = 14;
  private static final int ROW_SPACING = 6;
  private static final int COL_OFFSET = 2;
  private static final int ROW_OFFSET = 1;

  protected Match[][] gamePlaces;
  protected Player p1, p2, currentPlayer, winner;
  private Integer turn;
  private MatchStatus status;
  private boolean matchFinished;
  private final GameClock clock = new GameClock();
  private boolean starterIsP1 = true;

  public GameBoard() {
    startAllGames();
    status = MatchStatus.IN_PROGRESS;
  }

  public void startPlayer(char XorO) {
    p1 = new Player(new Piece(XorO));
    p2 = new Player(new Piece(XorO == 'X' ? 'O' : 'X'));
    currentPlayer = p1;
    starterIsP1 = true;
    turn = 1;
    clock.startMatch();
    clock.startTurn(currentPlayer.getPiece().getXorO());
  }

  public void startPlayer() {
    Random random = new Random();
    boolean sorteio = random.nextBoolean();

    if (sorteio) {
      p1 = new Player(new Piece('X'));
      p2 = new Player(new Piece('O'));
    } else {
      p1 = new Player(new Piece('O'));
      p2 = new Player(new Piece('X'));
    }
    currentPlayer = p1;
    starterIsP1 = true;
    turn = 1;
    clock.startMatch();
    clock.startTurn(currentPlayer.getPiece().getXorO());
  }

  /** Nova rodada alternando quem começa (revanche). Mantém peças, troca o titular. */
  public void newRoundAlternateStarter() {
    startAllGames();
    status = MatchStatus.IN_PROGRESS;
    matchFinished = false;
    winner = null;
    turn = 1;
    starterIsP1 = !starterIsP1;
    if (p1 == null || p2 == null) {
      startPlayer();
      return;
    }
    currentPlayer = starterIsP1 ? p1 : p2;
    clock.startMatch();
    clock.startTurn(currentPlayer.getPiece().getXorO());
  }

  public void getPlayers(Player p1, Player p2) {
    this.p1 = p1;
    this.p2 = p2;
  }

  public void makeMove(Match match, Position position) {
    matchFinished = false;
    match.makeMove(currentPlayer, position);
    if (match.getMatchStatus() == MatchStatus.VICTORY || match.getMatchStatus() == MatchStatus.DRAW) {
      matchFinished = true;
    }
    if (gameOver()) {
      winner = currentPlayer;
      status = MatchStatus.VICTORY;
      clock.stop();
    } else if (draw()) {
      status = MatchStatus.DRAW;
      clock.stop();
    }
  }

  public boolean getMatchFinished() {
    return matchFinished;
  }

  public void changeTurn() {
    clock.stopTurn();
    currentPlayer = currentPlayer == p1 ? p2 : p1;
    turn++;
    if (currentPlayer != null) {
      clock.startTurn(currentPlayer.getPiece().getXorO());
    }
  }

  public void startAllGames() {
    gamePlaces = new Match[BOARD_COUNT][BOARD_COUNT];
    for (int i = 0; i < gamePlaces.length; i++) {
      for (int j = 0; j < gamePlaces[i].length; j++) {
        gamePlaces[i][j] = new Match(i * ROW_SPACING + ROW_OFFSET, j * COL_SPACING + COL_OFFSET, i, j);
      }
    }
  }

  public void divisors(TextGraphics txt) {
    divisors(txt, 0, 0, -1, -1, false);
  }

  /**
   * Moldura com origem (centralização) + highlight do macro selecionado.
   * activeR/activeC = macro sob o cursor em bigMove (-1 = nenhum).
   * forced = true quando o destino é forçado (só destaca), false = escolha livre.
   */
  private void drawTitle(TextGraphics txt, int ox, int oy, int width) {
    String rawTitle = I18n.getLang().equals("pt") ? I18n.t("title.pt") : I18n.t("title");
    String title = " " + rawTitle + " ";
    // width é o delta (42); a borda tem width+1 células (0..width). Centraliza nisso.
    int tx = ox + Math.max(0, (width + 1 - title.length()) / 2);
    txt.setForegroundColor(TextColor.ANSI.WHITE_BRIGHT);
    txt.enableModifiers(SGR.BOLD);
    txt.putString(tx, oy + 0, title);
    txt.clearModifiers();
    txt.setBackgroundColor(null);
    txt.setForegroundColor(null);
  }

  public void divisors(TextGraphics txt, int ox, int oy, int activeR, int activeC, boolean forced) {
    int width = BOARD_COUNT * COL_SPACING;
    int height = BOARD_COUNT * ROW_SPACING;

    txt.clearModifiers();
    txt.setBackgroundColor(null);
    txt.setForegroundColor(Theme.DIM_FG);

    // internal vertical dividers
    for (int c = 1; c < BOARD_COUNT; c++) {
      for (int i = 0; i <= height; i++) {
        txt.putString(ox + c * COL_SPACING, oy + i, "║");
      }
    }

    // internal horizontal dividers
    for (int r = 1; r < BOARD_COUNT; r++) {
      for (int i = 0; i <= width; i++) {
        txt.putString(ox + i, oy + r * ROW_SPACING, "═");
      }
    }

    // internal connectors
    for (int r = 1; r < BOARD_COUNT; r++) {
      for (int c = 1; c < BOARD_COUNT; c++) {
        txt.putString(ox + c * COL_SPACING, oy + r * ROW_SPACING, "╬");
      }
    }

    // external contour columns
    for (int i = 0; i <= height; i++) {
      txt.putString(ox + 0, oy + i, "║");
      txt.putString(ox + width, oy + i, "║");
    }

    // external contour rows
    for (int i = 0; i <= width; i++) {
      txt.putString(ox + i, oy + 0, "═");
      txt.putString(ox + i, oy + height, "═");
    }

    // external contour connectors (antes do título para não cortar letras)
    txt.putString(ox + 0, oy + 0, "╔");
    txt.putString(ox + width, oy + 0, "╗");
    for (int r = 1; r < BOARD_COUNT; r++) {
      txt.putString(ox + 0, oy + r * ROW_SPACING, "╠");
      txt.putString(ox + width, oy + r * ROW_SPACING, "╣");
    }
    txt.putString(ox + 0, oy + height, "╚");
    txt.putString(ox + width, oy + height, "╝");
    for (int c = 1; c < BOARD_COUNT; c++) {
      txt.putString(ox + c * COL_SPACING, oy + height, "╩");
    }
    // ╦ do topo só onde o título NÃO passa (evita cortar U/H)
    String rawTitlePre = I18n.getLang().equals("pt") ? I18n.t("title.pt") : I18n.t("title");
    String titlePre = " " + rawTitlePre + " ";
    int txPre = ox + Math.max(0, (width + 1 - titlePre.length()) / 2);
    int titleEnd = txPre + titlePre.length();
    for (int c = 1; c < BOARD_COUNT; c++) {
      int jx = ox + c * COL_SPACING;
      if (jx >= txPre && jx < titleEnd) {
        continue;
      }
      txt.putString(jx, oy + 0, "╦");
    }
    txt.setForegroundColor(Theme.DIM_FG);
    drawTitle(txt, ox, oy, width);

    // highlight do macro ativo: moldura invertida ao redor do macro 14x6
    if (activeR >= 0 && activeC >= 0) {
      int mx = ox + activeC * COL_SPACING;
      int my = oy + activeR * ROW_SPACING;
      int mw = COL_SPACING;
      int mh = ROW_SPACING;
      txt.setForegroundColor(TextColor.ANSI.WHITE_BRIGHT);
      if (forced) {
        txt.enableModifiers(SGR.BOLD, SGR.UNDERLINE);
      } else {
        txt.enableModifiers(SGR.BOLD);
      }
      // topo / base do macro
      for (int i = 1; i < mw; i++) {
        // não sobrescreve conectores ╬/╦/╩, só reforça topo/base internos
        if (mx + i == ox + 0 || mx + i == ox + width) continue;
        // topo
        if (my > oy || true) {
          // usa ━ para destacar sem quebrar a grade
          txt.putString(mx + i, my, "━");
        }
        txt.putString(mx + i, my + mh, "━");
      }
      // laterais do macro
      for (int i = 1; i < mh; i++) {
        txt.putString(mx, my + i, "┃");
        txt.putString(mx + mw, my + i, "┃");
      }
      txt.putString(mx, my, forced ? "┏" : "╔");
      txt.putString(mx + mw, my, forced ? "┓" : "╗");
      txt.putString(mx, my + mh, "┗");
      txt.putString(mx + mw, my + mh, "┛");
      txt.clearModifiers();
      txt.setBackgroundColor(null);
      txt.setForegroundColor(null);
      // redesenha título caso o highlight tenha coberto a borda superior
      drawTitle(txt, ox, oy, width);
    } else {
      txt.clearModifiers();
      txt.setBackgroundColor(null);
      txt.setForegroundColor(null);
    }
  }

  public void renderAllGames(TextGraphics txt) {
    renderAllGames(txt, 0, 0, -1, -1, null, -1, -1, null);
  }

  /**
   * Render completo com origem + highlights.
   * bigSelR/bigSelC: cursor do bigMove (escolha livre) → moldura externa.
   * activeMatch + selCellR/selCellC: cursor do readInput → casa 3x1 + preview
   *   do próximo macro forçado (selCell == destino do oponente).
   * macroBg: ignorado (mantido por compat — não pinta mais o macro inteiro
   *   para preservar as divisórias).
   */
  public void renderAllGames(TextGraphics txt, int ox, int oy, int bigSelR, int bigSelC,
      Match activeMatch, int selCellR, int selCellC, TextColor macroBg) {
    for (int i = 0; i < gamePlaces.length; i++) {
      for (int j = 0; j < gamePlaces.length; j++) {
        Match m = gamePlaces[i][j];
        if (activeMatch != null && activeMatch.getGridRow() == i && activeMatch.getGridCol() == j) {
          m.render(txt, currentPlayer, null, selCellR, selCellC, ox, oy);
        } else {
          m.render(txt, currentPlayer, null, -1, -1, ox, oy);
        }
      }
    }
    // moldura externa = próximo tabuleiro a ser jogado
    int outerR = -1;
    int outerC = -1;
    boolean outerForced = false;
    if (bigSelR >= 0 && bigSelC >= 0) {
      outerR = bigSelR;
      outerC = bigSelC;
      outerForced = false;
    } else if (activeMatch != null && selCellR >= 0 && selCellC >= 0
        && selCellR < BOARD_COUNT && selCellC < BOARD_COUNT) {
      Match target = gamePlaces[selCellR][selCellC];
      if (target.getMatchStatus() == com.Campeol.MatchStatus.IN_PROGRESS) {
        outerR = selCellR;
        outerC = selCellC;
        outerForced = true;
      } else {
        // destino finalizado → próxima jogada será livre, sem preview forçado
        outerR = -1;
        outerC = -1;
      }
    }
    divisors(txt, ox, oy, outerR, outerC, outerForced);
    // garante reset de estilo
    txt.clearModifiers();
    txt.setBackgroundColor(null);
    txt.setForegroundColor(null);
  }

  public boolean gameOver() {
    return testColumn() || testDiagonal() || testRow();
  }

  public boolean draw() {
    for (int i = 0; i < gamePlaces.length; i++) {
      for (int j = 0; j < gamePlaces.length; j++) {
        if (gamePlaces[i][j].getMatchStatus() == MatchStatus.IN_PROGRESS) {
          return false;
        }
      }
    }
    return true;
  }

  private boolean samePlayer(int r1, int c1, int r2, int c2, int r3, int c3) {
    Match m1 = gamePlaces[r1][c1];
    Match m2 = gamePlaces[r2][c2];
    Match m3 = gamePlaces[r3][c3];

    if (m1.getMatchStatus() == MatchStatus.VICTORY &&
        m2.getMatchStatus() == MatchStatus.VICTORY &&
        m3.getMatchStatus() == MatchStatus.VICTORY) {
      Piece p1 = m1.getWinner().getPiece();
      return p1.equals(m2.getWinner().getPiece()) && p1.equals(m3.getWinner().getPiece());
    }
    return false;
  }

  private boolean testColumn() {
    return samePlayer(0, 0, 1, 0, 2, 0) ||
        samePlayer(0, 1, 1, 1, 2, 1) ||
        samePlayer(0, 2, 1, 2, 2, 2);
  }

  private boolean testRow() {
    return samePlayer(0, 0, 0, 1, 0, 2) ||
        samePlayer(1, 0, 1, 1, 1, 2) ||
        samePlayer(2, 0, 2, 1, 2, 2);
  }

  private boolean testDiagonal() {
    return samePlayer(0, 0, 1, 1, 2, 2) ||
        samePlayer(0, 2, 1, 1, 2, 0);
  }

  public Match getGamePlaces(int i, int j) {
    return gamePlaces[i][j];
  }

  public void setGamePlaces(int i, int j, Match match) {
    gamePlaces[i][j] = match;
  }

  public void updateGlobalStatus() {
    if (gameOver()) {
      status = MatchStatus.VICTORY;
      winner = getGameWinner();
    } else if (draw()) {
      status = MatchStatus.DRAW;
    }
  }

  public Player getGameWinner() {
    int[][] wins = {
        {0, 0, 0, 1, 0, 2},
        {1, 0, 1, 1, 1, 2},
        {2, 0, 2, 1, 2, 2},
        {0, 0, 1, 0, 2, 0},
        {0, 1, 1, 1, 2, 1},
        {0, 2, 1, 2, 2, 2},
        {0, 0, 1, 1, 2, 2},
        {0, 2, 1, 1, 2, 0}
    };
    for (int[] w : wins) {
      Player lineWinner = checkLineWinner(w[0], w[1], w[2], w[3], w[4], w[5]);
      if (lineWinner != null) {
        return lineWinner;
      }
    }
    return null;
  }

  private Player checkLineWinner(int r1, int c1, int r2, int c2, int r3, int c3) {
    Match m1 = gamePlaces[r1][c1];
    Match m2 = gamePlaces[r2][c2];
    Match m3 = gamePlaces[r3][c3];

    if (m1.getMatchStatus() == MatchStatus.VICTORY &&
        m2.getMatchStatus() == MatchStatus.VICTORY &&
        m3.getMatchStatus() == MatchStatus.VICTORY) {
      Piece winnerPiece = m1.getWinner().getPiece();
      if (winnerPiece.equals(m2.getWinner().getPiece()) &&
          winnerPiece.equals(m3.getWinner().getPiece())) {
        if (p1 != null && p1.getPiece().equals(winnerPiece))
          return p1;
        if (p2 != null && p2.getPiece().equals(winnerPiece))
          return p2;
      }
    }
    return null;
  }

  public void setGameStatus(MatchStatus status) {
    this.status = status;
  }

  public Player getCurrentPlayer() {
    return currentPlayer;
  }

  public Integer getTurn() {
    return turn;
  }

  public MatchStatus getStatus() {
    return status;
  }

  public Player getWinner() {
    return winner;
  }

  public GameClock getClock() {
    return clock;
  }

  public Player getP1() {
    return p1;
  }

  public Player getP2() {
    return p2;
  }

}
