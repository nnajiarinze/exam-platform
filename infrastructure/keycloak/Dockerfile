FROM alpine:3.22.1 AS apple-provider

ARG APPLE_IDP_VERSION=1.17.0
ARG APPLE_IDP_SHA256=4091dee2a1ec9e0771bef4bd46005197d86b0a2b1f25198c41738476b1d102bb
RUN wget -q -O /apple-identity-provider.jar \
      "https://github.com/klausbetz/apple-identity-provider-keycloak/releases/download/${APPLE_IDP_VERSION}/apple-identity-provider-${APPLE_IDP_VERSION}.jar" \
    && echo "${APPLE_IDP_SHA256}  /apple-identity-provider.jar" | sha256sum -c -

FROM quay.io/keycloak/keycloak:26.7.0 AS builder

ENV KC_DB=postgres
ENV KC_HEALTH_ENABLED=true
ENV KC_HTTP_RELATIVE_PATH=/auth
COPY --from=apple-provider --chown=1000:0 /apple-identity-provider.jar /opt/keycloak/providers/apple-identity-provider-1.17.0.jar
RUN /opt/keycloak/bin/kc.sh build

FROM quay.io/keycloak/keycloak:26.7.0

COPY --from=builder /opt/keycloak/ /opt/keycloak/
COPY --chown=1000:0 infrastructure/keycloak/themes/svea-study/ /opt/keycloak/themes/svea-study/

USER 1000
ENTRYPOINT ["/opt/keycloak/bin/kc.sh"]
CMD ["start", "--optimized", "--cache=local", "--http-enabled=true", "--http-host=0.0.0.0", "--proxy-headers=xforwarded"]
