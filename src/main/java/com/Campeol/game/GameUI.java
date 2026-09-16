package com.Campeol.game;

import java.io.IOException;

import com.Campeol.MatchStatus;
import com.Campeol.net.NetException;
import com.Campeol.net.NetLog;
import com.Campeol.net.NetPoll;
import com.Campeol.subgame.Match;
import com.Campeol.subgame.Position;
import com.Campeol.ui.ConfigOverlay;
import com.Campeol.ui.EndScreen;
import com.Campeol.ui.HelpOverlay;
import com.Campeol.ui.HudView;
import com.Campeol.ui.I18n;
import com.Campeol.ui.NetMessage;
import com.Campeol.ui.ProfileStore;
import com.Campeol.ui.SessionScore;
import com.Campeol.ui.Theme;
import com.Campeol.ui.UiUtils;
import com.Campeol.ui.Viewport;
import com.googlecode.lanterna.SGR;
import com.googlecode.lanterna.TextColor;
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
  /** Peça local no online (para mapear peça->nick). Null = desconhecida. */
  private Character localPiece = null;

  private boolean rankedMode = false;
  private com.Campeol.ui.RankedProfile rankedProfile = null;
  private boolean botThinking = false;
  /** Peça do humano no rankeado (para esconder o timer do bot). Null = n/a. */
  private Character rankedHumanPiece = null;

  private transient OnlineGame onlineGame = null;
  private transient boolean onlineIsServer = true;
  private transient Match stashedOnlineMatch = null;
  private volatile boolean escPending = false;

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

  public void setOnlineGame(OnlineGame og, boolean isServer) {
    this.onlineGame = og;
    this.onlineIsServer = isServer;
    this.stashedOnlineMatch = null;
  }

  public void clearOnlineGame() {
    this.onlineGame = null;
    this.stashedOnlineMatch = null;
  }

  public Match takeStashedOnlineMatch() {
    Match m = stashedOnlineMatch;
    stashedOnlineMatch = null;
    return m;
  }

  public boolean consumeEscPending() {
    boolean v = escPending;
    escPending = false;
    return v;
  }

  private void noteEscPending() {
    escPending = true;
  }

  private boolean checkOnlineInboxShort() {
    if (!onlineMode || onlineGame == null) {
      return gb.getStatus() != MatchStatus.IN_PROGRESS;
    }
    if (gb.getStatus() != MatchStatus.IN_PROGRESS) {
      return true;
    }
    NetPoll p;
    try {
      p = onlineIsServer ? onlineGame.pollServerShort() : onlineGame.pollClientShort();
    } catch (Exception e) {
      NetLog.log("checkOnlineInboxShort poll", e);
      return false;
    }
    if (p == null) {
      return false;
    }
    if (p.kind() == NetPoll.Kind.OK) {
      try {
        onlineGame.touch();
      } catch (Exception ignored) {
      }
      Object o = p.payload();
      if (o instanceof NetMessage) {
        NetMessage nm = (NetMessage) o;
        if (nm.getType() == null) {
          NetLog.log("checkOnlineInboxShort NetMessage sem tipo");
          return false;
        }
        if (nm.getType() == NetMessage.Type.NICK && nm.getPayload() != null) {
          opponentNick = nm.getPayload();
          return false;
        }
        if (nm.getType() == NetMessage.Type.QUIT) {
          leaveReason = LeaveReason.PEER_QUIT;
          gb.setGameStatus(MatchStatus.INTERRUPTED);
          try {
            gb.getClock().stop();
          } catch (Exception ignored) {
          }
          return true;
        }
        return false;
      }
      if (o instanceof Match) {
        if (stashedOnlineMatch == null) {
          stashedOnlineMatch = (Match) o;
        } else {
          // Segundo Match antes de consumir o primeiro: descarta com log
          // (não deveria acontecer em jogo por turnos; indica retransmissão/bug).
          NetLog.log("checkOnlineInboxShort descarta Match duplicado");
        }
        return false;
      }
      return false;
    }
    if (p.kind() == NetPoll.Kind.DISCONNECTED) {
      if (leaveReason == LeaveReason.NONE) {
        leaveReason = LeaveReason.PEER_LOST;
      }
      gb.setGameStatus(MatchStatus.INTERRUPTED);
      try {
        gb.getClock().stop();
      } catch (Exception ignored) {
      }
      return true;
    }
    try {
      if (onlineGame.isPeerDead()) {
        if (leaveReason == LeaveReason.NONE) {
          leaveReason = LeaveReason.PEER_LOST;
        }
        gb.setGameStatus(MatchStatus.INTERRUPTED);
        try {
          gb.getClock().stop();
        } catch (Exception ignored) {
        }
        return true;
      }
    } catch (Exception ignored) {
    }
    return gb.getStatus() != MatchStatus.IN_PROGRESS;
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
    // primeira execução: idioma (se faltar) e depois o nick, antes do menu
    ensureNickAtStartup();
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
      NetLog.log("hide cursor", e);
    }
  }

  /** Refresh que garante o cursor escondido. Usar no lugar de screen.refresh(). */
  private void refresh() throws IOException {
    hideCursor();
    screen.refresh();
  }

  // ---------- menu principal (Lanterna nativo) ----------
  public enum MainChoice {
    LOCAL,
    RANKED,
    CREATE,
    JOIN,
    CONFIG,
    HOWTO,
    QUIT
  }

  private String menuTitle() {
    return I18n.getLang().equals("pt") ? I18n.t("title.pt") : I18n.t("title");
  }

  public MainChoice mainMenu() throws IOException, InterruptedException {
    MainChoice[] items = MainChoice.values();
    int sel = 0;
    while (true) {
      waitForEnoughSize();
      screen.clear();
      int cols = terminal.getTerminalSize().getColumns();
      int rows = terminal.getTerminalSize().getRows();
      String title = menuTitle();
      String[] labels = {
          I18n.t("menu.local"),
          I18n.t("menu.ranked"),
          I18n.t("menu.create"),
          I18n.t("menu.join"),
          I18n.t("menu.config"),
          I18n.t("menu.howto"),
          I18n.t("menu.quit")
      };
      String hint = I18n.t("menu.hint");
      int contentW = title.length() + 2;
      for (String l : labels) {
        contentW = Math.max(contentW, l.length() + 6);
      }
      contentW = Math.max(contentW, hint.length() + 4);
      int w = contentW + 4;
      int h = labels.length + 6;
      int x0 = Math.max(0, (cols - w) / 2);
      int y0 = Math.max(0, (rows - h) / 2);
      txt.setBackgroundColor(TextColor.ANSI.BLACK);
      txt.setForegroundColor(TextColor.ANSI.WHITE);
      for (int y = 0; y < h; y++) {
        for (int x = 0; x < w; x++) {
          txt.putString(x0 + x, y0 + y, " ");
        }
      }
      txt.setForegroundColor(TextColor.ANSI.WHITE_BRIGHT);
      txt.enableModifiers(SGR.BOLD);
      txt.putString(x0, y0, "┌" + "─".repeat(w - 2) + "┐");
      for (int i = 1; i < h - 1; i++) {
        txt.putString(x0, y0 + i, "│");
        txt.putString(x0 + w - 1, y0 + i, "│");
      }
      txt.putString(x0, y0 + h - 1, "└" + "─".repeat(w - 2) + "┘");
      txt.putString(x0 + (w - title.length()) / 2, y0, " " + title + " ");
      txt.clearModifiers();
      for (int i = 0; i < labels.length; i++) {
        txt.setBackgroundColor(TextColor.ANSI.BLACK);
        if (i == sel) {
          txt.setForegroundColor(TextColor.ANSI.WHITE_BRIGHT);
          txt.enableModifiers(SGR.REVERSE, SGR.BOLD);
        } else {
          txt.setForegroundColor(TextColor.ANSI.WHITE);
        }
        String row = "  " + labels[i] + "  ";
        txt.putString(x0 + 3, y0 + 2 + i, row);
        txt.clearModifiers();
      }
      txt.setBackgroundColor(TextColor.ANSI.BLACK);
      txt.setForegroundColor(Theme.DIM_FG);
      txt.putString(x0 + Math.max(0, (w - hint.length()) / 2), y0 + h - 2, hint);
      txt.clearModifiers();
      txt.setBackgroundColor(null);
      txt.setForegroundColor(null);
      refresh();
      KeyStroke k = screen.readInput();
      if (k == null) continue;
      if (k.getKeyType() == KeyType.Escape) return MainChoice.QUIT;
      if (k.getKeyType() == KeyType.ArrowUp) {
        sel = (sel + items.length - 1) % items.length;
        continue;
      }
      if (k.getKeyType() == KeyType.ArrowDown) {
        sel = (sel + 1) % items.length;
        continue;
      }
      if (k.getKeyType() == KeyType.Enter) return items[sel];
    }
  }

  /** Escolha de peça em tela Lanterna (X/O). Esc volta (retorna ' '). */
  public char startLocalCustomGame() throws IOException, InterruptedException {
    int sel = 0;
    String[] pieces = {"X", "O"};
    while (true) {
      waitForEnoughSize();
      screen.clear();
      int cols = terminal.getTerminalSize().getColumns();
      int rows = terminal.getTerminalSize().getRows();
      String title = I18n.t("piece.title");
      String hint = I18n.t("piece.hint");
      int w = Math.max(title.length(), hint.length()) + 8;
      int h = 9;
      int x0 = Math.max(0, (cols - w) / 2);
      int y0 = Math.max(0, (rows - h) / 2);
      txt.setBackgroundColor(TextColor.ANSI.BLACK);
      txt.setForegroundColor(TextColor.ANSI.WHITE_BRIGHT);
      for (int y = 0; y < h; y++) {
        for (int x = 0; x < w; x++) {
          txt.putString(x0 + x, y0 + y, " ");
        }
      }
      txt.enableModifiers(SGR.BOLD);
      txt.putString(x0, y0, "┌" + "─".repeat(w - 2) + "┐");
      for (int i = 1; i < h - 1; i++) {
        txt.putString(x0, y0 + i, "│");
        txt.putString(x0 + w - 1, y0 + i, "│");
      }
      txt.putString(x0, y0 + h - 1, "└" + "─".repeat(w - 2) + "┘");
      txt.putString(x0 + (w - title.length()) / 2, y0, " " + title + " ");
      txt.clearModifiers();
      for (int i = 0; i < pieces.length; i++) {
        txt.setBackgroundColor(TextColor.ANSI.BLACK);
        if (i == sel) {
          txt.setForegroundColor(TextColor.ANSI.WHITE_BRIGHT);
          txt.enableModifiers(SGR.REVERSE, SGR.BOLD);
        } else {
          txt.setForegroundColor(TextColor.ANSI.WHITE);
        }
        txt.putString(x0 + (w - 5) / 2, y0 + 3 + i, "  " + pieces[i] + "  ");
        txt.clearModifiers();
      }
      txt.setBackgroundColor(TextColor.ANSI.BLACK);
      txt.setForegroundColor(Theme.DIM_FG);
      txt.putString(x0 + Math.max(0, (w - hint.length()) / 2), y0 + h - 2, hint);
      txt.clearModifiers();
      txt.setBackgroundColor(null);
      txt.setForegroundColor(null);
      refresh();
      KeyStroke k = screen.readInput();
      if (k == null) continue;
      if (k.getKeyType() == KeyType.Escape) {
        gb.setGameStatus(MatchStatus.INTERRUPTED);
        gb.getClock().stop();
        return ' ';
      }
      if (k.getKeyType() == KeyType.ArrowUp || k.getKeyType() == KeyType.ArrowLeft) {
        sel = 1 - sel;
        continue;
      }
      if (k.getKeyType() == KeyType.ArrowDown || k.getKeyType() == KeyType.ArrowRight) {
        sel = 1 - sel;
        continue;
      }
      if (k.getKeyType() == KeyType.Enter) return pieces[sel].charAt(0);
      Character c = k.getCharacter();
      if (c != null) {
        char u = Character.toUpperCase(c);
        if (u == 'X' || u == 'O') return u;
      }
    }
  }

  // ---------- idioma / nick ----------
  private void promptLanguage() throws IOException, InterruptedException {
    I18n.setLocale("pt");
    int sel = 0;
    while (true) {
      screen.clear();
      int cols = terminal.getTerminalSize().getColumns();
      int rows = terminal.getTerminalSize().getRows();
      String title = I18n.t("lang.title");
      String o1 = I18n.t("lang.pt");
      String o2 = I18n.t("lang.en");
      String hint = I18n.t("lang.hint");
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

  /**
   * Garante o nick sem perguntar (o nick é pedido uma vez no startup,
   * logo após o idioma; aqui só usa o salvo ou o padrão).
   */
  public String ensureOnlineNick() {
    if (localNick != null && !localNick.isEmpty()) return localNick;
    String saved = ProfileStore.getNick();
    if (saved != null && !saved.trim().isEmpty()) {
      localNick = UiUtils.clampNick(saved, 12);
    } else {
      localNick = I18n.t("nick.you");
    }
    return localNick;
  }

  /** Fluxo de primeira execução: se não há nick salvo, pede uma vez. */
  public void ensureNickAtStartup() throws IOException, InterruptedException {
    String saved = ProfileStore.getNick();
    if (saved != null && !saved.trim().isEmpty()) {
      localNick = UiUtils.clampNick(saved, 12);
    } else {
      localNick = promptNick();
    }
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
    if (online) {
      this.rankedMode = false;
    } else {
      // Saiu do online: não polir socket velho no próximo jogo local.
      this.onlineGame = null;
      this.stashedOnlineMatch = null;
    }
  }

  public void setRankedMode(boolean ranked) {
    this.rankedMode = ranked;
    if (ranked) {
      this.onlineMode = false;
    } else {
      this.rankedHumanPiece = null;
    }
  }

  public void setRankedHumanPiece(Character piece) {
    this.rankedHumanPiece = piece;
  }

  public Character getRankedHumanPiece() {
    return rankedHumanPiece;
  }

  private Character rankedHumanPieceForHud() {
    return (rankedMode && rankedHumanPiece != null) ? rankedHumanPiece : null;
  }

  public boolean isRankedMode() {
    return rankedMode;
  }

  public void setRankedProfile(com.Campeol.ui.RankedProfile p) {
    this.rankedProfile = p;
  }

  public com.Campeol.ui.RankedProfile getRankedProfile() {
    return rankedProfile;
  }

  public void setBotThinking(boolean thinking) {
    this.botThinking = thinking;
  }

  private String rankedHudLine() {
    if (!rankedMode || rankedProfile == null) return null;
    return rankedProfile.hudLine();
  }

  private String rankedBarLine() {
    if (!rankedMode || rankedProfile == null) return null;
    if (botThinking) {
      String bot = com.Campeol.ui.BotNames.name(rankedProfile.getLevel());
      return I18n.t("ranked.thinking", bot) + "  |  " + rankedProfile.progressBar();
    }
    return rankedProfile.progressBar();
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

  public String getOpponentNick() {
    return opponentNick;
  }

  public void setLocalPiece(Character piece) {
    this.localPiece = piece;
  }

  public Character getLocalPiece() {
    return localPiece;
  }

  /** Mapeia peça -> nick no online (só nick, sem peça, conforme pedido). */
  private String nickForPiece(char piece) {
    String you = (localNick != null && !localNick.isEmpty() ? localNick : I18n.t("nick.you"));
    String opp = (opponentNick != null && !opponentNick.isEmpty() ? opponentNick : I18n.t("nick.opp"));
    if (localPiece != null) {
      if (piece == localPiece) return you;
      return opp;
    }
    // Sem mapeamento: fallback para a peça (evita mostrar nick errado).
    return String.valueOf(piece);
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
    if (gb.getCurrentPlayer() == null || gb.getCurrentPlayer().getPiece() == null) return "--";
    char piece = gb.getCurrentPlayer().getPiece().getXorO();
    if (onlineMode) {
      return nickForPiece(piece);
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
    HudView.render(txt, vp, gb, sessionScore, lastDest, lastDestFree, label, rankedHudLine(), null, rankedHumanPieceForHud());
    drawFooter(vp);
    refresh();
  }

  private void renderWithBigSel(int selR, int selC) throws IOException {
    Viewport vp = currentViewport();
    screen.clear();
    gb.renderAllGames(txt, vp.getOriginX(), vp.getOriginY(), selR, selC, null, -1, -1, null);
    String label = onlineMode ? turnLabel() : localTurnLabel();
    HudView.render(txt, vp, gb, sessionScore, null, true, label, rankedHudLine(), null, rankedHumanPieceForHud());
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
    HudView.render(txt, vp, gb, sessionScore, dest, false, label, rankedHudLine(), null, rankedHumanPieceForHud());
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
    String line1 = transientMsg != null ? transientMsg : (info != null ? info : null);
    if ((line1 == null || line1.isEmpty()) && rankedMode && rankedProfile != null) {
      line1 = rankedBarLine();
    }
    if (line1 != null && !line1.isEmpty()) {
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
    if (rankedMode) {
      openRankedHelpTransient();
      return;
    }
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

  /** Tutorial do rankeado via '?' no meio da partida (? de novo = ajuda geral). */
  private void openRankedHelpTransient() throws IOException, InterruptedException {
    com.Campeol.ui.RankedHelpOverlay.show(screen, txt);
    refresh();
    while (true) {
      KeyStroke k2 = screen.readInput();
      if (k2 == null) continue;
      if (k2.getKeyType() == KeyType.Escape) break;
      if (k2.getKeyType() == KeyType.Enter) break;
      Character c2 = k2.getCharacter();
      if (c2 != null && (c2 == '?' || c2 == 'h' || c2 == 'H')) {
        Viewport vp = currentViewport();
        HelpOverlay.show(screen, txt, vp);
        refresh();
        while (true) {
          KeyStroke k3 = screen.readInput();
          if (k3 == null) continue;
          if (k3.getKeyType() == KeyType.Escape) break;
          if (k3.getKeyType() == KeyType.Enter) break;
          Character c3 = k3.getCharacter();
          if (c3 != null && (c3 == '?' || c3 == 'h' || c3 == 'H')) break;
        }
        break;
      }
    }
    transientMsg = null;
    render();
  }

  /** Tela cheia de ajuda (item "Como jogar" do menu). Qualquer tecla volta. */
  public void showHelpBlocking() throws IOException, InterruptedException {
    Viewport vp = currentViewport();
    HelpOverlay.show(screen, txt, vp);
    refresh();
    while (true) {
      KeyStroke k = screen.readInput();
      if (k == null) continue;
      if (k.getKeyType() == KeyType.Escape) break;
      Character c = k.getCharacter();
      if (c != null && (c == '?' || c == 'h' || c == 'H')) break;
      if (k.getKeyType() == KeyType.Enter) break;
    }
  }

  /**
   * Tutorial do rankeado na entrada do modo.
   * Retorna true se Enter (jogar), false se Esc (voltar ao menu).
   */
  public boolean showRankedHelpBlocking() throws IOException, InterruptedException {
    waitForEnoughSize();
    screen.clear();
    com.Campeol.ui.RankedHelpOverlay.show(screen, txt);
    refresh();
    while (true) {
      KeyStroke k = screen.readInput();
      if (k == null) continue;
      if (k.getKeyType() == KeyType.Escape) return false;
      if (k.getKeyType() == KeyType.Enter) return true;
      Character c = k.getCharacter();
      if (c != null) {
        char u = Character.toUpperCase(c);
        if (u == 'Q' || u == 'N') return false;
        if (u == 'Y' || u == 'S') return true;
      }
    }
  }

  /** Tela de espera (ex. buscando servidor). Sem entrada. */
  public void showWaiting(String messageKey) throws IOException, InterruptedException {
    showWaiting(messageKey);
  }

  /**
   * Tela de espera com linha extra (ex. segundos decorridos) e dica de
   * cancelamento. Sem entrada (o chamador consulta o teclado).
   */
  public void showWaiting(String messageKey, String extraLine)
      throws IOException, InterruptedException {
    waitForEnoughSize();
    screen.clear();
    int cols = terminal.getTerminalSize().getColumns();
    int rows = terminal.getTerminalSize().getRows();
    String msg = I18n.t(messageKey);
    String hint = I18n.t("wait.cancel");
    int contentW = Math.max(msg.length(), hint.length());
    if (extraLine != null) {
      contentW = Math.max(contentW, extraLine.length());
    }
    int w = contentW + 8;
    int h = 7;
    int x0 = Math.max(0, (cols - w) / 2);
    int y0 = Math.max(0, (rows - h) / 2);
    txt.setBackgroundColor(TextColor.ANSI.BLACK);
    txt.setForegroundColor(TextColor.ANSI.WHITE_BRIGHT);
    for (int y = 0; y < h; y++) {
      for (int x = 0; x < w; x++) {
        txt.putString(x0 + x, y0 + y, " ");
      }
    }
    txt.enableModifiers(SGR.BOLD);
    txt.putString(x0, y0, "┌" + "─".repeat(w - 2) + "┐");
    for (int i = 1; i < h - 1; i++) {
      txt.putString(x0, y0 + i, "│");
      txt.putString(x0 + w - 1, y0 + i, "│");
    }
    txt.putString(x0, y0 + h - 1, "└" + "─".repeat(w - 2) + "┘");
    txt.putString(x0 + (w - msg.length()) / 2, y0 + 2, msg);
    txt.clearModifiers();
    if (extraLine != null && !extraLine.isEmpty()) {
      txt.setForegroundColor(TextColor.ANSI.WHITE);
      txt.setBackgroundColor(TextColor.ANSI.BLACK);
      txt.putString(x0 + (w - extraLine.length()) / 2, y0 + 3, extraLine);
      txt.clearModifiers();
    }
    txt.setForegroundColor(Theme.DIM_FG);
    txt.setBackgroundColor(TextColor.ANSI.BLACK);
    txt.putString(x0 + (w - hint.length()) / 2, y0 + 4, hint);
    txt.clearModifiers();
    txt.setBackgroundColor(null);
    txt.setForegroundColor(null);
    refresh();
  }

  /**
   * Toast rápido: caixinha centralizada por alguns ms (ex. "Busca
   * cancelada"), sem render de tabuleiro. Drena o teclado ao sair.
   */
  public void toast(String messageKey, long ms) throws IOException, InterruptedException {
    screen.clear();
    int cols = terminal.getTerminalSize().getColumns();
    int rows = terminal.getTerminalSize().getRows();
    String msg = I18n.t(messageKey);
    int w = msg.length() + 8;
    int h = 5;
    int x0 = Math.max(0, (cols - w) / 2);
    int y0 = Math.max(0, (rows - h) / 2);
    txt.setBackgroundColor(TextColor.ANSI.BLACK);
    txt.setForegroundColor(TextColor.ANSI.WHITE_BRIGHT);
    for (int y = 0; y < h; y++) {
      for (int x = 0; x < w; x++) {
        txt.putString(x0 + x, y0 + y, " ");
      }
    }
    txt.enableModifiers(SGR.BOLD);
    txt.putString(x0, y0, "┌" + "─".repeat(w - 2) + "┐");
    for (int i = 1; i < h - 1; i++) {
      txt.putString(x0, y0 + i, "│");
      txt.putString(x0 + w - 1, y0 + i, "│");
    }
    txt.putString(x0, y0 + h - 1, "└" + "─".repeat(w - 2) + "┘");
    txt.putString(x0 + (w - msg.length()) / 2, y0 + 2, msg);
    txt.clearModifiers();
    txt.setBackgroundColor(null);
    txt.setForegroundColor(null);
    refresh();
    long end = System.currentTimeMillis() + ms;
    while (System.currentTimeMillis() < end) {
      KeyStroke tk = screen.pollInput();
      if (tk != null && tk.getKeyType() == KeyType.Escape) {
        noteEscPending();
      }
      Thread.sleep(50);
    }
    while (true) {
      KeyStroke dk = screen.pollInput();
      if (dk == null) {
        break;
      }
      if (dk.getKeyType() == KeyType.Escape) {
        noteEscPending();
      }
    }
  }

  /**
   * Caixa de senha Lanterna. Começa mascarada (****); Tab alterna
   * visível/invisível. Enter confirma, Esc cancela (retorna null).
   */
  public String promptPassword(String titleKey) throws IOException, InterruptedException {
    StringBuilder sb = new StringBuilder();
    boolean visible = false;
    while (true) {
      screen.clear();
      int cols = terminal.getTerminalSize().getColumns();
      int rows = terminal.getTerminalSize().getRows();
      String title = I18n.t(titleKey);
      String hint = I18n.t("pass.hint");
      String toggle = visible ? I18n.t("pass.hide") : I18n.t("pass.show");
      int w = 46;
      int h = 11;
      int x0 = Math.max(0, (cols - w) / 2);
      int y0 = Math.max(0, (rows - h) / 2);
      txt.setBackgroundColor(TextColor.ANSI.BLACK);
      txt.setForegroundColor(TextColor.ANSI.WHITE_BRIGHT);
      for (int y = 0; y < h; y++) {
        for (int x = 0; x < w; x++) {
          txt.putString(x0 + x, y0 + y, " ");
        }
      }
      txt.enableModifiers(SGR.BOLD);
      txt.putString(x0, y0, "┌" + "─".repeat(w - 2) + "┐");
      for (int i = 1; i < h - 1; i++) {
        txt.putString(x0, y0 + i, "│");
        txt.putString(x0 + w - 1, y0 + i, "│");
      }
      txt.putString(x0, y0 + h - 1, "└" + "─".repeat(w - 2) + "┘");
      txt.putString(x0 + (w - title.length()) / 2, y0, " " + title + " ");
      txt.clearModifiers();
      String shown = visible ? sb.toString() : "*".repeat(sb.length());
      String box = "[" + shown + (System.currentTimeMillis() % 1000 < 500 ? "_" : " ") + "]";
      txt.setForegroundColor(TextColor.ANSI.WHITE);
      txt.setBackgroundColor(TextColor.ANSI.BLACK);
      txt.putString(x0 + 2, y0 + 3, " ".repeat(w - 4));
      txt.putString(x0 + (w - Math.max(box.length(), 4)) / 2, y0 + 3,
          box.length() > w - 4 ? box.substring(0, w - 4) : box);
      txt.setForegroundColor(Theme.DIM_FG);
      txt.putString(x0 + Math.max(0, (w - toggle.length()) / 2), y0 + 5, toggle);
      txt.putString(x0 + Math.max(0, (w - hint.length()) / 2), y0 + 6, hint);
      txt.clearModifiers();
      txt.setBackgroundColor(null);
      txt.setForegroundColor(null);
      refresh();
      KeyStroke k = screen.readInput();
      if (k == null) continue;
      if (k.getKeyType() == KeyType.Escape) return null;
      if (k.getKeyType() == KeyType.Tab) {
        visible = !visible;
        continue;
      }
      if (k.getKeyType() == KeyType.Enter) {
        if (sb.length() == 0) continue;
        return sb.toString();
      }
      if (k.getKeyType() == KeyType.Backspace) {
        if (sb.length() > 0) sb.deleteCharAt(sb.length() - 1);
        continue;
      }
      Character c = k.getCharacter();
      if (c != null && !Character.isISOControl(c) && c != ' ' && sb.length() < 20) {
        sb.append(c);
      }
    }
  }

  /** Prepara tabuleiro zerado para uma nova sessão (menu → jogo). */
  public void resetForNewGame() {
    gb.startAllGames();
    gb.clearResult();
    lastDest = null;
    lastDestFree = true;
    transientMsg = null;
    resetLeaveReason();
    escPending = false;
    stashedOnlineMatch = null;
    botThinking = false;
    localPiece = null;
  }

  /** Zera o placar da sessão (troca de modo: local/rankeado/online). */
  public void clearSessionScore() {
    sessionScore.clear();
  }

  public void openConfig() throws IOException, InterruptedException {
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
        if (u == '3') {
          com.Campeol.ui.RankedProfile r = rankedProfile != null
              ? rankedProfile
              : com.Campeol.ui.RankedProfile.load();
          r.reset();
          r.save();
          if (rankedProfile != null) {
            // atualiza referência vigente
            rankedProfile.reset();
          }
          toast("ranked.reset", 1500);
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
    HudView.render(txt, vp, gb, sessionScore, lastDest, lastDestFree, label, rankedHudLine(), null, rankedHumanPieceForHud());
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
      // ESC preservado de holdMessage/toast anterior: online sai direto,
      // local/rankeado abre menu (Sair e salvar / Desistir).
      if (consumeEscPending()) {
        if (onlineMode) {
          gb.setGameStatus(MatchStatus.INTERRUPTED);
          gb.getClock().stop();
          return null;
        }
        boolean done = handleEscInGame();
        if (done) return null;
        match = gb.getGamePlaces(row, column);
        renderWithBigSel(row, column);
        continue;
      }
      if (gb.getStatus() != MatchStatus.IN_PROGRESS) {
        return null;
      }
      // poll com timeout para atualizar timers do HUD (xadrez)
      KeyStroke k = screen.pollInput();
      if (k == null) {
        // Online: peer pode ter dado ESC enquanto pensamos — detecta sem bloquear.
        if (checkOnlineInboxShort()) {
          return null;
        }
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
        if (onlineMode) {
          gb.setGameStatus(MatchStatus.INTERRUPTED);
          gb.getClock().stop();
          return null;
        }
        boolean done = handleEscInGame();
        if (done) return null;
        match = gb.getGamePlaces(row, column);
        renderWithBigSel(row, column);
        continue;
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
      KeyStroke hk = screen.pollInput();
      if (hk != null) {
        if (hk.getKeyType() == KeyType.Escape) {
          noteEscPending();
        }
        break;
      }
      // mensagem estática + timers de 1s: 250ms basta (50ms redesenhava ~20x/s à toa)
      Thread.sleep(250);
      // mantém timers vivos
      try {
        Viewport vp = currentViewport();
        String label = onlineMode ? turnLabel() : localTurnLabel();
        HudView.render(txt, vp, gb, sessionScore, lastDest, lastDestFree, label, rankedHudLine(), null, rankedHumanPieceForHud());
        drawFooter(vp);
        refresh();
      } catch (Exception ignored) {
      }
    }
    while (true) {
      KeyStroke dk = screen.pollInput();
      if (dk == null) {
        break;
      }
      if (dk.getKeyType() == KeyType.Escape) {
        noteEscPending();
      }
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
      if (consumeEscPending()) {
        if (onlineMode) {
          gb.setGameStatus(MatchStatus.INTERRUPTED);
          gb.getClock().stop();
          return null;
        }
        boolean done = handleEscInGame();
        if (done) return null;
        renderWithCellSel(match, row, column);
        continue;
      }
      if (gb.getStatus() != MatchStatus.IN_PROGRESS) {
        return null;
      }
      KeyStroke k = screen.pollInput();
      if (k == null) {
        if (checkOnlineInboxShort()) {
          return null;
        }
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
        if (onlineMode) {
          gb.setGameStatus(MatchStatus.INTERRUPTED);
          gb.getClock().stop();
          return null;
        }
        boolean done = handleEscInGame();
        if (done) return null;
        renderWithCellSel(match, row, column);
        continue;
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
    if (receivedMatch == null) {
      throw new NetException("Jogada invalida recebida");
    }
    int gridRow = receivedMatch.getGridRow();
    int gridCol = receivedMatch.getGridCol();
    if (gridRow < 0 || gridRow > 2 || gridCol < 0 || gridCol > 2) {
      NetLog.log("receiveOpponentMove grid fora: " + gridRow + "," + gridCol);
      throw new NetException(I18n.t("invalid.pos"));
    }
    if (receivedMatch.getBoard() == null) {
      throw new NetException(I18n.t("invalid.pos"));
    }
    com.Campeol.subgame.Position lm = receivedMatch.getLastMove();
    if (lm != null) {
      if (lm.getRow() == null || lm.getColumn() == null
          || lm.getRow() < 0 || lm.getRow() > 2 || lm.getColumn() < 0 || lm.getColumn() > 2) {
        NetLog.log("receiveOpponentMove lastMove fora");
        throw new NetException(I18n.t("invalid.pos"));
      }
    }
    // Valida delta contra o estado local: exatamente 1 peça nova, sem remover/trocar.
    Match old;
    try {
      old = gb.getGamePlaces(gridRow, gridCol);
    } catch (Exception e) {
      throw new NetException(I18n.t("invalid.pos"));
    }
    if (old == null || old.getBoard() == null) {
      throw new NetException(I18n.t("invalid.pos"));
    }
    // Não aceita sobrescrever mini já finalizado localmente (evita roubo de mini).
    if (old.getMatchStatus() != MatchStatus.IN_PROGRESS) {
      NetLog.log("receiveOpponentMove mini local finalizado: " + gridRow + "," + gridCol);
      throw new NetException(I18n.t("finished.game"));
    }
    int diffs = 0;
    int diffR = -1;
    int diffC = -1;
    for (int r = 0; r < 3; r++) {
      for (int c = 0; c < 3; c++) {
        com.Campeol.subgame.Piece a = old.getBoard().getPiece(r, c);
        com.Campeol.subgame.Piece b = receivedMatch.getBoard().getPiece(r, c);
        if (a == null && b == null) continue;
        if (a == null && b != null) {
          diffs++;
          diffR = r;
          diffC = c;
          // peça nova deve ser X/O válido
          if (b.getXorO() != 'X' && b.getXorO() != 'O') {
            throw new NetException(I18n.t("invalid.pos"));
          }
          // deve ser a peça de quem tinha a vez (oponente)
          try {
            if (gb.getCurrentPlayer() != null && gb.getCurrentPlayer().getPiece() != null
                && b.getXorO() != gb.getCurrentPlayer().getPiece().getXorO()) {
              NetLog.log("receiveOpponentMove peca inesperada: " + b.getXorO());
              throw new NetException(I18n.t("invalid.pos"));
            }
          } catch (NetException ne) {
            throw ne;
          } catch (Exception ignored) {
          }
        } else if (a != null && b == null) {
          NetLog.log("receiveOpponentMove removeu peca");
          throw new NetException(I18n.t("invalid.pos"));
        } else {
          // ambos presentes: peça não pode mudar
          if (a.getXorO() != b.getXorO()) {
            NetLog.log("receiveOpponentMove trocou peca");
            throw new NetException(I18n.t("invalid.pos"));
          }
        }
      }
    }
    if (diffs != 1) {
      NetLog.log("receiveOpponentMove diffs=" + diffs);
      throw new NetException(I18n.t("invalid.pos"));
    }
    if (lm != null && (lm.getRow() != diffR || lm.getColumn() != diffC)) {
      NetLog.log("receiveOpponentMove lastMove diverge do delta");
      throw new NetException(I18n.t("invalid.pos"));
    }
    // Recomputa status do mini a partir do tabuleiro; não confia no serializado.
    boolean win = receivedMatch.getBoard().testEndGame();
    boolean full = receivedMatch.getBoard().isFull();
    MatchStatus expected = win ? MatchStatus.VICTORY
        : (full ? MatchStatus.DRAW : MatchStatus.IN_PROGRESS);
    if (receivedMatch.getMatchStatus() != expected) {
      NetLog.log("receiveOpponentMove corrige status " + receivedMatch.getMatchStatus()
          + " -> " + expected);
      receivedMatch.setMatchStatus(expected);
    }
    if (expected == MatchStatus.VICTORY) {
      // vencedor deve existir e ser da peça jogada; se ausente/inválido, deriva do delta
      boolean winnerOk = receivedMatch.getWinner() != null
          && receivedMatch.getWinner().getPiece() != null
          && (receivedMatch.getWinner().getPiece().getXorO() == 'X'
              || receivedMatch.getWinner().getPiece().getXorO() == 'O');
      if (!winnerOk) {
        NetLog.log("receiveOpponentMove winner ausente/invalido");
        throw new NetException(I18n.t("invalid.pos"));
      }
      com.Campeol.subgame.Piece placed = receivedMatch.getBoard().getPiece(diffR, diffC);
      if (placed != null && placed.getXorO() != receivedMatch.getWinner().getPiece().getXorO()) {
        NetLog.log("receiveOpponentMove winner diverge da peca jogada");
        throw new NetException(I18n.t("invalid.pos"));
      }
    }
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
    if (pos.getRow() == null || pos.getColumn() == null
        || pos.getRow() < 0 || pos.getRow() > 2 || pos.getColumn() < 0 || pos.getColumn() > 2) {
      NetLog.log("changeMatch pos fora");
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
    if (gb.getWinner() != null && gb.getWinner().getPiece() != null) {
      char p = gb.getWinner().getPiece().getXorO();
      if (onlineMode) {
        return nickForPiece(p);
      }
      return String.valueOf(p);
    }
    return "";
  }

  public EndScreen.Result endGame() throws IOException, InterruptedException {
    return endGameWithExtra(null);
  }

  public EndScreen.Result endGameWithExtra(String extra) throws IOException, InterruptedException {
    gb.getClock().stop();
    // desenha board final de fundo rapidamente e depois o modal
    try {
      render();
    } catch (Exception ignored) {
    }
    long matchMs = gb.getClock().matchMillis();
    EndScreen.draw(screen, txt, gb.getStatus(), winnerLabel(), sessionScore, matchMs, extra);
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

  /**
   * Final do rankeado: mesma tela normal, mas R/Enter emendam a próxima
   * partida (rush de elo) e só Esc/Q/N volta ao menu. Sem tecla em 10s,
   * avança sozinho para a próxima.
   */
  public EndScreen.Result endRankedGame(String extra) throws IOException, InterruptedException {
    gb.getClock().stop();
    try {
      render();
    } catch (Exception ignored) {
    }
    long matchMs = gb.getClock().matchMillis();
    String hint = I18n.t("end.rematch.ranked");
    while (pollInput() != null) {
    }
    long end = System.currentTimeMillis() + 10_000L;
    int lastShown = -1;
    while (true) {
      long remain = end - System.currentTimeMillis();
      if (remain <= 0) return new EndScreen.Result(EndScreen.Choice.REMATCH);
      int sec = (int) ((remain + 999) / 1000);
      if (sec != lastShown) {
        lastShown = sec;
        EndScreen.draw(screen, txt, gb.getStatus(), winnerLabel(), sessionScore, matchMs,
            withCountdown(extra, sec), hint);
      }
      KeyStroke k = pollInput();
      if (k != null) {
        if (k.getKeyType() == KeyType.Escape) return new EndScreen.Result(EndScreen.Choice.QUIT);
        if (k.getKeyType() == KeyType.Enter) return new EndScreen.Result(EndScreen.Choice.REMATCH);
        Character c = k.getCharacter();
        if (c != null) {
          char u = Character.toUpperCase(c);
          if (u == 'R') return new EndScreen.Result(EndScreen.Choice.REMATCH);
          if (u == 'N' || u == 'Q') return new EndScreen.Result(EndScreen.Choice.QUIT);
        }
      }
      Thread.sleep(50);
    }
  }

  private static String withCountdown(String extra, int sec) {
    String cd = I18n.t("ranked.next.in", sec);
    if (extra == null || extra.isEmpty()) return cd;
    return extra + "  |  " + cd;
  }
  public void newRoundAlternateStarter() {
    gb.newRoundAlternateStarter();
    lastDest = null;
    lastDestFree = true;
    transientMsg = null;
    escPending = false;
    stashedOnlineMatch = null;
  }

  // ---------- menu ESC local/rankeado (Sair e salvar / Desistir) ----------
  public enum EscChoice {
    RESUME,
    SAVE_QUIT,
    RESIGN
  }

  private String saveMode() {
    return rankedMode ? "RANKED" : "LOCAL";
  }

  /**
   * Menu do ESC (só local/rankeado). Online mantém saída direta.
   * 1 = Sair e salvar, 2 = Desistir, Esc = voltar ao jogo.
   */
  public EscChoice showEscMenu() throws IOException, InterruptedException {
    int sel = 0;
    while (true) {
      waitForEnoughSize();
      screen.clear();
      int cols = terminal.getTerminalSize().getColumns();
      int rows = terminal.getTerminalSize().getRows();
      String title = I18n.t("esc.title");
      String o1 = I18n.t("esc.save");
      String o2 = I18n.t("esc.resign");
      String back = I18n.t("esc.back");
      String hint = I18n.t("esc.hint");
      int w = Math.max(Math.max(title.length(), o1.length()), Math.max(o2.length(), hint.length())) + 8;
      w = Math.max(w, back.length() + 8);
      int h = 10;
      int x0 = Math.max(0, (cols - w) / 2);
      int y0 = Math.max(0, (rows - h) / 2);
      txt.setBackgroundColor(TextColor.ANSI.BLACK);
      txt.setForegroundColor(TextColor.ANSI.WHITE_BRIGHT);
      for (int y = 0; y < h; y++) {
        for (int x = 0; x < w; x++) {
          txt.putString(x0 + x, y0 + y, " ");
        }
      }
      txt.enableModifiers(SGR.BOLD);
      txt.putString(x0, y0, "┌" + "─".repeat(w - 2) + "┐");
      for (int i = 1; i < h - 1; i++) {
        txt.putString(x0, y0 + i, "│");
        txt.putString(x0 + w - 1, y0 + i, "│");
      }
      txt.putString(x0, y0 + h - 1, "└" + "─".repeat(w - 2) + "┘");
      txt.putString(x0 + Math.max(0, (w - title.length()) / 2), y0, " " + title + " ");
      txt.clearModifiers();
      String[] opts = {o1, o2};
      for (int i = 0; i < opts.length; i++) {
        txt.setBackgroundColor(TextColor.ANSI.BLACK);
        if (i == sel) {
          txt.setForegroundColor(TextColor.ANSI.WHITE_BRIGHT);
          txt.enableModifiers(SGR.REVERSE, SGR.BOLD);
        } else {
          txt.setForegroundColor(TextColor.ANSI.WHITE);
        }
        txt.putString(x0 + 3, y0 + 2 + i, opts[i].length() > w - 6
            ? opts[i].substring(0, w - 6) : opts[i]);
        txt.clearModifiers();
      }
      txt.setBackgroundColor(TextColor.ANSI.BLACK);
      txt.setForegroundColor(Theme.DIM_FG);
      txt.putString(x0 + Math.max(0, (w - back.length()) / 2), y0 + 5, back);
      txt.putString(x0 + Math.max(0, (w - hint.length()) / 2), y0 + 6, hint);
      txt.clearModifiers();
      txt.setBackgroundColor(null);
      txt.setForegroundColor(null);
      refresh();
      KeyStroke k = screen.readInput();
      if (k == null) continue;
      if (k.getKeyType() == KeyType.Escape) return EscChoice.RESUME;
      if (k.getKeyType() == KeyType.ArrowUp || k.getKeyType() == KeyType.ArrowDown) {
        sel = 1 - sel;
        continue;
      }
      if (k.getKeyType() == KeyType.Enter) {
        return sel == 0 ? EscChoice.SAVE_QUIT : EscChoice.RESIGN;
      }
      Character c = k.getCharacter();
      if (c != null) {
        if (c == '1') return EscChoice.SAVE_QUIT;
        if (c == '2') return EscChoice.RESIGN;
      }
    }
  }

  /**
   * Trata ESC no meio do jogo local/rankeado.
   *
   * @return true se o chamador deve encerrar (SAVE_QUIT ou RESIGN já aplicados),
   *         false se deve continuar jogando (RESUME).
   */
  private boolean handleEscInGame() throws IOException, InterruptedException {
    if (onlineMode) {
      gb.setGameStatus(MatchStatus.INTERRUPTED);
      gb.getClock().stop();
      return true;
    }
    EscChoice choice = showEscMenu();
    if (choice == EscChoice.RESUME) {
      render();
      return false;
    }
    if (choice == EscChoice.SAVE_QUIT) {
      try {
        com.Campeol.ui.LocalSave.save(saveMode(), gb, sessionScore,
            lastDest, lastDestFree, rankedHumanPiece);
      } catch (Exception e) {
        NetLog.log("esc save", e);
      }
      gb.setGameStatus(MatchStatus.INTERRUPTED);
      gb.getClock().stop();
      try {
        toast("save.saved", 1200);
      } catch (Exception ignored) {
      }
      return true;
    }
    // RESIGN: quem perde é o humano (rankeado) ou quem tem a vez (local).
    try {
      com.Campeol.subgame.Player loser;
      com.Campeol.subgame.Player win;
      if (rankedMode && rankedHumanPiece != null) {
        char hp = rankedHumanPiece;
        if (gb.getP1() != null && gb.getP1().getPiece() != null
            && gb.getP1().getPiece().getXorO() == hp) {
          loser = gb.getP1();
          win = gb.getP2();
        } else {
          loser = gb.getP2();
          win = gb.getP1();
        }
      } else {
        com.Campeol.subgame.Player cur = gb.getCurrentPlayer();
        if (cur == gb.getP1()) {
          loser = gb.getP1();
          win = gb.getP2();
        } else {
          loser = gb.getP2();
          win = gb.getP1();
        }
      }
      if (win != null) {
        gb.setWinner(win);
        gb.setGameStatus(MatchStatus.VICTORY);
      } else {
        gb.setGameStatus(MatchStatus.DRAW);
      }
      gb.setMatchFinished(true);
      gb.getClock().stop();
      com.Campeol.ui.LocalSave.delete(saveMode());
    } catch (Exception e) {
      NetLog.log("esc resign", e);
      gb.setGameStatus(MatchStatus.INTERRUPTED);
      gb.getClock().stop();
    }
    return true;
  }

  /**
   * Pergunta Continuar vs Nova quando há save. Retorna true = continuar.
   * Se o save está corrompido, apaga, avisa e retorna false (nova).
   */
  public boolean askContinueSave(String mode) throws IOException, InterruptedException {
    if (!com.Campeol.ui.LocalSave.exists(mode)) {
      return false;
    }
    waitForEnoughSize();
    screen.clear();
    int cols = terminal.getTerminalSize().getColumns();
    int rows = terminal.getTerminalSize().getRows();
    String title = I18n.t("save.found");
    String hint = I18n.t("save.continue");
    int w = Math.max(title.length(), hint.length()) + 8;
    int h = 7;
    int x0 = Math.max(0, (cols - w) / 2);
    int y0 = Math.max(0, (rows - h) / 2);
    txt.setBackgroundColor(TextColor.ANSI.BLACK);
    txt.setForegroundColor(TextColor.ANSI.WHITE_BRIGHT);
    for (int y = 0; y < h; y++) {
      for (int x = 0; x < w; x++) {
        txt.putString(x0 + x, y0 + y, " ");
      }
    }
    txt.enableModifiers(SGR.BOLD);
    txt.putString(x0, y0, "┌" + "─".repeat(w - 2) + "┐");
    for (int i = 1; i < h - 1; i++) {
      txt.putString(x0, y0 + i, "│");
      txt.putString(x0 + w - 1, y0 + i, "│");
    }
    txt.putString(x0, y0 + h - 1, "└" + "─".repeat(w - 2) + "┘");
    txt.putString(x0 + Math.max(0, (w - title.length()) / 2), y0 + 2, title);
    txt.clearModifiers();
    txt.setForegroundColor(Theme.DIM_FG);
    txt.putString(x0 + Math.max(0, (w - hint.length()) / 2), y0 + 4, hint);
    txt.setForegroundColor(null);
    txt.setBackgroundColor(null);
    refresh();
    while (true) {
      KeyStroke k = screen.readInput();
      if (k == null) continue;
      if (k.getKeyType() == KeyType.Escape) return false;
      Character c = k.getCharacter();
      if (c != null) {
        char u = Character.toUpperCase(c);
        if (u == 'S' || u == 'Y') return true;
        if (u == 'N') return false;
      }
      if (k.getKeyType() == KeyType.Enter) return true;
    }
  }

  /** Restaura save no board atual. Retorna false se corrompido. */
  public boolean restoreSave(String mode) {
    com.Campeol.ui.LocalSave s = com.Campeol.ui.LocalSave.load(mode);
    if (s == null) {
      return false;
    }
    boolean ok = s.applyTo(gb);
    if (!ok) {
      com.Campeol.ui.LocalSave.delete(mode);
      return false;
    }
    try {
      if (s.getSessionScore() != null) {
        sessionScore.restoreFrom(s.getSessionScore());
      }
    } catch (Exception ignored) {
    }
    try {
      lastDest = s.getLastDest();
      lastDestFree = s.isLastDestFree();
    } catch (Exception ignored) {
    }
    try {
      if ("RANKED".equals(mode) && s.getRankedHumanPiece() != null) {
        rankedHumanPiece = s.getRankedHumanPiece();
        gb.setNoClockPiece(oppositePiece(rankedHumanPiece));
      }
    } catch (Exception ignored) {
    }
    transientMsg = null;
    escPending = false;
    stashedOnlineMatch = null;
    resetLeaveReason();
    return true;
  }

  private static Character oppositePiece(Character p) {
    if (p == null) return null;
    if (p == 'X') return 'O';
    if (p == 'O') return 'X';
    return null;
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
