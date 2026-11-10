package hbv.web;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;

/**
 * Datenbankzugriff (DAO) fuer Mitarbeitende, Mitglieder, Exemplare und Ausleihen.
 *
 * Alle Abfragen nutzen PreparedStatements (Schutz vor SQL-Injection).
 * Ausleihe und Rueckgabe laufen jeweils in EINER Transaktion: entweder werden alle
 * Exemplare gebucht oder keines.
 */
public class LibraryDao {

    /** Leihfrist in Tagen. */
    public static final int LOAN_DAYS = 14;

    /** Haengt die Autor/innen eines Buchs (durch ", " getrennt) als Spalte "autoren" an. */
    private static final String AUTHORS_JOIN =
        "left join (" +
        "  select ab.buch_id, group_concat(a.name order by a.name separator ', ') as autoren " +
        "  from BIB_Autorin_Buch ab " +
        "  join BIB_Autorin a on a.id = ab.autorin_id " +
        "  group by ab.buch_id" +
        ") a on a.buch_id = b.id ";

    // ------------------------------------------------------------------
    // Datentransferobjekte
    // ------------------------------------------------------------------

    public record Employee(int id, String username, String name, String passwordHash) {}

    public record Member(int id, String code, String name) {}

    public record Exemplar(int id, int code, String title, String author, boolean borrowed) {}

    public record BorrowedBook(int code, String title, String author) {}

    public record BorrowResult(Member member, LocalDateTime dueDate, List<BorrowedBook> books) {}

    /** Status eines Exemplars vor der Rueckgabe. memberCode == null heisst: nicht verliehen. */
    public record LoanStatus(int code, String title, String author, String memberCode, String memberName,
                             LocalDateTime dueDate, boolean overdue, long days) {

        public boolean isBorrowed() {
            return memberCode != null;
        }

        /** Lesbarer Text wie "3 Tag(e) ueberfaellig" oder "Heute faellig". */
        public String timeDiffText() {
            if (!isBorrowed()) return "Nicht ausgeliehen";
            if (overdue) return days == 0 ? "Heute überfällig" : days + " Tag(e) überfällig";
            return days == 0 ? "Heute fällig" : days + " Tag(e) verbleibend";
        }
    }

    public record ReturnedBook(int code, String title, String author, String memberCode, String memberName,
                               boolean overdue, long overdueDays) {}

    // ------------------------------------------------------------------

    private final DataSource ds;

    public LibraryDao(DataSource ds) {
        this.ds = ds;
    }

    // ------------------------------------------------------------------
    // Mitarbeitende (Login)
    // ------------------------------------------------------------------

    public Employee findEmployee(String username) throws SQLException {
        String sql = "select id, benutzername, name, passwort_hash from BIB_Mitarbeiter where benutzername = ?";
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new Employee(rs.getInt("id"), rs.getString("benutzername"),
                                        rs.getString("name"), rs.getString("passwort_hash"));
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Ausleihe
    // ------------------------------------------------------------------

    public Member findMember(String code) throws SQLException {
        try (Connection conn = ds.getConnection()) {
            return findMember(conn, code);
        }
    }

    private Member findMember(Connection conn, String code) throws SQLException {
        String sql = "select id, code, name from BIB_Mitglied where code = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new Member(rs.getInt("id"), rs.getString("code"), rs.getString("name"));
                }
            }
        }
        return null;
    }

    public Exemplar findExemplar(int code) throws SQLException {
        try (Connection conn = ds.getConnection()) {
            return findExemplar(conn, code, false);
        }
    }

    /**
     * @param forUpdate true = Zeile des Exemplars sperren (SELECT ... FOR UPDATE), damit
     *                  zwei gleichzeitige Ausleihen desselben Exemplars nacheinander laufen.
     */
    private Exemplar findExemplar(Connection conn, int code, boolean forUpdate) throws SQLException {
        String sql =
            "select be.id, be.code, b.titel, a.autoren, " +
            "  exists(select 1 from BIB_Ausleihe au " +
            "         where au.buchexemplar_id = be.id and au.tatsaechliche_rueckgabe is null) as ausgeliehen " +
            "from BIB_Buchexemplar be " +
            "join BIB_Buch b on b.id = be.buch_id " +
            AUTHORS_JOIN +
            "where be.code = ?" + (forUpdate ? " for update" : "");
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new Exemplar(rs.getInt("id"), rs.getInt("code"), rs.getString("titel"),
                                        authorOrUnknown(rs.getString("autoren")), rs.getBoolean("ausgeliehen"));
                }
            }
        }
        return null;
    }

    public BorrowResult borrow(String memberCode, List<Integer> exemplarCodes) throws SQLException, LibraryException {
        try (Connection conn = ds.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Member member = findMember(conn, memberCode);
                if (member == null) {
                    throw new LibraryException("Mitglied " + memberCode + " nicht gefunden.");
                }

                String insertSql =
                    "insert into BIB_Ausleihe (mitglied_id, buchexemplar_id, ausleihdatum, rueckgabe_frist) " +
                    "values (?, ?, ?, ?)";
                // Zeit aus der Datenbank holen: dieselbe Uhr wie bei der Ueberfaellig-Pruefung
                LocalDateTime now;
                LocalDateTime due;
                try (PreparedStatement ps = conn.prepareStatement(
                        "select now() as jetzt, date_add(now(), interval ? day) as frist")) {
                    ps.setInt(1, LOAN_DAYS);
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        now = rs.getObject("jetzt", LocalDateTime.class);
                        due = rs.getObject("frist", LocalDateTime.class);
                    }
                }

                List<BorrowedBook> books = new ArrayList<>();
                try (PreparedStatement insert = conn.prepareStatement(insertSql)) {
                    for (int code : exemplarCodes) {
                        Exemplar ex = findExemplar(conn, code, true);
                        if (ex == null) {
                            throw new LibraryException("Exemplar " + code + " nicht gefunden.");
                        }
                        if (ex.borrowed()) {
                            throw new LibraryException("Exemplar " + code + " ist bereits verliehen.");
                        }
                        insert.setInt(1, member.id());
                        insert.setInt(2, ex.id());
                        insert.setObject(3, now);
                        insert.setObject(4, due);
                        insert.executeUpdate();
                        books.add(new BorrowedBook(ex.code(), ex.title(), ex.author()));
                    }
                }

                conn.commit();
                return new BorrowResult(member, due, books);
            } catch (SQLException | LibraryException | RuntimeException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    // ------------------------------------------------------------------
    // Rueckgabe
    // ------------------------------------------------------------------

    private static final String LOAN_STATUS_SQL =
        "select be.code, b.titel, a.autoren, au.id as loan_id, au.rueckgabe_frist, " +
        "  m.code as member_code, m.name as member_name, " +
        "  au.rueckgabe_frist < now() as ueberfaellig, " +
        // Kalendertage, von der Datenbank berechnet (vermeidet Zeitzonen-Probleme in Java)
        "  abs(datediff(au.rueckgabe_frist, now())) as tage " +
        "from BIB_Buchexemplar be " +
        "join BIB_Buch b on b.id = be.buch_id " +
        AUTHORS_JOIN +
        "left join BIB_Ausleihe au on au.buchexemplar_id = be.id and au.tatsaechliche_rueckgabe is null " +
        "left join BIB_Mitglied m on m.id = au.mitglied_id " +
        "where be.code = ?";

    public LoanStatus findLoanStatus(int code) throws SQLException {
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(LOAN_STATUS_SQL)) {
            ps.setInt(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? toLoanStatus(rs) : null;
            }
        }
    }

    private static LoanStatus toLoanStatus(ResultSet rs) throws SQLException {
        String title = rs.getString("titel");
        String author = authorOrUnknown(rs.getString("autoren"));
        int code = rs.getInt("code");
        String memberCode = rs.getString("member_code");
        if (memberCode == null) {
            return new LoanStatus(code, title, author, null, null, null, false, 0);
        }
        return new LoanStatus(code, title, author, memberCode, rs.getString("member_name"),
                              rs.getObject("rueckgabe_frist", LocalDateTime.class),
                              rs.getBoolean("ueberfaellig"), rs.getLong("tage"));
    }

    public List<ReturnedBook> giveBack(List<Integer> exemplarCodes) throws SQLException, LibraryException {
        String lockSql = LOAN_STATUS_SQL + " for update";
        String updateSql = "update BIB_Ausleihe set tatsaechliche_rueckgabe = now() where id = ?";

        try (Connection conn = ds.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement lock = conn.prepareStatement(lockSql);
                 PreparedStatement update = conn.prepareStatement(updateSql)) {
                List<ReturnedBook> books = new ArrayList<>();
                for (int code : exemplarCodes) {
                    lock.setInt(1, code);
                    int loanId;
                    LoanStatus status;
                    try (ResultSet rs = lock.executeQuery()) {
                        if (!rs.next()) {
                            throw new LibraryException("Exemplar " + code + " nicht gefunden.");
                        }
                        status = toLoanStatus(rs);
                        loanId = rs.getInt("loan_id");
                    }
                    if (!status.isBorrowed()) {
                        throw new LibraryException("Exemplar " + code + " ist zurzeit nicht ausgeliehen.");
                    }
                    update.setInt(1, loanId);
                    update.executeUpdate();
                    books.add(new ReturnedBook(code, status.title(), status.author(), status.memberCode(),
                                               status.memberName(), status.overdue(),
                                               status.overdue() ? status.days() : 0));
                }
                conn.commit();
                return books;
            } catch (SQLException | LibraryException | RuntimeException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    private static String authorOrUnknown(String author) {
        return author == null ? "Unbekannt" : author;
    }
}
