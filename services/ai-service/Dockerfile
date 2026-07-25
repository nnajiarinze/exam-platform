FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY . .
RUN ./gradlew :services:ai-service:bootJar --no-daemon

FROM eclipse-temurin:21-jre-alpine
RUN apk upgrade --no-cache && apk add --no-cache curl
WORKDIR /app
COPY --from=build /workspace/services/ai-service/build/libs/ai-service-*.jar app.jar
USER 65532:65532
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --start-period=45s --retries=5 \
  CMD curl --fail --silent --show-error http://127.0.0.1:8080/actuator/health/readiness >/dev/null || exit 1
ENTRYPOINT ["java","-jar","/app/app.jar"]
