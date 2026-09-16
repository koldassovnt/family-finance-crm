# Builds the application jar inside the image, so `docker compose up --build`
# needs nothing but Docker on the host. Lint and tests are not run here —
# `./gradlew build` is the gate for those.
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

# Wrapper and build scripts first, so the dependency layer is only rebuilt
# when they change rather than on every source edit.
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle gradle
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon dependencies > /dev/null

COPY src src
# bootJar alone never runs the plain `jar` task, so build/libs holds one file.
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon bootJar \
    && cp build/libs/*.jar app.jar

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S app && adduser -S -G app app
COPY --from=build /workspace/app.jar app.jar
USER app

EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=5s --start-period=60s --retries=5 \
    CMD wget -qO- http://localhost:8080/actuator/health > /dev/null || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
