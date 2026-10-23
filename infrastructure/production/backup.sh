#!/bin/sh
# Nightly database dump (custom format, compressed) and a list of media files, kept BACKUP_KEEP_DAYS days.
# Restore: pg_restore --clean --if-exists -d dukkan /backups/dukkan-<date>.dump (see BACKUP.md).
set -eu
mkdir -p /backups
while true; do
  now=$(date +%s)
  next=$(date -d "$(date +%Y-%m-%d) 03:15" +%s 2>/dev/null || echo $((now + 86400)))
  [ "$next" -le "$now" ] && next=$((next + 86400))
  sleep $((next - now))
  stamp=$(date +%Y-%m-%d)
  if pg_dump --format=custom --file="/backups/dukkan-$stamp.dump.partial"; then
    mv "/backups/dukkan-$stamp.dump.partial" "/backups/dukkan-$stamp.dump"
    find /media -type f | sort > "/backups/media-$stamp.txt"
    echo "backup ok: dukkan-$stamp.dump"
  else
    echo "backup FAILED for $stamp" >&2
  fi
  find /backups -name 'dukkan-*.dump' -mtime +"$BACKUP_KEEP_DAYS" -delete
  find /backups -name 'media-*.txt' -mtime +"$BACKUP_KEEP_DAYS" -delete
done
