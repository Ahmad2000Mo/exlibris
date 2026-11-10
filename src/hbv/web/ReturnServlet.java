package hbv.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Rueckgabe.
 *
 * GET  /return?action=checkExemplar&code=100  -> Ausleihstatus eines Exemplars
 * POST /return  exemplarCodes=100,101          -> Rueckgabe buchen
 */
public class ReturnServlet extends ApiServlet {
    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!"checkExemplar".equals(request.getParameter("action"))) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, "Ungültige Aktion.");
            return;
        }
        try {
            LibraryDao.LoanStatus status = dao.findLoanStatus(parseCode(request.getParameter("code")));
            if (status == null) {
                sendError(response, HttpServletResponse.SC_NOT_FOUND, "Exemplar nicht gefunden.");
            } else if (!status.isBorrowed()) {
                sendError(response, HttpServletResponse.SC_CONFLICT, "Dieses Exemplar ist zurzeit nicht ausgeliehen.");
            } else {
                sendOk(response, Json.obj(
                    "success", true,
                    "code", status.code(),
                    "title", status.title(),
                    "author", status.author(),
                    "memberCode", status.memberCode(),
                    "memberName", status.memberName(),
                    "dueDate", status.dueDate().toLocalDate().toString(),
                    "overdue", status.overdue(),
                    "timeDiffText", status.timeDiffText()));
            }
        } catch (LibraryException e) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        } catch (SQLException e) {
            sendServerError(response, "Fehler bei Rückgabe-Abfrage", e);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            List<Integer> codes = parseCodeList(request.getParameter("exemplarCodes"));
            List<LibraryDao.ReturnedBook> books = dao.giveBack(codes);

            // Pro Mitglied ein eigener Beleg, auch wenn mehrere Mitglieder auf einmal zurueckgeben
            Map<String, List<LibraryDao.ReturnedBook>> byMember = new LinkedHashMap<>();
            for (LibraryDao.ReturnedBook book : books) {
                byMember.computeIfAbsent(book.memberCode(), k -> new ArrayList<>()).add(book);
            }
            long timestamp = System.currentTimeMillis() / 1000;
            List<String> jobs = new ArrayList<>();
            for (List<LibraryDao.ReturnedBook> group : byMember.values()) {
                LibraryDao.ReturnedBook first = group.get(0);
                jobs.add(PrintQueue.returnJob(first.memberCode(), first.memberName(), group, timestamp));
            }
            boolean printed = PrintQueue.enqueue(jobs);

            sendOk(response, Json.obj(
                "success", true,
                "printed", printed,
                "receipts", jobs.size(),
                "message", printed
                    ? "Rückgabe gebucht, " + jobs.size() + " Beleg(e) werden gedruckt."
                    : "Rückgabe gebucht, aber der Beleg konnte NICHT gedruckt werden."));

        } catch (LibraryException e) {
            sendError(response, HttpServletResponse.SC_CONFLICT, e.getMessage());
        } catch (SQLException e) {
            sendServerError(response, "Fehler bei Rückgabe-Buchung", e);
        }
    }
}
