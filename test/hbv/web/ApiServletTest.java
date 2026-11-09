package hbv.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ApiServletTest {

    @Test
    void parsesCommaSeparatedCodes() throws LibraryException {
        assertEquals(List.of(100, 101, 7), ApiServlet.parseCodeList(" 100, 101 ,007"));
    }

    @Test
    void rejectsDuplicates() {
        LibraryException e = assertThrows(LibraryException.class, () -> ApiServlet.parseCodeList("100,100"));
        assertTrue(e.getMessage().contains("doppelt"));
    }

    @Test
    void rejectsInvalidCodes() {
        assertThrows(LibraryException.class, () -> ApiServlet.parseCodeList(""));
        assertThrows(LibraryException.class, () -> ApiServlet.parseCodeList("100,abc"));
        assertThrows(LibraryException.class, () -> ApiServlet.parseCodeList("100,,101"));
        assertThrows(LibraryException.class, () -> ApiServlet.parseCode("-5"));
        assertThrows(LibraryException.class, () -> ApiServlet.parseCode("99999999999"));
    }

    @Test
    void limitsNumberOfItems() {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= ApiServlet.MAX_ITEMS_PER_REQUEST + 1; i++) {
            sb.append(i).append(',');
        }
        assertThrows(LibraryException.class, () -> ApiServlet.parseCodeList(sb.toString()));
    }

    @Test
    void loanStatusText() {
        assertEquals("3 Tag(e) überfällig",
            new LibraryDao.LoanStatus(1, "t", "a", "M1", "n", null, true, 3).timeDiffText());
        assertEquals("Heute fällig",
            new LibraryDao.LoanStatus(1, "t", "a", "M1", "n", null, false, 0).timeDiffText());
        assertEquals("Nicht ausgeliehen",
            new LibraryDao.LoanStatus(1, "t", "a", null, null, null, false, 0).timeDiffText());
    }
}
