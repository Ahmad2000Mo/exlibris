#!/usr/bin/env bash
# Legt local/config.txt und local/netrc fuer den Hochschulserver "hopper" an.
# Fuer lokale Entwicklung stattdessen config.example.txt nach local/config.txt kopieren.
cd "$(dirname "$0")/.." || exit 1

if [ "$(uname -n)" = hopper ]; then
  YOURUSER=$(whoami)
  DEPLOYPASSWORD=$(grep password ~/.my.cnf | cut -d '=' -f2)
  SSHHOST=
else
  ssh hopper whoami >/dev/null || { echo "'ssh hopper' muss funktionieren" >&2; exit 1; }
  YOURUSER="$(ssh hopper whoami)"
  DEPLOYPASSWORD=$(ssh hopper grep password .my.cnf | cut -d '=' -f2)
  SSHHOST=hopper
fi

if [ -z "$DEPLOYPASSWORD" ]; then
  echo "Konnte das Passwort nicht aus .my.cnf auf hopper lesen" >&2
  exit 1
fi

mkdir -p local
chmod 700 local
umask 077

cat > local/config.txt <<CONF
user=$YOURUSER
baseurl=https://informatik.hs-bremerhaven.de
webapp=docker-$YOURUSER-java
manager=docker-$YOURUSER-manager
dbserver=mysql-server
dbname=${YOURUSER}_db
dbpassword=$DEPLOYPASSWORD
dbuser=$YOURUSER
redisserver=localhost
redisport=6379
redispassword=$DEPLOYPASSWORD
appuser=admin
apppassword=admin
sshhost=$SSHHOST
CONF

cat > local/netrc <<CONF
machine informatik.hs-bremerhaven.de login manager password $DEPLOYPASSWORD
machine localhost login manager password $DEPLOYPASSWORD
CONF

echo "local/config.txt und local/netrc angelegt ($YOURUSER)"
