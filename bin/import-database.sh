#!/usr/bin/env bash
# Setzt die Datenbank komplett zurueck: Schema, 1000 Testbuecher, Beispiel-Ausleihen.
source "$(dirname "$0")/lib.sh"
load_config

for file in createdb.sql mock_books.sql testdata.sql; do
  echo "Spiele $file ein..."
  if ! run_sql < "$file"; then
    echo "Fehler beim Einspielen von $file" >&2
    exit 1
  fi
done
echo "Datenbank bereit (Login: admin / admin, Mitglieder M10001-M10003, Exemplare 100-102 und 200000-200999)."
