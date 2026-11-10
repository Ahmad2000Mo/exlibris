#!/usr/bin/env bash
# Legt einen Mitarbeiter-Zugang an oder setzt dessen Passwort neu.
#   bin/add-employee.sh <benutzername> "<Name>"
# Das Passwort wird verdeckt abgefragt und nur als PBKDF2-Hash gespeichert.
source "$(dirname "$0")/lib.sh"
load_config

user="$1"
name="$2"
if [ -z "$user" ] || [ -z "$name" ]; then
  echo "Aufruf: $0 <benutzername> \"<Name>\"" >&2
  exit 1
fi
if [ ! -d build/WEB-INF/classes ]; then
  echo "Erst bauen: bin/build.sh --no-deploy" >&2
  exit 1
fi

read -r -s -p "Passwort fuer $user: " pw1; echo
read -r -s -p "Passwort wiederholen: " pw2; echo
if [ "$pw1" != "$pw2" ] || [ ${#pw1} -lt 8 ]; then
  echo "Passwoerter stimmen nicht ueberein oder sind kuerzer als 8 Zeichen." >&2
  exit 1
fi

hash=$(printf '%s' "$pw1" | java -cp build/WEB-INF/classes hbv.web.PasswordHasher 2>/dev/null) || exit 1

echo "insert into BIB_Mitarbeiter (benutzername, name, passwort_hash)
values ('$(sql_escape "$user")', '$(sql_escape "$name")', '$hash')
on duplicate key update name = values(name), passwort_hash = values(passwort_hash);" | run_sql \
  && echo "Zugang '$user' gespeichert."
