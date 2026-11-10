#!/usr/bin/env bash
# Laedt zusaetzliche Zufallsbuecher von fakerapi.it in die Datenbank.
#   bin/populate-mock.sh [anzahl]   (Standard: 10)
source "$(dirname "$0")/lib.sh"
load_config

quantity="${1:-10}"
if ! [[ "$quantity" =~ ^[0-9]+$ ]]; then
  echo "Anzahl muss eine Zahl sein" >&2
  exit 1
fi

echo "Hole $quantity Buecher von fakerapi.it..."

# jq baut das SQL; einfache Anfuehrungszeichen und Backslashes werden escaped.
# Die Barcodes kommen aus den letzten 8 Stellen der ISBN; "insert ignore" ueberspringt Kollisionen.
# shellcheck disable=SC2016
curl -sf "https://fakerapi.it/api/v1/books?_quantity=${quantity}" | jq -r '
  def q: gsub("\\\\"; "\\\\") | gsub("'\''"; "'\'''\''") | "'\''" + . + "'\''";
  .data[] | "
start transaction;
insert into BIB_Autorin (name) values (" + (.author|q) + ") on duplicate key update id = last_insert_id(id);
set @autor_id = last_insert_id();
insert into BIB_Buch (titel) values (" + (.title|q) + ");
set @buch_id = last_insert_id();
insert into BIB_Autorin_Buch (autorin_id, buch_id) values (@autor_id, @buch_id);
insert ignore into BIB_Buchexemplar (code, buch_id) values (" + (.isbn[-8:] | tonumber | tostring) + ", @buch_id);
commit;"' | run_sql

if [ "${PIPESTATUS[0]}" -eq 0 ] && [ "${PIPESTATUS[2]}" -eq 0 ]; then
  echo "$quantity Buecher importiert."
else
  echo "Import fehlgeschlagen." >&2
  exit 1
fi
