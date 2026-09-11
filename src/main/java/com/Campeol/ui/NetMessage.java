package com.Campeol.ui;

import java.io.Serializable;

/**
 * Mensagem de controle para o online (revanche + nick).
 * Trafega no mesmo ObjectStream que o Match.
 */
public class NetMessage implements Serializable {
  public enum Type {
    REMATCH_REQUEST,
    REMATCH_ACCEPT,
    REMATCH_DECLINE,
    NICK,
    /** Saída voluntária no meio do jogo (avisa o peer antes de fechar). */
    QUIT,
    /** Heartbeat: "tô vivo" (prova de vida; não exige resposta). */
    PING
  }

  private final Type type;
  private final String payload;

  public NetMessage(Type type, String payload) {
    this.type = type;
    this.payload = payload;
  }

  public Type getType() {
    return type;
  }

  public String getPayload() {
    return payload;
  }
}
