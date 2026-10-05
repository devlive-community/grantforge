<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

# Deploying GrantForge

| Path | What |
| --- | --- |
| [`docker/Dockerfile`](docker/Dockerfile) | The server image, built from the packaged release (`./mvnw -DskipTests package`, then `docker build -f deploy/docker/Dockerfile -t grantforge dist`). It runs as an unprivileged user, logs to the console and holds no database. |
| [`compose/`](compose) | One Docker Compose example per database: `h2`, `postgres`, `mariadb`, `mysql`, `sqlserver`, `oracle`. `docker compose -f deploy/compose/postgres.yml up -d`, then open http://127.0.0.1:9999/. |
| [`helm/grantforge`](helm/grantforge) | A Helm chart: a StatefulSet in front of an external database; each replica takes its ID node from its pod index (Kubernetes 1.28 or newer). |

The first start creates the schema and prints a one-time setup token in the log; set `GRANTFORGE_SETUP_TOKEN`
(Helm: `setupToken`) to choose it instead. MySQL Connector/J is GPL and not bundled: put its jar into the image's
`/opt/grantforge/drivers` (Compose: `deploy/compose/drivers`), or into `drivers/` of an unpacked release.

Every instance of a cluster needs its own `GRANTFORGE_ID_NODE` (0-1023). Sessions are kept in the database, so any
instance may serve any request.

`script/ci/docker_smoke.sh <database>` builds the image, starts a compose example, completes the setup and signs in
across a restart; it runs every night for each database.
