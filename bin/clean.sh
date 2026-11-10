#!/usr/bin/env bash
# Loescht Build-Ergebnisse und temporaere Dateien. local/ (Zugangsdaten) bleibt erhalten,
# ausser man ruft "bin/clean.sh --all" auf.
cd "$(dirname "$0")/.." || exit 1
rm -rf build target tmp test-classes
if [ "${1:-}" = "--all" ]; then
  rm -rf local
fi
