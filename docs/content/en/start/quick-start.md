---
title: Get Started in Five Minutes
description: Start GrantForge from the distribution package or Docker, complete initialization, create a user, and grant the first role.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

This guide starts GrantForge on your local machine with the default embedded H2 database. For production, see [Installing the distribution package](/en/deploy/installation/) and [Databases](/en/deploy/databases/).

## 1. Start the Service

Java 17 or later is required. Download the distribution package from [GitHub Releases](https://github.com/devlive-community/grantforge/releases), or build it yourself by running `./mvnw -DskipTests package` in the source directory (the artifact is written to `dist/grantforge-release.tar.gz`), then extract and start it:

```bash
tar -xzf grantforge-release.tar.gz
cd grantforge
bin/startup.sh
```

Docker works too: build the image from the distribution package first, then start it with the Compose example:

```bash
docker build -f deploy/docker/Dockerfile -t grantforge dist
docker compose -f deploy/compose/h2.yml up -d
```

The service listens on port `9999` by default. The first start creates the database tables and prints a one-time **setup token** to the log:

```text
WARN  ... First-run setup is pending. Open the console and enter this setup token: 7rV2...
```

For the distribution package, logs are written to `logs/grantforge.log`; with Docker, view them with `docker compose logs`.

## 2. Complete Initialization

Open http://127.0.0.1:9999/ in a browser and the console goes straight to the setup page. Enter the token from the log, the organization name, and the username and password for the first administrator (at least 12 characters).

![First-run setup and login page](/screenshots/login.png)

> [!TIP]
> For automated installations, you can pre-set the token with the `GRANTFORGE_SETUP_TOKEN` environment variable; see the [Configuration reference](/en/reference/configuration/).

Once initialization completes, this administrator holds both the **tenant administrator** and **platform administrator** system roles and can use every console feature. The setup page is then disabled permanently.

## 3. Create a User

Go to **Access Control → User Management**, click "Create user", and fill in the username, initial password, and primary department. New users must change their password the first time they log in.

## 4. Create a Role and Grant Permissions

1. Go to **Access Control → Role Management**, click "New role", for example "Read-only auditor".
2. Click "Grant" on the role's row and tick the "Audit log" page in the grant matrix. The matrix automatically pulls in the APIs that page needs.
3. Click "Assign" and assign the role to the user you just created.

![Role management](/screenshots/roles.png)

## 5. Verify the Result

Log in as the new user: the left menu shows only "Audit log". Back on the administrator account, click the "View effective permissions" icon on the user's row to see where each of their permissions comes from.

## Next Steps

- Learn about the [core concepts](/en/start/concepts/).
- Get to know every menu with the [user guide](/en/guide/console/).
- [Integrate your application](/en/integration/overview/) with GrantForge.
