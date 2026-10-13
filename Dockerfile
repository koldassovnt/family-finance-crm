FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S app && adduser -S -G app app
COPY build/libs/app.jar app.jar

# The tag compose gives this image. Refuse to build when the jar is another
# version, so a stale jar is never packaged under a new or an older tag.
ARG VERSION
RUN unzip -p app.jar META-INF/MANIFEST.MF | tr -d '\r' \
        | grep -qx "Implementation-Version: ${VERSION}" \
    || { echo "build/libs/app.jar is not version ${VERSION}: run ./gradlew bootJar" >&2; exit 1; }
LABEL org.opencontainers.image.version="${VERSION}"
USER app

EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=5s --start-period=60s --retries=5 \
    CMD wget -qO- http://localhost:8080/actuator/health > /dev/null || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
