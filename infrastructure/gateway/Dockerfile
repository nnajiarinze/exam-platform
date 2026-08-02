FROM node:22-alpine AS admin-build
WORKDIR /workspace/apps/admin
COPY apps/admin/package.json apps/admin/package-lock.json ./
RUN npm ci
COPY apps/admin/ ./
RUN npm run build

FROM nginxinc/nginx-unprivileged:1.29-alpine
USER root
RUN apk upgrade --no-cache
USER 101:101
COPY infrastructure/gateway/default.conf.template /etc/nginx/templates/default.conf.template
COPY --from=admin-build --chown=101:101 /workspace/apps/admin/dist/ /usr/share/nginx/html/
HEALTHCHECK --interval=15s --timeout=3s --start-period=10s --retries=3 \
  CMD wget -q -O /dev/null http://127.0.0.1:8080/healthz || exit 1
EXPOSE 8080
