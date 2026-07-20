ARG RUNTIME_IMAGE=eclipse-temurin:21-jre
FROM ${RUNTIME_IMAGE}
WORKDIR /app
COPY build/libs/content-service-*.jar app.jar
USER 65532:65532
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
