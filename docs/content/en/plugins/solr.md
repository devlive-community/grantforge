---
title: Apache Solr
description: Install and configure the Solr service plugin, reach one node or several, sign in with basic authentication or a JSON web token, look up collections and their request paths, and manage who may query, update and administer what.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

The Solr plugin looks up collections and the request paths within them, and manages access policies on what Solr decides: querying, updating, reading and editing schemas and configs, administering collections, and administering the cluster. Its plugin ID and service type are both `solr`. The service plugin runs in the GrantForge server; the [Solr agent](/en/external/solr-agent/), installed in the nodes, enforces the policies.

The repository's Testcontainers integration tests cover SolrCloud nodes of Solr `9.4.1`, `9.8.1`, `9.11.0` and `10.0.0`; this does not certify every other release.

## Installation

The distribution includes the plugin in `plugins/solr`. Confirm that `solr` is enabled under **Platform management → Plugins**; rescan the plugin directory after updating it. The plugin talks to the nodes through SolrJ 9.11's JDK HTTP client, in a class loader of its own.

## Add a data service

1. Open **Data permissions → Data services**, add a service and select Apache Solr (`solr`).
2. Enter the base URL of a node, or of several nodes.
3. Choose how the lookup user signs in, enter its credentials, test the connection and save the service. The test signs in and describes the cluster: its Solr release, whether it runs SolrCloud and how many collections it has.

The lookup user lets GrantForge discover resources; it is separate from the users who query and update. A successful connection test does not mean access is already subject to policies.

| Setting | Purpose |
| --- | --- |
| `solr.urls` | Required: a node's base URL, such as `https://solr1.example.com:8983/solr`; several separated by commas, tried in turn when one cannot be reached |
| `authentication` | `none` (default), `basic` or `jwt` |
| `username`, `password` | basic: the lookup user and its password; the password is stored encrypted |
| `token` | jwt: the lookup user's JSON web token; stored encrypted |
| `javax.net.ssl.trustStore`, `javax.net.ssl.trustStorePassword`, `javax.net.ssl.trustStoreType` | https: the trust store the nodes' certificates are checked against, on the **GrantForge server**, its password and its type, default `PKCS12`; empty uses the JVM's |
| `timeout` | How long a request may take, default `30000`, range `1..600000` milliseconds |

The settings of one sign-in are refused with another, so a service says exactly how it signs in. A refused sign-in, a user that may not see something, and a node that cannot be reached, also behind a proxy that answers for it, are reported as such.

### What the lookup user may see

Listing collections is Solr's `collection-admin-read` permission, and a collection's request paths come from its config, `config-read`. Give the lookup user those in Solr's own rules or, once the agent is installed, `admin_read` on the cluster and `config_read` on the collections. Paths of collections the lookup user may not see are left out of the suggestions. A node that runs without SolrCloud has cores instead of collections, and the plugin names them as collections.

## Resources and access types

| Level | Matching | Access types |
| --- | --- | --- |
| `collection` | Wildcard, case-sensitive; the collection's name | `query`, `update`, `schema_read`, `schema_edit`, `config_read`, `config_edit`, `admin_collection` |
| `path` | Wildcard, case-sensitive; below a collection; the request path, such as `/select`, `/export` or `/update/json` | `query`, `update`, `schema_read`, `schema_edit`, `config_read`, `config_edit` |
| `cluster` | Its one value, `*` | `health`, `metrics`, `admin_read`, `security`, `admin` |

| Access type | Solr's permissions | Implies |
| --- | --- | --- |
| `query` | `read`: searching, getting, exporting | — |
| `update` | `update` | — |
| `schema_read`, `schema_edit` | `schema-read`, `schema-edit` | `schema_edit` implies `schema_read` |
| `config_read`, `config_edit` | `config-read`, `config-edit` | `config_edit` implies `config_read` |
| `admin_collection` | `collection-admin-read` and `collection-admin-edit` of the Collections API requests that name the collection: create, reload, back up, delete and the like | `query`, `update`, `schema_edit`, `config_edit` |
| `health`, `metrics` | `health`, `metrics-read` | — |
| `admin_read` | `collection-admin-read` of the whole cluster, `core-admin-read`, `zk-read`, `filestore-read`, `package-read`, and reading the nodes' config and schema | `health`, `metrics` |
| `security` | `security-read`, `security-edit` | — |
| `admin` | Everything | `admin_read`, `security`, `admin_collection` |

A request to a collection is decided at the collection and at the collection with its request path, and a denial at either refuses it; so a policy can allow a collection and deny its `/export` handler, or allow updating only through `/update*`. A request to several collections needs each of them. Below a collection, the request path is the handler Solr routes the request to, as Solr names it.

A Collections API request that names collections, by its `name` or `collection` parameter, needs `admin_collection` on each of them, so a policy on `team-*` lets a team create collections whose name starts with `team-`, and reload, back up or delete them, but no other. A request that names none, such as listing every collection, is the cluster's. `admin` on the cluster grants everything, as Solr's own `all` permission does. In policy values, `*` matches any run of characters and `?` one. The policy editor suggests the collections and the request paths of the collections a policy names.

### Client addresses

A policy item can hold for some clients only: its **Client addresses** condition (`ip-range`) takes addresses and ranges such as `10.1.0.0/16` or `2001:db8::/32`. Host names are not looked up. Behind a proxy, the client is the proxy unless the agent trusts it: then the client is the last address of `X-Forwarded-For` that no trusted proxy added, so a client cannot claim an address of its own choosing.

## Policy example

Suppose the cluster has collections `sales` and `hr`. Create these enabled access policies in the same data service:

| Resources | Rule |
| --- | --- |
| `collection=sales` | Allow `query` to group `analysts` |
| `collection=sales`, `path=/export` | Deny `query` to user `alice` |
| `collection=sales`, `path=/update*` | Allow `update` to user `ingest` |
| `collection=team-*` | Allow `admin_collection` to group `search-team` |
| `cluster=*` | Allow `metrics` and `health` to user `monitoring` |
| `collection=hr` | Allow `query` to group `hr-staff`, from `10.0.0.0/8` |
| `cluster=*` | Allow `admin` to group `solr-admins` |

Once the agent applies the new snapshot, members of `analysts` query `sales`, `alice` all but its export handler; `ingest` updates `sales` through its update handlers and may not query it; members of `search-team` create and administer their own `team-` collections; `monitoring` reads metrics and health; members of `hr-staff` query `hr` from the internal network only; and members of `solr-admins` may do everything.

## Enforce policies

The service plugin manages policies and looks up resources. Enforcing them needs the Solr agent in every node. Deployment and checks are in the [Solr agent](/en/external/solr-agent/).

## Related guides

- [Data services, policies and agents](/en/external/data-services/)
- [Solr agent](/en/external/solr-agent/)
- [Plugins and service types](/en/develop/plugins/)
