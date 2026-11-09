#!/usr/bin/env bash
# Rauchtest gegen die laufende Anwendung: Login, Abfragen, Ausleihe, Rueckgabe, Logout.
# Erwartet frisch eingespielte Testdaten (bin/import-database.sh).
source "$(dirname "$0")/lib.sh"
load_config
mkdir -p tmp
cookie=tmp/cookie-$$.jar
trap 'rm -f "$cookie"' EXIT

failures=0
expect() {  # expect <beschreibung> <erwarteter Text> <antwort>
  if [[ "$3" == *"$2"* ]]; then
    echo "OK    $1"
  else
    echo "FEHLER $1"
    echo "      erwartet: $2"
    echo "      bekommen: $3"
    failures=$((failures + 1))
  fi
}
get()  { curl -s -b "$cookie" "$apppath/$1"; }
post() { curl -s -b "$cookie" -H "X-CSRF-Token: $csrf" --data "$2" "$apppath/$1"; }

expect "Status vor Login"         '"loggedIn":false'   "$(curl -s "$apppath/session-status")"
expect "API ohne Login gesperrt"  '"success":false'    "$(curl -s "$apppath/borrow?action=checkMember&code=M10001")"

wrong=$(curl -s -o /dev/null -w '%{redirect_url}' --data 'user=admin&passwd=falsch' "$apppath/login")
expect "Falsches Passwort"        'login.html?error=1' "$wrong"

csrf=$(app_login "$cookie")
expect "Login"                    '"loggedIn":true'    "$(get session-status)"
expect "Mitglied pruefen"         '"name":"Max Mustermann"' "$(get 'borrow?action=checkMember&code=M10001')"
expect "Freies Exemplar"          '"success":true'     "$(get 'borrow?action=checkExemplar&code=102')"
expect "Verliehenes Exemplar"     'bereits verliehen'  "$(get 'borrow?action=checkExemplar&code=100')"
expect "Rueckgabe-Status"         'berfällig'          "$(get 'return?action=checkExemplar&code=100')"
expect "POST ohne CSRF-Token"     'CSRF'               "$(curl -s -b "$cookie" --data 'memberCode=M10003&exemplarCodes=102' "$apppath/borrow")"
expect "Ausleihe buchen"          '"success":true'     "$(post borrow 'memberCode=M10003&exemplarCodes=102')"
expect "Rueckgabe buchen"         '"success":true'     "$(post return 'exemplarCodes=102')"
expect "Logout"                   '"success":true'     "$(post logout '')"
expect "Status nach Logout"       '"loggedIn":false'   "$(get session-status)"

echo
if [ "$failures" -eq 0 ]; then
  echo "Alle Pruefungen bestanden."
else
  echo "$failures Pruefung(en) fehlgeschlagen."
  exit 1
fi
