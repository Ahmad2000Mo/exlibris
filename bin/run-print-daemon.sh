#!/usr/bin/env bash
# Startet den PrintDaemon als eigenen Java-Prozess (Strg+C beendet ihn sauber).
cd "$(dirname "$0")/.." || exit 1

if [ ! -d build/WEB-INF/classes ]; then
  echo "Das Projekt muss zuerst gebaut werden: bin/build.sh --no-deploy" >&2
  exit 1
fi

exec java -cp "build/WEB-INF/classes:build/WEB-INF/lib/*" hbv.web.PrintDaemon local/config.txt
