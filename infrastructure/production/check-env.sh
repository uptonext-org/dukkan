#!/bin/sh
# Checks a production .env before the first start (docs/launch/go-live.md, phase 2):
#   sh check-env.sh            # reads ./.env
#   sh check-env.sh other.env
# Prints every problem and exits 1 if there is one. It reads the file only; nothing is sent.
# The backend checks a few things again at startup (ProductionReadinessCheck).
set -u

FILE="${1:-.env}"
if [ ! -f "$FILE" ]; then
  echo "No $FILE: cp .env.example .env and fill it in." >&2
  exit 1
fi

problems=0
warnings=0
problem() { echo "PROBLEM  $1"; problems=$((problems + 1)); }
warn() { echo "WARNING  $1"; warnings=$((warnings + 1)); }

# The last assignment of a key, without surrounding quotes.
value() {
  sed -n "s/^[[:space:]]*$1[[:space:]]*=[[:space:]]*//p" "$FILE" | tail -n 1 | sed -e 's/[[:space:]]*$//' -e 's/^"\(.*\)"$/\1/' -e "s/^'\(.*\)'\$/\1/"
}

required() {
  for key in "$@"; do
    [ -n "$(value "$key")" ] || problem "$key is empty"
  done
}

https_url() {
  v="$(value "$1")"
  case "$v" in
    "") problem "$1 is empty (required: $2)" ;;
    https://*) ;;
    *) problem "$1 must start with https:// ($v)" ;;
  esac
}

secret() {
  v="$(value "$1")"
  [ -n "$v" ] || return 0
  case "$v" in
    *change-me* | *changeme* | *example* | dukkan | password | secret | local-dev-*) problem "$1 is a placeholder; generate one: $3" ;;
  esac
  if [ "${#v}" -lt "$2" ]; then
    problem "$1 is too short (${#v} characters, at least $2): $3"
  fi
}

domain() {
  v="$(value "$1")"
  case "$v" in
    "") ;;
    *://* | */*) problem "$1 must be a bare domain without https:// or a path ($v)" ;;
    localhost | *.localhost | *.local | *.test | *.example | example.* | *.example.*) problem "$1 is not a public domain ($v)" ;;
    *.*) ;;
    *) problem "$1 does not look like a domain ($v)" ;;
  esac
}

# --- release and domains
required DUKKAN_IMAGE_PREFIX DUKKAN_VERSION DUKKAN_DOMAIN DUKKAN_PORTAL_DOMAIN DUKKAN_ACME_EMAIL
case "$(value DUKKAN_VERSION)" in
  "" | latest) [ -z "$(value DUKKAN_VERSION)" ] || problem "DUKKAN_VERSION must be a release, e.g. 1.0.0, not latest" ;;
  v*) problem "DUKKAN_VERSION without the leading v, e.g. 1.0.0" ;;
esac
domain DUKKAN_DOMAIN
domain DUKKAN_PORTAL_DOMAIN
[ "$(value DUKKAN_DOMAIN)" != "$(value DUKKAN_PORTAL_DOMAIN)" ] || [ -z "$(value DUKKAN_DOMAIN)" ] || problem "DUKKAN_DOMAIN and DUKKAN_PORTAL_DOMAIN must differ"

# --- secrets
required POSTGRES_PASSWORD DUKKAN_JWT_SECRET RESTIC_PASSWORD
secret POSTGRES_PASSWORD 24 "openssl rand -base64 32"
secret DUKKAN_JWT_SECRET 48 "openssl rand -base64 48"
secret RESTIC_PASSWORD 24 "openssl rand -base64 32"

# --- mail
required SPRING_MAIL_HOST SPRING_MAIL_USERNAME SPRING_MAIL_PASSWORD DUKKAN_MAIL_FROM
case "$(value DUKKAN_MAIL_FROM)" in
  *example.org* | *example.com* | *dukkan.app*) problem "DUKKAN_MAIL_FROM must be an address of your own domain (SPF/DKIM)" ;;
esac
case "$(value SPRING_MAIL_PORT)" in
  "" | 587 | 465) ;;
  25) warn "SPRING_MAIL_PORT 25: most providers expect 587 (STARTTLS)" ;;
esac

# --- legal and launch decisions (go-live.md 3.1)
https_url DUKKAN_IMPRINT_URL "Impressum"
https_url DUKKAN_PRIVACY_URL "Datenschutzerklärung"
https_url DUKKAN_TERMS_URL "AGB"
required DUKKAN_TERMS_VERSION DUKKAN_SUPPORT_EMAIL
[ "$(value DUKKAN_PAYMENT)" = "ON_SITE" ] || problem "DUKKAN_PAYMENT must be ON_SITE (decision: customers pay the market)"
case "$(value DUKKAN_REQUIRE_VERIFIED_EMAIL)" in
  "" | true) ;;
  *) problem "DUKKAN_REQUIRE_VERIFIED_EMAIL must be true (decision: confirmed e-mail before ordering)" ;;
esac
case "$(value DUKKAN_MERCHANT_AUTO_APPROVE)" in
  "" | false) ;;
  *) problem "DUKKAN_MERCHANT_AUTO_APPROVE must be false: markets wait for an admin's review" ;;
esac
[ -z "$(value DUKKAN_ANTHROPIC_API_KEY)" ] || warn "DUKKAN_ANTHROPIC_API_KEY is set: AI functions are on (decision: off at launch; DPA with Anthropic and privacy notice needed)"
[ -n "$(value DUKKAN_BOOTSTRAP_ADMIN_EMAIL)" ] || warn "DUKKAN_BOOTSTRAP_ADMIN_EMAIL is empty: no admin can review markets until one is set"

# --- map tiles (the OpenStreetMap servers are not for production)
if [ -z "$(value DUKKAN_MAP_TILE_URL)" ]; then
  warn "DUKKAN_MAP_TILE_URL is empty: the web app uses the OpenStreetMap servers, which are not meant for production load"
elif [ -z "$(value DUKKAN_MAP_ATTRIBUTION)" ]; then
  problem "DUKKAN_MAP_ATTRIBUTION is empty: the tile provider's attribution is required"
fi

# --- backups
required RESTIC_REPOSITORY
case "$(value RESTIC_REPOSITORY)" in
  "") ;;
  /* | local:*) problem "RESTIC_REPOSITORY is on this server; it must be storage elsewhere (an S3 bucket in the EU)" ;;
  s3:*)
    if [ -z "$(value RESTIC_S3_ACCESS_KEY)" ] || [ -z "$(value RESTIC_S3_SECRET_KEY)" ]; then
      problem "RESTIC_S3_ACCESS_KEY and RESTIC_S3_SECRET_KEY are needed for an S3 repository"
    fi
    ;;
esac
[ -n "$(value OFFSITE_HEALTHCHECK_URL)" ] || warn "OFFSITE_HEALTHCHECK_URL is empty: nobody is alerted when a nightly backup fails"

# --- file hygiene
if grep -q "^[[:space:]]*SPRING_PROFILES_ACTIVE" "$FILE"; then
  problem "SPRING_PROFILES_ACTIVE must not be set in .env (compose sets prod; local/demo create demo accounts)"
fi
if [ -n "$(find "$FILE" -perm /077 2>/dev/null)" ]; then
  warn "$FILE is readable by other users on this server: chmod 600 $FILE"
fi

echo
if [ "$problems" -gt 0 ]; then
  echo "$problems problem(s), $warnings warning(s). Fix the problems before docker compose up."
  exit 1
fi
echo "No problems, $warnings warning(s)."
