---
title: Data services, policies, and agents
description: "Manage permissions for external systems such as HDFS, ZooKeeper and MySQL through plugins: data services, access policies, agents, and access audits."
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

The "Data permissions" group manages permissions for data systems outside GrantForge. The architecture is similar to Apache Ranger: plugins define service types, administrators write policies in the console, and agents on the access path download the policies and evaluate access locally.

> [!NOTE]
> The current release provides the plugin framework, a general policy editor, signed policy distribution and access auditing, with HDFS, ZooKeeper and MySQL service plugins and their agents. See the [Apache Hadoop HDFS NameNode agent](/en/external/hdfs-agent/) for Hadoop version compatibility. MySQL uses a standalone network proxy that clients connect to; see the [MySQL proxy agent](/en/external/mysql-agent/).

```mermaid
flowchart LR
  C["Console: data services and policies"] --> S["GrantForge server"]
  S -->|Signed policy snapshots| A["Policy enforcement agent"]
  A -->|Heartbeats and access audits| S
  U["User data access"] --> A
```

## Plugins

**Platform management → Plugins** lists the loaded service type plugins. Built-in plugins ship with the server; for any other plugin, drop it into the `plugins` directory and click "Rescan". Each plugin is loaded independently, so a failure disables only that plugin. For plugin development, see [Plugins and service types](/en/develop/plugins/).

![Plugins](/screenshots/plugins.png)

## HDFS

The distribution ships with the HDFS service type plug-in; installation, connection settings, directory browsing and path policies are described under [Apache Hadoop HDFS](/en/plugins/hdfs/).

## MySQL

The MySQL service plugin handles connection validation, database/table/column lookup, and access, column masking and row filtering policy management; see [MySQL](/en/plugins/mysql/). A separately deployed [MySQL proxy agent](/en/external/mysql-agent/) enforces the policies: clients connect to the proxy listener, which accesses the backend MySQL server. It is not installed inside the MySQL server process; direct client connections to the backend bypass GrantForge policies.

## Data services

**Data permissions → Data services**: A service is an instance of an external system whose permissions GrantForge manages, for example one HDFS cluster. When adding a service, choose the service type and fill in the connection details defined by the plugin's configuration items; you can **Test connection** first. Sensitive settings such as passwords are stored encrypted and are no longer shown after saving.

![Data services](/screenshots/services.png)

## Policies

**Data permissions → Policies** decide who can do what with which resources in a data service:

- **Access policies** allow or deny access;
- **Masking policies** mask fields;
- **Row filter policies** let through only a subset of rows.

The resource hierarchy (such as MySQL's databases, tables and columns), the access types (such as select and update) and the conditions all come from the service type's plugin; when filling in a resource you can search for resources that actually exist in the target system. Policies apply to users, user groups or roles.

Levels that can be browsed, such as HDFS paths, have a **Browse** button: open directories level by level, see their owner, group and permissions, and choose several files or directories at once. When a lookup or browsing fails, the reason is shown (no permission, unreachable, or a directory too large) and you can retry.

![Policies](/screenshots/policies.png)

## Agents

**Data permissions → Agents**: Agents run inside the target systems or as standalone network proxies; with their token they periodically report heartbeats and download signed policy snapshots, and they evaluate access locally. From here you issue agent tokens (shown only once) and check whether each agent has already picked up the latest policies.

![Agents](/screenshots/agents.png)

## Access audits

**Data permissions → Access audits**: Every access decision reported by an agent: who did what to which resource, when and from where, whether it was allowed or denied, and which policy decided. Records are retained for 90 days by default.

![Access audit](/screenshots/access-audit.png)
