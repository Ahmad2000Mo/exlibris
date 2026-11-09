#!/usr/bin/env bash
# Kompiliert, baut target/webapp.war und laedt es (falls "manager" konfiguriert ist) in Tomcat hoch.
#   bin/build.sh            bauen + deployen
#   bin/build.sh --no-deploy  nur bauen
set -euo pipefail
source "$(dirname "$0")/lib.sh"
load_config

rm -rf build target
mkdir -p build target
cp -r app/* build

# Platzhalter ersetzen. Werte kommen ueber Umgebungsvariablen in Perl, damit Sonderzeichen
# im Passwort (z. B. "/" oder "&") die Ersetzung nicht kaputtmachen.
export redisserver redisport redispassword dbserver dbuser dbpassword dbname
perl -pi -e 's/REDISSERVER/$ENV{redisserver}/g; s/REDISPORT/$ENV{redisport}/g; s/REDISPASSWORD/$ENV{redispassword}/g' build/WEB-INF/web.xml
perl -pi -e 's/DBSERVER/$ENV{dbserver}/g; s/DBUSER/$ENV{dbuser}/g; s/DBPASSWORD/$ENV{dbpassword}/g; s/DBNAME/$ENV{dbname}/g' build/META-INF/context.xml

# shellcheck disable=SC2046
javac -Xlint:all --release 21 -encoding UTF-8 -cp 'lib/*:app/WEB-INF/lib/*' -d build/WEB-INF/classes $(find src -name '*.java')
jar -cf target/webapp.war -C build .
echo "Gebaut: target/webapp.war"

if [ "${1:-}" = "--no-deploy" ] || [ -z "${manager:-}" ]; then
  exit 0
fi

curl --location --netrc-file local/netrc --fail --upload-file target/webapp.war \
  "$baseurl/$manager/text/deploy?path=/$webapp&update=true"
echo
# Kurzer Rauchtest: antwortet die Anwendung?
if curl -sf "$apppath/session-status" >/dev/null; then
  echo "Deploy erfolgreich: $apppath/"
else
  echo "Deploy hochgeladen, aber $apppath/session-status antwortet nicht" >&2
  exit 1
fi
