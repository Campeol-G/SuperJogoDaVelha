package com.Campeol.game;

import java.io.IOException;
import java.util.Scanner;

import com.Campeol.MatchStatus;
import com.Campeol.subgame.Match;
import com.Campeol.subgame.Position;
import com.Campeol.ui.ConfigOverlay;
import com.Campeol.ui.EndScreen;
import com.Campeol.ui.HelpOverlay;
import com.Campeol.ui.HudView;
import com.Campeol.ui.I18n;
import com.Campeol.ui.ProfileStore;
import com.Campeol.ui.SessionScore;
import com.Campeol.ui.Theme;
import com.Campeol.ui.UiUtils;
import com.Campeol.ui.Viewport;
import com.googlecode.lanterna.SGR;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.TerminalPosition;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.Terminal;

public class GameUI implements AutoCloseable {

  private static final int MIN_COLS = 72;
  private static final int MIN_ROWS = 24;
  private Screen screen;
  private Terminal terminal;
  private TextGraphics txt;
  GameBoard gb;

  private final SessionScore sessionScore = new SessionScore();
  private Position lastDest = null;
  private boolean lastDestFree = true;
  private String transientMsg = null;

  private boolean onlineMode = false;
  private String localNick = null;
  private String opponentNick = null;

  /** Quem encerrou a partida online (INTERRUPTED sozinho não diz quem saiu). */
  public enum LeaveReason {
    NONE,
    /** Saída local (Esc / fechar). */
    LOCAL,
    /** Peer avisou com QUIT. */
    PEER_QUIT,
    /** Peer caiu (EOF/reset ou silêncio além do limite, sem heartbeat). */
    PEER_LOST
  }

  private LeaveReason leaveReason = LeaveReason.NONE;

  public LeaveReason getLeaveReason() {
    return leaveReason;
  }

  public void setLeaveReason(LeaveReason reason) {
    this.leaveReason = reason;
  }

  public void resetLeaveReason() {
    this.leaveReason = LeaveReason.NONE;
  }
  // Chamado quando o nick local muda no meio da partida (config "1").
  // O App registra aqui o envio da mensagem NICK ao oponente.
  private java.util.function.Consumer<String> nickChangeListener = null;

  public void setNickChangeListener(java.util.function.Consumer<String> listener) {
    this.nickChangeListener = listener;
  }

  public GameUI() throws IOException, InterruptedException {
    // idioma primeiro (antes de qualquer texto)
    String savedLang = ProfileStore.getLang();
    if (savedLang == null) {
      // precisa do terminal para perguntar; cria temporário depois seleciona
      this.terminal = new DefaultTerminalFactory().createTerminal();
      this.screen = new TerminalScreen(terminal);
      screen.startScreen();
      this.txt = screen.newTextGraphics();
      promptLanguage();
    } else {
      I18n.setLocale(savedLang);
      this.terminal = new DefaultTerminalFactory().createTerminal();
      this.screen = new TerminalScreen(terminal);
      screen.startScreen();
      this.txt = screen.newTextGraphics();
    }
    hideCursor();
    gb = new GameBoard();
    waitForEnoughSize();
  }

  /**
   * Esconde o cursor de hardware (não tem mais utilidade: a navegação é
   * toda por highlight). Precisa ser reaplicado após cada refresh, pois
   * resize/refresh do terminal pode reverter a flag.
   */
  private void hideCursor() {
    try {
      screen.setCursorPosition(null);
    } catch (Exception ignored) {
    }
    try {
      terminal.setCursorVisible(false);
    } catch (Exception e) {
      System.err.println("warn: cannot hide cursor: " + e.getMessage());
    }
  }

  /** Refresh que garante o cursor escondido. Usar no lugar de screen.refresh(). */
  private void refresh() throws IOException {
    hideCursor();
    screen.refresh();
  }

  // ---------- menu legado (fora do escopo, mantido) ----------
  public Integer Menu(Scanner sc) throws IOException {
    screen.clear();
    txt.putString(0, 0, "1. Multiplayer.");
    txt.putString(0, 1, "2. Local.");
    // menu legado usa Scanner: cursor visível e posicionado na linha de digitação
    try {
      screen.setCursorPosition(new TerminalPosition(0, 2));
    } catch (Exception ignored) {
    }
    try {
      terminal.setCursorVisible(true);
    } catch (Exception ignored) {
    }
    screen.refresh();
    String i = sc.nextLine();
    hideCursor();
    if (i.equals("1")) {
      return 1;
    } else {
      return 2;
    }
  }

  public Integer OnlineMenu(Scanner sc) throws IOException {
    screen.clear();
    txt.putString(0, 0, "1. Create game.");
    txt.putString(0, 1, "2. Get in the game.");
    // menu legado usa Scanner: cursor visível e posicionado na linha de digitação
    try {
      screen.setCursorPosition(new TerminalPosition(0, 2));
    } catch (Exception ignored) {
    }
    try {
      terminal.setCursorVisible(true);
    } catch (Exception ignored) {
    }
    screen.refresh();
    String i = sc.nextLine();
    hideCursor();
    if (i.equals("1")) {
      return 1;
    } else {
      return 2;
    }
  }

  public char startLocalCustomGame() throws IOException, InterruptedException {
    screen.clear();
    char kp;
    do {
      waitForEnoughSize();
      txt.putString(0, 0, "Chose a piece to play[X/O]: ");
      refresh();
      KeyStroke keyPressed = screen.readInput();
      if (keyPressed.getKeyType() == KeyType.Escape) {
        gb.setGameStatus(MatchStatus.INTERRUPTED);
        gb.getClock().stop();
        return ' ';
      }
      Character c = keyPressed.getCharacter();
      kp = c == null ? ' ' : Character.toUpperCase(c);
    } while (kp != 'X' && kp != 'O');
    return kp;
  }

  // ---------- idioma / nick ----------
  private void promptLanguage() throws IOException, InterruptedException {
    I18n.setLocale("pt");
    int sel = 0;
    while (true) {
      screen.clear();
      int cols = terminal.getTerminalSize().getColumns();
      int rows = terminal.getTerminalSize().getRows();
      String title = "Escolha o idioma / Choose language";
      String o1 = "1 - Portugues";
      String o2 = "2 - English";
      String hint = "Use 1/2 ou setas + Enter / Use 1/2 or arrows + Enter";
      int w = Math.max(title.length(), hint.length()) + 6;
      int h = 9;
      int x0 = Math.max(0, (cols - w) / 2);
      int y0 = Math.max(0, (rows - h) / 2);
      txt.setForegroundColor(TextColor.ANSI.WHITE_BRIGHT);
      txt.enableModifiers(SGR.BOLD);
      txt.putString(x0 + (w - title.length()) / 2, y0 + 1, title);
      txt.clearModifiers();
      txt.setForegroundColor(TextColor.ANSI.WHITE);
      if (sel == 0) {
        txt.enableModifiers(SGR.REVERSE, SGR.BOLD);
      }
      txt.putString(x0 + 3, y0 + 3, o1);
      txt.clearModifiers();
      txt.setForegroundColor(TextColor.ANSI.WHITE);
      if (sel == 1) {
        txt.enableModifiers(SGR.REVERSE, SGR.BOLD);
      }
      txt.putString(x0 + 3, y0 + 4, o2);
      txt.clearModifiers();
      txt.setForegroundColor(Theme.DIM_FG);
      txt.putString(x0 + Math.max(0, (w - hint.length()) / 2), y0 + 6, hint);
      txt.setForegroundColor(null);
      refresh();
      KeyStroke k = screen.readInput();
      if (k == null) continue;
      if (k.getKeyType() == KeyType.ArrowUp || k.getKeyType() == KeyType.ArrowDown) {
        sel = 1 - sel;
        continue;
      }
      if (k.getKeyType() == KeyType.Enter) {
        ProfileStore.setLang(sel == 0 ? "pt" : "en");
        return;
      }
      Character c = k.getCharacter();
      if (c != null) {
        if (c == '1') {
          ProfileStore.setLang("pt");
          return;
        }
        if (c == '2') {
          ProfileStore.setLang("en");
          return;
        }
      }
      if (k.getKeyType() == KeyType.Escape) {
        ProfileStore.setLang("pt");
        return;
      }
    }
  }

  public String ensureOnlineNick() throws IOException, InterruptedException {
    if (localNick != null && !localNick.isEmpty()) return localNick;
    String saved = ProfileStore.getNick();
    if (saved != null && !saved.trim().isEmpty()) {
      localNick = UiUtils.clampNick(saved, 12);
      return localNick;
    }
    localNick = promptNick();
    return localNick;
  }

  private String promptNick() throws IOException, InterruptedException {
    StringBuilder sb = new StringBuilder();
    while (true) {
      screen.clear();
      int cols = terminal.getTerminalSize().getColumns();
      int rows = terminal.getTerminalSize().getRows();
      String title = I18n.t("nick.title");
      String hint = I18n.t("nick.hint");
      int w = 44;
      int h = 9;
      int x0 = Math.max(0, (cols - w) / 2);
      int y0 = Math.max(0, (rows - h) / 2);
      txt.setForegroundColor(TextColor.ANSI.WHITE_BRIGHT);
      txt.enableModifiers(SGR.BOLD);
      txt.putString(x0 + (w - title.length()) / 2, y0 + 1, title);
      txt.clearModifiers();
      txt.setForegroundColor(TextColor.ANSI.WHITE);
      String box = "[" + sb.toString() + (System.currentTimeMillis() % 1000 < 500 ? "_" : " ") + "]";
      txt.putString(x0 + (w - 22) / 2, y0 + 3, "                      ");
      txt.putString(x0 + (w - Math.max(box.length(), 4)) / 2, y0 + 3, box);
      txt.setForegroundColor(Theme.DIM_FG);
      txt.putString(x0 + Math.max(0, (w - hint.length()) / 2), y0 + 5, hint);
      txt.setForegroundColor(null);
      refresh();
      KeyStroke k = screen.readInput();
      if (k == null) continue;
      if (k.getKeyType() == KeyType.Enter) {
        String nick = UiUtils.clampNick(sb.toString(), 12);
        if (nick.isEmpty()) nick = I18n.t("nick.you");
        ProfileStore.setNick(nick);
        return nick;
      }
      if (k.getKeyType() == KeyType.Escape) {
        String nick = sb.length() > 0 ? UiUtils.clampNick(sb.toString(), 12) : I18n.t("nick.you");
        ProfileStore.setNick(nick);
        return nick;
      }
      if (k.getKeyType() == KeyType.Backspace) {
        if (sb.length() > 0) sb.deleteCharAt(sb.length() - 1);
        continue;
      }
      Character c = k.getCharacter();
      if (c != null && (Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == ' ') && sb.length() < 12) {
        sb.append(c);
      }
    }
  }

  public void setOnlineMode(boolean online) {
    this.onlineMode = online;
  }

  public void setLocalNick(String nick) {
    this.localNick = nick;
  }

  public void setOpponentNick(String nick) {
    this.opponentNick = nick;
  }

  public String getLocalNick() {
    return localNick;
  }

  public SessionScore getSessionScore() {
    return sessionScore;
  }

  public GameBoard getBoard() {
    return gb;
  }

  // ---------- render centralizado + HUD ----------
  private Viewport currentViewport() throws IOException {
    return Viewport.centered(terminal.getTerminalSize().getColumns(),
        terminal.getTerminalSize().getRows());
  }

  private String turnLabel() {
    if (gb.getCurrentPlayer() == null) return "--";
    char piece = gb.getCurrentPlayer().getPiece().getXorO();
    if (onlineMode) {
      // o nick local só aparece quando é a vez dele; senão mostra oponente.
      // Como não sabemos de quem é a peça local sem handshake extra,
      // App define localNick/opponentNick e a peça local via setLocalPiece? Simplificação:
      // mostra nick + peça quando disponível.
      String you = (localNick != null ? localNick : I18n.t("nick.you"));
      String opp = (opponentNick != null ? opponentNick : I18n.t("nick.opp"));
      // Heurística: se o turno atual é após receiveOpponentMove, é nossa vez.
      // App atualiza labels via setTurnLabelOverride? Mantemos genérico:
      return String.format("%s (%c)", you + " / " + opp, piece);
    }
    return String.valueOf(piece);
  }

  /** Rótulo enxuto para o local: só X ou O (conforme pedido). */
  private String localTurnLabel() {
    if (gb.getCurrentPlayer() == null) return "--";
    return String.valueOf(gb.getCurrentPlayer().getPiece().getXorO());
  }

  public void render() throws IOException, InterruptedException {
    Viewport vp = currentViewport();
    waitForEnoughSize();
    vp = currentViewport();
    screen.clear();
    gb.renderAllGames(txt, vp.getOriginX(), vp.getOriginY(), -1, -1, null, -1, -1, null);
    String label = onlineMode ? turnLabel() : localTurnLabel();
    HudView.render(txt, vp, gb, sessionScore, lastDest, lastDestFree, label);
    drawFooter(vp);
    refresh();
  }

  private void renderWithBigSel(int selR, int selC) throws IOException {
    Viewport vp = currentViewport();
    screen.clear();
    gb.renderAllGames(txt, vp.getOriginX(), vp.getOriginY(), selR, selC, null, -1, -1, null);
    String label = onlineMode ? turnLabel() : localTurnLabel();
    HudView.render(txt, vp, gb, sessionScore, null, true, label);
    // silêncio total: sem dica persistente (destino já está no HUD + highlight)
    drawFooter(vp);
    refresh();
  }

  private void renderWithCellSel(Match active, int selR, int selC) throws IOException {
    Viewport vp = currentViewport();
    screen.clear();
    gb.renderAllGames(txt, vp.getOriginX(), vp.getOriginY(), -1, -1, active, selR, selC, null);
    String label = onlineMode ? turnLabel() : localTurnLabel();
    Position dest = new Position(active.getGridRow(), active.getGridCol());
    HudView.render(txt, vp, gb, sessionScore, dest, false, label);
    // silêncio total: sem aviso de destino (HUD Destino + highlight já mostram)
    drawFooter(vp);
    refresh();
  }

  private void drawFooter(Viewport vp) {
    drawFooter(vp, null);
  }

  private void drawFooter(Viewport vp, String info) {
    int fx = vp.getOriginX();
    int my = vp.messageY();
    // Só a linha de mensagem — a dica [?]/[Esc]/[C] vive apenas no HUD lateral.
    UiUtils.clearRect(txt, fx, my, Viewport.TOTAL_W, 1);
    String line1 = transientMsg != null ? transientMsg : (info != null ? info : "");
    if (!line1.isEmpty()) {
      txt.setForegroundColor(TextColor.ANSI.WHITE_BRIGHT);
      txt.enableModifiers(SGR.BOLD);
      txt.putString(fx, my, line1.length() > Viewport.TOTAL_W ? line1.substring(0, Viewport.TOTAL_W) : line1);
      txt.clearModifiers();
      txt.setForegroundColor(null);
    }
  }

  // ---------- helpers de cursor (item 4) ----------
  /** Move o cursor do macro com clamp 0..2. Retorna {row, col}. */
  public static int[] moveBigCursor(int row, int col, KeyType key) {
    switch (key) {
      case ArrowRight:
        col = Math.min(2, col + 1);
        break;
      case ArrowLeft:
        col = Math.max(0, col - 1);
        break;
      case ArrowUp:
        row = Math.max(0, row - 1);
        break;
      case ArrowDown:
        row = Math.min(2, row + 1);
        break;
      default:
        break;
    }
    return new int[] { row, col };
  }

  public static int[] moveCellCursor(int row, int col, KeyType key) {
    return moveBigCursor(row, col, key);
  }

  private void openHelpIfNeeded(KeyStroke k) throws IOException, InterruptedException {
    if (k == null) return;
    Character c = k.getCharacter();
    boolean isHelp = (c != null && (c == '?' || c == 'h' || c == 'H'));
    if (!isHelp) return;
    Viewport vp = currentViewport();
    HelpOverlay.show(screen, txt, vp);
    // espera fechar
    while (true) {
      KeyStroke k2 = screen.readInput();
      if (k2 == null) continue;
      Character c2 = k2.getCharacter();
      if (k2.getKeyType() == KeyType.Escape) break;
      if (c2 != null && (c2 == '?' || c2 == 'h' || c2 == 'H')) break;
    }
    transientMsg = null;
    render();
  }

  private void openConfig() throws IOException, InterruptedException {
    ConfigOverlay.show(screen, txt);
    while (true) {
      KeyStroke k2 = screen.readInput();
      if (k2 == null) continue;
      if (k2.getKeyType() == KeyType.Escape) break;
      Character c2 = k2.getCharacter();
      if (c2 != null) {
        char u = Character.toUpperCase(c2);
        if (u == 'C') break;
        if (c2 != null && c2.equals('?')) break;
        if (u == '1') {
          String nick = promptNick();
          localNick = nick;
          // promptNick() já salva no ProfileStore; avisa o oponente
          // para o HUD dele atualizar junto (modo online).
          if (nickChangeListener != null) {
            try {
              nickChangeListener.accept(nick);
            } catch (Exception ignored) {
            }
          }
          ConfigOverlay.show(screen, txt);
          continue;
        }
        if (u == '2') {
          String cur = I18n.getLang();
          ProfileStore.setLang("pt".equals(cur) ? "en" : "pt");
          ConfigOverlay.show(screen, txt);
          continue;
        }
      }
    }
    transientMsg = null;
    render();
  }

  // Chave do último estado exibido no HUD (evita reescrever texto idêntico).
  private String lastHudClockKey = null;

  private void refreshHudThrottle(long[] last) throws IOException {
    long now = System.currentTimeMillis();
    if (now - last[0] < 1000) return;
    Viewport vp = currentViewport();
    // timers têm resolução de 1s: só redesenha se algum segundo exibido mudou
    // (ou se a viewport mudou, ex. resize). Linha vazia reescrita = flicker.
    String key = vp.getOriginX() + "," + vp.getOriginY() + "|"
        + UiUtils.formatDuration(gb.getClock().totalFor('X')) + "|"
        + UiUtils.formatDuration(gb.getClock().totalFor('O')) + "|"
        + UiUtils.formatDuration(gb.getClock().matchMillis()) + "|"
        + UiUtils.formatDuration(gb.getClock().currentTurnMillis());
    if (key.equals(lastHudClockKey)) return;
    lastHudClockKey = key;
    last[0] = now;
    // re-render leve: só HUD + footer (board já está na tela)
    String label = onlineMode ? turnLabel() : localTurnLabel();
    HudView.render(txt, vp, gb, sessionScore, lastDest, lastDestFree, label);
    drawFooter(vp);
    refresh();
  }

  // ---------- bigMove com highlight dedicado ----------
  public Match bigMove() throws IOException, InterruptedException {
    int row = 1;
    int column = 1;
    lastDest = null;
    lastDestFree = true;
    transientMsg = null;
    Match match = gb.getGamePlaces(row, column);
    renderWithBigSel(row, column);
    long[] lastHud = { 0 };
    KeyStroke keyPressed = null;

    while (true) {
      // poll com timeout para atualizar timers do HUD (xadrez)
      KeyStroke k = screen.pollInput();
      if (k == null) {
        refreshHudThrottle(lastHud);
        Thread.sleep(80);
        continue;
      }
      // ajuda ? / config C
      Character ch = k.getCharacter();
      if (ch != null && (ch == '?' || ch == 'h' || ch == 'H')) {
        openHelpIfNeeded(k);
        renderWithBigSel(row, column);
        keyPressed = null;
        continue;
      }
      if (ch != null && (ch == 'c' || ch == 'C')) {
        openConfig();
        renderWithBigSel(row, column);
        keyPressed = null;
        continue;
      }
      if (k.getKeyType() == KeyType.Escape) {
        gb.setGameStatus(MatchStatus.INTERRUPTED);
        gb.getClock().stop();
        return null;
      }
      if (k.getKeyType() == KeyType.Enter) {
        match = gb.getGamePlaces(row, column);
        if (match.getMatchStatus() != MatchStatus.IN_PROGRESS) {
          transientMsg = I18n.t("finished.game");
          renderWithBigSel(row, column);
          transientMsg = holdMessage(1800);
          renderWithBigSel(row, column);
          continue;
        }
        lastDest = new Position(row, column);
        lastDestFree = true;
        transientMsg = null;
        return match;
      }
      if (k.getKeyType() == KeyType.ArrowRight || k.getKeyType() == KeyType.ArrowLeft
          || k.getKeyType() == KeyType.ArrowUp || k.getKeyType() == KeyType.ArrowDown) {
        int[] nc = moveBigCursor(row, column, k.getKeyType());
        row = nc[0];
        column = nc[1];
        match = gb.getGamePlaces(row, column);
        renderWithBigSel(row, column);
        continue;
      }
      // input inválido
      transientMsg = I18n.t("invalid.input");
      renderWithBigSel(row, column);
      transientMsg = holdMessage(1200);
      renderWithBigSel(row, column);
    }
  }

  /** Mostra transientMsg por ms, interrompível por tecla. Retorna null (limpa). */
  private String holdMessage(long ms) throws IOException, InterruptedException {
    long end = System.currentTimeMillis() + ms;
    while (System.currentTimeMillis() < end) {
      if (screen.pollInput() != null) break;
      // mensagem estática + timers de 1s: 250ms basta (50ms redesenhava ~20x/s à toa)
      Thread.sleep(250);
      // mantém timers vivos
      try {
        Viewport vp = currentViewport();
        String label = onlineMode ? turnLabel() : localTurnLabel();
        HudView.render(txt, vp, gb, sessionScore, lastDest, lastDestFree, label);
        drawFooter(vp);
        refresh();
      } catch (Exception ignored) {
      }
    }
    // drena inputs durante a mensagem para não digitarem no próximo estado
    while (screen.pollInput() != null) {
    }
    return null;
  }

  // ---------- readInput com highlight por célula (fix do bug) ----------
  public Position readInput(Match match) throws IOException, InterruptedException {
    int row = 0;
    int column = 0;
    Position pos = new Position(row, column);
    lastDest = new Position(match.getGridRow(), match.getGridCol());
    lastDestFree = false;
    transientMsg = null;

    renderWithCellSel(match, row, column);
    long[] lastHud = { 0 };

    while (true) {
      KeyStroke k = screen.pollInput();
      if (k == null) {
        refreshHudThrottle(lastHud);
        // re-desenha seleção para manter highlight mesmo com refresh do HUD
        Thread.sleep(80);
        continue;
      }
      Character ch = k.getCharacter();
      if (ch != null && (ch == '?' || ch == 'h' || ch == 'H')) {
        openHelpIfNeeded(k);
        renderWithCellSel(match, row, column);
        continue;
      }
      if (ch != null && (ch == 'c' || ch == 'C')) {
        openConfig();
        renderWithCellSel(match, row, column);
        continue;
      }
      if (k.getKeyType() == KeyType.Escape) {
        gb.setGameStatus(MatchStatus.INTERRUPTED);
        gb.getClock().stop();
        return null;
      }
      if (k.getKeyType() == KeyType.Enter) {
        pos.setPosition(row, column);
        transientMsg = null;
        return pos;
      }
      if (k.getKeyType() == KeyType.ArrowRight || k.getKeyType() == KeyType.ArrowLeft
          || k.getKeyType() == KeyType.ArrowUp || k.getKeyType() == KeyType.ArrowDown) {
        int[] nc = moveCellCursor(row, column, k.getKeyType());
        row = nc[0];
        column = nc[1];
        pos.setPosition(row, column);
        renderWithCellSel(match, row, column);
        continue;
      }
      transientMsg = I18n.t("invalid.input");
      renderWithCellSel(match, row, column);
      transientMsg = holdMessage(1200);
      renderWithCellSel(match, row, column);
    }
  }

  public void makeMove(Match match, Position position) throws IOException, InterruptedException {
    gb.makeMove(match, position);
    if (gb.getMatchFinished()) {
      render();
    }
  }

  public void changeTurn() {
    gb.changeTurn();
  }

  public void receiveOpponentMove(Match receivedMatch) throws IOException, InterruptedException {
    int gridRow = receivedMatch.getGridRow();
    int gridCol = receivedMatch.getGridCol();
    gb.setGamePlaces(gridRow, gridCol, receivedMatch);
    if (receivedMatch.getMatchStatus() != MatchStatus.IN_PROGRESS) {
      render();
    }
    gb.updateGlobalStatus();
    if (gb.getStatus() != MatchStatus.IN_PROGRESS) {
      gb.getClock().stop();
    } else {
      gb.changeTurn();
    }
  }

  public Match changeMatch(Position pos) throws IOException, InterruptedException {
    if (pos == null) {
      lastDestFree = true;
      lastDest = null;
      return bigMove();
    }
    if (gb.getGamePlaces(pos.getRow(), pos.getColumn()).getMatchStatus() != MatchStatus.IN_PROGRESS) {
      lastDestFree = true;
      lastDest = null;
      return bigMove();
    } else {
      lastDest = new Position(pos.getRow(), pos.getColumn());
      lastDestFree = false;
      return gb.getGamePlaces(pos.getRow(), pos.getColumn());
    }
  }

  public void showErro(String msg) throws IOException, InterruptedException {
    transientMsg = msg;
    render();
    transientMsg = holdMessage(1800);
    render();
  }

  public void endMatch(Match match) throws IOException, InterruptedException {
    render();
  }

  /** Registra o resultado no placar agregado. Chamar uma vez por partida. */
  public void recordResult() {
    if (gb.getStatus() == MatchStatus.VICTORY && gb.getWinner() != null) {
      sessionScore.registerWin(gb.getWinner().getPiece().getXorO());
    } else if (gb.getStatus() == MatchStatus.DRAW) {
      sessionScore.registerDraw();
    }
  }

  public String winnerLabel() {
    if (gb.getWinner() != null) {
      char p = gb.getWinner().getPiece().getXorO();
      if (onlineMode) {
        // sem mapeamento peça->nick confiável, mostra peça + nicks
        String you = localNick != null ? localNick : I18n.t("nick.you");
        String opp = opponentNick != null ? opponentNick : I18n.t("nick.opp");
        return p + " (" + you + "/" + opp + ")";
      }
      return String.valueOf(p);
    }
    return "";
  }

  public EndScreen.Result endGame() throws IOException, InterruptedException {
    gb.getClock().stop();
    // desenha board final de fundo rapidamente e depois o modal
    try {
      render();
    } catch (Exception ignored) {
    }
    long matchMs = gb.getClock().matchMillis();
    EndScreen.draw(screen, txt, gb.getStatus(), winnerLabel(), sessionScore, matchMs, null);
    // espera R / Esc (local). Online usa handshake separado via App.
    while (true) {
      KeyStroke k = screen.readInput();
      if (k == null) continue;
      if (k.getKeyType() == KeyType.Escape) return new EndScreen.Result(EndScreen.Choice.QUIT);
      Character c = k.getCharacter();
      if (c != null) {
        char u = Character.toUpperCase(c);
        if (u == 'R') return new EndScreen.Result(EndScreen.Choice.REMATCH);
        if (u == 'N' || u == 'Q') return new EndScreen.Result(EndScreen.Choice.QUIT);
      }
      if (k.getKeyType() == KeyType.Enter) return new EndScreen.Result(EndScreen.Choice.QUIT);
    }
  }

  /** Nova rodada alternando quem começa (revanche). */
  public void newRoundAlternateStarter() {
    gb.newRoundAlternateStarter();
    lastDest = null;
    lastDestFree = true;
    transientMsg = null;
  }

  public void close() throws IOException {
    // devolve o cursor ao terminal antes de sair da tela alternativa
    try {
      terminal.setCursorVisible(true);
    } catch (Exception ignored) {
    }
    screen.stopScreen();
  }

  public Match getMatch(int i, int j) {
    return gb.getGamePlaces(i, j);
  }

  public com.googlecode.lanterna.input.KeyStroke pollInput() throws IOException {
    return screen.pollInput();
  }

  public Screen getScreen() {
    return screen;
  }

  public TextGraphics getTextGraphics() {
    return txt;
  }

  public MatchStatus getStatus() {
    return gb.getStatus();
  }

  public void setGameStatus(MatchStatus status) {
    gb.setGameStatus(status);
  }

  public void startPlayer(char XorO) {
    gb.startPlayer(XorO);
  }

  public void startPlayer() {
    gb.startPlayer();
  }

  private boolean isSizeOk() throws IOException {
    return terminal.getTerminalSize().getRows() >= MIN_ROWS
        && terminal.getTerminalSize().getColumns() >= MIN_COLS;
  }

  private void waitForEnoughSize() throws IOException, InterruptedException {
    while (!isSizeOk()) {
      screen.clear();
      int cols = terminal.getTerminalSize().getColumns();
      int rows = terminal.getTerminalSize().getRows();
      String l1 = I18n.t("resize.line1");
      String l2 = I18n.t("resize.min");
      int x = Math.max(0, (cols - Math.max(l1.length(), l2.length())) / 2);
      int y = Math.max(0, rows / 2 - 1);
      txt.setForegroundColor(TextColor.ANSI.WHITE_BRIGHT);
      txt.putString(x, y, l1);
      txt.setForegroundColor(Theme.DIM_FG);
      txt.putString(x, y + 1, l2);
      txt.setForegroundColor(null);
      refresh();
      long endTime = System.currentTimeMillis() + 300;
      while (System.currentTimeMillis() < endTime) {
        if (screen.pollInput() != null) break;
        Thread.sleep(50);
      }
    }
  }

}
