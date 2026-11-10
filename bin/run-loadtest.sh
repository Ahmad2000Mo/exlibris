#!/usr/bin/env bash
# Lasttest auf hopper (2000 Requests, 20 parallel) + Latenz-Diagramm mit gnuplot.
# Ergebnis: app/latency_plot.png (nicht im Git).
source "$(dirname "$0")/lib.sh"
load_config

echo "=== 1. Login auf hopper ==="
jsessionid=$(ssh hopper "curl -s -c - --data-urlencode $(printf %q "user=$appuser") --data-urlencode $(printf %q "passwd=$apppassword") '$apppath/login' | awk '\$6 == \"JSESSIONID\" {print \$7}'")
if [ -z "$jsessionid" ]; then
  echo "Login auf hopper fehlgeschlagen, keine JSESSIONID" >&2
  exit 1
fi

echo "=== 2. Lasttest (2000 Requests, Concurrency 20) ==="
ssh hopper "hey -n 2000 -c 20 -o csv -H 'Cookie: JSESSIONID=$jsessionid' '$apppath/borrow?action=checkExemplar&code=200002' > ~/latency.csv"

echo "=== 3. Diagramm mit gnuplot ==="
ssh hopper "cat << 'GP' > ~/plot.gp
set datafile separator ','
set title 'ExLibris Antwortzeit (2000 Requests, Concurrency=20)' textcolor rgb '#ffffff' font 'sans,12'
set xlabel 'Request Index' textcolor rgb '#ffffff'
set ylabel 'Antwortzeit (Sekunden)' textcolor rgb '#ffffff'
set border lw 1 lc rgb '#ffffff'
set key textcolor rgb '#ffffff'
set grid lc rgb '#333333'
set object 1 rectangle from graph 0,0 to graph 1,1 behind fillcolor rgb '#0b0c16' fillstyle solid 1.0 noborder
set term png size 900,500 background '#0b0c16'
set output 'latency_plot.png'
plot 'latency.csv' every ::1 using 1 with lines lc rgb '#6366f1' lw 1.5 title 'Antwortzeit (s)'
GP
gnuplot ~/plot.gp"

echo "=== 4. Diagramm herunterladen ==="
scp hopper:~/latency_plot.png app/latency_plot.png

echo "=== 5. Aufraeumen ==="
ssh hopper "rm -f ~/latency.csv ~/plot.gp ~/latency_plot.png"
echo "Fertig: app/latency_plot.png"
