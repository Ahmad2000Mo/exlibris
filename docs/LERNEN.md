# Was wurde verbessert, und warum?

Diese Datei erklärt jede Änderung so, dass du sie in einem Vorstellungsgespräch erklären kannst.
Zu jedem Abschnitt gehören **Interviewfragen**. Beantworte sie laut oder schriftlich, *bevor* du die Antwort aufklappst.

> **Wichtig für deinen CV:** Schreib nur Dinge hinein, die du erklären kannst. Arbeite diese Datei durch,
> lies den zugehörigen Code und ändere testweise etwas daran (z. B. einen Test kaputt machen und schauen, was passiert).

---

## 1. Login mit Passwort-Hashing

**Vorher:** `LoginServlet` hat jede Kombination aus nicht-leerem Benutzernamen und Passwort akzeptiert. Es gab keine Benutzertabelle.

**Jetzt:**
- Tabelle `BIB_Mitarbeiter` mit `passwort_hash` (`createdb.sql`)
- `PasswordHasher.java`: PBKDF2-HMAC-SHA256, 600 000 Iterationen, 16 Byte Zufalls-Salt
- Gespeichert wird ein String `pbkdf2$600000$<salt>$<hash>`
- Vergleich mit `MessageDigest.isEqual` (konstante Laufzeit)
- Bei unbekanntem Benutzer wird trotzdem ein Hash berechnet (`DUMMY_HASH`), damit die Antwortzeit nicht verrät, ob es den Benutzer gibt
- Login nur per POST, sonst steht das Passwort in der URL und damit in Server-Logs und im Browser-Verlauf

**Konzepte:** Hashing vs. Verschlüsselung · Salt · langsame Hashfunktionen · Timing-Angriffe · User Enumeration

<details><summary>Warum reicht SHA-256 nicht?</summary>

SHA-256 ist absichtlich *schnell*. Eine Grafikkarte schafft Milliarden Versuche pro Sekunde, ein geleaktes Passwort wie „sommer2024“ ist in Sekunden gefunden. PBKDF2 wiederholt den Hash 600 000-mal und macht jeden Versuch entsprechend teurer. Für einen einzelnen Login kostet das ca. 0,3 s, für einen Angreifer mit Millionen Versuchen ist es unbezahlbar.
</details>

<details><summary>Wozu ein Salt?</summary>

Ohne Salt haben zwei Benutzer mit gleichem Passwort denselben Hash, und Angreifer können vorberechnete Tabellen (Rainbow Tables) benutzen. Mit einem zufälligen Salt pro Benutzer muss jeder Hash einzeln geknackt werden.
</details>

<details><summary>Warum steht die Iterationszahl im gespeicherten String?</summary>

Damit man sie später erhöhen kann: Alte Hashes bleiben prüfbar, neue bekommen mehr Iterationen.
</details>

---

## 2. Sessions und CSRF-Schutz

**Vorher:** Jeder Servlet hat den Login selbst geprüft (Code doppelt), CSS-Dateien der Login-Seite waren ohne Login gesperrt, und es gab keinen CSRF-Schutz.

**Jetzt:** `AuthFilter.java` ist die *eine* Stelle für:
1. **Öffentliche Pfade** (Whitelist): alles andere braucht eine Session
2. **CSRF-Token**: wird beim Login zufällig erzeugt, in der Session gespeichert und über `/session-status` an das Dashboard gegeben. Jeder POST muss es im Header `X-CSRF-Token` mitschicken.
3. **Sicherheits-Header**: `Content-Security-Policy`, `X-Frame-Options`, `X-Content-Type-Options`

Außerdem in `web.xml`: Session-Timeout 30 min, Cookie `HttpOnly`, Session-ID nie in der URL.

<details><summary>Was ist CSRF, und warum hilft das Token?</summary>

Cross-Site Request Forgery: Eine fremde Webseite enthält ein verstecktes Formular, das an `/borrow` sendet. Der Browser schickt das Session-Cookie automatisch mit, und die Buchung würde im Namen der angemeldeten Person ausgeführt. Die fremde Seite kann aber das Token nicht lesen (Same-Origin-Policy), also fehlt der Header, und der Filter lehnt mit 403 ab.
</details>

<details><summary>Was ist Session Fixation?</summary>

Ein Angreifer schiebt dem Opfer eine ihm bekannte Session-ID unter. Meldet sich das Opfer an, kennt der Angreifer eine eingeloggte Session. Gegenmittel: beim Login die alte Session verwerfen und eine neue anlegen (`LoginServlet`).
</details>

<details><summary>Was bringt die Content-Security-Policy?</summary>

`script-src 'self'` erlaubt nur Skripte aus eigenen Dateien. Selbst wenn ein Angreifer `<script>` in einen Buchtitel schmuggelt, führt der Browser es nicht aus. Deshalb wurde das JavaScript aus `dashboard.html` nach `dashboard.js` verschoben und `onclick="…"` durch Event-Delegation ersetzt.
</details>

---

## 3. Transaktionen und Nebenläufigkeit

**Unverändert gut (und jetzt getestet):** `LibraryDao.borrow()` und `giveBack()` laufen in einer Transaktion. Jede Exemplarzeile wird mit `SELECT … FOR UPDATE` gesperrt.

**Neu:**
- Datum und Frist kommen aus der *Datenbank-Uhr* (`now()`), dieselbe Uhr wie bei der Überfällig-Prüfung, also keine Zeitzonen-Fehler zwischen Java und DB
- Fachliche Fehler (`LibraryException`) vs. technische Fehler (`SQLException`) getrennt
- `try-with-resources` statt manuellem `finally`, damit Verbindungen sicher zurück in den Pool gehen

**Nachgewiesen:** 10 parallele Ausleihen desselben Exemplars ergeben genau 1 Buchung. Eine Ausleihe von `200009,200007` (zweites schon verliehen) bucht auch das erste *nicht*.

<details><summary>Was passiert ohne FOR UPDATE?</summary>

Race Condition: Zwei Anfragen lesen gleichzeitig „Exemplar frei“, beide fügen eine Ausleihe ein, und das Buch ist doppelt verliehen. `FOR UPDATE` sperrt die Zeile bis zum Commit; die zweite Anfrage wartet und sieht danach „verliehen“.
</details>

<details><summary>Was bedeutet ACID, und wo siehst du das hier?</summary>

Atomicity (alles oder nichts, Rollback), Consistency (Fremdschlüssel, CHECK-Constraint), Isolation (FOR UPDATE), Durability (Commit in InnoDB).
</details>

---

## 4. Zuverlässige Warteschlange (Reliable Queue)

**Vorher:** `BRPOP` holt einen Auftrag aus Redis. Stürzt der Daemon danach ab, ist der Auftrag weg. Ist Redis aus, sah der Nutzer trotzdem „Beleg gedruckt“.

**Jetzt (`PrintDaemon.java`):**
1. `BLMOVE print_queue → print_processing` (atomar)
2. Beleg in eine `.tmp`-Datei schreiben, dann umbenennen, sodass nie halbe Belege entstehen
3. Erst danach `LREM` aus `print_processing`
4. Beim Start: alles aus `print_processing` zurück in die Queue
5. Unlesbare Aufträge kommen nach `print_failed` (sonst würde der Daemon ewig daran hängen; Fachbegriff: *Poison Message* bzw. *Dead Letter Queue*)

Dateinamen enthalten eine UUID (keine Überschreibung bei zwei Belegen pro Sekunde) und keine Sonderzeichen aus Nutzerdaten (kein `../`).

<details><summary>Was heißt „at-least-once delivery“, und was ist der Nachteil?</summary>

Jeder Auftrag wird *mindestens* einmal verarbeitet. Stürzt der Daemon zwischen Schreiben und `LREM` ab, wird der Beleg nach dem Neustart ein zweites Mal gedruckt. Doppelt drucken ist hier harmlos, verloren gehen wäre schlimmer. „Exactly once“ ist in verteilten Systemen sehr schwer.
</details>

<details><summary>Warum überhaupt eine Queue statt direkt drucken?</summary>

Entkopplung: Ein langsamer oder kaputter Drucker blockiert keine Ausleihe. Webanwendung und Daemon können unabhängig neu gestartet und skaliert werden.
</details>

---

## 5. JSON ohne Bastelei

**Vorher:** `JsonHelper` suchte mit `indexOf` nach dem nächsten `"`, `]` oder `}`. Ein Buchtitel wie `Arrays [] und {Objekte}` hat den Beleg zerstört.

**Jetzt:** `Json.java` ist ein echter Parser (rekursiver Abstieg) und Serializer. Die Tests in `JsonTest` beweisen, dass genau diese Fälle funktionieren.

<details><summary>Warum keine Bibliothek wie Jackson oder Gson?</summary>

Das Projekt hat kein Build-Tool (Maven/Gradle). Jede Bibliothek müsste als `.jar` von Hand verwaltet werden. Für die zwei Nachrichtentypen reichen ~250 Zeilen. In einem größeren Projekt wäre Jackson die richtige Wahl.
</details>

---

## 6. XSS im Frontend

Alle Werte, die per `innerHTML` eingefügt werden, gehen durch `escapeHTML()`, vorher fehlten `memberCode` und `timeDiffText`. Zusätzlich escaped der Server `<` in JSON als `<`.

<details><summary>Warum nicht einfach überall textContent?</summary>

Wäre noch sicherer. Für die Listen-Templates ist `innerHTML` mit konsequentem Escaping lesbarer; die CSP ist die zweite Verteidigungslinie („Defense in Depth“).
</details>

---

## 7. Datenbank und Testdaten

- `BIB_Autorin_Buch` hat jetzt Primärschlüssel und Fremdschlüssel (vorher waren Duplikate und Waisen möglich)
- Spalte `status` entfernt: Sie hat nur `tatsaechliche_rueckgabe` gedoppelt (Redundanz führt zu Widersprüchen)
- Index auf `(buchexemplar_id, tatsaechliche_rueckgabe)` für die häufigste Abfrage
- **Bug behoben:** `mock_books.sql` hat mit abgeschalteten Fremdschlüsseln geleert. Danach zeigten bestehende Ausleihen auf *andere* Bücher. Jetzt: Schema → Bücher → `testdata.sql`

---

## 8. Skripte

- `bin/lib.sh`: gemeinsame Funktionen statt viermal kopiertem Code (DRY)
- Passwort über `MYSQL_PWD` bzw. stdin statt Kommandozeile (sonst sichtbar in `ps`)
- Sonderzeichen im Passwort machen `build.sh` nicht mehr kaputt (Perl liest aus Umgebungsvariablen)
- SQL-Escaping in `add-book.sh`/`populate-mock.sh` auch für Backslashes
- `shellcheck` ohne Warnungen

---

## 9. Tests und CI

- `bin/test.sh`: 19 JUnit-5-Tests
- `bin/check.sh`: 13 End-to-End-Prüfungen gegen die laufende App
- `.github/workflows/ci.yml`: Bei jedem Push startet GitHub Tomcat, MariaDB und Redis als Container und führt alles aus

<details><summary>Unterschied Unit-Test vs. Integrationstest?</summary>

Unit-Tests prüfen eine Klasse isoliert, schnell, ohne Datenbank (z. B. `PasswordHasher`). Integrationstests prüfen das Zusammenspiel echter Komponenten (Servlet + MariaDB + Redis). Beide sind nötig: Unit-Tests finden Fehler genau, Integrationstests finden Fehler an den Schnittstellen. Beispiel: Erst der echte Daemon-Start hat gezeigt, dass er wegen einer Servlet-Abhängigkeit außerhalb von Tomcat nicht lief.
</details>

---

## Mögliche CV-Formulierungen

Nur verwenden, wenn du die Abschnitte oben erklären kannst. Und klar trennen, was im Team und was von dir allein kam.

- Bibliotheksverwaltung mit Java/Jakarta Servlets, MariaDB und Redis im 4er-Team entwickelt
- Asynchronen Belegdruck über eine zuverlässige Redis-Warteschlange (BLMOVE, Wiederaufnahme nach Absturz, Dead-Letter-Queue) umgesetzt
- Nebenläufigkeitssichere Ausleihe mit Datenbank-Transaktionen und Zeilensperren (`SELECT … FOR UPDATE`), per Lasttest nachgewiesen
- Authentifizierung mit PBKDF2-Passwort-Hashing, CSRF-Schutz und Content-Security-Policy abgesichert
- CI-Pipeline mit GitHub Actions: Unit-Tests (JUnit 5) und End-to-End-Tests gegen Tomcat, MariaDB und Redis

## Was noch fehlt (gute nächste Schritte zum Selbermachen)

- **Maven oder Gradle** statt eingecheckter `.jar`-Dateien
- **Login-Sperre** nach mehreren Fehlversuchen (Brute-Force-Schutz)
- **Funktionen:** Fristverlängerung, Liste überfälliger Ausleihen, Buchsuche, Ausleih-Limit pro Mitglied
- **Docker Compose** für den lokalen Start mit einem Befehl
- **DAO-Tests** gegen eine echte Test-Datenbank (z. B. mit Testcontainers)
