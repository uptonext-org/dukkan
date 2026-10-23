# Backups and restore

Concept, retention periods and the legal side (German): `docs/operations/datensicherung.md`.

## What runs

| When (server time) | Service | What |
|---|---|---|
| 03:15 nightly | `backup` | `pg_dump` (custom format) into the `backups` volume, kept 7 days (`BACKUP_KEEP_DAYS`) |
| Sundays, after the dump | `backup` | **Restore test**: the new dump into a scratch database `restore_check`, row counts and schema version in the log, then dropped |
| 04:30 nightly | `offsite` | restic: the newest dump, the photos (`media`) and the erasure journal, **encrypted** (AES-256, key `RESTIC_PASSWORD`) to an S3 bucket outside the server; keeps 7 daily, 4 weekly, 3 monthly snapshots and prunes the rest |
| Sundays | `offsite` | `restic check --read-data-subset=10%`: reads part of the stored data back |

If the dump or its restore test fails, `latest.dump` is removed, so the offsite run fails too.
With `OFFSITE_HEALTHCHECK_URL` (e.g. a free check at healthchecks.io, expected every 24 h with
a grace time of a few hours) you get an e-mail when a night fails **or does not happen at all**.

```bash
docker compose logs --since 48h backup offsite      # "backup ok", "restore test ok", "offsite ok"
docker compose exec offsite restic snapshots         # offsite snapshots
docker compose exec backup sh /usr/local/bin/dukkan-backup.sh --now      # a dump now (Ctrl-C after "backup ok")
docker compose exec offsite sh /usr/local/bin/dukkan-offsite.sh --now    # an offsite run now
```

## Setting up the offsite storage

1. Create a bucket at an S3 provider in the EU, at a **different provider or location** than the
   server (e.g. server in Falkenstein, bucket in Helsinki, or another provider). Conclude the
   provider's data processing agreement (AVV).
2. Create an access key limited to that bucket. If the provider offers it, enable object lock or
   versioning so that a compromised server cannot delete the backups.
3. In `.env`: `RESTIC_REPOSITORY=s3:https://<endpoint>/<bucket>`, `RESTIC_S3_ACCESS_KEY`,
   `RESTIC_S3_SECRET_KEY`, `RESTIC_PASSWORD=$(openssl rand -base64 32)`.
4. **Store `RESTIC_PASSWORD` twice outside the server**: password manager and on paper in a
   safe. Without it nobody can read the backups, you included.
5. `docker compose up -d`; the first night creates the repository (`restic init`).

## Restore

```bash
./restore.sh local dukkan-2026-11-02.dump              # from the server's own dumps (7 days)
./restore.sh offsite latest                            # newest offsite snapshot
./restore.sh offsite 8818e506 --with-media             # a given snapshot, with the photos
./restore.sh offsite latest --with-media --with-journal    # a new server: everything
```

The script asks for confirmation, stops the backend, replaces the database, starts the backend
again. The **erasure journal on the server is kept**: at startup the backend erases every account
again that was deleted after the backup was taken, so a restore never brings back data a user had
deleted (`docker compose logs backend | grep -i erasure`). Only a new server takes the journal from
the snapshot (`--with-journal`).

### Disaster recovery (server lost)

1. New server, Docker, this repository, the same `.env` (from the password manager).
2. `docker compose up -d postgres backup offsite` (the backend first creates its volumes:
   `docker compose up -d backend && docker compose stop backend`).
3. `./restore.sh offsite latest --with-media --with-journal`
4. DNS to the new server, `docker compose up -d`.

**Practise this once before launch** and every 6 months, and write down the date and how long it
took (Art. 32(1)(d) GDPR: regular testing). Data loss in the worst case: one day (RPO 24 h).
