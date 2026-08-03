FROM node:22-alpine AS admin-build
WORKDIR /workspace/apps/admin
COPY apps/admin/package.json apps/admin/package-lock.json ./
RUN npm ci
COPY apps/admin/ ./
RUN npm run build

FROM nginxinc/nginx-unprivileged:1.29-alpine
LABEL se.medbo.gateway.runtime-mode="hosted-https-v1"
USER root
RUN apk upgrade --no-cache && apk add --no-cache su-exec && \
    sed -i \
      -e 's#^error_log .*#error_log /tmp/nginx-error.log notice;#' \
      -e 's#^[[:space:]]*access_log .*#    access_log /tmp/nginx-access.log main;#' \
      /etc/nginx/nginx.conf
COPY --chmod=0755 infrastructure/gateway/hosted-entrypoint.sh /usr/local/bin/hosted-entrypoint
COPY infrastructure/gateway/default.conf.template /etc/nginx/templates/default.conf.template
COPY infrastructure/gateway/hosted-tls.conf infrastructure/gateway/hosted-locations.conf /etc/nginx/snippets/
COPY --from=admin-build --chown=101:101 /workspace/apps/admin/dist/ /usr/share/nginx/html/
USER 0:101
HEALTHCHECK --interval=15s --timeout=3s --start-period=10s --retries=3 \
  CMD test "$(wget -q -O - http://127.0.0.1:8080/healthz)" = '{"status":"UP","mode":"hosted-https"}' && \
    test "$(wget --no-check-certificate -q -O - https://127.0.0.1:8443/healthz)" = '{"status":"UP","mode":"hosted-https"}'
EXPOSE 8080 8443
ENTRYPOINT ["/usr/local/bin/hosted-entrypoint"]
CMD ["nginx", "-g", "daemon off;"]
