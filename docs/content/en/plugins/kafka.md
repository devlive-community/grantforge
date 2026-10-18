---
title: Apache Kafka
description: Install and configure the Kafka service plugin, sign in with PLAIN, SCRAM or Kerberos over plaintext or TLS, look up topics, groups, transactional IDs and users, and manage access policies in Kafka's own terms.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

The Kafka plugin looks up topics, consumer groups, transactional IDs, SCRAM users and delegation tokens, and manages access policies in Kafka's own terms: its resource types are the resource levels and its ACL operations the access types. Its plugin ID and service type are both `kafka`. The service plugin runs in the GrantForge server; the [Kafka agent](/en/external/kafka-agent/), installed in the brokers and controllers, enforces the policies.

The repository's Testcontainers integration tests cover Kafka `3.7.2`, `3.9.2`, `4.1.2` and `4.3.1` with SASL PLAIN and SCRAM sign-ins and Kafka's own authorizer, and Kerberos (keytab and password) and TLS against `4.3.1`; this does not certify every other release.

## Installation

The distribution includes the plugin in `plugins/kafka`. Confirm that `kafka` is enabled under **Platform management → Plugins**; rescan the plugin directory after updating it. The plugin talks to the brokers through Kafka's own Admin client (`kafka-clients` 4.3.1) in a class loader of its own.

## Add a data service

1. Open **Data permissions → Data services**, add a service and select Apache Kafka (`kafka`).
2. Enter the bootstrap servers and how the brokers' listener is secured.
3. Enter the lookup user, test the connection and save the service. The test signs in and describes the cluster: its ID and how many brokers it has.

The settings take Kafka's own client names, so they can be copied from a client configuration. The lookup user lets GrantForge discover resources; it is separate from the clients that produce and consume. A successful connection test does not mean requests are already subject to policies.

| Setting | Purpose |
| --- | --- |
| `bootstrap.servers` | Required brokers to start from, `host:port` separated by commas, such as `kafka1:9092,kafka2:9092` |
| `security.protocol` | `PLAINTEXT` (default), `SSL`, `SASL_PLAINTEXT` or `SASL_SSL` |
| `sasl.mechanism` | SASL protocols only: `PLAIN`, `SCRAM-SHA-256` or `SCRAM-SHA-512` with a password, `GSSAPI` for Kerberos |
| `username` | SASL: the lookup user; with Kerberos the full principal, such as `grantforge@EXAMPLE.COM` |
| `password` | The user's password, or the principal's Kerberos password unless a keytab is given; stored encrypted |
| `keytab` | Kerberos: path of the principal's keytab, readable on the **GrantForge server** |
| `kerberos.kdc` | Kerberos: KDCs of the principal's realm, `host[:port]` separated by commas; empty uses the server's `krb5.conf` |
| `sasl.kerberos.service.name` | Kerberos: the first part of the brokers' principals, default `kafka` |
| `ssl.truststore.location`, `ssl.truststore.password`, `ssl.truststore.type` | TLS: the truststore that verifies the brokers (`JKS`, `PKCS12` or `PEM`); empty trusts what Java trusts |
| `ssl.keystore.location`, `ssl.keystore.password`, `ssl.key.password` | TLS: GrantForge's certificate, for brokers that ask clients for one |
| `ssl.verify.hostname` | TLS: whether the brokers' certificates must name the hosts connected to, default `true` |
| `request.timeout.ms` | Request timeout, default `30000`, range `1..600000` |

The SASL settings are refused with `PLAINTEXT` and `SSL`, and the TLS settings with `PLAINTEXT` and `SASL_PLAINTEXT`. `PLAIN` and SCRAM need a password; `GSSAPI` needs a password or a keytab.

### Kerberos

With `GSSAPI`, GrantForge signs the principal in with Kafka's own Kerberos login, from the keytab or with the password, which GrantForge hands to the login itself; nothing else on the GrantForge server needs Kerberos settings. Realms whose KDCs are entered in `kerberos.kdc` are declared for the whole GrantForge server, the same way the HDFS and Hive plugins do, so several services can use different realms side by side.

### What the lookup user sees

Kafka shows a user only what it may describe, so the lookup user needs `describe` on the topics, groups and transactional IDs it should suggest, and `describe` on the cluster for SCRAM users. Kafka leaves out topics and groups it may not describe, so the suggestions are empty rather than refused. Internal topics, such as `__consumer_offsets`, are suggested once the typed text starts with `_`. A cluster without delegation tokens, or without SCRAM, suggests none.

Once the [Kafka agent](/en/external/kafka-agent/) enforces the policies, they decide what the lookup user may describe too: give it a policy that allows `describe`, or name it in the brokers' `super.users`.

## Resources and access types

| Level | Kafka resource | Access types |
| --- | --- | --- |
| `topic` | Topics | `read`, `write`, `create`, `delete`, `alter`, `describe`, `describe_configs`, `alter_configs`, `all` |
| `group` | Consumer groups | `read`, `delete`, `describe`, `all` |
| `transactional_id` | Transactional IDs | `write`, `describe`, `all` |
| `cluster` | The cluster, always named `kafka-cluster` | `create`, `alter`, `describe`, `cluster_action`, `describe_configs`, `alter_configs`, `idempotent_write`, `all` |
| `delegation_token` | Delegation tokens, by token ID | `describe`, `all` |
| `user` | Users whose delegation tokens are created or described | `create_tokens`, `describe_tokens`, `all` |

Every level stands on its own; a policy names one. Values are case-sensitive: `*` matches any number of characters and `?` matches one. The access types imply others as Kafka's ACL operations do: `read`, `write`, `delete` and `alter` imply `describe`, `alter_configs` implies `describe_configs`, and `all` implies every other one.

Implications count in denials too: a policy that denies `write` on a topic also denies the `describe` that `write` implies, so the user no longer sees the topic. Kafka's own ACLs apply implications to allowed operations only.

The policy editor suggests names by prefix from the cluster; for `cluster` it suggests `kafka-cluster`.

## Policy example

Suppose producers of the user `alice` write to topics whose names start with `orders`, and the group `analysts` reads them with the consumer group `billing`. Create three enabled access policies in the same data service:

| Resources | Rule |
| --- | --- |
| `topic=orders*` | Allow `write` for user `alice` |
| `topic=orders*` | Allow `read` for group `analysts` |
| `group=billing` | Allow `read` for group `analysts` |

Once the agent applies the new snapshot, `alice` may produce to `orders-eu`, also with an idempotent producer and without `idempotent_write` on the cluster; members of `analysts` consume it as `billing`; other topics and groups refuse both. `kafka-acls --list` shows the three policies as prefixed and literal ACLs.

## Enforce policies

The service plugin manages policies and looks up resources. Enforcing them needs the Kafka agent in every broker and controller. See the [Kafka agent](/en/external/kafka-agent/) for its deployment and what it checks.

## Related guides

- [Data services, policies and agents](/en/external/data-services/)
- [Kafka agent](/en/external/kafka-agent/)
- [Plugin and service type development](/en/develop/plugins/)
