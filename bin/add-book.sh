#!/usr/bin/env bash
# Legt ein Buch an, optional mit einem Exemplar.
#   bin/add-book.sh "<Titel>" "<Autor/in>" [Barcode]
source "$(dirname "$0")/lib.sh"
load_config

titel="$1"
autor="$2"
code="$3"

if [ -z "$titel" ] || [ -z "$autor" ]; then
  echo "Aufruf: $0 <Buchtitel> <Autor/in> [Barcode]" >&2
  exit 1
fi
if [ -n "$code" ] && ! [[ "$code" =~ ^[1-9][0-9]*$ ]]; then
  echo "Barcode muss eine positive Zahl sein" >&2
  exit 1
fi

safe_titel=$(sql_escape "$titel")
safe_autor=$(sql_escape "$autor")

sql="start transaction;
insert into BIB_Autorin (name) values ('$safe_autor') on duplicate key update id = last_insert_id(id);
set @autor_id = last_insert_id();
insert into BIB_Buch (titel) values ('$safe_titel');
set @buch_id = last_insert_id();
insert into BIB_Autorin_Buch (autorin_id, buch_id) values (@autor_id, @buch_id);"
if [ -n "$code" ]; then
  sql="$sql
insert into BIB_Buchexemplar (code, buch_id) values ($code, @buch_id);"
fi
sql="$sql
commit;"

echo "Lege '$titel' von '$autor' an..."
if echo "$sql" | run_sql; then
  echo "Buch angelegt."
else
  echo "Fehler beim Anlegen." >&2
  exit 1
fi
