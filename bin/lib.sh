#!/usr/bin/env bash
# Gemeinsame Funktionen fuer alle bin/-Skripte. Wird per "source bin/lib.sh" eingebunden.

# Immer vom Projektverzeichnis aus arbeiten, egal von wo das Skript aufgerufen wird
cd "$(dirname "${BASH_SOURCE[0]}")/.." || exit 1

load_config() {
  if [ ! -f local/config.txt ]; then
    echo "local/config.txt fehlt. Erst bin/configure.sh ausfuehren (Hochschule)" >&2
    echo "oder config.example.txt nach local/config.txt kopieren (lokal)." >&2
    exit 1
  fi
  # shellcheck source=/dev/null
  source local/config.txt
  redisport="${redisport:-6379}"
  appuser="${appuser:-admin}"
  apppassword="${apppassword:-admin}"
  apppath="$baseurl/$webapp"
}

# Fuehrt SQL von stdin in der Projekt-Datenbank aus.
# Ist "sshhost" in der Konfiguration gesetzt, laeuft der Client ueber SSH auf diesem Rechner.
# Das Passwort geht ueber MYSQL_PWD statt -p, damit es nicht in der Prozessliste steht.
run_sql() {
  if [ -n "${sshhost:-}" ]; then
    ssh "$sshhost" "MYSQL_PWD=$(printf %q "$dbpassword") mariadb -h $(printf %q "$dbserver") -u $(printf %q "$dbuser") $(printf %q "$dbname")"
  else
    MYSQL_PWD="$dbpassword" mariadb -h "$dbserver" -u "$dbuser" "$dbname"
  fi
}

# Escaped einen Text fuer ein SQL-Stringliteral in einfachen Anfuehrungszeichen.
sql_escape() {
  local s=${1//\\/\\\\}
  printf '%s' "${s//\'/\'\'}"
}

# Meldet sich an der Webanwendung an und schreibt das Session-Cookie in die Datei $1.
# Gibt das CSRF-Token auf stdout aus.
app_login() {
  local cookie="$1"
  curl -s -o /dev/null -c "$cookie" -b "$cookie" \
    --data-urlencode "user=$appuser" --data-urlencode "passwd=$apppassword" "$apppath/login"
  curl -s -b "$cookie" "$apppath/session-status" | sed -n 's/.*"csrfToken":"\([^"]*\)".*/\1/p'
}
