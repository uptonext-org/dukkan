#!/bin/sh
# Database backup on the server (postgres image): a nightly pg_dump into the backups volume, kept
# BACKUP_KEEP_DAYS days, and a weekly restore test into a scratch database (GDPR Art. 32(1)(c, d):
# restorability, regularly tested). The offsite service copies the dumps encrypted elsewhere.
# Restore by hand: see README.md.
set -eu

DUMP_AT="${BACKUP_TIME:-0315}"          # HHMM, server time
KEEP_DAYS="${BACKUP_KEEP_DAYS:-7}"
TEST_WEEKDAY="${RESTORE_TEST_WEEKDAY:-7}" # 1 = Monday … 7 = Sunday

minutes() { echo "$1" | sed 's/^0*\([0-9]\)/\1/'; }

sleep_until() {
  target=$(( $(minutes "$(echo "$1" | cut -c1-2)") * 60 + $(minutes "$(echo "$1" | cut -c3-4)") ))
  now=$(( $(minutes "$(date +%H)") * 60 + $(minutes "$(date +%M)") ))
  wait=$(( (target - now + 1440) % 1440 ))
  [ "$wait" -eq 0 ] && wait=1440
  sleep $(( wait * 60 - $(minutes "$(date +%S)") ))
}

restore_test() {
  dump="$1"
  echo "restore test: $dump"
  dropdb --if-exists restore_check
  createdb restore_check
  if pg_restore --no-owner --exit-on-error -d restore_check "$dump"; then
    counts=$(psql -d restore_check -tAc "select (select count(*) from app_user)||' accounts, '||(select count(*) from market)||' markets, '||(select count(*) from customer_order)||' orders, schema '||(select max(version::int) from flyway_schema_history where version is not null)")
    echo "restore test ok: $counts"
    dropdb restore_check
    return 0
  fi
  echo "restore test FAILED for $dump" >&2
  dropdb --if-exists restore_check
  return 1
}

mkdir -p /backups
[ "${1:-}" = "--now" ] && DUMP_AT=""
while true; do
  [ -n "$DUMP_AT" ] && sleep_until "$DUMP_AT"
  DUMP_AT="${BACKUP_TIME:-0315}"
  stamp=$(date +%Y-%m-%d)
  ok=1
  if pg_dump --format=custom --file="/backups/dukkan-$stamp.dump.partial"; then
    mv "/backups/dukkan-$stamp.dump.partial" "/backups/dukkan-$stamp.dump"
    # The offsite service backs up latest.dump (a hard link, no extra space).
    ln -f "/backups/dukkan-$stamp.dump" /backups/latest.dump
    echo "backup ok: dukkan-$stamp.dump ($(du -h "/backups/dukkan-$stamp.dump" | cut -f1))"
    if [ "$(date +%u)" = "$TEST_WEEKDAY" ]; then
      restore_test "/backups/dukkan-$stamp.dump" || ok=0
    fi
  else
    rm -f "/backups/dukkan-$stamp.dump.partial"
    echo "backup FAILED for $stamp" >&2
    ok=0
  fi
  find /backups -name 'dukkan-*.dump' -mtime +"$KEEP_DAYS" -delete
  # A failed dump or restore test stops latest.dump from being refreshed; the offsite run (and its
  # health check) then fails too.
  [ "$ok" = 1 ] || rm -f /backups/latest.dump
done
