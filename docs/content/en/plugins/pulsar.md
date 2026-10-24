---
title: Apache Pulsar
description: Install and configure the Pulsar service plugin, reach one broker or several, sign in with a token, a password, OAuth 2.0 or a client certificate, look up tenants, namespaces, topics and subscriptions, and manage who may produce, consume and administer what.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

The Pulsar plugin looks up tenants, namespaces, topics, subscriptions and clusters, and manages access policies on what Pulsar decides: producing, consuming, functions, sources, sinks and packages, and administering topics, namespaces, tenants and clusters. Its plugin ID and service type are both `pulsar`. The service plugin runs in the GrantForge server; the [Pulsar agent](/en/external/pulsar-agent/), installed in the brokers, enforces the policies.

The repository's Testcontainers integration tests cover the brokers of Pulsar `3.0.17`, `3.3.9`, `4.0.14` and `4.2.5`; this does not certify every other release.

## Installation

The distribution includes the plugin in `plugins/pulsar`. Confirm that `pulsar` is enabled under **Platform management → Plugins**; rescan the plugin directory after updating it. The plugin talks to the brokers' web service through Pulsar's own admin client 4.2.5, in a class loader of its own.

## Add a data service

1. Open **Data permissions → Data services**, add a service and select Apache Pulsar (`pulsar`).
2. Enter the web service URL of a broker, or of several brokers.
3. Choose how the lookup role signs in, enter its credentials, test the connection and save the service. The test signs in and describes the cluster: its clusters and how many tenants it has.

The settings take the names of Pulsar's own `client.conf`, so they can be copied from it. The lookup role lets GrantForge discover resources; it is separate from the roles that produce and consume. A successful connection test does not mean access is already subject to policies.

| Setting | Purpose |
| --- | --- |
| `webServiceUrl` | Required: a broker's web service, `http://host:port` or `https://host:port`, default port `8080` or `8443`; several brokers separated by commas, such as `http://broker1:8080,broker2:8080` |
| `authentication` | `none` (default), `token`, `basic`, `oauth2` or `tls` |
| `token` | token: the lookup role's token; stored encrypted |
| `username`, `password` | basic: the lookup role's user name and password; the password is stored encrypted |
| `issuerUrl`, `privateKey`, `audience`, `scope` | oauth2: the authorization server, the path of the JSON credentials file with `client_id` and `client_secret` on the **GrantForge server**, and the audience and scope, if the brokers expect them |
| `tlsCertificateFilePath`, `tlsKeyFilePath` | tls: paths of the client certificate and its private key, PEM, on the **GrantForge server** |
| `tlsTrustCertsFilePath` | https: the CA certificates the brokers' certificates are checked against, PEM; empty uses the JVM's |
| `tlsEnableHostnameVerification` | https: whether a broker's certificate must name its host, default `true` |
| `timeout` | How long a request may take, default `30000`, range `1..600000` milliseconds |

The settings of one sign-in are refused with another, so a service says exactly how it signs in. A refused sign-in, a role that may not see something, and a broker that cannot be reached, also behind a proxy that answers for it, are reported as such.

### What the lookup role may see

Pulsar lets only its superusers list clusters and tenants. Name the lookup role in the brokers' `superUserRoles`, or, once the agent is installed, allow it `admin` on the cluster, which makes it a superuser too. Namespaces, topics and subscriptions the lookup role may not see are left out of the suggestions.

## Resources and access types

| Level | Matching | Access types |
| --- | --- | --- |
| `tenant` | Wildcard, case-sensitive | All but `admin` |
| `namespace` | Wildcard, case-sensitive; below a tenant; the namespace's own name, such as `orders` | `produce`, `consume`, `functions`, `sources`, `sinks`, `packages`, `admin_topic`, `admin_namespace` |
| `topic` | Wildcard, case-sensitive; below a namespace; the topic's own name, such as `payments` | `produce`, `consume`, `admin_topic` |
| `subscription` | Wildcard, case-sensitive; below a topic; the subscription's name | `consume` |
| `cluster` | Wildcard, case-sensitive; the cluster's name, such as `standalone` | `admin`: Pulsar's superuser access |

| Access type | Implies |
| --- | --- |
| `produce`, `consume`, `functions`, `sources`, `sinks`, `packages` | — |
| `admin_topic`: terminate, compact, offload, unload and truncate topics, and change their policies | `produce`, `consume` |
| `admin_namespace`: create and delete topics, and change the namespace's policies | `admin_topic` |
| `admin_tenant`: create, list and delete namespaces | `admin_namespace`, `functions`, `sources`, `sinks`, `packages` |
| `admin`: everything Pulsar's superusers may do | `admin_tenant` |

As with Pulsar's own grants, a permission on a tenant or a namespace covers what is in it: `produce` on namespace `orders` of tenant `sales` produces to every topic in it. A check is decided at the tenant, the namespace, the topic and the subscription in turn, and a denial at any of them refuses it; so a policy can allow a namespace and deny one topic in it. Below a topic, a subscription level says through which subscriptions a role may consume: `consume` on topic `*` with subscription `billing-*` lets a role consume every topic of the namespace, but only through subscriptions whose name starts with `billing-`.

A topic is decided by its own name, without its domain, so a persistent and a non-persistent topic of the same name share their policies; a partition of a partitioned topic is decided as the partitioned topic. In policy values, `*` matches any run of characters and `?` one. The policy editor suggests the clusters, the tenants, and the namespaces, topics and subscriptions in what a policy names.

### Client addresses

A policy item can hold for some clients only: its **Client addresses** condition (`ip-range`) takes addresses and ranges such as `10.1.0.0/16` or `2001:db8::/32`. Host names are not looked up. Behind a proxy, the client is the proxy unless it forwards the client's own address: with the HAProxy protocol for producers and consumers, and with `X-Forwarded-For` for the web service when the brokers trust it.

## Policy example

Suppose tenant `sales` has namespaces `orders` and `returns`. Create these enabled access policies in the same data service:

| Resources | Rule |
| --- | --- |
| `tenant=sales`, `namespace=orders` | Allow `produce` to group `analysts` |
| `tenant=sales`, `namespace=orders`, `topic=audit` | Deny `produce` to user `alice` |
| `tenant=sales`, `namespace=orders`, `topic=*`, `subscription=billing-*` | Allow `consume` to user `alice` |
| `tenant=sales` | Allow `admin_tenant` to user `bob` |
| `tenant=public` | Allow `produce` to group `ingest`, from `10.0.0.0/8` |
| `cluster=standalone` | Allow `admin` to group `pulsar-admins` |

Once the agent applies the new snapshot, members of `analysts` produce to every topic of `sales/orders`, `alice` to all but `audit`; `alice` consumes there through the `billing-` subscriptions only; `bob` creates namespaces and topics in `sales` and administers them; members of `ingest` produce to the `public` tenant from the internal network only; and members of `pulsar-admins` are Pulsar's superusers.

## Enforce policies

The service plugin manages policies and looks up resources. Enforcing them needs the Pulsar agent in every broker. Deployment and checks are in the [Pulsar agent](/en/external/pulsar-agent/).

## Related guides

- [Data services, policies and agents](/en/external/data-services/)
- [Pulsar agent](/en/external/pulsar-agent/)
- [Plugins and service types](/en/develop/plugins/)
