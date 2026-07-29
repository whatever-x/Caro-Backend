# ===== Build stage =====
FROM eclipse-temurin:25-jdk AS builder
WORKDIR /workspace
ENV GRADLE_USER_HOME=/workspace/.gradlehome

# Cache dependencies first
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
COPY docker/resolve-deps.init.gradle.kts ./
RUN chmod +x ./gradlew

RUN ./gradlew --no-daemon \
    -I /workspace/resolve-deps.init.gradle.kts resolveAllDependencies

ARG RELEASE_VERSION=0.0.1-SNAPSHOT
ARG GIT_COMMIT=unknown
ARG BUILD_TIME=unknown

COPY src ./src
RUN ./gradlew --no-daemon bootJar -x test \
    -Pversion=$RELEASE_VERSION -PgitCommit=$GIT_COMMIT -PbuildTime=$BUILD_TIME \
 && grep -q "^build.version=$RELEASE_VERSION$" build/resources/main/META-INF/build-info.properties

RUN java -Djarmode=tools -jar build/libs/app.jar \
        extract --layers --destination application

# ===== Runtime stage =====
FROM eclipse-temurin:25-jre

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

ENV TZ=UTC
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom"
#ENV SPRING_PROFILES_ACTIVE=staging
#ENV INFISICAL_ENV=staging

EXPOSE 8080 9090
STOPSIGNAL SIGTERM

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl -fsS http://localhost:9090/actuator/health || exit 1

ENTRYPOINT ["/app/entrypoint.sh"]
