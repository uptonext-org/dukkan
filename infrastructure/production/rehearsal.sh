#!/bin/sh
# Dress rehearsal before go-live: the real production images and compose.yaml (with
# compose.rehearsal.yaml for localhost, Mailpit and a local restic repository) on this machine,
# then everything a launch day needs, through Caddy over HTTPS:
#
#    1. edge: HSTS, no actuator, no API docs, launch decisions in GET /platform
#    2. first admin: register, confirm the mail, restart with DUKKAN_BOOTSTRAP_ADMIN_EMAIL
#    3. start catalog: docs/launch/start-katalog.csv, preview then import
#    4. merchant: register, confirm, open a market, admin approves, list a catalog product
#    5. customer: register, confirm, cart, pickup slot, order, confirmation mail
#    6. data export (Art. 15/20)
#    7. backup: dump with restore test, encrypted offsite snapshot
#    8. account deletion after the backup, restore from offsite: the order is back, the deleted
#       account stays deleted (erasure journal)
#
#   DUKKAN_IMAGE_PREFIX=ghcr.io/uptonext-org DUKKAN_VERSION=1.0.0-rc.1 sh rehearsal.sh
#
# Needs docker compose, curl, jq and free ports 80, 443 and 8025. Runs in a scratch copy of this
# directory and removes its containers and volumes at the end (KEEP=1 keeps them for a look).
set -eu

: "${DUKKAN_IMAGE_PREFIX:?set DUKKAN_IMAGE_PREFIX}"
: "${DUKKAN_VERSION:?set DUKKAN_VERSION}"
here=$(cd "$(dirname "$0")" && pwd)
catalog="$here/../../docs/launch/start-katalog.csv"
work=$(mktemp -d)
cp "$here/compose.yaml" "$here/compose.rehearsal.yaml" "$here/Caddyfile" "$here"/*.sh "$work/"
cd "$work"
export COMPOSE_FILE=compose.yaml:compose.rehearsal.yaml COMPOSE_PROJECT_NAME=dukkan-rehearsal

step() { printf '\n== %s\n' "$*"; }
fail() { printf 'FAILED: %s\n' "$*" >&2; exit 1; }
cleanup() {
  status=$?
  if [ "$status" != 0 ]; then docker compose logs --tail 80 backend caddy >&2 || true; fi
  if [ "${KEEP:-0}" != 1 ]; then docker compose down -v --remove-orphans >/dev/null 2>&1 || true; rm -rf "$work"; fi
  exit "$status"
}
trap cleanup EXIT

secret() { head -c 48 /dev/urandom | base64 | tr -d '/+=\n' | cut -c1-"$1"; }
admin=admin@rehearsal.test merchant=merchant@rehearsal.test customer=customer@rehearsal.test
password=$(secret 20)
terms=2026-11

cat > .env <<EOF
DUKKAN_IMAGE_PREFIX=$DUKKAN_IMAGE_PREFIX
DUKKAN_VERSION=$DUKKAN_VERSION
DUKKAN_DOMAIN=localhost
DUKKAN_PORTAL_DOMAIN=portal.localhost
DUKKAN_ACME_EMAIL=ops@rehearsal.test
POSTGRES_PASSWORD=$(secret 32)
DUKKAN_JWT_SECRET=$(secret 64)
SPRING_MAIL_HOST=mailpit
SPRING_MAIL_PORT=1025
DUKKAN_MAIL_FROM=Dukkan <no-reply@rehearsal.test>
DUKKAN_IMPRINT_URL=https://localhost/impressum.html
DUKKAN_PRIVACY_URL=https://localhost/datenschutz.html
DUKKAN_TERMS_URL=https://localhost/agb.html
DUKKAN_TERMS_VERSION=$terms
DUKKAN_SUPPORT_EMAIL=support@rehearsal.test
DUKKAN_PAYMENT=ON_SITE
DUKKAN_REQUIRE_VERIFIED_EMAIL=true
DUKKAN_MERCHANT_AUTO_APPROVE=false
DUKKAN_BOOTSTRAP_ADMIN_EMAIL=
RESTIC_REPOSITORY=/repo
RESTIC_PASSWORD=$(secret 32)
EOF
chmod 600 .env

# --- helpers ------------------------------------------------------------------------------
ca=$work/caddy-root.crt
api() { # api METHOD PATH [TOKEN] [JSON] -> body on stdout, status in $work/status
  set -- "$1" "$2" "${3:-}" "${4:-}"
  curl -sS --cacert "$ca" -o "$work/body" -w '%{http_code}' -X "$1" \
    -H 'Accept-Language: de' ${3:+-H "Authorization: Bearer $3"} \
    ${4:+-H 'Content-Type: application/json' --data "$4"} "https://localhost/api/v1$2" > "$work/status"
  cat "$work/body"
}
expect() { # expect STATUS: the last api call answered with it
  [ "$(cat "$work/status")" = "$1" ] || fail "expected HTTP $1, got $(cat "$work/status"): $(head -c 400 "$work/body")"
}
wait_backend() {
  for _ in $(seq 1 90); do
    [ "$(docker inspect -f '{{.State.Health.Status}}' "$(docker compose ps -q backend)" 2>/dev/null)" = healthy ] && return 0
    sleep 2
  done
  fail "backend not healthy"
}
register() { # register EMAIL TYPE
  api POST /auth/register "" "$(jq -nc --arg e "$1" --arg p "$password" --arg t "$2" --arg v "$terms" \
    '{email:$e,password:$p,firstName:"Probe",lastName:"Lauf",accountType:$t,preferredLocale:"de",acceptedTermsVersion:$v}')" >/dev/null
  expect 201
}
mail_text() { # mail_text EMAIL SUBJECT-WORDS -> text of the newest matching mail
  for _ in $(seq 1 30); do
    id=$(curl -sS "http://127.0.0.1:8025/api/v1/search?query=$(jq -rn --arg q "to:$1 $2" '$q|@uri')" | jq -r '.messages[0].ID // empty')
    [ -n "$id" ] && { curl -sS "http://127.0.0.1:8025/api/v1/message/$id" | jq -r .Text; return 0; }
    sleep 1
  done
  fail "no mail to $1 ($2)"
}
wait_mail() { # wait_mail EMAIL TEXT: a mail to EMAIL containing TEXT arrives (mails go out after the commit)
  for _ in $(seq 1 60); do
    for id in $(curl -sS "http://127.0.0.1:8025/api/v1/search?query=$(jq -rn --arg q "to:$1" '$q|@uri')" | jq -r '.messages[].ID'); do
      curl -sS "http://127.0.0.1:8025/api/v1/message/$id" | jq -r '.Subject + "\n" + .Text' | grep -q "$2" && return 0
    done
    sleep 1
  done
  return 1
}
confirm() { # confirm EMAIL: opens the link of the welcome mail
  token=$(mail_text "$1" "" | grep -o 'verify-email?token=[A-Za-z0-9_-]*' | head -1 | cut -d= -f2)
  [ -n "$token" ] || fail "no confirmation link for $1"
  api POST /auth/verify-email "" "$(jq -nc --arg t "$token" '{token:$t}')" >/dev/null
  expect 204
}
login() { # login EMAIL -> access token
  api POST /auth/login "" "$(jq -nc --arg e "$1" --arg p "$password" '{email:$e,password:$p}')" | jq -r .accessToken
}
psql_q() { docker compose exec -T postgres psql -U dukkan -d dukkan -tAc "$1"; }

# --- start --------------------------------------------------------------------------------
step "Start the stack ($DUKKAN_IMAGE_PREFIX, $DUKKAN_VERSION)"
docker compose up -d --quiet-pull
wait_backend
for _ in $(seq 1 30); do docker compose cp caddy:/data/caddy/pki/authorities/local/root.crt "$ca" >/dev/null 2>&1 && break; sleep 1; done
[ -s "$ca" ] || fail "Caddy has no local CA"
for _ in $(seq 1 30); do curl -sf --cacert "$ca" -o /dev/null https://localhost/ && break; sleep 1; done

step "1. Edge: HTTPS, headers, closed internals, launch decisions"
curl -sfI --cacert "$ca" https://localhost/ | grep -qi '^strict-transport-security: max-age=31536000' || fail "no HSTS"
curl -sfI --cacert "$ca" https://localhost/ | grep -qi "^content-security-policy:" || fail "no CSP on the web app"
curl -sf --cacert "$ca" https://portal.localhost/ | grep -q '<div id="root">' || fail "portal not served"
for path in /actuator/health /actuator/prometheus; do
  code=$(curl -s --cacert "$ca" -o /dev/null -w '%{http_code}' "https://localhost$path")
  [ "$code" = 404 ] || fail "$path answers $code"
done
# Through the API route: the backend's public port has no actuator (it runs on 8081), so the
# request ends in Spring Security or a 404, never in actuator data.
code=$(curl -s --path-as-is --cacert "$ca" -o "$work/body" -w '%{http_code}' "https://localhost/api/v1/../../actuator/env")
case "$code" in 401|403|404) ;; *) fail "/api/v1/../../actuator/env answers $code: $(head -c 200 "$work/body")" ;; esac
for path in /api/v3/api-docs /api/swagger-ui.html /api/v1/v3/api-docs; do
  code=$(curl -s --cacert "$ca" -o /dev/null -w '%{http_code}' "https://localhost$path")
  case "$code" in 401|403|404) ;; *) fail "$path answers $code" ;; esac
done
code=$(curl -s -o /dev/null -w '%{http_code}' http://localhost/)
case "$code" in 301|302|307|308) ;; *) fail "plain http answers $code instead of a redirect" ;; esac
platform=$(api GET /platform)
expect 200
[ "$(echo "$platform" | jq -r .payment)" = ON_SITE ] || fail "payment is not ON_SITE: $platform"
[ "$(echo "$platform" | jq -r .legal.termsVersion)" = "$terms" ] || fail "terms version missing: $platform"
api GET /markets/00000000-0000-0000-0000-000000000000 > "$work/error"
expect 404
grep -qiE 'exception|stacktrace|at de\.dukkan|org\.springframework' "$work/error" && fail "error answer shows internals"
echo ok

step "2. First admin"
register "$admin" CUSTOMER
confirm "$admin"
sed -i "s/^DUKKAN_BOOTSTRAP_ADMIN_EMAIL=.*/DUKKAN_BOOTSTRAP_ADMIN_EMAIL=$admin/" .env
docker compose up -d backend
wait_backend
docker compose logs backend | grep -q 'Bootstrap admin: user .* has the ADMIN role' || fail "bootstrap admin not granted"
sed -i "s/^DUKKAN_BOOTSTRAP_ADMIN_EMAIL=.*/DUKKAN_BOOTSTRAP_ADMIN_EMAIL=/" .env
admin_token=$(login "$admin")
api GET /admin/markets "$admin_token" >/dev/null
expect 200
echo ok

step "3. Start catalog"
import() { # import DRYRUN
  curl -sS --cacert "$ca" -o "$work/body" -w '%{http_code}' -H "Authorization: Bearer $admin_token" \
    -F "file=@$catalog;type=text/csv" "https://localhost/api/v1/admin/catalog/import?dryRun=$1" > "$work/status"
  cat "$work/body"
}
preview=$(import true)
expect 200
rows=$(echo "$preview" | jq '.rows|length')
[ "$(echo "$preview" | jq .failed)" = 0 ] || fail "start list has errors: $(echo "$preview" | jq -c '[.rows[]|select(.action=="ERROR")][:3]')"
result=$(import false)
expect 200
[ "$(echo "$result" | jq .created)" = "$rows" ] || fail "import created $(echo "$result" | jq .created) of $rows"
echo "ok: $rows products"

step "4. Merchant and market"
register "$merchant" MERCHANT
merchant_token=$(login "$merchant")
market=$(jq -nc '{name:"Probemarkt",typeCode:"GROCERY",cuisineCodes:["TURKISH"],description:{de:"Generalprobe"},
  primaryLocale:"de",addressLine:"Probestraße 1, 20095 Hamburg",location:{lat:53.5511,lng:9.9937},tags:[],
  fulfilment:{delivery:false,pickup:true,deliveryRadiusKm:3.0,deliveryFee:{amountCents:290,currency:"EUR"},
    freeDeliveryFrom:{amountCents:4000,currency:"EUR"},minimumOrderValue:{amountCents:100,currency:"EUR"}},
  openingHours:[range(1;8)|{dayOfWeek:.,opensAt:"00:00",closesAt:"23:59"}],
  phone:"+49 40 1234567",legalName:"Probe Lauf e.K."}')
api POST /merchant/markets "$merchant_token" "$market" >/dev/null
expect 422 # opening a market needs a confirmed address
confirm "$merchant"
market_id=$(api POST /merchant/markets "$merchant_token" "$market" | jq -r .id)
expect 201
api PUT "/merchant/markets/$market_id/order-settings" "$merchant_token" \
  '{"ordersEnabled":true,"deliveryHours":[],"autoAccept":false,"paymentMethods":["CASH"]}' >/dev/null
expect 200
api GET "/markets/$market_id" >/dev/null
expect 404 # not visible before the review
api POST "/admin/markets/$market_id/status" "$admin_token" '{"status":"ACTIVE"}' >/dev/null
expect 200
product=$(api GET "/merchant/catalog?q=Linsen&marketId=$market_id" "$merchant_token" | jq -r '.items[0].id // empty')
expect 200
[ -n "$product" ] || fail "start catalog product not found"
listing=$(api POST "/merchant/markets/$market_id/listings" "$merchant_token" \
  "$(jq -nc --arg p "$product" '{catalogProductId:$p,price:{amountCents:199,currency:"EUR"}}')" | jq -r .id)
expect 201
echo ok

step "5. Customer order"
register "$customer" CUSTOMER
customer_token=$(login "$customer")
api PUT "/markets/$market_id/cart/items/$listing" "$customer_token" '{"quantity":2}' >/dev/null
expect 200
slot=$(api GET "/markets/$market_id/slots?type=PICKUP" | jq -r '[.[]|select(.available)][0].id // empty')
[ -n "$slot" ] || fail "no pickup slot"
api PUT "/markets/$market_id/cart/fulfilment" "$customer_token" "$(jq -nc --arg s "$slot" '{type:"PICKUP",slotId:$s}')" >/dev/null
expect 200
api POST "/markets/$market_id/orders" "$customer_token" >/dev/null
expect 422 # ordering needs a confirmed address
confirm "$customer"
order=$(api POST "/markets/$market_id/orders" "$customer_token" | jq -r .id)
expect 201
wait_mail "$customer" "Probemarkt" || fail "no order confirmation mail"
api GET "/merchant/markets/$market_id/orders" "$merchant_token" | jq -e --arg o "$order" 'tostring|contains($o)' >/dev/null \
  || fail "merchant does not see the order"
echo "ok: order $order"

step "6. Data export"
api GET /me/export "$customer_token" | jq -e --arg o "$order" --arg e "$customer" 'tostring|contains($o) and contains($e)' >/dev/null \
  || fail "export lacks the order or the address"
expect 200
echo ok

step "7. Backup, restore test, offsite snapshot"
docker compose exec -T -e RESTORE_TEST_WEEKDAY="$(date +%u)" backup sh -c 'timeout 60 sh /usr/local/bin/dukkan-backup.sh --now || true' > backup.log
grep -q 'restore test ok' backup.log || { cat backup.log; fail "backup"; }
docker compose exec -T -e RESTORE_TEST_WEEKDAY="$(date +%u)" offsite sh -c 'timeout 120 sh /usr/local/bin/dukkan-offsite.sh --now || true' > offsite.log
grep -q 'offsite ok' offsite.log || { cat offsite.log; fail "offsite"; }
echo ok

step "8. Deletion after the backup, restore from offsite"
customer_id=$(api GET /me "$customer_token" | jq -r .id)
api DELETE /me "$customer_token" "$(jq -nc --arg p "$password" '{password:$p}')" >/dev/null
expect 204
echo RESTORE | sh ./restore.sh offsite latest --with-media
wait_backend
[ "$(psql_q "select count(*) from customer_order where id = '$order'")" = 1 ] || fail "order not restored"
[ "$(psql_q "select deleted_at is not null from app_user where id = '$customer_id'")" = t ] || fail "deleted account came back"
api POST /auth/login "" "$(jq -nc --arg e "$customer" --arg p "$password" '{email:$e,password:$p}')" >/dev/null
expect 401
docker compose logs backend | grep -q 'Erasure journal: erased 1 account' || fail "no erasure replay in the log"
[ "$(login "$merchant")" != null ] || fail "merchant cannot sign in after the restore"
echo ok

printf '\nRehearsal passed.\n'
