lein compile
rm .tmp-*
lein run -u "$1" -p "$2" -s $4 -j "$5" by-status > daily.html 

cat - daily.html <<HERE | sendmail -oi -t
From: $1@abebooks.com
To: $3
Subject: Daily Tiger sprint snapshot 
Content-Type: text/html; charset=us-ascii
Content-Transfer-Encoding: 7bit
MIME-Version: 1.0

HERE
