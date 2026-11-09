package hbv.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;

/**
 * Liefert, ob jemand angemeldet ist, und das CSRF-Token fuer schreibende Anfragen.
 */
public class SessionStatusServlet extends ApiServlet {
    private static final long serialVersionUID = 1L;

    @Override
    public void init() {
        // braucht keine Datenbank
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute(AuthFilter.USER) != null) {
            sendOk(response, Json.obj(
                "loggedIn", true,
                "user", session.getAttribute(AuthFilter.USER),
                "name", session.getAttribute(AuthFilter.DISPLAY_NAME),
                "csrfToken", session.getAttribute(AuthFilter.CSRF_TOKEN)));
        } else {
            sendOk(response, Json.obj("loggedIn", false));
        }
    }
}
