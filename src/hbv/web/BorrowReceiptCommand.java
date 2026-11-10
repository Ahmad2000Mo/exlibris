package hbv.web;

import java.io.PrintWriter;
import java.time.LocalDate;
import java.util.List;

/** Beleg fuer eine Ausleihe. */
public record BorrowReceiptCommand(String memberCode, String memberName, long timestamp, LocalDate dueDate,
                                   List<Item> items) implements PrintCommand {

    public record Item(String code, String title, String author) {}

    @Override
    public String type() {
        return "borrow";
    }

    @Override
    public void execute(PrintWriter writer) {
        writer.println(LINE);
        writer.println("           AUSLEIHE-QUITTUNG");
        writer.println(LINE);
        writer.println("Bibliothek Brot & Butter");
        writer.println("Datum:      " + PrintCommand.formatTimestamp(timestamp));
        writer.println("Mitglied:   " + memberName + " (" + memberCode + ")");
        writer.println(THIN_LINE);
        writer.println("Ausgeliehene Exemplare:");
        for (Item item : items) {
            writer.println("- [" + item.code() + "] " + item.title() + " (von " + item.author() + ")");
        }
        writer.println(THIN_LINE);
        if (dueDate != null) {
            writer.println("Rückgabe bis: " + PrintCommand.DATE.format(dueDate));
        }
        writer.println("Vielen Dank für Ihren Besuch!");
        writer.println(LINE);
        writer.flush();
    }
}
