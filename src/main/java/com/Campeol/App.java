package com.Campeol;

import java.io.IOException;
import java.util.Scanner;

import com.Campeol.game.GameUI;
import com.Campeol.game.OnlineGame;
import com.Campeol.net.NetException;
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
    Scanner sc = new Scanner(System.in);
    int exitCode = 0;
    try (GameUI ui = new GameUI()) {
      if (ui.Menu(sc) == 1) { // MULTIPLAYER
        OnlineGame og = new OnlineGame();
        if (ui.OnlineMenu(sc) == 1) { // CREATE GAME (server)
          ui.setOnlineMode(true);
          String nick = ui.ensureOnlineNick();
          ui.setLocalNick(nick);
          Boolean firstMove = og.createGame();
          exchangeNick(og, true, ui);
          // Troca de nick no meio da partida (config "1"): propaga ao oponente
          // para o HUD dele atualizar junto.
          ui.setNickChangeListener(newNick -> sendCtrl(og, true,
              new NetMessage(NetMessage.Type.NICK, newNick)));
          og.startHeartbeat();
          ui.startPlayer(og.getServerPiece());
          boolean serverStarts = firstMove;
          boolean keepPlaying = true;
          while (keepPlaying) {
            ui.resetLeaveReason();
            playOnlineMatch(ui, og, true, serverStarts);
            if (ui.getStatus() == MatchStatus.IN_PROGRESS) break;
            ui.recordResult();
            if (handleOnlineLeave(ui)) {
              keepPlaying = false;
              continue;
            }
            boolean rematch = onlineRematchHandshake(ui, og, true);
            if (ui.getLeaveReason() == GameUI.LeaveReason.PEER_QUIT
                || ui.getLeaveReason() == GameUI.LeaveReason.PEER_LOST) {
              showPeerLeftCountdown(ui, ui.getLeaveReason());
              keepPlaying = false;
            } else if (rematch) {
              ui.newRoundAlternateStarter();
              serverStarts = !serverStarts;
            } else {
              keepPlaying = false;
            }
          }
          og.close();
        } else { // GET IN THE GAME (client)
          ui.setOnlineMode(true);
          String nick = ui.ensureOnlineNick();
          ui.setLocalNick(nick);
          Boolean firstMove = og.getInTheGame();
          exchangeNick(og, false, ui);
          // Troca de nick no meio da partida (config "1"): propaga ao oponente
          // para o HUD dele atualizar junto.
          ui.setNickChangeListener(newNick -> sendCtrl(og, false,
              new NetMessage(NetMessage.Type.NICK, newNick)));
          og.startHeartbeat();
          ui.startPlayer(og.getClientPiece());
          boolean clientStarts = !firstMove;
          boolean keepPlaying = true;
          while (keepPlaying) {
            ui.resetLeaveReason();
            playOnlineMatch(ui, og, false, clientStarts);
            if (ui.getStatus() == MatchStatus.IN_PROGRESS) break;
            ui.recordResult();
            if (handleOnlineLeave(ui)) {
              keepPlaying = false;
              continue;
            }
            boolean rematch = onlineRematchHandshake(ui, og, false);
            if (ui.getLeaveReason() == GameUI.LeaveReason.PEER_QUIT
                || ui.getLeaveReason() == GameUI.LeaveReason.PEER_LOST) {
              showPeerLeftCountdown(ui, ui.getLeaveReason());
              keepPlaying = false;
            } else if (rematch) {
              ui.newRoundAlternateStarter();
              clientStarts = !clientStarts;
            } else {
              keepPlaying = false;
            }
          }
          og.close();
        }
      } else {
        ui.setOnlineMode(false);
        // sessao local com revanche alternando quem começa
        char piece = ui.startLocalCustomGame();
        if (ui.getStatus() != MatchStatus.IN_PROGRESS) {
          // ESC na escolha da peça → sai direto, sem modal duplo
          exitCode = 0;
        } else {
          ui.startPlayer(piece);
          while (true) {
            playLocalMatch(ui);
            if (ui.getStatus() != MatchStatus.IN_PROGRESS) {
              ui.recordResult();
              EndScreen.Result r = ui.endGame();
              if (r.choice == EndScreen.Choice.REMATCH) {
                ui.newRoundAlternateStarter();
                continue;
              }
            }
            break;
          }
        }
      }
    } catch (IOException e) {
      e.printStackTrace();
      exitCode = 1;
    } catch (InterruptedException y) {
      y.printStackTrace();
      exitCode = 1;
    } finally {
      try {
        sc.close();
      } catch (Exception ignored) {
      }
    }
    // Garante que o ESC realmente encerra o processo (try-with-resources
    // sozinho não mata threads do terminal/Scanner em todos os SOs).
    System.exit(exitCode);
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
          x.printStackTrace();
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
        // se a primeira jogada for inválida (quase impossível), segue para o loop
      }
      if (ui.getStatus() != MatchStatus.IN_PROGRESS) return;
      ui.changeTurn();
      sendMatch(og, isServer, match);
      ui.render();
    }
    while (ui.getStatus() == MatchStatus.IN_PROGRESS) {
      match = receiveWithEscCheck(og, isServer, ui);
      if (match == null) {
        quitMatch(og, isServer, ui);
        return;
      }
      ui.receiveOpponentMove(match);
      if (ui.getStatus() != MatchStatus.IN_PROGRESS) return;
      match = ui.changeMatch(match.getLastMove());
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
        sendMatch(og, isServer, match);
        ui.render();
      } catch (SubGameException e) {
        try {
          ui.showErro(e.getMessage());
        } catch (InterruptedException x) {
          x.printStackTrace();
        }
      } catch (NetException ex) {
        try {
          ui.showErro(ex.getMessage());
        } catch (InterruptedException y) {
          y.printStackTrace();
        }
      }
    }
  }

  private static void sendMatch(OnlineGame og, boolean isServer, Match m) {
    if (isServer) og.getSendServer(m);
    else og.getSendClient(m);
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
    while (true) {
      KeyStroke key = ui.pollInput();
      if (key != null && key.getKeyType() == KeyType.Escape) {
        ui.setGameStatus(MatchStatus.INTERRUPTED);
        quitMatch(og, isServer, ui);
        return null;
      }
      NetPoll p = isServer ? og.pollServer() : og.pollClient();
      if (p.kind() == NetPoll.Kind.OK) {
        og.touch();
        Object o = p.payload();
        if (o instanceof Match) return (Match) o;
        if (o instanceof NetMessage) {
          NetMessage nm = (NetMessage) o;
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
  private static void exchangeNick(OnlineGame og, boolean isServer, GameUI ui) {
    try {
      String myNick = ui.getLocalNick() != null ? ui.getLocalNick() : I18n.t("nick.you");
      NetMessage hello = new NetMessage(NetMessage.Type.NICK, myNick);
      if (isServer) og.sendServerObject(hello);
      else og.sendClientObject(hello);
      // espera o nick do oponente (até ~4s)
      long end = System.currentTimeMillis() + 4000;
      while (System.currentTimeMillis() < end) {
        Object o = isServer ? og.receiveServerObject() : og.receiveClientObject();
        if (o instanceof NetMessage) {
          NetMessage nm = (NetMessage) o;
          if (nm.getType() == NetMessage.Type.NICK && nm.getPayload() != null) {
            ui.setOpponentNick(nm.getPayload());
            break;
          }
          // ignora outros tipos aqui (revanche ainda não começou)
        } else if (o instanceof Match) {
          // não deveria chegar Match antes do jogo; ignora
        }
        Thread.sleep(50);
      }
    } catch (Exception ignored) {
    }
  }

  // ---------- revanche online ----------
  private static boolean onlineRematchHandshake(GameUI ui, OnlineGame og, boolean isServer) {
    try {
      // desenha tela final base
      drawEnd(ui, null);
      boolean iRequested = false;
      boolean peerRequested = false;
      // drena teclado
      while (ui.pollInput() != null) {
      }
      while (true) {
        // 1) teclado local
        KeyStroke k = ui.pollInput();
        if (k != null) {
          if (k.getKeyType() == KeyType.Escape) {
            sendCtrl(og, isServer, new NetMessage(NetMessage.Type.REMATCH_DECLINE, ""));
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
        if (p.kind() == NetPoll.Kind.OK) {
          og.touch();
          Object o = p.payload();
          if (!(o instanceof NetMessage)) {
            // Match atrasado, ignora
            Thread.sleep(50);
            continue;
          }
          NetMessage nm = (NetMessage) o;
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
    } catch (Exception e) {
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
      while (ui.pollInput() != null) {
      }
      for (int s = 5; s > 0; s--) {
        drawEnd(ui, base + " " + I18n.t("net.leaving", s));
        long end = System.currentTimeMillis() + 1000;
        boolean skip = false;
        while (System.currentTimeMillis() < end) {
          KeyStroke k = ui.pollInput();
          if (k != null && k.getKeyType() == KeyType.Escape) {
            skip = true;
            break;
          }
          Thread.sleep(50);
        }
        if (skip) break;
      }
    } catch (Exception ignored) {
    }
  }

  private static void drawEnd(GameUI ui, String extra) {
    try {
      com.Campeol.ui.EndScreen.draw(ui.getScreen(), ui.getTextGraphics(), ui.getStatus(), ui.winnerLabel(),
          ui.getSessionScore(), ui.getBoard().getClock().matchMillis(), extra);
    } catch (Exception ignored) {
    }
  }

  private static void sendCtrl(OnlineGame og, boolean isServer, NetMessage m) {
    try {
      if (isServer) og.sendServerObject(m);
      else og.sendClientObject(m);
    } catch (Exception ignored) {
    }
  }

}
