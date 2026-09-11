package com.Campeol.net;

import java.io.InputStream;
import java.security.KeyStore;
import java.security.SecureRandom;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

public final class SslUtil {

  private SslUtil() {
  }

  public static final String KEYSTORE_PASSWORD = "ocihc1044";

  private static final String KEYSTORE_TYPE = "PKCS12";

  private static final String SERVER_KEYSTORE = "/server-keystore.p12";
  private static final String CLIENT_TRUSTSTORE = "/client-truststore.p12";

  /**
   * Monta o erro com a causa raiz na mensagem (o rodapé do jogo mostra só
   * getMessage(); sem isso a causa real — ex. keystore ausente no jar —
   * ficava invisível).
   */
  private static RuntimeException fail(String what, Exception e) {
    Throwable root = e;
    while (root.getCause() != null) {
      root = root.getCause();
    }
    String detail = root.getMessage() != null ? root.getMessage() : root.toString();
    return new RuntimeException(what + ": " + detail, e);
  }

  private static KeyStore loadKeyStore(String resource) {
    try {
      KeyStore keyStore = KeyStore.getInstance(KEYSTORE_TYPE);
      try (InputStream is = SslUtil.class.getResourceAsStream(resource)) {
        if (is == null) {
          throw new IllegalStateException("Recurso não encontrado: " + resource);
        }
        keyStore.load(is, KEYSTORE_PASSWORD.toCharArray());
      }
      return keyStore;
    } catch (Exception e) {
      throw fail("Falha ao carregar keystore " + resource, e);
    }
  }

  public static SSLServerSocketFactory getServerSocketFactory() {
    try {
      KeyStore keyStore = loadKeyStore(SERVER_KEYSTORE);
      KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
      kmf.init(keyStore, KEYSTORE_PASSWORD.toCharArray());

      SSLContext context = SSLContext.getInstance("TLS");
      context.init(kmf.getKeyManagers(), null, new SecureRandom());
      return context.getServerSocketFactory();
    } catch (Exception e) {
      throw fail("Falha ao criar SSLServerSocketFactory", e);
    }
  }

  public static SSLSocketFactory getSocketFactory() {
    try {
      KeyStore trustStore = loadKeyStore(CLIENT_TRUSTSTORE);
      TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
      tmf.init(trustStore);

      SSLContext context = SSLContext.getInstance("TLS");
      context.init(null, tmf.getTrustManagers(), new SecureRandom());
      return context.getSocketFactory();
    } catch (Exception e) {
      throw fail("Falha ao criar SSLSocketFactory", e);
    }
  }
}
