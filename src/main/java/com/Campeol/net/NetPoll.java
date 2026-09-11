package com.Campeol.net;

/**
 * Resultado de uma leitura não-bloqueante (com timeout) no socket.
 * Distingue "sem dados ainda" (TIMEOUT: peer vivo, segue esperando) de
 * "conexão morta" (DISCONNECTED: EOF/reset — peer saiu ou caiu).
 */
public final class NetPoll {
  public enum Kind {
    /** Chegou um objeto (payload). */
    OK,
    /** Nada chegou dentro do timeout (conexão segue aberta). */
    TIMEOUT,
    /** Stream fechado/resetado (peer saiu ou caiu). */
    DISCONNECTED
  }

  private final Kind kind;
  private final Object payload;

  private NetPoll(Kind kind, Object payload) {
    this.kind = kind;
    this.payload = payload;
  }

  public static NetPoll ok(Object payload) {
    return new NetPoll(Kind.OK, payload);
  }

  public static NetPoll timeout() {
    return new NetPoll(Kind.TIMEOUT, null);
  }

  public static NetPoll disconnected() {
    return new NetPoll(Kind.DISCONNECTED, null);
  }

  public Kind kind() {
    return kind;
  }

  public Object payload() {
    return payload;
  }
}
