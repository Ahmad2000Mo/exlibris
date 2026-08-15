ExLibris – Bibliotheksverwaltungssystem

Webbasierte Bibliotheksverwaltung, entwickelt im Team (4 Studierende) im Modul Softwareengineering II an der Hochschule Bremerhaven.

Funktionen
Login/Logout mit Session-basierter Authentifizierung
Ausleihe & Rückgabe von Büchern über Mitglieds- und Exemplar-Codes
Asynchroner Quittungsdruck: Ausleih- und Rückgabequittungen werden nicht direkt im Request verarbeitet, sondern über eine Redis-Queue an einen separaten PrintDaemon-Prozess übergeben – das entkoppelt den Druckvorgang von der Webanwendung
Lasttests: Skripte auf Basis von hey testen die Kern-Endpunkte (2000 Requests, Concurrency 20) und erzeugen automatisch generierte Latenz-Diagramme via Gnuplot
Architektur
Browser  ──▶  Servlets (Tomcat)  ──▶  MariaDB (via JDBC/DAO)
                     │
                     ▼
              Redis Queue  ──▶  PrintDaemon  ──▶  gedruckte Quittung
Backend: Jakarta Servlets auf Apache Tomcat
Datenbank: MariaDB, Zugriff über ein eigenes DAO (LibraryDao)
Queue: Redis (Jedis-Client) als Verbindung zwischen Webanwendung und Druckprozess
Deployment: Automatisiertes Deploy-Skript für den Hochschul-Anwendungsserver (Tomcat Manager Upload via curl)
Tech-Stack

Java · Jakarta Servlets · Apache Tomcat · MariaDB · Redis (Jedis) · JDBC

Setup & Deployment

Das Projekt ist für das Deployment auf dem Anwendungsserver der Hochschule Bremerhaven vorbereitet:

bash
bin/configure.sh          # lädt Zugangsdaten für DB/Redis und legt lokale Konfiguration an
bin/import-database.sh    # legt das Datenbankschema an (createdb.sql)
bin/populate-mock.sh      # optional: Testdaten einspielen
bin/build.sh              # kompiliert, baut das WAR-Archiv und deployt es
bin/run-print-daemon.sh   # startet den asynchronen Druck-Prozess
Lasttests
bash
bin/run-loadtest.sh          # führt den Lasttest aus und erzeugt ein Latenz-Diagramm
bin/hey-loadtest-borrow.sh   # Lasttest speziell gegen den Borrow-Endpunkt
