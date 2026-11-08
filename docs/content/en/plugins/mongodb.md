---
title: MongoDB
description: Install and configure the MongoDB service plugin, connect to a replica set or sharded cluster, sign in with SCRAM, a client certificate, LDAP or Kerberos, look up databases and collections, and manage who may read, write and administer what, collection by collection.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

The MongoDB plugin looks up databases and collections, and manages access policies in MongoDB's own terms: reading, change streams, inserting, updating and removing, indexes, collection options, skipping document validation and dropping, by collection; creating collections and dropping the database, by database; listing every database and monitoring, for the deployment as a whole. Plugin ID and service type are both `mongodb`. The service plugin runs in the GrantForge server; the [MongoDB agent](/en/external/mongodb-agent/), which runs beside the deployment, enforces the policies with MongoDB's own roles.

The repository's Testcontainers integration tests cover MongoDB `6.0`, `7.0`, `8.0` and `9.0` replica sets, and Kerberos on Percona Server for MongoDB `8.0`. This does not certify every other release.

## Installation

The distribution ships the plugin in `plugins/mongodb`. On **Platform administration → Plugins**, check that `mongodb` is enabled, and rescan after updating the plugin directory. The plugin talks to MongoDB through the official MongoDB Java driver, in a class loader of its own.

## Add a data service

1. Open **Data authorization → Data services**, add a service and choose MongoDB (`mongodb`).
2. Enter the connection string of the deployment, as any MongoDB client takes it.
3. Enter how the lookup user signs in, test the connection and save the service. The test signs in and describes the deployment: the MongoDB release, whether it is a replica set, a sharded cluster or a single server, who signed in and how many databases that user sees.

The lookup user lets GrantForge find resources and is not the user that reads or writes. A connection test that succeeds does not mean access already follows the policies.

| Setting | Purpose |
| --- | --- |
| `mongodb.uri` | Required: the connection string, such as `mongodb://db1.example.com:27017,db2.example.com:27017/?replicaSet=rs0` or `mongodb+srv://cluster.example.com/`. Options such as `tls=true` go here; credentials do not, and a string that holds any is refused |
| `auth.mechanism` | `SCRAM-SHA-256` (default) or `SCRAM-SHA-1` for users with a password, `MONGODB-X509` for a client certificate, `PLAIN` for LDAP, `GSSAPI` for Kerberos |
| `username` | The lookup user. With `GSSAPI` the principal; with `MONGODB-X509` empty takes it from the certificate |
| `password` | The lookup user's password, stored encrypted |
| `auth.source` | SCRAM: the database the user is defined in, `admin` by default. The other mechanisms use `$external` |
| `tls.ca.file` | With `tls=true`: path on the **GrantForge server** of the PEM CA certificates the servers are checked against. Empty uses the JVM's |
| `tls.certificate.key.file` | With `tls=true`: path on the **GrantForge server** of one PEM file with the client certificate and its key, as `mongosh` takes it |
| `tls.certificate.key.password` | The password of an encrypted key, stored encrypted |
| `kerberos.keytab` | `GSSAPI`: path on the **GrantForge server** of the principal's keytab. Empty uses the password |
| `kerberos.kdc` | `GSSAPI`: the realm's KDCs separated by commas. Empty uses the server's `krb5.conf` |
| `kerberos.service.name` | `GSSAPI`: the service name of the servers' principal, `mongodb` by default |
| `timeout` | How long finding a server, connecting and a command may take, `30000` by default, in the range `1..600000` milliseconds |

A refused sign-in, a lookup user that lacks a privilege and a deployment that cannot be reached are each reported as such.

### What the lookup user sees

MongoDB lists the databases and collections a user has any privilege on, so a lookup shows what the lookup user may see. Give it `read` on what policies are written for, or `readAnyDatabase` for everything. MongoDB's own databases `admin`, `config` and `local`, and system collections, are left out: policies do not decide them.

## Resources and access types

| Level | Matching | Access types |
| --- | --- | --- |
| `deployment` | Wildcards, case-sensitive. The replica set's name, `sharded` for a sharded cluster, `standalone` for a single server | `list_databases`, `monitor` |
| `database` | Wildcards, case-sensitive | `create`, `drop_database` |
| `collection` | Wildcards, case-sensitive. A collection or view of a database | `read`, `change_stream`, `insert`, `update`, `remove`, `create_index`, `drop_index`, `alter`, `bypass_validation`, `drop` |

| Access type | MongoDB's actions |
| --- | --- |
| `read` | `find`, `listIndexes`, `collStats`, `killCursors`; on a whole database also `listCollections` and `dbStats` |
| `change_stream` | `changeStream`; MongoDB also wants `find` to open one |
| `insert`, `update`, `remove` | `insert`, `update`, `remove` |
| `create_index`, `drop_index` | `createIndex`, `dropIndex` |
| `alter` | `collMod`, `validate`, `convertToCapped` |
| `bypass_validation` | `bypassDocumentValidation` |
| `drop` | `dropCollection` |
| `create` | `createCollection` on the database: creating collections and views |
| `drop_database` | `dropDatabase` |
| `list_databases` | `listDatabases`: every database's name, not only those the user has a privilege on |
| `monitor` | `serverStatus`, `top`, `replSetGetStatus`, `connPoolStats`, `hostInfo`, `inprog` |

In policy values `*` matches any text and `?` one character. The policy editor suggests the deployment, databases, and the collections of the databases a policy names.

MongoDB's privilege to drop a database is the privilege to drop every collection in it, so the agent grants `drop_database` only where the policies also allow dropping every collection. Otherwise it withholds the privilege and says so, rather than give more than the policies allow. User and role administration are no access types, and no policy gives them.

### Rows and fields

MongoDB filters no rows and masks no fields by role, so this service type offers neither. A view gives a restricted reading: MongoDB checks a view's reader against the view alone, so a policy that allows `read` on a view, and nothing on its source, shows the user what the view shows and no more.

```javascript
db.getSiblingDB("sales").createView("orders_public", "orders", [{ $project: { card: 0 } }])
```

## Policy examples

Suppose the deployment has databases `sales` and `hr`. Create these enabled policies in one data service:

| Resource | Rule |
| --- | --- |
| `database=sales`, `collection=*` | Allow `read` to group `analysts` |
| `database=sales`, `collection=orders_public` | Allow `read` to user `support` |
| `database=sales`, `collection=orders` | Allow `insert` and `update` to user `ingest`; deny `read` to group `contractors` |
| `database=team_*` | Allow `create` to group `data-team` |
| `database=team_*`, `collection=*` | Allow `read`, `insert`, `update`, `remove`, `create_index` and `drop` to group `data-team` |
| `deployment=*` | Allow `monitor` to user `dashboards` |

Once the agent has applied the snapshot, members of `analysts` read all of `sales`, also collections created later; `support` reads the view without its source; `ingest` writes `orders` without reading it; members of `data-team` work freely within `team_` databases; and `dashboards` sees the server's state and no data.

## Enforcing the policies

The service plugin manages policies and looks up resources. Enforcing them takes one MongoDB agent per deployment; see [MongoDB agent](/en/external/mongodb-agent/) for deployment and checks.

## Related guides

- [Data services, policies and agents](/en/external/data-services/)
- [MongoDB agent](/en/external/mongodb-agent/)
- [Plugins and service types](/en/develop/plugins/)
