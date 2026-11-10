package hbv.web;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import redis.clients.jedis.RedisClient;

/**
 * Schnittstelle zwischen Webanwendung und PrintDaemon.
 *
 * Die Webanwendung legt Druckauftraege als JSON in die Redis-Liste {@link #QUEUE}.
 * Der PrintDaemon holt sie dort ab (siehe {@link PrintDaemon}).
 */
public final class PrintQueue {

    /** Neue Auftraege. */
    public static final String QUEUE = "print_queue";
    /** Auftraege, die der Daemon gerade bearbeitet (fuer Wiederaufnahme nach Absturz). */
    public static final String PROCESSING = "print_processing";
    /** Auftraege, die nicht verarbeitet werden konnten (zur manuellen Analyse). */
    public static final String FAILED = "print_failed";

    private PrintQueue() {}

    public static String borrowJob(LibraryDao.BorrowResult result, long timestamp) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (LibraryDao.BorrowedBook book : result.books()) {
            items.add(Json.obj("code", book.code(), "title", book.title(), "author", book.author()));
        }
        return Json.stringify(Json.obj(
            "commandType", "BORROW",
            "memberCode", result.member().code(),
            "memberName", result.member().name(),
            "timestamp", timestamp,
            "dueDate", result.dueDate().toLocalDate().toString(),
            "items", items));
    }

    /** Ein Rueckgabe-Beleg gehoert genau EINEM Mitglied; die Liste muss vorher gruppiert sein. */
    public static String returnJob(String memberCode, String memberName, List<LibraryDao.ReturnedBook> books,
                                   long timestamp) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (LibraryDao.ReturnedBook book : books) {
            items.add(Json.obj("code", book.code(), "title", book.title(), "author", book.author(),
                               "overdue", book.overdue(), "overdueDays", book.overdueDays()));
        }
        return Json.stringify(Json.obj(
            "commandType", "RETURN",
            "memberCode", memberCode,
            "memberName", memberName,
            "timestamp", timestamp,
            "items", items));
    }

    /**
     * Legt Auftraege in die Warteschlange.
     *
     * @return true, wenn alle Auftraege angenommen wurden. false heisst: die Buchung ist
     *         gespeichert, aber der Beleg wird NICHT gedruckt; das muss der Nutzer erfahren.
     */
    public static boolean enqueue(List<String> jobs) {
        if (jobs.isEmpty()) return true;
        // Zwei Versuche: nach einem Redis-Neustart liegen im Pool evtl. noch tote Verbindungen,
        // die beim ersten Versuch verworfen werden.
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                RedisClient redis = JedisAdapter.getClient();
                redis.lpush(QUEUE, jobs.toArray(new String[0]));
                return true;
            } catch (RuntimeException e) {
                System.err.println("Druckauftrag konnte nicht in Redis abgelegt werden (Versuch "
                        + attempt + "): " + e);
            }
        }
        return false;
    }
}
