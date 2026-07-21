FROM quay.io/keycloak/keycloak:26.3

ENTRYPOINT ["/opt/keycloak/bin/kc.sh"]
CMD ["start", "--http-enabled=true", "--http-host=0.0.0.0", "--http-relative-path=/auth", "--proxy-headers=xforwarded", "--health-enabled=true"]
