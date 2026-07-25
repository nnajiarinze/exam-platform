FROM quay.io/keycloak/keycloak:26.7.0 AS builder

ENV KC_DB=postgres
ENV KC_HEALTH_ENABLED=true
ENV KC_HTTP_RELATIVE_PATH=/auth
RUN /opt/keycloak/bin/kc.sh build

FROM quay.io/keycloak/keycloak:26.7.0

COPY --from=builder /opt/keycloak/ /opt/keycloak/
COPY infrastructure/keycloak/exam-platform-realm.json /opt/keycloak/data/import/exam-platform-realm.json

ENTRYPOINT ["/opt/keycloak/bin/kc.sh"]
CMD ["start", "--optimized", "--cache=local", "--http-enabled=true", "--http-host=0.0.0.0", "--proxy-headers=xforwarded", "--import-realm"]
