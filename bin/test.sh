#!/usr/bin/env bash
# Kompiliert Quellcode und Unit-Tests und fuehrt die Tests mit JUnit 5 aus.
set -euo pipefail
cd "$(dirname "$0")/.."

rm -rf test-classes
mkdir -p test-classes
cp='lib/*:lib/test/*:app/WEB-INF/lib/*'
# shellcheck disable=SC2046
javac -Xlint:all -Werror --release 21 -encoding UTF-8 -cp "$cp" -d test-classes \
  $(find src test -name '*.java')
java -jar lib/test/junit-platform-console-standalone-*.jar execute \
  --class-path "test-classes:app/WEB-INF/lib/*:lib/jakarta.servlet-api-6.1.0.jar" \
  --scan-class-path test-classes --disable-banner --details=tree
