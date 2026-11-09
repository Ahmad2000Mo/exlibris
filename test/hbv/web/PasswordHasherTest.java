package hbv.web;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PasswordHasherTest {

    // Wenige Iterationen, damit der Test schnell ist; das Verfahren ist dasselbe
    private static final int FAST = 1_000;

    @Test
    void correctPasswordIsAccepted() {
        String stored = PasswordHasher.hash("geheim123", FAST);
        assertTrue(PasswordHasher.verify("geheim123", stored));
    }

    @Test
    void wrongPasswordIsRejected() {
        String stored = PasswordHasher.hash("geheim123", FAST);
        assertFalse(PasswordHasher.verify("geheim124", stored));
        assertFalse(PasswordHasher.verify("", stored));
        assertFalse(PasswordHasher.verify(null, stored));
    }

    @Test
    void samePasswordGivesDifferentHashesBecauseOfSalt() {
        assertNotEquals(PasswordHasher.hash("abc", FAST), PasswordHasher.hash("abc", FAST));
    }

    @Test
    void storedFormatContainsAlgorithmAndIterations() {
        assertTrue(PasswordHasher.hash("abc", FAST).startsWith("pbkdf2$1000$"));
    }

    @Test
    void brokenHashIsRejectedWithoutException() {
        assertFalse(PasswordHasher.verify("abc", "kein-hash"));
        assertFalse(PasswordHasher.verify("abc", "pbkdf2$x$y$z"));
        assertFalse(PasswordHasher.verify("abc", null));
    }

    @Test
    void seedAdminHashInCreateDbMatchesDocumentedPassword() throws Exception {
        String sql = java.nio.file.Files.readString(java.nio.file.Path.of("createdb.sql"));
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("'(pbkdf2\\$[^']+)'").matcher(sql);
        assertTrue(m.find(), "Admin-Hash in createdb.sql nicht gefunden");
        assertTrue(PasswordHasher.verify("admin", m.group(1)));
    }
}
