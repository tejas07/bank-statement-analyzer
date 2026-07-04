# syntax=docker/dockerfile:1
# ── Stage 1: Build ──────────────────────────────────────────────────────────
# Multi-module reactor (Phase 2): bank-common, parser-module, report-module,
# and analysis-module are still all packaged into ONE runnable app by
# gateway-module — this remains a single deployable, not yet split services.
FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /app

COPY pom.xml .
COPY bank-common/pom.xml bank-common/pom.xml
COPY parser-module/pom.xml parser-module/pom.xml
COPY report-module/pom.xml report-module/pom.xml
COPY analysis-module/pom.xml analysis-module/pom.xml
COPY gateway-module/pom.xml gateway-module/pom.xml

# BuildKit cache mount: ~/.m2 is reused across ALL builds on this machine.
# Maven never re-downloads a jar it already fetched, even if pom.xml changes.
RUN --mount=type=cache,target=/root/.m2 \
    mvn dependency:go-offline -q

COPY bank-common/src bank-common/src
COPY parser-module/src parser-module/src
COPY report-module/src report-module/src
COPY analysis-module/src analysis-module/src
COPY gateway-module/src gateway-module/src

RUN --mount=type=cache,target=/root/.m2 \
    mvn clean install -DskipTests -q

# ── Stage 2: Runtime ─────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

COPY --from=build /app/gateway-module/target/bank-statement-analyzer-*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
