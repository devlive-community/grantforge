<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

# Deploying GrantForge

| Path | What |
| --- | --- |
| [`docker/Dockerfile`](docker/Dockerfile) | The server image, published as `ghcr.io/devlive-community/grantforge:<version>` for every release, or built from the packaged release (`./mvnw -DskipTests package`, then `docker build -f deploy/docker/Dockerfile -t grantforge dist`). It runs as an unprivileged user, logs to the console and holds no database. |
| [`compose/`](compose) | One Docker Compose example per database: `h2`, `postgres`, `mariadb`, `mysql`, `sqlserver`, `oracle`. `docker compose -f deploy/compose/postgres.yml up -d`, then open http://127.0.0.1:9999/. |
| [`helm/grantforge`](helm/grantforge) | A Helm chart: a StatefulSet in front of an external database; each replica takes its ID node from its pod index (Kubernetes 1.28 or newer). |

The first start creates the schema and prints a one-time setup token in the log; set `GRANTFORGE_SETUP_TOKEN`
(Helm: `setupToken`) to choose it instead. MySQL Connector/J is GPL and not bundled: put its jar into the image's
`/opt/grantforge/drivers` (Compose: `deploy/compose/drivers`), or into `drivers/` of an unpacked release.

Every instance of a cluster needs its own `GRANTFORGE_ID_NODE` (0-1023). Sessions are kept in the database, so any
instance may serve any request.

`script/ci/docker_smoke.sh <database>` builds the image, starts a compose example, completes the setup and signs in
across a restart; it runs every night for each database.

## Upgrading from 1.x

1.x kept its data in other tables, which GrantForge 2 does not read. Install 2.x on a new database, complete the
setup, stop the server, then import the old accounts, roles and menus into a tenant:

```bash
bin/import-legacy.sh --source-url jdbc:mysql://old-db:3306/authx --source-user authx --tenant default
bin/import-legacy.sh --source-url jdbc:mysql://old-db:3306/authx --source-user authx --tenant default --apply
```

The first command is a dry run; both write `logs/legacy-import-report.json` with what was (or would be) created, what
was left out and why, and the new ID of every old one. The old database's JDBC driver goes into `drivers/`, and its
password into `GRANTFORGE_LEGACY_SOURCE_PASSWORD` (or the script asks for it). Running the import again only adds what
is missing.

- Accounts keep their passwords; the first sign-in rehashes them. Login names GrantForge does not accept (3-64 letters,
  digits or `._@-`), accounts without a password and names taken in another tenant are left out.
- Roles keep their names; their codes become lowercase (`GLY` → `gly`).
- Menus become resources of the `legacy` application: `#` folders menus, other URLs pages, and what lies below a page
  buttons. A menu's URL with its HTTP methods becomes API resources `api:<METHOD>:<path>`; a URL ending in `*` becomes
  `<path>/**`, which matches by path segments rather than by prefix, so the report lists each one to check.
- Only explicit grants come over: 1.x let anyone reach a URL registered as a menu, GrantForge never does.
