-- ExLibris: kleine Beispieldaten fuer Tests und Vorfuehrung.
-- Wird von bin/import-database.sh NACH createdb.sql und mock_books.sql eingespielt.

-- Ein Buch mit zwei Exemplaren (Barcodes 100 und 101)
insert into BIB_Autorin (name) values ('Martin Kleppmann')
  on duplicate key update id = last_insert_id(id);
set @autorin_id = last_insert_id();
insert into BIB_Buch (titel) values ('Designing Data-Intensive Applications');
set @buch_id = last_insert_id();
insert into BIB_Autorin_Buch (autorin_id, buch_id) values (@autorin_id, @buch_id);
insert into BIB_Buchexemplar (code, buch_id) values (100, @buch_id), (101, @buch_id), (102, @buch_id);

-- Max Mustermann hat Exemplar 100 ausgeliehen (seit 2 Tagen ueberfaellig)
insert into BIB_Ausleihe (mitglied_id, buchexemplar_id, ausleihdatum, rueckgabe_frist)
select m.id, be.id, now() - interval 16 day, now() - interval 2 day
from BIB_Mitglied m, BIB_Buchexemplar be
where m.code = 'M10001' and be.code = 100;

-- Erika Musterfrau hat Exemplar 101 ausgeliehen (noch 9 Tage Zeit)
insert into BIB_Ausleihe (mitglied_id, buchexemplar_id, ausleihdatum, rueckgabe_frist)
select m.id, be.id, now() - interval 5 day, now() + interval 9 day
from BIB_Mitglied m, BIB_Buchexemplar be
where m.code = 'M10002' and be.code = 101;

-- Exemplar 102 ist frei und kann ausgeliehen werden.
