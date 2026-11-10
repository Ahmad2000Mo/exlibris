package hbv.web;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Properties;
import java.util.UUID;
import redis.clients.jedis.RedisClient;
import redis.clients.jedis.args.ListDirection;

/**
 * Eigenstaendiger Prozess, der Druckauftraege aus Redis abholt und als Textdatei "druckt".
 *
 * Zuverlaessige Warteschlange ("reliable queue"):
 *  - BLMOVE verschiebt einen Auftrag atomar von print_queue nach print_processing.
 *  - Erst nach erfolgreichem Schreiben der Datei wird er aus print_processing geloescht.
 *  - Stuerzt der Daemon dazwischen ab, liegt der Auftrag noch in print_processing und wird
 *    beim naechsten Start wieder in die Warteschlange gelegt. Es geht also nichts verloren.
 *  - Auftraege, die sich gar nicht verarbeiten lassen (kaputtes JSON), landen in print_failed,
 *    statt den Daemon in einer Endlosschleife festzuhalten.
 */
public class PrintDaemon {

    private static volatile boolean running = true;

    public static void main(String[] args) {
        Properties props = new Properties();
        String configFile = args.length > 0 ? args[0] : "local/config.txt";
        try (InputStream is = new FileInputStream(configFile)) {
            props.load(is);
        } catch (IOException e) {
            System.err.println("Fehler beim Laden von " + configFile + ": " + e.getMessage());
            System.exit(1);
        }

        String server = props.getProperty("redisserver", "localhost");
        String password = props.getProperty("redispassword", "");
        int port = JedisAdapter.parsePort(props.getProperty("redisport"));
        File printDir = new File(props.getProperty("printdir", "printed_receipts"));

        if (!printDir.isDirectory() && !printDir.mkdirs()) {
            System.err.println("Kann Verzeichnis nicht anlegen: " + printDir.getAbsolutePath());
            System.exit(1);
        }

        JedisAdapter.init(server, port, password, 4);
        RedisClient redis = JedisAdapter.getClient();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            running = false;
            System.out.println("Print Daemon wird beendet...");
        }));

        recoverUnfinishedJobs(redis);

        System.out.println("Print Daemon gestartet. Redis: " + server + ":" + port
                + ", Belege nach: " + printDir.getAbsolutePath());

        while (running) {
            try {
                // Timeout 5 s, damit der Daemon regelmaessig 'running' prueft und sauber beendet
                String job = redis.blmove(PrintQueue.QUEUE, PrintQueue.PROCESSING,
                                          ListDirection.RIGHT, ListDirection.LEFT, 5.0);
                if (job == null) continue;

                process(redis, job, printDir);
            } catch (RuntimeException e) {
                if (!running) break;
                System.err.println("Redis-Fehler: " + e.getMessage() + " (neuer Versuch in 2 s)");
                sleep(2000);
            }
        }

        JedisAdapter.destroy();
    }

    /** Legt Auftraege, die beim letzten Lauf mitten in der Bearbeitung waren, zurueck in die Queue. */
    private static void recoverUnfinishedJobs(RedisClient redis) {
        int recovered = 0;
        while (redis.lmove(PrintQueue.PROCESSING, PrintQueue.QUEUE, ListDirection.RIGHT, ListDirection.RIGHT) != null) {
            recovered++;
        }
        if (recovered > 0) {
            System.out.println(recovered + " unterbrochene(r) Auftrag/Auftraege wieder eingereiht.");
        }
    }

    static void process(RedisClient redis, String job, File printDir) {
        try {
            PrintCommand command = PrintCommand.fromJson(job);
            Path file = writeReceipt(command, printDir);
            redis.lrem(PrintQueue.PROCESSING, 1, job);
            System.out.println("Beleg gedruckt: " + file.getFileName());
        } catch (IllegalArgumentException e) {
            // Auftrag ist kaputt -> wird nie funktionieren, also beiseitelegen
            System.err.println("Ungueltiger Druckauftrag, verschoben nach " + PrintQueue.FAILED + ": " + e.getMessage());
            redis.lpush(PrintQueue.FAILED, job);
            redis.lrem(PrintQueue.PROCESSING, 1, job);
        } catch (IOException e) {
            // z. B. Platte voll -> Auftrag bleibt in print_processing und wird beim Neustart wiederholt
            System.err.println("Beleg konnte nicht geschrieben werden: " + e.getMessage());
            sleep(2000);
        }
    }

    static Path writeReceipt(PrintCommand command, File printDir) throws IOException {
        // Dateiname eindeutig (UUID) und ohne Sonderzeichen aus Nutzerdaten (kein "../" moeglich)
        String safeMember = command.memberCode() == null ? "unbekannt"
                : command.memberCode().replaceAll("[^A-Za-z0-9_-]", "_");
        String name = String.format("quittung_%d_%s_%s_%s.txt", command.timestamp(), safeMember,
                command.type(), UUID.randomUUID().toString().substring(0, 8));
        Path target = printDir.toPath().resolve(name);
        Path tmp = printDir.toPath().resolve(name + ".tmp");

        // Erst in eine temporaere Datei schreiben, dann umbenennen: nie halbe Belege im Ordner
        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(
                Files.newOutputStream(tmp, StandardOpenOption.CREATE_NEW), StandardCharsets.UTF_8))) {
            command.execute(pw);
            if (pw.checkError()) throw new IOException("Schreibfehler in " + tmp);
        }
        Files.move(tmp, target);
        return target;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            running = false;
        }
    }
}
