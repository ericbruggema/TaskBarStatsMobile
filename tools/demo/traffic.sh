# wisselend download/upload-verkeer voor de demo
while true; do
  (printf 'GET /100MB.zip HTTP/1.0\r\nHost: speedtest.tele2.net\r\n\r\n'; sleep 6) | timeout 6 nc speedtest.tele2.net 80 > /dev/null &
  sleep 3
  (printf 'POST /upload.php HTTP/1.0\r\nHost: speedtest.tele2.net\r\nContent-Length: 30000000\r\n\r\n'; head -c 30000000 /dev/zero) | timeout 4 nc speedtest.tele2.net 80 > /dev/null &
  sleep 6
  sleep 3
done
