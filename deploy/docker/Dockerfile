# Copyright (c) 2026 devlive-community/grantforge
#
# Licensed under the MIT License. See the LICENSE file in the
# project root for full license text.

# The GrantForge server: the release archive on a Java runtime. The database runs apart (GRANTFORGE_DB_URL); without
# one the server keeps an embedded H2 database in the data volume. Built from the packaged release:
#
#   ./mvnw -DskipTests package
#   docker build -f deploy/docker/Dockerfile -t grantforge dist
#
# JDBC drivers that are not bundled (MySQL Connector/J is GPL) go into /opt/grantforge/drivers.
FROM eclipse-temurin:21-jre

ARG RELEASE=grantforge-release.tar.gz

RUN groupadd --system --gid 10001 grantforge \
    && useradd --system --uid 10001 --gid grantforge --home-dir /opt/grantforge --no-create-home grantforge

# The archive unpacks into /opt/grantforge (bin, configure, lib).
ADD ${RELEASE} /opt/
RUN mkdir -p /opt/grantforge/data /opt/grantforge/plugins /opt/grantforge/drivers \
    && chown grantforge:grantforge /opt/grantforge/data /opt/grantforge/plugins /opt/grantforge/drivers

WORKDIR /opt/grantforge
USER 10001:10001

# Logs go to the console only, as containers expect. The heap follows the container's memory limit.
ENV GRANTFORGE_HOME=/opt/grantforge \
    GRANTFORGE_SERVER_PORT=9999 \
    GRANTFORGE_LOG_FILE= \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

EXPOSE 9999
VOLUME ["/opt/grantforge/data", "/opt/grantforge/plugins"]

HEALTHCHECK --interval=10s --timeout=3s --start-period=120s --retries=3 \
    CMD curl --silent --fail --output /dev/null http://127.0.0.1:${GRANTFORGE_SERVER_PORT}/actuator/health/readiness || exit 1

ENTRYPOINT ["java", "-classpath", "lib/*:drivers/*", "org.devlive.grantforge.server.GrantForge", \
    "--spring.config.additional-location=/opt/grantforge/configure/"]
