FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY . .
RUN ./gradlew :services:ai-service:bootJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/services/ai-service/build/libs/ai-service-*.jar app.jar
USER 65532:65532
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
