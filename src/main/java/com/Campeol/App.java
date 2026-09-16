package com.Campeol;

import java.io.IOException;

import com.Campeol.game.GameUI;
import com.Campeol.game.OnlineGame;
import com.Campeol.net.NetException;
import com.Campeol.net.NetLog;
import com.Campeol.subgame.Match;
import com.Campeol.subgame.Position;
import com.Campeol.subgame.exception.SubGameException;
import com.Campeol.net.NetPoll;
import com.Campeol.ui.EndScreen;
import com.Campeol.ui.I18n;
import com.Campeol.ui.NetMessage;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;

public class App {
  public static void main(String[] args) {
    int exitCode = 0;
    try (GameUI ui = new GameUI()) {
      boolean running = true;
      while (running) {
        GameUI.MainChoice choice = ui.mainMenu();
        switch (choice) {
          case LOCAL:
            playLocalSession(ui);
            break;
          case RANKED:
            playRankedSession(ui);
            break;
          case CREATE:
            playOnlineSession(ui, true);
            break;
          case JOIN:
            playOnlineSession(ui, false);
            break;
          case CONFIG:
            ui.openConfig();
            break;
          case HOWTO:
            ui.showHelpBlocking();
            break;
          case QUIT:
          default:
            running = false;
            break;
        }
      }
    } catch (IOException e) {
      NetLog.log("main io", e);
      exitCode = 1;
    } catch (InterruptedException y) {
      Thread.currentThread().interrupt();
      NetLog.log("main interrupted", y);
      exitCode = 1;
    }
    // Garante que o ESC realmente encerra o processo (try-with-resources
    // sozinho não mata threads do terminal em todos os SOs).
    System.exit(exitCode);
  }

  // ---------- sessoes (chamadas pelo menu) ----------
  private static void playLocalSession(GameUI ui) throws IOException, InterruptedException {
    ui.setOnlineMode(false);
    ui.setRankedMode(false);
    ui.resetForNewGame();
    ui.clearSessionScore();
    // save? pergunta Continuar vs Nova ao entrar
    boolean resumed = false;
    try {
      if (com.Campeol.ui.LocalSave.exists("LOCAL") && ui.askContinueSave("LOCAL")) {
        if (ui.restoreSave("LOCAL")) {
          try {
            ui.toast("save.loaded", 1200);
          } catch (Exception ignored) {
          }
          resumed = true;
        } else {
          try {
            ui.toast("save.invalid", 1500);
          } catch (Exception ignored) {
          }
          com.Campeol.ui.LocalSave.delete("LOCAL");
        }
      } else if (com.Campeol.ui.LocalSave.exists("LOCAL")) {
        // escolheu Nova: apaga o save antigo
        com.Campeol.ui.LocalSave.delete("LOCAL");
      }
    } catch (Exception e) {
      NetLog.log("local save ask", e);
    }
    if (!resumed) {
      // sessao local com revanche alternando quem começa
      char piece = ui.startLocalCustomGame();
      if (ui.getStatus() != MatchStatus.IN_PROGRESS) {
        // ESC na escolha da peça → volta ao menu com estado limpo
        ui.resetForNewGame();
        return;
      }
      ui.startPlayer(piece);
    }
    while (true) {
      playLocalMatch(ui);
      if (ui.getStatus() == MatchStatus.INTERRUPTED) {
        // ESC > Sair e salvar volta direto ao menu (save já gravado).
        // Sem EndScreen para não confundir com fim de partida.
        break;
      }
      if (ui.getStatus() != MatchStatus.IN_PROGRESS) {
        // Partida terminou (inclui Desistir = VICTORY do adversário): limpa save.
        com.Campeol.ui.LocalSave.delete("LOCAL");
        ui.recordResult();
        EndScreen.Result r = ui.endGame();
        if (r.choice == EndScreen.Choice.REMATCH) {
          ui.newRoundAlternateStarter();
          continue;
        }
      }
      break;
    }
    ui.resetForNewGame();
  }

  // ---------- rankeado local (vs Bot, L1-L10) ----------
  private static void playRankedSession(GameUI ui) throws IOException, InterruptedException {
    ui.setRankedMode(true);
    ui.clearSessionScore();
    // tutorial só na 1ª vez (depois fica no '?' durante a partida)
    if (!com.Campeol.ui.RankedProfile.isHelpSeen()) {
      boolean go = ui.showRankedHelpBlocking();
      com.Campeol.ui.RankedProfile.setHelpSeen();
      if (!go) {
        ui.setRankedMode(false);
        ui.setRankedProfile(null);
        ui.resetForNewGame();
        return;
      }
    }
    com.Campeol.ui.RankedProfile rank = com.Campeol.ui.RankedProfile.load();
    ui.setRankedProfile(rank);
    ui.resetForNewGame();
    java.util.Random rng = new java.util.Random();
    char humanPiece;
    char botPiece;
    boolean resumed = false;
    try {
      if (com.Campeol.ui.LocalSave.exists("RANKED") && ui.askContinueSave("RANKED")) {
        if (ui.restoreSave("RANKED")) {
          try {
            ui.toast("save.loaded", 1200);
          } catch (Exception ignored) {
          }
          resumed = true;
          Character hp = ui.getRankedHumanPiece();
          if (hp != null && (hp == 'X' || hp == 'O')) {
            humanPiece = hp;
            botPiece = (humanPiece == 'X' ? 'O' : 'X');
          } else {
            // save sem peça (corrompido parcial): deriva do board
            humanPiece = ui.getBoard().getP1().getPiece().getXorO();
            botPiece = ui.getBoard().getP2().getPiece().getXorO();
            ui.setRankedHumanPiece(humanPiece);
          }
          ui.getBoard().setNoClockPiece(botPiece);
        } else {
          try {
            ui.toast("save.invalid", 1500);
          } catch (Exception ignored) {
          }
          com.Campeol.ui.LocalSave.delete("RANKED");
          resumed = false;
        }
      } else if (com.Campeol.ui.LocalSave.exists("RANKED")) {
        com.Campeol.ui.LocalSave.delete("RANKED");
      }
    } catch (Exception e) {
      NetLog.log("ranked save ask", e);
    }
    if (!resumed) {
      // peças sorteadas (Aleatório), humano = p1, bot = p2
      ui.startPlayer();
      // sorteia quem começa na primeira partida; revanche alterna
      if (rng.nextBoolean()) {
        ui.changeTurn();
      }
      humanPiece = ui.getBoard().getP1().getPiece().getXorO();
      botPiece = ui.getBoard().getP2().getPiece().getXorO();
      // bot não tem timer: congela o relógio dele e esconde a linha no HUD
      ui.getBoard().setNoClockPiece(botPiece);
      ui.setRankedHumanPiece(humanPiece);
    } else {
      humanPiece = ui.getRankedHumanPiece() != null ? ui.getRankedHumanPiece()
          : ui.getBoard().getP1().getPiece().getXorO();
      botPiece = (humanPiece == 'X' ? 'O' : 'X');
    }
    while (true) {
      playRankedMatch(ui, rank, rng, humanPiece, botPiece);
      if (ui.getStatus() == MatchStatus.INTERRUPTED) {
        // SAVE_QUIT: mantém o save para continuar depois.
        break;
      }
      if (ui.getStatus() == MatchStatus.IN_PROGRESS) {
        break;
      }
      // Partida terminou (inclui Desistir = derrota do humano): limpa save.
      com.Campeol.ui.LocalSave.delete("RANKED");
      boolean humanWon = false;
      boolean botWon = false;
      if (ui.getStatus() == MatchStatus.VICTORY && ui.getBoard().getWinner() != null) {
        char w = ui.getBoard().getWinner().getPiece().getXorO();
        humanWon = (w == humanPiece);
        botWon = !humanWon;
      }
      com.Campeol.ui.RankedProfile.Result res;
      if (humanWon) {
        res = rank.registerWin();
      } else if (botWon) {
        res = rank.registerLoss();
      } else {
        res = rank.registerDraw();
      }
      rank.save();
      ui.recordResult();
      String extra = rankedExtraMessage(ui, rank, res, humanWon, botWon);
      EndScreen.Result r = ui.endRankedGame(extra);
      if (r.choice == EndScreen.Choice.REMATCH) {
        ui.newRoundAlternateStarter();
        // peças mantidas, só alterna quem começa (newRoundAlternateStarter já faz)
        continue;
      }
      break;
    }
    ui.getBoard().clearNoClockPiece();
    ui.setRankedMode(false);
    ui.setRankedProfile(null);
    ui.resetForNewGame();
  }

  private static String rankedExtraMessage(GameUI ui, com.Campeol.ui.RankedProfile rank,
      com.Campeol.ui.RankedProfile.Result res, boolean humanWon, boolean botWon) {
    com.Campeol.ui.RankedProfile r = rank;
    String bot = com.Campeol.ui.BotNames.name(r.getLevel());
    switch (res) {
      case PROMOTED:
        return I18n.t("ranked.up", r.getLevel(), bot);
      case DEMOTED:
        return I18n.t("ranked.down", r.getLevel(), bot);
      case FELL_FROM_10:
        return I18n.t("ranked.fell");
      case LIVES_LOST:
        return I18n.t("ranked.lives", r.getLives());
      case STREAK:
        return I18n.t("ranked.streak", r.getStreak());
      default:
        return null;
    }
  }

  private static void playRankedMatch(GameUI ui, com.Campeol.ui.RankedProfile rank,
      java.util.Random rng, char humanPiece, char botPiece)
      throws IOException, InterruptedException {
    if (ui.getStatus() != MatchStatus.IN_PROGRESS) return;
    Position lastPos = null;
    // primeira escolha livre: quem começa decide
    boolean humanTurn = ui.getBoard().getCurrentPlayer() != null
        && ui.getBoard().getCurrentPlayer().getPiece().getXorO() == humanPiece;
    Match active;
    if (humanTurn) {
      active = ui.bigMove();
      if (active == null || ui.getStatus() != MatchStatus.IN_PROGRESS) return;
    } else {
      int[] macro = com.Campeol.game.BotPlayer.chooseMacro(ui.getBoard(), botPiece,
          rank.getLevel(), rng);
      Match picked = null;
      try {
        picked = ui.getBoard().getGamePlaces(macro[0], macro[1]);
      } catch (Exception e) {
        picked = null;
      }
      if (picked == null || picked.getMatchStatus() != MatchStatus.IN_PROGRESS) {
        // Bot com macro inválido/finalizado: re-sorteia entre livres, nunca pede ao humano.
        NetLog.log("ranked macro do bot invalido, re-sorteia");
        picked = firstFreeMacro(ui);
        if (picked == null || ui.getStatus() != MatchStatus.IN_PROGRESS) return;
      }
      active = picked;
    }
    ui.render();
    while (ui.getStatus() == MatchStatus.IN_PROGRESS) {
      humanTurn = ui.getBoard().getCurrentPlayer() != null
          && ui.getBoard().getCurrentPlayer().getPiece().getXorO() == humanPiece;
      if (humanTurn) {
        Position pos;
        try {
          pos = ui.readInput(active);
        } catch (SubGameException e) {
          try {
            ui.showErro(e.getMessage());
          } catch (InterruptedException x) {
            Thread.currentThread().interrupt();
          }
          continue;
        }
        if (pos == null || ui.getStatus() != MatchStatus.IN_PROGRESS) return;
        try {
          ui.makeMove(active, pos);
        } catch (SubGameException e) {
          ui.showErro(e.getMessage());
          continue;
        }
        if (ui.getStatus() != MatchStatus.IN_PROGRESS) return;
        ui.changeTurn();
        lastPos = pos;
      } else {
        // vez do bot com pausinha "pensando"
        ui.setBotThinking(true);
        ui.render();
        try {
          Thread.sleep(400 + rng.nextInt(300));
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          ui.setBotThinking(false);
          return;
        }
        Position bpos;
        try {
          bpos = com.Campeol.game.BotPlayer.chooseCell(ui.getBoard(), active, botPiece,
              rank.getLevel(), rng);
        } catch (Exception e) {
          // fallback: primeira livre; se não há, o mini está inconsistente -> livre
          NetLog.log("ranked chooseCell falhou", e);
          bpos = null;
          try {
            for (com.Campeol.subgame.Position p : active.getBoard().freeCells()) {
              bpos = p;
              break;
            }
          } catch (Exception ignored) {
          }
          if (bpos == null) {
            // Mini sem casa livre mas IN_PROGRESS: trata como livre (evita loop infinito).
            Match free = firstFreeMacro(ui);
            if (free == null || ui.getStatus() != MatchStatus.IN_PROGRESS) {
              ui.setBotThinking(false);
              return;
            }
            active = free;
            ui.setBotThinking(false);
            ui.render();
            continue;
          }
        }
        try {
          ui.makeMove(active, bpos);
        } catch (SubGameException e) {
          // bot escolheu ocupada (não deveria): tenta aleatória livre
          boolean moved = false;
          for (com.Campeol.subgame.Position p : active.getBoard().freeCells()) {
            try {
              ui.makeMove(active, p);
              bpos = p;
              moved = true;
              break;
            } catch (SubGameException ignored) {
            }
          }
          if (!moved) {
            ui.setBotThinking(false);
            continue;
          }
        }
        ui.setBotThinking(false);
        if (ui.getStatus() != MatchStatus.IN_PROGRESS) {
          ui.render();
          return;
        }
        ui.changeTurn();
        lastPos = bpos;
        ui.render();
      }
      if (ui.getStatus() != MatchStatus.IN_PROGRESS) return;
      // próximo mini: forçado pelo lastPos, ou livre
      if (lastPos == null) {
        // não deveria: pede ao humano ou re-sorteia bot
        humanTurn = ui.getBoard().getCurrentPlayer() != null
            && ui.getBoard().getCurrentPlayer().getPiece().getXorO() == humanPiece;
        if (humanTurn) {
          active = ui.bigMove();
          if (active == null || ui.getStatus() != MatchStatus.IN_PROGRESS) return;
        } else {
          int[] macro = com.Campeol.game.BotPlayer.chooseMacro(ui.getBoard(), botPiece,
              rank.getLevel(), rng);
          Match picked = null;
          try {
            picked = ui.getBoard().getGamePlaces(macro[0], macro[1]);
          } catch (Exception e) {
            picked = null;
          }
          if (picked == null || picked.getMatchStatus() != MatchStatus.IN_PROGRESS) {
            picked = firstFreeMacro(ui);
            if (picked == null || ui.getStatus() != MatchStatus.IN_PROGRESS) return;
          }
          active = picked;
        }
        continue;
      }
      Match target = ui.getBoard().getGamePlaces(lastPos.getRow(), lastPos.getColumn());
      if (target.getMatchStatus() != MatchStatus.IN_PROGRESS) {
        // destino finalizado → próxima jogada livre
        humanTurn = ui.getBoard().getCurrentPlayer() != null
            && ui.getBoard().getCurrentPlayer().getPiece().getXorO() == humanPiece;
        if (humanTurn) {
          active = ui.bigMove();
          if (active == null || ui.getStatus() != MatchStatus.IN_PROGRESS) return;
          lastPos = null; // bigMove já escolheu o macro
        } else {
          int[] macro = com.Campeol.game.BotPlayer.chooseMacro(ui.getBoard(), botPiece,
              rank.getLevel(), rng);
          Match picked = null;
          try {
            picked = ui.getBoard().getGamePlaces(macro[0], macro[1]);
          } catch (Exception e) {
            picked = null;
          }
          if (picked == null || picked.getMatchStatus() != MatchStatus.IN_PROGRESS) {
            picked = firstFreeMacro(ui);
            if (picked == null || ui.getStatus() != MatchStatus.IN_PROGRESS) return;
          }
          active = picked;
        }
      } else {
        active = target;
      }
      ui.render();
    }
  }

  /** Primeiro macro IN_PROGRESS (fallback seguro do bot, sem pedir ao humano). */
  private static Match firstFreeMacro(GameUI ui) {
    try {
      for (int i = 0; i < 3; i++) {
        for (int j = 0; j < 3; j++) {
          Match m = ui.getBoard().getGamePlaces(i, j);
          if (m != null && m.getMatchStatus() == MatchStatus.IN_PROGRESS) {
            return m;
          }
        }
      }
    } catch (Exception e) {
      NetLog.log("firstFreeMacro", e);
    }
    return null;
  }

  private static void playOnlineSession(GameUI ui, boolean isServer)
      throws IOException, InterruptedException {
    ui.setOnlineMode(true);
    ui.setLocalNick(ui.ensureOnlineNick());
    ui.clearSessionScore();
    OnlineGame og = new OnlineGame();
    try {
      String password;
      boolean starts;
      if (isServer) {
        password = ui.promptPassword("pass.create");
        if (password == null) return;
        Boolean connected = waitForConnect(ui, og, true, password);
        if (connected == null) return;
        starts = connected;
        if (!exchangeNick(og, true, ui)) return;
        ui.setNickChangeListener(newNick -> sendCtrl(og, true,
            new NetMessage(NetMessage.Type.NICK, newNick)));
        og.startHeartbeat();
        ui.resetForNewGame();
        ui.setOnlineGame(og, true);
        ui.startPlayer(og.getServerPiece());
        ui.setLocalPiece(og.getServerPiece());
      } else {
        password = ui.promptPassword("pass.join");
        if (password == null) return;
        Boolean connected = waitForConnect(ui, og, false, password);
        if (connected == null) return;
        starts = !connected;
        if (!exchangeNick(og, false, ui)) return;
        ui.setNickChangeListener(newNick -> sendCtrl(og, false,
            new NetMessage(NetMessage.Type.NICK, newNick)));
        og.startHeartbeat();
        ui.resetForNewGame();
        ui.setOnlineGame(og, false);
        ui.startPlayer(og.getClientPiece());
        ui.setLocalPiece(og.getClientPiece());
      }
      boolean keepPlaying = true;
      while (keepPlaying) {
        ui.resetLeaveReason();
        playOnlineMatch(ui, og, isServer, starts);
        if (ui.getStatus() == MatchStatus.IN_PROGRESS) break;
        ui.recordResult();
        if (handleOnlineLeave(ui)) {
          keepPlaying = false;
          continue;
        }
        boolean rematch = onlineRematchHandshake(ui, og, isServer);
        if (ui.getLeaveReason() == GameUI.LeaveReason.PEER_QUIT
            || ui.getLeaveReason() == GameUI.LeaveReason.PEER_LOST) {
          showPeerLeftCountdown(ui, ui.getLeaveReason());
          keepPlaying = false;
        } else if (rematch) {
          ui.newRoundAlternateStarter();
          starts = !starts;
        } else {
          keepPlaying = false;
        }
      }
    } catch (RuntimeException e) {
      // ex. servidor não encontrado, senha inválida: mostra e volta ao menu
      String msg = e.getMessage() != null ? e.getMessage() : e.toString();
      try {
        ui.showErro(msg);
      } catch (InterruptedException x) {
        Thread.currentThread().interrupt();
      }
    } finally {
      try {
        ui.clearOnlineGame();
      } catch (Exception ignored) {
      }
      try {
        ui.setNickChangeListener(null);
      } catch (Exception ignored) {
      }
      og.close();
    }
    ui.resetForNewGame();
  }

  // ---------- local ----------
  private static void playLocalMatch(GameUI ui) throws IOException, InterruptedException {
    if (ui.getStatus() != MatchStatus.IN_PROGRESS) return;
    Match match = ui.bigMove();
    if (match == null || ui.getStatus() != MatchStatus.IN_PROGRESS) return;
    while (ui.getStatus() == MatchStatus.IN_PROGRESS) {
      ui.render();
      try {
        Position pos = ui.readInput(match);
        if (pos == null || ui.getStatus() != MatchStatus.IN_PROGRESS) return;
        try {
          ui.makeMove(match, pos);
        } catch (SubGameException e) {
          ui.showErro(e.getMessage());
          continue;
        }
        if (ui.getStatus() != MatchStatus.IN_PROGRESS) return;
        ui.changeTurn();
        match = ui.changeMatch(pos);
        if (match == null || ui.getStatus() != MatchStatus.IN_PROGRESS) return;
      } catch (SubGameException e) {
        try {
          ui.showErro(e.getMessage());
        } catch (InterruptedException x) {
          Thread.currentThread().interrupt();
          NetLog.log("playLocal showErro", x);
        }
      }
    }
  }

  // ---------- online ----------
  private static void playOnlineMatch(GameUI ui, OnlineGame og, boolean isServer, boolean iStart)
      throws IOException, InterruptedException {
    Match match;
    ui.render();
    if (iStart) {
      match = ui.bigMove();
      if (match == null || ui.getStatus() != MatchStatus.IN_PROGRESS) {
        quitMatch(og, isServer, ui);
        return;
      }
      Position pos = ui.readInput(match);
      if (pos == null || ui.getStatus() != MatchStatus.IN_PROGRESS) {
        quitMatch(og, isServer, ui);
        return;
      }
      try {
        ui.makeMove(match, pos);
      } catch (SubGameException e) {
        ui.showErro(e.getMessage());
        // Primeira jogada inválida: volta a pedir sem trocar turno nem enviar.
        // Evita dessincronizar os peers com estado não-movido.
        pos = ui.readInput(match);
        if (pos == null || ui.getStatus() != MatchStatus.IN_PROGRESS) {
          quitMatch(og, isServer, ui);
          return;
        }
        try {
          ui.makeMove(match, pos);
        } catch (SubGameException e2) {
          ui.showErro(e2.getMessage());
          quitMatch(og, isServer, ui);
          return;
        }
      }
      if (ui.getStatus() != MatchStatus.IN_PROGRESS) return;
      ui.changeTurn();
      if (!sendMatch(og, isServer, match)) {
        ui.setLeaveReason(GameUI.LeaveReason.PEER_LOST);
        ui.setGameStatus(MatchStatus.INTERRUPTED);
        return;
      }
      ui.render();
    }
    while (ui.getStatus() == MatchStatus.IN_PROGRESS) {
      match = receiveWithEscCheck(og, isServer, ui);
      if (match == null) {
        quitMatch(og, isServer, ui);
        return;
      }
      try {
        ui.receiveOpponentMove(match);
      } catch (NetException ne) {
        // Jogada inválida do peer (bug/cheat): encerra como queda, sem crash.
        NetLog.log("playOnline jogada invalida do peer", ne);
        try {
          ui.showErro(ne.getMessage());
        } catch (InterruptedException x) {
          Thread.currentThread().interrupt();
        }
        ui.setLeaveReason(GameUI.LeaveReason.PEER_LOST);
        ui.setGameStatus(MatchStatus.INTERRUPTED);
        return;
      }
      if (ui.getStatus() != MatchStatus.IN_PROGRESS) return;
      Position forced = null;
      try {
        forced = match.getLastMove();
      } catch (Exception ignored) {
      }
      try {
        match = ui.changeMatch(forced);
      } catch (Exception e) {
        NetLog.log("playOnline changeMatch", e);
        ui.setLeaveReason(GameUI.LeaveReason.PEER_LOST);
        ui.setGameStatus(MatchStatus.INTERRUPTED);
        return;
      }
      if (match == null || ui.getStatus() != MatchStatus.IN_PROGRESS) {
        quitMatch(og, isServer, ui);
        return;
      }
      ui.render();
      try {
        Position pos = ui.readInput(match);
        if (pos == null || ui.getStatus() != MatchStatus.IN_PROGRESS) {
          quitMatch(og, isServer, ui);
          return;
        }
        // retry até achar casa livre (occupied)
        while (true) {
          try {
            ui.makeMove(match, pos);
            break;
          } catch (SubGameException e) {
            ui.showErro(e.getMessage());
            pos = ui.readInput(match);
            if (pos == null || ui.getStatus() != MatchStatus.IN_PROGRESS) {
              quitMatch(og, isServer, ui);
              return;
            }
          }
        }
        if (ui.getStatus() != MatchStatus.IN_PROGRESS) return;
        ui.changeTurn();
        if (!sendMatch(og, isServer, match)) {
          ui.setLeaveReason(GameUI.LeaveReason.PEER_LOST);
          ui.setGameStatus(MatchStatus.INTERRUPTED);
          return;
        }
        ui.render();
      } catch (SubGameException e) {
        try {
          ui.showErro(e.getMessage());
        } catch (InterruptedException x) {
          Thread.currentThread().interrupt();
          NetLog.log("playOnline showErro", x);
        }
      } catch (NetException ex) {
        try {
          ui.showErro(ex.getMessage());
        } catch (InterruptedException y) {
          Thread.currentThread().interrupt();
          NetLog.log("playOnline netErro", y);
        }
      }
    }
  }

  /**
   * Espera a conexão com Esc cancelável (estilo Clash Royale): a conexão
   * roda em worker-thread e este loop consulta o teclado. Conexão concluída
   * vence o Esc da mesma fatia. Retorna null se o usuário cancelou (com
   * toast) — nesse caso volta ao menu. Falha de rede vira RuntimeException
   * (tratada pelo chamador com showErro + volta ao menu).
   */
  private static Boolean waitForConnect(GameUI ui, OnlineGame og, boolean isServer, String password)
      throws IOException, InterruptedException {
    og.connectAsync(isServer, password);
    String waitKey = isServer ? "wait.host" : "wait.search";
    long t0 = System.currentTimeMillis();
    int lastSec = -1;
    // drena teclas antigas para um Esc velho não cancelar na hora
    while (ui.pollInput() != null) {
    }
    try {
      ui.consumeEscPending();
    } catch (Exception ignored) {
    }
    while (!og.isConnectDone()) {
      KeyStroke k = ui.pollInput();
      if (k != null && k.getKeyType() == KeyType.Escape) {
        og.cancelConnect();
        og.close();
        ui.toast("wait.cancelled", 1200);
        try {
          ui.consumeEscPending();
        } catch (Exception ignored) {
        }
        return null;
      }
      int sec = (int) ((System.currentTimeMillis() - t0) / 1000);
      if (sec != lastSec) {
        lastSec = sec;
        ui.showWaiting(waitKey, sec + "s");
      }
      Thread.sleep(120);
    }
    if (og.wasCancelRequested()) {
      og.close();
      ui.toast("wait.cancelled", 1200);
      return null;
    }
    Throwable err = og.connectError();
    if (err != null) {
      throw new RuntimeException(err.getMessage() != null ? err.getMessage() : err.toString());
    }
    Boolean result = og.connectResult();
    if (result == null) {
      // não deveria acontecer (worker sempre preenche); sem isso o
      // unboxing em starts quebraria com NPE
      throw new RuntimeException("connectResult vazio");
    }
    return result;
  }

  private static boolean sendMatch(OnlineGame og, boolean isServer, Match m) {
    try {
      if (isServer) return og.getSendServer(m);
      else return og.getSendClient(m);
    } catch (Exception e) {
      NetLog.log("sendMatch", e);
      return false;
    }
  }

  /**
   * Saída local: avisa o peer uma única vez (best-effort) antes de fechar.
   * Não faz nada se o jogo terminou normal ou se a saída foi do peer.
   */
  private static void quitMatch(OnlineGame og, boolean isServer, GameUI ui) {
    if (ui.getStatus() != MatchStatus.INTERRUPTED) return;
    if (ui.getLeaveReason() != GameUI.LeaveReason.NONE) return;
    ui.setLeaveReason(GameUI.LeaveReason.LOCAL);
    String nick = ui.getLocalNick() != null ? ui.getLocalNick() : I18n.t("nick.you");
    sendCtrl(og, isServer, new NetMessage(NetMessage.Type.QUIT, nick));
  }

  private static Match receiveWithEscCheck(OnlineGame og, boolean isServer, GameUI ui)
      throws IOException, InterruptedException {
    // Match chegado durante input local (poll curto fez stash): consome primeiro.
    try {
      Match stashed = ui.takeStashedOnlineMatch();
      if (stashed != null) {
        og.touch();
        if (!isValidStash(stashed)) {
          NetLog.log("receiveWithEscCheck stash invalido, descarta");
        } else {
          return stashed;
        }
      }
    } catch (Exception e) {
      NetLog.log("receiveWithEscCheck stash", e);
    }
    if (ui.consumeEscPending()) {
      ui.setGameStatus(MatchStatus.INTERRUPTED);
      quitMatch(og, isServer, ui);
      return null;
    }
    while (true) {
      KeyStroke key = ui.pollInput();
      if (key != null && key.getKeyType() == KeyType.Escape) {
        ui.setGameStatus(MatchStatus.INTERRUPTED);
        quitMatch(og, isServer, ui);
        return null;
      }
      // Peer pode ter saído enquanto estávamos no input: checa stash de novo.
      try {
        Match stashed = ui.takeStashedOnlineMatch();
        if (stashed != null) {
          og.touch();
          if (!isValidStash(stashed)) {
            NetLog.log("receiveWithEscCheck stash invalido, descarta");
          } else {
            return stashed;
          }
        }
      } catch (Exception e) {
        NetLog.log("receiveWithEscCheck stash", e);
      }
      if (ui.getStatus() != MatchStatus.IN_PROGRESS) {
        quitMatch(og, isServer, ui);
        return null;
      }
      NetPoll p = isServer ? og.pollServer() : og.pollClient();
      if (p == null) {
        continue;
      }
      if (p.kind() == NetPoll.Kind.OK) {
        og.touch();
        Object o = p.payload();
        if (o instanceof Match) {
          if (!isValidStash((Match) o)) {
            NetLog.log("receiveWithEscCheck Match invalido, descarta");
            continue;
          }
          return (Match) o;
        }
        if (o instanceof NetMessage) {
          NetMessage nm = (NetMessage) o;
          if (nm.getType() == null) {
            NetLog.log("receiveWithEscCheck NetMessage sem tipo");
            continue;
          }
          if (nm.getType() == NetMessage.Type.NICK && nm.getPayload() != null) {
            ui.setOpponentNick(nm.getPayload());
          } else if (nm.getType() == NetMessage.Type.QUIT) {
            ui.setLeaveReason(GameUI.LeaveReason.PEER_QUIT);
            ui.setGameStatus(MatchStatus.INTERRUPTED);
            return null;
          }
          // PING e outro controle fora de hora: ignora e segue esperando o Match
        }
        if (ui.getStatus() != MatchStatus.IN_PROGRESS) return null;
        continue;
      }
      if (p.kind() == NetPoll.Kind.DISCONNECTED) {
        ui.setLeaveReason(GameUI.LeaveReason.PEER_LOST);
        ui.setGameStatus(MatchStatus.INTERRUPTED);
        return null;
      }
      // TIMEOUT: peer vivo, só pensando — mas confere o silêncio total
      if (og.isPeerDead()) {
        ui.setLeaveReason(GameUI.LeaveReason.PEER_LOST);
        ui.setGameStatus(MatchStatus.INTERRUPTED);
        return null;
      }
      if (ui.getStatus() != MatchStatus.IN_PROGRESS) return null;
    }
  }

  // ---------- nick ----------
  // Retorna false se o usuário cancelou com ESC (volta ao menu).
  // Timeout sem nick NÃO é sucesso: avisa e volta ao menu (evita jogo quebrado).
  private static boolean exchangeNick(OnlineGame og, boolean isServer, GameUI ui) {
    try {
      String myNick = ui.getLocalNick() != null ? ui.getLocalNick() : I18n.t("nick.you");
      NetMessage hello = new NetMessage(NetMessage.Type.NICK, myNick);
      boolean sent = isServer ? og.sendServerObject(hello) : og.sendClientObject(hello);
      if (!sent) {
        NetLog.log("exchangeNick falhou ao enviar");
        ui.setLeaveReason(GameUI.LeaveReason.PEER_LOST);
        ui.setGameStatus(MatchStatus.INTERRUPTED);
        return false;
      }
      // espera o nick do oponente (até ~4s), com ESC cancelável
      long end = System.currentTimeMillis() + 4000;
      while (System.currentTimeMillis() < end) {
        KeyStroke k = null;
        try {
          k = ui.pollInput();
        } catch (Exception e) {
          NetLog.log("exchangeNick pollInput", e);
        }
        if (k != null && k.getKeyType() == KeyType.Escape) {
          return false;
        }
        if (ui.consumeEscPending()) {
          return false;
        }
        NetPoll p = isServer ? og.pollServer() : og.pollClient();
        if (p == null) {
          Thread.sleep(50);
          continue;
        }
        if (p.kind() == NetPoll.Kind.OK) {
          og.touch();
          Object o = p.payload();
          if (o instanceof NetMessage) {
            NetMessage nm = (NetMessage) o;
            if (nm.getType() == null) {
              continue;
            }
            if (nm.getType() == NetMessage.Type.NICK && nm.getPayload() != null) {
              ui.setOpponentNick(nm.getPayload());
              return true;
            }
            if (nm.getType() == NetMessage.Type.QUIT) {
              ui.setLeaveReason(GameUI.LeaveReason.PEER_QUIT);
              ui.setGameStatus(MatchStatus.INTERRUPTED);
              return false;
            }
            // ignora outros tipos aqui (revanche ainda não começou)
          } else if (o instanceof Match) {
            // não deveria chegar Match antes do jogo; ignora com log
            NetLog.log("exchangeNick Match precoce, ignora");
          }
        } else if (p.kind() == NetPoll.Kind.DISCONNECTED) {
          ui.setLeaveReason(GameUI.LeaveReason.PEER_LOST);
          ui.setGameStatus(MatchStatus.INTERRUPTED);
          return false;
        }
        Thread.sleep(50);
      }
      // Timeout: sem nick do oponente, segue com fallback mas avisa no log.
      // Não é erro fatal (HUD usa nick.you/nick.opp), então retorna true.
      NetLog.log("exchangeNick timeout, segue sem nick do oponente");
      return true;
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
      return false;
    } catch (Exception e) {
      NetLog.log("exchangeNick", e);
      return false;
    }
  }

  /** Validação leve do stash: bounds + board presente (detalhe em receiveOpponentMove). */
  private static boolean isValidStash(Match m) {
    if (m == null || m.getBoard() == null) return false;
    try {
      int gr = m.getGridRow();
      int gc = m.getGridCol();
      if (gr < 0 || gr > 2 || gc < 0 || gc > 2) return false;
      com.Campeol.subgame.Position lm = m.getLastMove();
      if (lm != null && (lm.getRow() == null || lm.getColumn() == null
          || lm.getRow() < 0 || lm.getRow() > 2 || lm.getColumn() < 0 || lm.getColumn() > 2)) {
        return false;
      }
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  // ---------- revanche online ----------
  private static boolean onlineRematchHandshake(GameUI ui, OnlineGame og, boolean isServer) {
    try {
      // desenha tela final base
      drawEnd(ui, null);
      boolean iRequested = false;
      boolean peerRequested = false;
      // drena teclado preservando ESC (vira escPending)
      while (true) {
        KeyStroke dk;
        try {
          dk = ui.pollInput();
        } catch (Exception ignored) {
          break;
        }
        if (dk == null) {
          break;
        }
        if (dk.getKeyType() == KeyType.Escape) {
          try {
            ui.consumeEscPending();
          } catch (Exception ignored) {
          }
          // ESC aqui = saída local: marca LOCAL para distinção
          sendCtrl(og, isServer, new NetMessage(NetMessage.Type.REMATCH_DECLINE, ""));
          ui.setLeaveReason(GameUI.LeaveReason.LOCAL);
          return false;
        }
      }
      while (true) {
        // 1) teclado local
        KeyStroke k = ui.pollInput();
        if (k != null) {
          if (k.getKeyType() == KeyType.Escape || ui.consumeEscPending()) {
            sendCtrl(og, isServer, new NetMessage(NetMessage.Type.REMATCH_DECLINE, ""));
            if (ui.getLeaveReason() == GameUI.LeaveReason.NONE) {
              ui.setLeaveReason(GameUI.LeaveReason.LOCAL);
            }
            return false;
          }
          Character c = k.getCharacter();
          if (c != null) {
            char u = Character.toUpperCase(c);
            if (u == 'R') {
              if (peerRequested) {
                sendCtrl(og, isServer, new NetMessage(NetMessage.Type.REMATCH_ACCEPT, ""));
                return true;
              }
              if (!iRequested) {
                sendCtrl(og, isServer, new NetMessage(NetMessage.Type.REMATCH_REQUEST, ""));
                iRequested = true;
                drawEnd(ui, I18n.t("end.rematch.wait"));
              }
            } else if (u == 'N' || u == 'Q') {
              sendCtrl(og, isServer, new NetMessage(NetMessage.Type.REMATCH_DECLINE, ""));
              return false;
            } else if ((u == 'S' || u == 'Y') && peerRequested) {
              sendCtrl(og, isServer, new NetMessage(NetMessage.Type.REMATCH_ACCEPT, ""));
              return true;
            }
          }
        }
        // 2) rede (não-bloqueante via timeout 500ms interno)
        NetPoll p = isServer ? og.pollServer() : og.pollClient();
        if (p == null) {
          Thread.sleep(50);
          continue;
        }
        if (p.kind() == NetPoll.Kind.OK) {
          og.touch();
          Object o = p.payload();
          if (!(o instanceof NetMessage)) {
            // Match atrasado, ignora
            NetLog.log("rematch Match atrasado, ignora");
            Thread.sleep(50);
            continue;
          }
          NetMessage nm = (NetMessage) o;
          if (nm.getType() == null) {
            NetLog.log("rematch NetMessage sem tipo");
            Thread.sleep(50);
            continue;
          }
          if (nm.getType() == NetMessage.Type.NICK && nm.getPayload() != null) {
            // nick trocado no meio da sessão: atualiza e redesenha a tela final
            ui.setOpponentNick(nm.getPayload());
            drawEnd(ui, peerRequested ? I18n.t("end.rematch.request")
                : (iRequested ? I18n.t("end.rematch.wait") : null));
            Thread.sleep(50);
            continue;
          }
          if (nm.getType() == NetMessage.Type.QUIT) {
            ui.setLeaveReason(GameUI.LeaveReason.PEER_QUIT);
            return false;
          }
          if (nm.getType() == NetMessage.Type.PING) {
            Thread.sleep(50);
            continue;
          }
          switch (nm.getType()) {
            case REMATCH_REQUEST:
              if (iRequested) {
                // ambos pediram ao mesmo tempo = aceite mútuo
                sendCtrl(og, isServer, new NetMessage(NetMessage.Type.REMATCH_ACCEPT, ""));
                return true;
              }
              peerRequested = true;
              drawEnd(ui, I18n.t("end.rematch.request"));
              break;
            case REMATCH_ACCEPT:
              if (iRequested || peerRequested) return true;
              // aceite sem pedido? ignora
              break;
            case REMATCH_DECLINE:
              return false;
            default:
              break;
          }
        } else if (p.kind() == NetPoll.Kind.DISCONNECTED) {
          ui.setLeaveReason(GameUI.LeaveReason.PEER_LOST);
          return false;
        } else {
          // TIMEOUT: peer vivo, só indeciso — mas confere o silêncio total
          if (og.isPeerDead()) {
            ui.setLeaveReason(GameUI.LeaveReason.PEER_LOST);
            return false;
          }
        }
        if (ui.getStatus() == MatchStatus.INTERRUPTED) return false;
        Thread.sleep(50);
      }
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
      return false;
    } catch (Exception e) {
      NetLog.log("onlineRematchHandshake", e);
      return false;
    }
  }

  /**
   * Se alguém saiu no meio do jogo: conta como nada (só interrompe).
   * Peer que saiu/caiu → aviso com saída automática. Saída local →
   * sai direto (QUIT já enviado). Retorna true se tratou (encerrar o loop).
   */
  private static boolean handleOnlineLeave(GameUI ui) {
    GameUI.LeaveReason left = ui.getLeaveReason();
    if (left == GameUI.LeaveReason.PEER_QUIT || left == GameUI.LeaveReason.PEER_LOST) {
      showPeerLeftCountdown(ui, left);
      return true;
    }
    return left == GameUI.LeaveReason.LOCAL;
  }

  /** Tela "oponente saiu/caiu" com saída automática em ~5s (Esc pula). */
  private static void showPeerLeftCountdown(GameUI ui, GameUI.LeaveReason reason) {
    String base = reason == GameUI.LeaveReason.PEER_QUIT
        ? I18n.t("net.peer.quit")
        : I18n.t("net.peer.lost");
    try {
      while (true) {
        KeyStroke dk = ui.pollInput();
        if (dk == null) {
          break;
        }
        // Preserva ESC como skip via loop interno; outras teclas descarta.
        if (dk.getKeyType() != KeyType.Escape) {
          continue;
        }
        // Devolve como pending para pular imediatamente.
        // GameUI não expõe setter, então trata direto no loop abaixo.
        // Simplificação: sai do countdown já (mesmo efeito do skip).
        drawEnd(ui, base + " " + I18n.t("net.leaving", 0));
        return;
      }
      for (int s = 5; s > 0; s--) {
        drawEnd(ui, base + " " + I18n.t("net.leaving", s));
        long end = System.currentTimeMillis() + 1000;
        boolean skip = false;
        while (System.currentTimeMillis() < end) {
          KeyStroke k = ui.pollInput();
          if (k != null) {
            if (k.getKeyType() == KeyType.Escape || ui.consumeEscPending()) {
              skip = true;
              break;
            }
          }
          Thread.sleep(50);
        }
        if (skip) break;
      }
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
    } catch (Exception e) {
      NetLog.log("showPeerLeftCountdown", e);
    }
  }

  private static void drawEnd(GameUI ui, String extra) {
    try {
      com.Campeol.ui.EndScreen.draw(ui.getScreen(), ui.getTextGraphics(), ui.getStatus(), ui.winnerLabel(),
          ui.getSessionScore(), ui.getBoard().getClock().matchMillis(), extra);
    } catch (Exception e) {
      NetLog.log("drawEnd", e);
    }
  }

  private static void sendCtrl(OnlineGame og, boolean isServer, NetMessage m) {
    boolean ok = false;
    try {
      if (isServer) ok = og.sendServerObject(m);
      else ok = og.sendClientObject(m);
    } catch (Exception e) {
      NetLog.log("sendCtrl", e);
      return;
    }
    if (ok && m.getType() == NetMessage.Type.QUIT) {
      // Dá chance ao TCP entregar o QUIT antes do close() (que envia FIN).
      // Sem isso o peer via só DISCONNECTED ou nada (bug relatado).
      try {
        Thread.sleep(250);
      } catch (InterruptedException ie) {
        Thread.currentThread().interrupt();
      }
    }
  }

}
