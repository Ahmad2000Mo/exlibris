#!/usr/bin/env bash
# Lasttest des Endpunkts checkExemplar mit "hey" (2000 Requests, 20 parallel).
source "$(dirname "$0")/lib.sh"
load_config
mkdir -p tmp
cookie=tmp/cookie-$$.jar
trap 'rm -f "$cookie"' EXIT

csrf=$(app_login "$cookie")
jsessionid=$(awk '$6 == "JSESSIONID" {print $7}' "$cookie")
if [ -z "$jsessionid" ]; then
  echo "Login fehlgeschlagen, kein JSESSIONID-Cookie" >&2
  exit 1
fi

echo "Starte Lasttest auf checkExemplar (2000 Requests, Concurrency 20)..."
hey -n 2000 -c 20 -H "Cookie: JSESSIONID=$jsessionid" "$apppath/borrow?action=checkExemplar&code=200005"

curl -s -o /dev/null -b "$cookie" -H "X-CSRF-Token: $csrf" -X POST "$apppath/logout"
echo "Lasttest beendet."
