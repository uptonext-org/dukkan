# syntax=docker/dockerfile:1
# Dukkan for the browser: static files served by nginx (unprivileged, port 8080).
# Build from the repository root, because the app code lives in apps/mobile:
#   docker build -f apps/customer-web/Dockerfile -t dukkan-customer-web .

FROM node:26.12.0-alpine3.24@sha256:6f08a523443ddedf52941874c203475b0ad503e859ad70cb26ab8be9a517dbd5 AS build
WORKDIR /repo
COPY apps/mobile/package.json apps/mobile/package-lock.json apps/mobile/
COPY apps/mobile/modules apps/mobile/modules
# The app's native dependencies are only needed as JavaScript here; skip their install scripts.
RUN cd apps/mobile && npm ci --ignore-scripts --no-audit --no-fund
COPY apps/customer-web/package.json apps/customer-web/package-lock.json apps/customer-web/
RUN cd apps/customer-web && PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1 npm ci --ignore-scripts --no-audit --no-fund
COPY apps/mobile/app.json apps/mobile/index.js apps/mobile/
COPY apps/mobile/assets apps/mobile/assets
COPY apps/mobile/src apps/mobile/src
COPY apps/customer-web apps/customer-web
RUN cd apps/customer-web && npm run build

FROM nginxinc/nginx-unprivileged:1.30.6-alpine@sha256:f5116ed4606f3eb015dd4a8e28cb9b351476dc0ad4bc6e9f93b19cec130cdf03
# Runtime settings (see deploy/40-dukkan-config.sh and src/config.ts); empty = built-in default.
ENV DUKKAN_API_BASE_URL="" \
    DUKKAN_MAP_TILE_URL="" \
    DUKKAN_MAP_ATTRIBUTION="" \
    DUKKAN_CSP_CONNECT_SRC="'self'" \
    NGINX_ENVSUBST_OUTPUT_DIR=/tmp
COPY apps/customer-web/deploy/nginx.conf /etc/nginx/nginx.conf
COPY apps/customer-web/deploy/default.conf.template /etc/nginx/templates/default.conf.template
COPY --chmod=0755 apps/customer-web/deploy/40-dukkan-config.sh /docker-entrypoint.d/40-dukkan-config.sh
COPY --from=build /repo/apps/customer-web/dist /usr/share/nginx/html
EXPOSE 8080
