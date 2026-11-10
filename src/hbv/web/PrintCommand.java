package hbv.web;

import java.io.PrintWriter;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Ein Druckauftrag (Command-Pattern): jede Belegart weiss selbst, wie sie sich druckt.
 */
public interface PrintCommand {

    ZoneId ZONE = ZoneId.of("Europe/Berlin");
    DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");
    DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    String LINE = "==========================================";
    String THIN_LINE = "------------------------------------------";

    /** Schreibt den Beleg. */
    void execute(PrintWriter writer);

    /** Kurzname fuer den Dateinamen, z. B. "borrow". */
    String type();

    String memberCode();

    long timestamp();

    static String formatTimestamp(long epochSeconds) {
        return DATE_TIME.format(Instant.ofEpochSecond(epochSeconds).atZone(ZONE));
    }

    /**
     * Baut aus dem JSON der Warteschlange den passenden Beleg.
     *
     * @throws IllegalArgumentException bei kaputtem JSON oder unbekanntem Typ
     */
    static PrintCommand fromJson(String json) {
        Map<String, Object> job = Json.parseObject(json);
        String type = Json.getString(job, "commandType");
        String memberCode = Json.getString(job, "memberCode");
        String memberName = Json.getString(job, "memberName");
        long timestamp = Json.getLong(job, "timestamp");
        List<Map<String, Object>> rawItems = Json.getObjectList(job, "items");

        if ("BORROW".equals(type)) {
            List<BorrowReceiptCommand.Item> items = new ArrayList<>();
            for (Map<String, Object> it : rawItems) {
                items.add(new BorrowReceiptCommand.Item(Json.getString(it, "code"),
                        Json.getString(it, "title"), Json.getString(it, "author")));
            }
            String due = Json.getString(job, "dueDate");
            return new BorrowReceiptCommand(memberCode, memberName, timestamp,
                    due == null ? null : LocalDate.parse(due), items);
        }
        if ("RETURN".equals(type)) {
            List<ReturnReceiptCommand.Item> items = new ArrayList<>();
            for (Map<String, Object> it : rawItems) {
                items.add(new ReturnReceiptCommand.Item(Json.getString(it, "code"),
                        Json.getString(it, "title"), Json.getString(it, "author"),
                        Json.getBoolean(it, "overdue"), Json.getLong(it, "overdueDays")));
            }
            return new ReturnReceiptCommand(memberCode, memberName, timestamp, items);
        }
        throw new IllegalArgumentException("Unbekannter commandType: " + type);
    }
}
