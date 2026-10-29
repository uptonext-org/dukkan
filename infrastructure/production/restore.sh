#!/bin/sh
# Restores the database (and optionally the photos) on the production server. Run it on the
# server in this directory:
#
#   ./restore.sh local dukkan-2026-11-02.dump    # a dump from the backups volume (last 7 days)
#   ./restore.sh offsite latest                  # the newest offsite snapshot (restic)
#   ./restore.sh offsite 8818e506 --with-media   # a given snapshot, photos too
#   ./restore.sh offsite latest --with-media --with-journal   # new server: everything
#
# The erasure journal on this server is kept (only --with-journal restores it, for a new server
# whose journal is gone). After the restore the backend starts and erases again every account that
# was deleted after the backup was taken (ErasureReplay), so no deleted personal data comes back.
set -eu
cd "$(dirname "$0")"

source="${1:-}"; what="${2:-}"
# `shift` of missing arguments ends a POSIX shell before `||` could run: check first.
if [ -z "$source" ] || [ -z "$what" ]; then
  sed -n '2,13p' "$0"
  exit 1
fi
shift 2
with_media=0; with_journal=0
for option in "$@"; do
  case "$option" in
    --with-media) with_media=1 ;;
    --with-journal) with_journal=1 ;;
    *) echo "unknown option $option"; exit 1 ;;
  esac
done

printf 'This replaces the production database with %s %s. Type RESTORE to continue: ' "$source" "$what"
read -r answer
[ "$answer" = "RESTORE" ] || { echo "aborted"; exit 1; }

docker compose stop backend

case "$source" in
  local)
    dump="/backups/$what"
    ;;
  offsite)
    includes="--include /backups/latest.dump"
    [ "$with_media" = 1 ] && includes="$includes --include /data/media"
    [ "$with_journal" = 1 ] && includes="$includes --include /data/erasures"
    # Restores into a scratch folder, then moves the files into place.
    docker compose run --rm --no-deps --entrypoint sh offsite -c "
      set -e
      rm -rf /backups/restore && mkdir -p /backups/restore
      restic restore '$what' --target /backups/restore $includes
      if [ -d /backups/restore/data/media ]; then cp -a /backups/restore/data/media/. /data/media/; fi
      if [ -d /backups/restore/data/erasures ]; then cp -a /backups/restore/data/erasures/. /data/erasures/; fi"
    dump="/backups/restore/backups/latest.dump"
    ;;
  *)
    echo "source must be local or offsite"; exit 1 ;;
esac

docker compose exec -T backup sh -c "
  set -e
  test -f '$dump'
  dropdb --if-exists dukkan
  createdb dukkan
  pg_restore --no-owner --exit-on-error -d dukkan '$dump'
  echo restored: \$(psql -d dukkan -tAc 'select count(*) from app_user') accounts"

docker compose start backend
echo "Backend starting; erasures after the backup are applied again (docker compose logs backend | grep -i erasure)."
