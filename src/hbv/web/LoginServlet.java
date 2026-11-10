package hbv.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.util.Base64;

/**
 * Login fuer Mitarbeitende.
 *
 * Nur POST: bei GET stuende das Passwort in der URL und damit im Server-Log und Browser-Verlauf.
 */
public class LoginServlet extends ApiServlet {
    private static final long serialVersionUID = 1L;

    /**
     * Wird geprueft, wenn der Benutzer nicht existiert. So dauert ein Login mit falschem
     * Benutzernamen genauso lange wie mit falschem Passwort und verraet nicht, welche
     * Benutzernamen es gibt.
     */
    private static final String DUMMY_HASH = PasswordHasher.hash("dummy-passwort-fuer-gleiche-laufzeit");

    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String user = request.getParameter("user");
        String passwd = request.getParameter("passwd");
        String base = request.getContextPath() + "/";

        if (user == null || user.isBlank() || passwd == null || passwd.isEmpty()) {
            response.sendRedirect(base + "login.html?error=1");
            return;
        }

        LibraryDao.Employee employee;
        try {
            employee = dao.findEmployee(user.trim());
        } catch (SQLException e) {
            log("Login: Datenbankfehler", e);
            response.sendRedirect(base + "login.html?error=server");
            return;
        }

        String storedHash = employee != null ? employee.passwordHash() : DUMMY_HASH;
        boolean valid = PasswordHasher.verify(passwd, storedHash) && employee != null;
        if (!valid) {
            log("Login fehlgeschlagen fuer Benutzer '" + user.trim() + "' von " + request.getRemoteAddr());
            response.sendRedirect(base + "login.html?error=1");
            return;
        }

        // Session Fixation verhindern: alte Session verwerfen, neue anlegen
        HttpSession old = request.getSession(false);
        if (old != null) {
            old.invalidate();
        }
        HttpSession session = request.getSession(true);
        session.setAttribute(AuthFilter.USER, employee.username());
        session.setAttribute(AuthFilter.DISPLAY_NAME, employee.name());
        session.setAttribute(AuthFilter.CSRF_TOKEN, newCsrfToken());

        response.sendRedirect(base + "dashboard.html");
    }

    private static String newCsrfToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
