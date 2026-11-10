package hbv.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

/**
 * Ausleihe.
 *
 * GET  /borrow?action=checkMember&code=M10001   -> Mitglied pruefen
 * GET  /borrow?action=checkExemplar&code=100    -> Exemplar pruefen (existiert, frei?)
 * POST /borrow  memberCode=M10001&exemplarCodes=100,101 -> Ausleihe buchen
 */
public class BorrowServlet extends ApiServlet {
    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String action = request.getParameter("action");
        String code = request.getParameter("code");
        try {
            if ("checkMember".equals(action)) {
                if (code == null || code.isBlank()) {
                    throw new LibraryException("Mitgliedscode darf nicht leer sein.");
                }
                LibraryDao.Member member = dao.findMember(code.trim());
                if (member == null) {
                    sendError(response, HttpServletResponse.SC_NOT_FOUND, "Mitglied nicht gefunden.");
                    return;
                }
                sendOk(response, Json.obj("success", true, "code", member.code(), "name", member.name()));

            } else if ("checkExemplar".equals(action)) {
                LibraryDao.Exemplar ex = dao.findExemplar(parseCode(code));
                if (ex == null) {
                    sendError(response, HttpServletResponse.SC_NOT_FOUND, "Exemplar nicht gefunden.");
                } else if (ex.borrowed()) {
                    sendError(response, HttpServletResponse.SC_CONFLICT, "Dieses Exemplar ist bereits verliehen.");
                } else {
                    sendOk(response, Json.obj("success", true, "code", ex.code(),
                                              "title", ex.title(), "author", ex.author()));
                }

            } else {
                sendError(response, HttpServletResponse.SC_BAD_REQUEST, "Ungültige Aktion.");
            }
        } catch (LibraryException e) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        } catch (SQLException e) {
            sendServerError(response, "Fehler bei Ausleihe-Abfrage", e);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            String memberCode = request.getParameter("memberCode");
            if (memberCode == null || memberCode.isBlank()) {
                throw new LibraryException("Kein Mitglied angegeben.");
            }
            List<Integer> codes = parseCodeList(request.getParameter("exemplarCodes"));

            LibraryDao.BorrowResult result = dao.borrow(memberCode.trim(), codes);

            long timestamp = System.currentTimeMillis() / 1000;
            boolean printed = PrintQueue.enqueue(List.of(PrintQueue.borrowJob(result, timestamp)));

            sendOk(response, Json.obj(
                "success", true,
                "printed", printed,
                "dueDate", result.dueDate().toLocalDate().toString(),
                "message", printed
                    ? "Ausleihe gebucht, Beleg wird gedruckt."
                    : "Ausleihe gebucht, aber der Beleg konnte NICHT gedruckt werden."));

        } catch (LibraryException e) {
            sendError(response, HttpServletResponse.SC_CONFLICT, e.getMessage());
        } catch (SQLException e) {
            sendServerError(response, "Fehler bei Ausleihe-Buchung", e);
        }
    }
}
