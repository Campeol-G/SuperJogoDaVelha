package com.Campeol.game;

import java.util.Random;

import com.Campeol.client.Client;
import com.Campeol.net.NetPoll;
import com.Campeol.server.Server;
import com.Campeol.subgame.Match;
import com.Campeol.ui.NetMessage;

/**
 * OnlineGame
 */
public class OnlineGame {

  private Server server;
  private Client client;
  private final static int portNumber = 8080;

  /** Heartbeat: PING a cada tantos ms parado; morte após tanto ms sem nada. */
  public static final long HEARTBEAT_INTERVAL_MILLIS = 3000;
  public static final long PEER_DEAD_AFTER_MILLIS = 25000;

  /** Último objeto recebido (qualquer tipo: Match, NICK, PING...). */
  private volatile long lastSeenMillis = System.currentTimeMillis();
  private volatile boolean heartbeatOn = false;
  private Thread heartbeatThread = null;

  /** Conexão assíncrona (espera cancelável estilo Clash Royale). */
  private volatile Thread connectThread = null;
  private volatile boolean connectDone = false;
  private volatile Boolean connectResult = null;
  private volatile Throwable connectError = null;
  private volatile boolean cancelRequested = false;

  public Boolean createGame(String password) {
    server = new Server();
    return server.start(portNumber, password);
  }

  public char getServerPiece() {
    return server.getServerPiece();
  }

  public void getSendServer(Match match) {
    server.send(match);
  }

  public Match getReceiveServer() {
    return server.receive();
  }

  public void sendServerObject(Object o) {
    server.sendObject(o);
  }

  public Object receiveServerObject() {
    return server.receiveObject();
  }

  public NetPoll pollServer() {
    return server.pollObject();
  }

  public Boolean getInTheGame(String password) {
    client = new Client();
    return client.start(portNumber, password);
  }

  public char getClientPiece() {
    return client.getClientPiece();
  }

  public void getSendClient(Match match) {
    client.send(match);
  }

  public Match getReceiveClient() {
    return client.receive();
  }

  public void sendClientObject(Object o) {
    client.sendObject(o);
  }

  public Object receiveClientObject() {
    return client.receiveObject();
  }

  public NetPoll pollClient() {
    return client.pollObject();
  }

  /** Marca prova de vida (qualquer objeto recebido vale como heartbeat). */
  public void touch() {
    lastSeenMillis = System.currentTimeMillis();
  }

  public long getLastSeenMillis() {
    return lastSeenMillis;
  }

  /** O peer está em silêncio há mais que o limite? (queda, não "pensando") */
  public boolean isPeerDead() {
    return System.currentTimeMillis() - lastSeenMillis > PEER_DEAD_AFTER_MILLIS;
  }

  /**
   * Inicia o heartbeat: thread daemon que envia PING quando nada foi
   * escrito há HEARTBEAT_INTERVAL_MILLIS. Necessário porque, enquanto o
   * jogador local pensa (readInput), a thread do jogo não lê nem escreve
   * no socket — sem isso o peer declararia morte por engano.
   */
  public synchronized void startHeartbeat() {
    stopHeartbeat();
    touch();
    heartbeatOn = true;
    heartbeatThread = new Thread(this::heartbeatLoop, "heartbeat");
    heartbeatThread.setDaemon(true);
    heartbeatThread.start();
  }

  public synchronized void stopHeartbeat() {
    heartbeatOn = false;
    if (heartbeatThread != null) {
      heartbeatThread.interrupt();
      heartbeatThread = null;
    }
  }

  private void heartbeatLoop() {
    if (server == null && client == null) return;
    NetMessage ping = new NetMessage(NetMessage.Type.PING, "");
    while (heartbeatOn) {
      try {
        Thread.sleep(1000);
      } catch (InterruptedException e) {
        return;
      }
      if (!heartbeatOn) return;
      try {
        long idle = System.currentTimeMillis()
            - (server != null ? server.lastWriteMillis() : client.lastWriteMillis());
        if (idle >= HEARTBEAT_INTERVAL_MILLIS) {
          if (server != null) server.sendObject(ping);
          else if (client != null) client.sendObject(ping);
        }
      } catch (Exception ignored) {
      }
    }
  }

  /**
   * Conecta em worker-thread daemon para a UI poder mostrar a espera e
   * aceitar Esc (cancelar partida). O Boolean tem a mesma semântica dos
   * métodos síncronos (sorteio); use connectError() para falhas.
   */
  public synchronized void connectAsync(boolean isServer, String password) {
    cancelConnect();
    connectDone = false;
    connectResult = null;
    connectError = null;
    cancelRequested = false;
    if (isServer) {
      server = new Server();
    } else {
      client = new Client();
    }
    connectThread = new Thread(() -> {
      try {
        Boolean r = isServer
            ? server.start(portNumber, password)
            : client.start(portNumber, password);
        connectResult = r;
      } catch (Throwable e) {
        connectError = e;
        connectResult = Boolean.FALSE;
      } finally {
        connectDone = true;
      }
    }, "connect");
    connectThread.setDaemon(true);
    connectThread.start();
  }

  public synchronized boolean isConnectDone() {
    return connectDone;
  }

  public synchronized Boolean connectResult() {
    return connectResult;
  }

  public synchronized Throwable connectError() {
    return connectError;
  }

  public synchronized boolean wasCancelRequested() {
    return cancelRequested;
  }

  /**
   * Cancela uma conexão pendente: fecha os sockets para soltar os
   * bloqueios (accept/receive) e interrompe a worker. Idempotente.
   */
  public synchronized void cancelConnect() {
    cancelRequested = true;
    if (server != null) {
      try {
        server.cancel();
      } catch (Exception ignored) {
      }
    }
    if (client != null) {
      try {
        client.cancelDiscovery();
      } catch (Exception ignored) {
      }
    }
    if (connectThread != null) {
      try {
        connectThread.interrupt();
      } catch (Exception ignored) {
      }
      connectThread = null;
    }
  }

  public void close() {
    stopHeartbeat();
    cancelConnect();
    if (server != null) {
      server.close();
    }
    if (client != null) {
      client.close();
    }
  }

}
