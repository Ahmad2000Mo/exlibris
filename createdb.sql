-- ExLibris: Datenbankschema (MariaDB)
-- ACHTUNG: loescht alle vorhandenen BIB_-Tabellen und legt sie neu an.

-- Reihenfolge: zuerst Tabellen, die auf andere verweisen (Fremdschluessel)
drop table if exists BIB_Ausleihe;
drop table if exists BIB_Autorin_Buch;
drop table if exists BIB_Buchexemplar;
drop table if exists BIB_Buch;
drop table if exists BIB_Autorin;
drop table if exists BIB_Mitglied;
drop table if exists BIB_Mitarbeiter;

-- Mitarbeitende, die sich am System anmelden.
-- Passwoerter nur als PBKDF2-Hash (siehe hbv.web.PasswordHasher), nie im Klartext.
create table BIB_Mitarbeiter (
  id int primary key auto_increment,
  benutzername varchar(100) not null unique,
  name varchar(255) not null,
  passwort_hash varchar(255) not null
);

create table BIB_Buch (
  id int primary key auto_increment,
  titel text not null
);

create table BIB_Autorin (
  id int primary key auto_increment,
  name varchar(255) not null unique
);

-- n:m-Beziehung Buch <-> Autor/in
create table BIB_Autorin_Buch (
  autorin_id int not null,
  buch_id int not null,
  primary key (autorin_id, buch_id),
  constraint BIB_fk_AutorinBuch_Autorin foreign key (autorin_id) references BIB_Autorin(id) on delete cascade,
  constraint BIB_fk_AutorinBuch_Buch foreign key (buch_id) references BIB_Buch(id) on delete cascade
);

-- Ein Buch kann mehrere physische Exemplare haben, jedes mit eigenem Barcode
create table BIB_Buchexemplar (
  id int primary key auto_increment,
  code int not null unique,
  buch_id int not null,
  constraint BIB_fk_Buchexemplar_Buch foreign key (buch_id) references BIB_Buch(id) on delete restrict
);

create table BIB_Mitglied (
  id int primary key auto_increment,
  code varchar(100) not null unique,
  name varchar(255) not null
);

-- Eine Ausleihe ist aktiv, solange tatsaechliche_rueckgabe NULL ist
create table BIB_Ausleihe (
  id int primary key auto_increment,
  mitglied_id int not null,
  buchexemplar_id int not null,
  ausleihdatum datetime not null default current_timestamp,
  rueckgabe_frist datetime not null,
  tatsaechliche_rueckgabe datetime null default null,
  constraint BIB_fk_Ausleihe_Mitglied foreign key (mitglied_id) references BIB_Mitglied(id) on delete restrict,
  constraint BIB_fk_Ausleihe_Exemplar foreign key (buchexemplar_id) references BIB_Buchexemplar(id) on delete restrict,
  constraint BIB_chk_Ausleihe_Frist check (rueckgabe_frist > ausleihdatum),
  -- schnelle Suche nach der aktiven Ausleihe eines Exemplars
  index BIB_idx_Ausleihe_aktiv (buchexemplar_id, tatsaechliche_rueckgabe)
);

-- Test-Zugang fuer die Entwicklung: Benutzer "admin", Passwort "admin".
-- Fuer den echten Betrieb mit bin/add-employee.sh eigene Zugaenge anlegen und diesen loeschen.
insert into BIB_Mitarbeiter (benutzername, name, passwort_hash) values
('admin', 'Admin', 'pbkdf2$600000$2VVjdU19+D+CoaIgz9RmuA==$Zr7amSbg21T9pnUvtaPcaGmAanS3VRLegHPvs/9K/5Y=');

insert into BIB_Mitglied (code, name) values
('M10001', 'Max Mustermann'),
('M10002', 'Erika Musterfrau'),
('M10003', 'John Doe');
