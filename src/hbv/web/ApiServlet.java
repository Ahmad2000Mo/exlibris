package hbv.web;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

/**
 * Gemeinsame Basis aller JSON-Servlets: DAO-Initialisierung, JSON-Antworten und Eingabepruefung.
 *
 * Die Login-Pruefung passiert zentral im {@link AuthFilter}, deshalb nicht noch einmal hier.
 */
public abstract class ApiServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    /** Obergrenze, damit niemand mit einer riesigen Liste die Datenbank blockiert. */
    static final int MAX_ITEMS_PER_REQUEST = 50;

    protected transient LibraryDao dao;

    @Override
    public void init() throws ServletException {
        try {
            DataSource ds = (DataSource) new InitialContext().lookup("java:/comp/env/jdbc/mariadb");
            this.dao = new LibraryDao(ds);
        } catch (NamingException e) {
            throw new ServletException("JNDI-Lookup der Datenquelle fehlgeschlagen", e);
        }
    }

    protected static void sendJson(HttpServletResponse response, int status, Map<String, Object> body)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().print(Json.stringify(body));
    }

    protected static void sendOk(HttpServletResponse response, Map<String, Object> body) throws IOException {
        sendJson(response, HttpServletResponse.SC_OK, body);
    }

    /** Fachlicher Fehler: Meldung darf angezeigt werden. */
    protected static void sendError(HttpServletResponse response, int status, String message) throws IOException {
        sendJson(response, status, Json.obj("success", false, "message", message));
    }

    /** Technischer Fehler: Details nur ins Log, der Browser bekommt eine allgemeine Meldung. */
    protected void sendServerError(HttpServletResponse response, String context, Exception e) throws IOException {
        log(context, e);
        sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                  "Interner Fehler. Bitte erneut versuchen oder den Admin informieren.");
    }

    /** Wandelt einen Barcode-Parameter in eine Zahl um. */
    protected static int parseCode(String raw) throws LibraryException {
        if (raw == null || raw.isBlank()) {
            throw new LibraryException("Code darf nicht leer sein.");
        }
        try {
            int code = Integer.parseInt(raw.trim());
            if (code <= 0) throw new NumberFormatException();
            return code;
        } catch (NumberFormatException e) {
            throw new LibraryException("Ungültiges Barcode-Format: " + raw.trim());
        }
    }

    /** Wandelt "100,101,102" in eine Liste eindeutiger Barcodes um. */
    protected static List<Integer> parseCodeList(String raw) throws LibraryException {
        if (raw == null || raw.isBlank()) {
            throw new LibraryException("Es wurden keine Exemplare übergeben.");
        }
        Set<Integer> codes = new LinkedHashSet<>();
        for (String part : raw.split(",")) {
            int code = parseCode(part);
            if (!codes.add(code)) {
                throw new LibraryException("Exemplar " + code + " ist doppelt in der Liste.");
            }
        }
        if (codes.size() > MAX_ITEMS_PER_REQUEST) {
            throw new LibraryException("Maximal " + MAX_ITEMS_PER_REQUEST + " Exemplare pro Vorgang.");
        }
        return new ArrayList<>(codes);
    }
}
