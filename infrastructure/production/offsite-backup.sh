#!/bin/sh
# Encrypted offsite copy (restic image): the newest database dump, uploaded photos and the erasure
# journal go to a restic repository outside this server (S3 bucket or SFTP storage in the EU).
# restic encrypts everything with RESTIC_PASSWORD before it leaves the server (AES-256); without
# that password the backups cannot be read, by the storage provider or by you. Keep it in a
# password manager AND on paper in a safe place.
#
# Retention (also stated in the privacy notice): daily for RESTIC_KEEP_DAILY days, weekly for
# RESTIC_KEEP_WEEKLY weeks, monthly for RESTIC_KEEP_MONTHLY months; older snapshots are pruned.
set -eu

RUN_AT="${OFFSITE_TIME:-0430}"
TEST_WEEKDAY="${RESTORE_TEST_WEEKDAY:-7}"

minutes() { echo "$1" | sed 's/^0*\([0-9]\)/\1/'; }

sleep_until() {
  target=$(( $(minutes "$(echo "$1" | cut -c1-2)") * 60 + $(minutes "$(echo "$1" | cut -c3-4)") ))
  now=$(( $(minutes "$(date +%H)") * 60 + $(minutes "$(date +%M)") ))
  wait=$(( (target - now + 1440) % 1440 ))
  [ "$wait" -eq 0 ] && wait=1440
  sleep $(( wait * 60 - $(minutes "$(date +%S)") ))
}

ping() {
  [ -n "${OFFSITE_HEALTHCHECK_URL:-}" ] || return 0
  wget -q -T 10 -O /dev/null "${OFFSITE_HEALTHCHECK_URL}$1" 2>/dev/null || true
}

run_once() {
  restic cat config >/dev/null 2>&1 || restic init || return 1
  # The nightly dump must be fresh: a stale one means the database backup itself stopped, and
  # this run's health check then reports that too.
  if [ -z "$(find /backups/latest.dump -mmin -1560 2>/dev/null)" ]; then
    echo "offsite: /backups/latest.dump is missing or older than 26 hours" >&2
    return 1
  fi
  # Called as an `if` condition, where `set -e` does not apply: every step checks its own status.
  restic backup --host dukkan --tag nightly /backups/latest.dump /data/media /data/erasures || return 1
  restic forget --host dukkan --prune \
    --keep-daily "${RESTIC_KEEP_DAILY:-7}" --keep-weekly "${RESTIC_KEEP_WEEKLY:-4}" --keep-monthly "${RESTIC_KEEP_MONTHLY:-3}" || return 1
  if [ "$(date +%u)" = "$TEST_WEEKDAY" ]; then
    # Reads back a part of the stored data every week; corrupted or missing packs fail the run.
    restic check --read-data-subset="${RESTIC_CHECK_SUBSET:-10%}" || return 1
  fi
}

: "${RESTIC_REPOSITORY:?set RESTIC_REPOSITORY (offsite storage)}"
: "${RESTIC_PASSWORD:?set RESTIC_PASSWORD}"
[ "${1:-}" = "--now" ] && RUN_AT=""
while true; do
  [ -n "$RUN_AT" ] && sleep_until "$RUN_AT"
  RUN_AT="${OFFSITE_TIME:-0430}"
  ping /start
  if run_once; then
    echo "offsite ok: $(date +%Y-%m-%d)"
    ping ""
  else
    echo "offsite FAILED: $(date +%Y-%m-%d)" >&2
    ping /fail
  fi
done
