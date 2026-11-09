package hbv.web;

import java.io.PrintWriter;
import java.util.List;

/** Beleg fuer eine Rueckgabe (immer fuer genau ein Mitglied). */
public record ReturnReceiptCommand(String memberCode, String memberName, long timestamp, List<Item> items)
        implements PrintCommand {

    public record Item(String code, String title, String author, boolean overdue, long overdueDays) {}

    @Override
    public String type() {
        return "return";
    }

    @Override
    public void execute(PrintWriter writer) {
        writer.println(LINE);
        writer.println("           RÜCKGABE-QUITTUNG");
        writer.println(LINE);
        writer.println("Bibliothek Brot & Butter");
        writer.println("Datum:      " + PrintCommand.formatTimestamp(timestamp));
        writer.println("Mitglied:   " + memberName + " (" + memberCode + ")");
        writer.println(THIN_LINE);
        writer.println("Zurückgegebene Exemplare:");
        for (Item item : items) {
            writer.println("- [" + item.code() + "] " + item.title() + " (von " + item.author() + ")");
            if (item.overdue()) {
                writer.println(item.overdueDays() == 0
                        ? "  Status: ÜBERFÄLLIG (seit heute)"
                        : "  Status: ÜBERFÄLLIG um " + item.overdueDays() + " Tag(e)");
            } else {
                writer.println("  Status: Fristgerecht zurückgegeben");
            }
        }
        writer.println(THIN_LINE);
        writer.println("Rückgabe erfolgreich registriert.");
        writer.println(LINE);
        writer.flush();
    }
}
