package hbv.web;

/**
 * Fachlicher Fehler (z. B. "Exemplar bereits verliehen").
 *
 * Die Meldung ist fuer Mitarbeitende gedacht und darf im Browser angezeigt werden.
 * Technische Fehler (SQLException usw.) werden dagegen nur ins Server-Log geschrieben.
 */
public class LibraryException extends Exception {
    private static final long serialVersionUID = 1L;

    public LibraryException(String message) {
        super(message);
    }
}
