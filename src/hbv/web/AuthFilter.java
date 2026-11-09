package hbv.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;

/**
 * Zentraler Filter fuer alle Anfragen:
 *  1. setzt Sicherheits-Header,
 *  2. laesst nur oeffentliche Seiten ohne Login durch,
 *  3. prueft bei schreibenden Anfragen (POST) das CSRF-Token.
 */
public class AuthFilter extends HttpFilter {
    private static final long serialVersionUID = 1L;

    /** Session-Attribute */
    public static final String USER = "user";
    public static final String DISPLAY_NAME = "displayName";
    public static final String CSRF_TOKEN = "csrfToken";

    public static final String CSRF_HEADER = "X-CSRF-Token";

    /** Seiten und Endpunkte, die ohne Login erreichbar sind. */
    private static final Set<String> PUBLIC_PATHS = Set.of(
        "/", "/index.html", "/index.css",
        "/login.html", "/login.css", "/login.js", "/login",
        "/session-status", "/favicon.ico");

    @Override
    protected void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        addSecurityHeaders(response);

        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (path.isEmpty()) path = "/";

        if (PUBLIC_PATHS.contains(path)) {
            chain.doFilter(request, response);
            return;
        }

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute(USER) == null) {
            if (path.endsWith(".html")) {
                // Seitenaufruf im Browser -> zum Login umleiten
                response.sendRedirect(request.getContextPath() + "/login.html");
            } else {
                // API-Aufruf (fetch) -> maschinenlesbarer Fehler
                ApiServlet.sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "Nicht angemeldet.");
            }
            return;
        }

        if (!isSafeMethod(request.getMethod()) && !csrfTokenValid(request, session)) {
            ApiServlet.sendError(response, HttpServletResponse.SC_FORBIDDEN,
                                 "Ungültiges oder fehlendes CSRF-Token. Bitte Seite neu laden.");
            return;
        }

        chain.doFilter(request, response);
    }

    private static boolean isSafeMethod(String method) {
        return "GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method);
    }

    private static boolean csrfTokenValid(HttpServletRequest request, HttpSession session) {
        Object expected = session.getAttribute(CSRF_TOKEN);
        String actual = request.getHeader(CSRF_HEADER);
        if (!(expected instanceof String) || actual == null) {
            return false;
        }
        return MessageDigest.isEqual(((String) expected).getBytes(StandardCharsets.UTF_8),
                                     actual.getBytes(StandardCharsets.UTF_8));
    }

    private static void addSecurityHeaders(HttpServletResponse response) {
        // Browser soll Content-Type nicht "erraten" (verhindert z. B. JSON als HTML)
        response.setHeader("X-Content-Type-Options", "nosniff");
        // Seite darf nicht in fremde iFrames eingebettet werden (Clickjacking)
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Referrer-Policy", "same-origin");
        // Nur Skripte von der eigenen Seite, keine Inline-Skripte -> eingeschleustes <script> laeuft nicht.
        // Inline-Styles (style="...") bleiben erlaubt, die sind fuer XSS kaum nutzbar.
        response.setHeader("Content-Security-Policy",
            "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; "
            + "font-src 'self' https://fonts.gstatic.com; frame-ancestors 'none'; form-action 'self'");
    }
}
