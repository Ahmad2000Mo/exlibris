package hbv.web;

import java.time.Duration;
import redis.clients.jedis.*;

public class JedisAdapter {

  // volatile stellt sicher, dass Schreiboperationen sofort für alle Threads sichtbar sind
  private static volatile RedisClient redisClient = null;

  // Privater Konstruktor verhindert Instanziierung von außen
  private JedisAdapter() {}

  public static void init(String host, int port, String password, int maxConnections) {
    // Double-Checked Locking für maximale Performance ohne dauerhaftes Blockieren
    if (redisClient == null) {
      synchronized (JedisAdapter.class) {
        if (redisClient == null) {

          // Konfiguration des Verbindungspools
          ConnectionPoolConfig poolConfig = new ConnectionPoolConfig();
          poolConfig.setMaxTotal(maxConnections);
          poolConfig.setMaxIdle(maxConnections / 2);

          // Verhindert Fehler, falls maxConnections kleiner als 10 ist
          poolConfig.setMinIdle(Math.min(10, maxConnections));

          // Blockierungs-Schutz bei hoher Concurrency
          poolConfig.setBlockWhenExhausted(true);
          poolConfig.setMaxWait(Duration.ofMillis(3000));

          DefaultJedisClientConfig.Builder clientConfig = DefaultJedisClientConfig.builder()
            .timeoutMillis(2000);
          // Redis ohne Passwort (z. B. lokal) -> kein AUTH senden
          if (password != null && !password.isEmpty()) {
            clientConfig.password(password);
          }

          redisClient = RedisClient.builder()
            .poolConfig(poolConfig)
            .hostAndPort(host, port)
            .clientConfig(clientConfig.build())
            .build();
        }
      }
    }
  }

  /** Port aus der Konfiguration; leer oder nicht ersetzter Platzhalter -> Standardport 6379. */
  public static int parsePort(String value) {
    if (value == null || value.isBlank() || value.startsWith("REDIS")) {
      return 6379;
    }
    return Integer.parseInt(value.trim());
  }

  public static RedisClient getClient() {
    RedisClient client = redisClient;
    if (client == null) {
      throw new IllegalStateException("JedisAdapter wurde noch nicht initialisiert!");
    }
    return client;
  }

  public static void destroy() {
    synchronized (JedisAdapter.class) {
      if (redisClient != null) {
        redisClient.close();
        redisClient = null;
      }
    }
  }
}
