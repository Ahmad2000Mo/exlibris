#!/usr/bin/env bash
# Einfacher Lasttest mit wrk.
#   bin/wrk.sh [pfad] [verbindungen] [sekunden]   (Standard: session-status 100 5)
source "$(dirname "$0")/lib.sh"
load_config
action="${1:-session-status}"
connections="${2:-100}"
duration="${3:-5}"
threads=$((connections / 10))
[ "$threads" -eq 0 ] && threads=1
wrk -d "$duration" -c "$connections" -t "$threads" "$apppath/$action"
