# Production on one server

The simplest production setup: one Linux server (e.g. 4 vCPU / 8 GB, Ubuntu 24.04, in an EU
data centre) with Docker. Caddy terminates TLS with Let's Encrypt certificates and routes:

| Host | Path | Goes to |
|---|---|---|
| `DUKKAN_DOMAIN` (customers) | `/api/*` | backend (prod profile) |
| | everything else | customer web app (`/reset-password`, `/verify-email` from the mails too) |
| `DUKKAN_PORTAL_DOMAIN` (merchants, admins) | `/api/*` | backend |
| | everything else | web portal |

`/actuator/*` is never public; health and Prometheus metrics are on the backend's port 8081
inside the Docker network.

## First start

```bash
# On the server, as a user in the docker group:
git clone https://github.com/uptonext-org/dukkan.git && cd dukkan/infrastructure/production
cp .env.example .env && chmod 600 .env && $EDITOR .env      # every REQUIRED value
echo "$GHCR_TOKEN" | docker login ghcr.io -u <github-user> --password-stdin   # if the packages are private
docker compose up -d
docker compose logs -f backend      # "Started DukkanApplication"; a refused start lists every problem
```

Then register the first account in the web app, set `DUKKAN_BOOTSTRAP_ADMIN_EMAIL` to it,
`docker compose up -d backend` once, and remove the setting again.

## Updates

```bash
sed -i 's/^DUKKAN_VERSION=.*/DUKKAN_VERSION=1.0.1/' .env
docker compose pull && docker compose up -d     # Flyway migrates on start; a few seconds of downtime
```

Roll back by setting the previous version. Database migrations only add (columns, tables), so an
older backend runs on a newer schema; if a release says otherwise, restore the dump taken before it.

## Backups

The `backup` service writes `dukkan-YYYY-MM-DD.dump` (pg_dump custom format) every night at 03:15
into the `backups` volume and keeps 14 days. **Copy them off the server** too, for example with the
hosting provider's snapshot/backup feature or `rclone`/`restic` to an object store in another
location, together with the `media` volume (uploaded photos, unless S3 is used).

Restore into a fresh database:

```bash
docker compose stop backend
docker compose exec -T postgres dropdb -U dukkan dukkan
docker compose exec -T postgres createdb -U dukkan dukkan
docker compose exec -T postgres pg_restore -U dukkan -d dukkan < /path/to/dukkan-2026-11-02.dump
docker compose start backend
```

Practise this once before launch and write down how long it took.

## Monitoring

At least an external uptime check (e.g. UptimeRobot, Better Stack) on
`https://<DUKKAN_DOMAIN>/api/v1/locales` and on the portal, alerting by e-mail/SMS. The backend's
Prometheus metrics and the dashboard/alert rules in `infrastructure/kubernetes/monitoring` can be
used with a Prometheus/Grafana of your choice (scrape `backend:8081/actuator/prometheus`).

## Kubernetes instead

`infrastructure/kubernetes` is the starting point for a cluster; it still lacks an ingress, TLS,
managed PostgreSQL and the web deployments. One server is enough for the start in Hamburg.
