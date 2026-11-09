#!/usr/bin/env bash
# Prueft alle Shell-Skripte mit shellcheck.
# Abgeschaltet, weil local/config.txt erst zur Laufzeit eingebunden wird:
#   SC2034 (Variable unbenutzt), SC2154 (Variable nicht zugewiesen), SC1091 (Datei nicht gefunden).
# SC2029: in den ssh-Befehlen sollen Variablen bewusst lokal eingesetzt werden.
cd "$(dirname "$0")/.." || exit 1
shellcheck -a -x -e SC2034,SC2154,SC1091,SC2029 --color=never --format=gcc bin/*.sh hooks/pre-receive
