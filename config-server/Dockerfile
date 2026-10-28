# =============================================================
# config-server — multi-stage build
# Build context is the repository root (docker build -f config-server/Dockerfile .)
# =============================================================

# ---------- Stage 1: build (Maven + Java 21) ----------
FROM maven:3.9.11-eclipse-temurin-21 AS build

WORKDIR /workspace

# Copy only what config-server needs to compile:
# the parent POM (config-server resolves it via <relativePath>../pom.xml</relativePath>)
# and the config-server module sources. Other modules never enter the image.
COPY pom.xml .
COPY config-server/pom.xml config-server/pom.xml
COPY config-server/src config-server/src

# Repackage explicitly: the Spring Boot 4.x maven plugin no longer auto-binds
# the repackage goal to the package phase from a bare plugin declaration.
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -f config-server/pom.xml -DskipTests package spring-boot:repackage

# ---------- Stage 2: runtime (Java 21 JRE, non-root) ----------
FROM eclipse-temurin:21-jre-jammy

# Non-root runtime user (dedicated system account, no shell)
RUN useradd --system --uid 10001 --create-home appuser

WORKDIR /app

# Copy only the repackaged (fat) jar produced by spring-boot-maven-plugin.
# The glob does not match the .jar.original artifact left by the plugin.
COPY --chown=appuser:appuser --from=build /workspace/config-server/target/config-server-*.jar /app/app.jar

USER appuser

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
