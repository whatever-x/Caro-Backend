# ===== Build stage =====
FROM eclipse-temurin:24-jdk AS builder
WORKDIR /workspace

# Cache dependencies first
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
RUN chmod +x ./gradlew

COPY src ./src
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon bootJar -x test \
 && mv build/libs/caro-*-SNAPSHOT.jar build/libs/app.jar

RUN java -Djarmode=tools -jar build/libs/app.jar \
        extract --layers --destination application

# ===== Runtime stage =====
FROM eclipse-temurin:24-jre

# Infisical CLI install
RUN apt-get update \
    && apt-get install -y --no-install-recommends bash curl ca-certificates \
    && curl -1sLf 'https://artifacts-cli.infisical.com/setup.deb.sh' | bash \
    && apt-get update \
    && apt-get install -y --no-install-recommends infisical \
    && rm -rf /var/lib/apt/lists/*

RUN groupadd --system app && useradd --system --gid app --home-dir /app app
WORKDIR /app

COPY --from=builder --chown=app:app /workspace/application/dependencies/            ./
COPY --from=builder --chown=app:app /workspace/application/spring-boot-loader/      ./
COPY --from=builder --chown=app:app /workspace/application/snapshot-dependencies/   ./
COPY --from=builder --chown=app:app /workspace/application/application/             ./

COPY --chown=app:app docker/entrypoint.sh /app/entrypoint.sh
RUN chmod +x /app/entrypoint.sh

USER app

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom"
#ENV SPRING_PROFILES_ACTIVE=staging
#ENV INFISICAL_ENV=staging

EXPOSE 8080 9090
STOPSIGNAL SIGTERM

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl -fsS http://localhost:9090/actuator/health || exit 1

ENTRYPOINT ["/app/entrypoint.sh"]
