# syntax=docker/dockerfile:1
# ── Stage 1: Build ──────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /app

COPY pom.xml .

# BuildKit cache mount: ~/.m2 is reused across ALL builds on this machine.
# Maven never re-downloads a jar it already fetched, even if pom.xml changes.
RUN --mount=type=cache,target=/root/.m2 \
    mvn dependency:go-offline -q

COPY src src

RUN --mount=type=cache,target=/root/.m2 \
    mvn clean package -DskipTests -q

# ── Stage 2: Runtime ─────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

COPY --from=build /app/target/bank-statement-analyzer-*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
