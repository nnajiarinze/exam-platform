FROM quay.io/keycloak/keycloak:26.3 AS builder

ENV KC_DB=postgres
ENV KC_HEALTH_ENABLED=true
RUN /opt/keycloak/bin/kc.sh build

FROM quay.io/keycloak/keycloak:26.3

COPY --from=builder /opt/keycloak/ /opt/keycloak/

ENTRYPOINT ["/opt/keycloak/bin/kc.sh"]
CMD ["start", "--optimized", "--http-enabled=true", "--http-host=0.0.0.0", "--http-relative-path=/auth", "--proxy-headers=xforwarded"]
