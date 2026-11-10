package hbv.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;

/**
 * Logout. Nur per POST (mit CSRF-Token, siehe {@link AuthFilter}), damit eine fremde Seite
 * niemanden per eingebettetem Link abmelden kann.
 */
public class LogoutServlet extends ApiServlet {
    private static final long serialVersionUID = 1L;

    @Override
    public void init() {
        // braucht keine Datenbank
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        sendOk(response, Json.obj("success", true, "message", "Abgemeldet."));
    }
}
