package hbv.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PrintCommandTest {

    private static final long TIMESTAMP = 1_767_268_800L; // 01.01.2026 13:00 Uhr in Berlin

    private static String render(PrintCommand cmd) {
        StringWriter out = new StringWriter();
        cmd.execute(new PrintWriter(out));
        return out.toString();
    }

    @Test
    void borrowJobSurvivesQueueRoundTrip() {
        LibraryDao.BorrowResult result = new LibraryDao.BorrowResult(
            new LibraryDao.Member(1, "M10001", "Max \"Maxi\" Mustermann"),
            LocalDateTime.of(2026, 1, 15, 13, 0),
            List.of(new LibraryDao.BorrowedBook(100, "Titel mit ] und }", "Autorin, Zweite")));

        PrintCommand cmd = PrintCommand.fromJson(PrintQueue.borrowJob(result, TIMESTAMP));

        BorrowReceiptCommand borrow = assertInstanceOf(BorrowReceiptCommand.class, cmd);
        assertEquals("Max \"Maxi\" Mustermann", borrow.memberName());
        assertEquals(LocalDate.of(2026, 1, 15), borrow.dueDate());
        assertEquals("Titel mit ] und }", borrow.items().get(0).title());

        String text = render(cmd);
        assertTrue(text.contains("AUSLEIHE-QUITTUNG"));
        assertTrue(text.contains("01.01.2026 13:00:00"));
        assertTrue(text.contains("Rückgabe bis: 15.01.2026"));
        assertTrue(text.contains("[100] Titel mit ] und } (von Autorin, Zweite)"));
    }

    @Test
    void returnJobShowsOverdueStatus() {
        List<LibraryDao.ReturnedBook> books = List.of(
            new LibraryDao.ReturnedBook(100, "A", "X", "M1", "Max", true, 3),
            new LibraryDao.ReturnedBook(101, "B", "Y", "M1", "Max", false, 0));

        String text = render(PrintCommand.fromJson(PrintQueue.returnJob("M1", "Max", books, TIMESTAMP)));

        assertTrue(text.contains("RÜCKGABE-QUITTUNG"));
        assertTrue(text.contains("ÜBERFÄLLIG um 3 Tag(e)"));
        assertTrue(text.contains("Fristgerecht zurückgegeben"));
    }

    @Test
    void unknownCommandTypeIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> PrintCommand.fromJson("{\"commandType\":\"FAX\"}"));
        assertThrows(IllegalArgumentException.class, () -> PrintCommand.fromJson("kein json"));
    }

    @Test
    void receiptFileNameIsUniqueAndSafe(@TempDir Path dir) throws Exception {
        PrintCommand cmd = new ReturnReceiptCommand("../../etc/passwd", "Böse", TIMESTAMP, List.of());

        Path first = PrintDaemon.writeReceipt(cmd, dir.toFile());
        Path second = PrintDaemon.writeReceipt(cmd, dir.toFile());

        assertEquals(dir, first.getParent(), "Datei muss im Belegordner bleiben");
        assertFalse(first.getFileName().toString().contains(".."));
        assertFalse(first.equals(second), "Zwei Belege in derselben Sekunde duerfen sich nicht ueberschreiben");
        assertTrue(Files.readString(first).contains("RÜCKGABE-QUITTUNG"));
    }
}
