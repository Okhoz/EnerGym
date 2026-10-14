# Imágenes oficiales fijadas por digest desde configuración privada del operador.
ARG BUILD_IMAGE
ARG RUNTIME_IMAGE
FROM ${BUILD_IMAGE} AS build
ARG BUILD_IMAGE
ARG RUNTIME_IMAGE
ARG MAVEN_DISTRIBUTION_SHA256
WORKDIR /build
COPY pom.xml mvnw ./
COPY .mvn .mvn
COPY src src
RUN case "$BUILD_IMAGE" in eclipse-temurin:17.*-jdk-jammy@sha256:*) ;; *) exit 1 ;; esac && \
	case "$RUNTIME_IMAGE" in eclipse-temurin:17.*-jre-jammy@sha256:*) ;; *) exit 1 ;; esac && \
	test "${#MAVEN_DISTRIBUTION_SHA256}" -eq 64 && \
	case "$MAVEN_DISTRIBUTION_SHA256" in *[!0-9a-f]*) exit 1 ;; esac && \
	printf '\ndistributionSha256Sum=%s\n' "$MAVEN_DISTRIBUTION_SHA256" >> .mvn/wrapper/maven-wrapper.properties && \
	chmod 0555 mvnw && ./mvnw -B -ntp -Dproject.build.outputTimestamp=2026-10-14T00:00:00Z verify

FROM ${RUNTIME_IMAGE} AS runtime
WORKDIR /app
RUN groupadd --gid 10001 energym && useradd --uid 10001 --gid 10001 --no-create-home --shell /usr/sbin/nologin energym
COPY --from=build --chown=10001:10001 /build/target/energym-backend-1.0.0.jar /app/backend.jar
COPY --chown=10001:10001 deploy/entrypoint.sh /app/entrypoint.sh
RUN chmod 0555 /app/entrypoint.sh && chmod 0444 /app/backend.jar
USER 10001:10001
ENV ENERGYM_HOST=127.0.0.1 ENERGYM_PORT=8080 ENERGYM_DEV_MODE=true \
	ENERGYM_AI_PROVIDER=none GEMINI_ENABLED=false OPENAI_ENABLED=false
HEALTHCHECK --interval=30s --timeout=10s --start-period=30s --retries=3 \
	CMD ["java", "-Xmx64m", "-cp", "/app/backend.jar", "com.energym.server.Healthcheck"]
ENTRYPOINT ["/bin/sh", "/app/entrypoint.sh"]
