---
title: OpenSearch
description: Install and configure the OpenSearch service plugin, reach one node or several, sign in with a password or a TLS client certificate, look up indices, data streams, fields and the cluster, and manage who may read, write and administer what, down to fields, masked values and documents.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

The OpenSearch plugin looks up indices, data streams, fields and the cluster, and manages access policies in OpenSearch's own terms: reading fields, indexing, deleting, creating and administering indices, and the cluster's own operations, with masks of fields and row filters of indices. Its plugin ID and service type are both `opensearch`. The service plugin runs in the GrantForge server; the [OpenSearch agent](/en/external/opensearch-agent/), a service beside the cluster, enforces the policies with the security plugin's own roles, field level security, masked fields and document level security.

The repository's Testcontainers integration tests cover OpenSearch `2.19`, `3.3`, `3.6` and `3.9` with the security plugin; this does not certify every other release.

## Installation

The distribution includes the plugin in `plugins/opensearch`. Confirm that `opensearch` is enabled under **Platform management → Plugins**; rescan the plugin directory after updating it. The plugin talks to the nodes through OpenSearch's own low-level REST client, in a class loader of its own.

## Add a data service

1. Open **Data permissions → Data services**, add a service and select OpenSearch (`opensearch`).
2. Enter a node as a URL, or several nodes.
3. Choose how the lookup user signs in, enter its credentials, test the connection and save the service. The test signs in and describes the cluster: its OpenSearch release and name, the user it signed in as and how many indices and data streams it has. A cluster without the security plugin fails the test, as the agent enforces policies through it.

The lookup user lets GrantForge discover resources; it is separate from the users who read and write. A successful connection test does not mean access is already subject to policies.

| Setting | Purpose |
| --- | --- |
| `opensearch.hosts` | Required: a node as a URL, such as `https://opensearch1.example.com:9200`; several separated by commas, tried in turn when one cannot be reached. Without a scheme a node is `https`, and the port defaults to `9200`; the nodes of one service are all `http` or all `https` |
| `authentication` | `password` (default), or `certificate`: a TLS client certificate that the security plugin maps to a user |
| `username`, `password` | password: the lookup user and its password, stored encrypted |
| `ssl.verificationMode` | As OpenSearch Dashboards names it: `full` (default) checks the nodes' certificates and names, `certificate` their certificates only, `none` nothing |
| `ssl.certificateAuthorities` | Path on the **GrantForge server** of the PEM CA certificates the nodes are checked against; empty uses the JVM's |
| `ssl.certificate`, `ssl.key` | certificate: paths on the GrantForge server of the PEM client certificate and its private key, PKCS #8 or the older OpenSSL form; they need `https` nodes |
| `ssl.keyPassphrase` | certificate: the passphrase of an encrypted private key, stored encrypted |
| `timeout` | How long connecting and a request may take, default `30000`, range `1..600000` milliseconds |

Settings of one sign-in method are refused for the other, so the service says exactly how it signs in. Refused sign-ins, missing permissions, nodes that cannot be reached and certificates that cannot be verified are reported as such.

### What the lookup user sees

The lookup user needs to read what it lists: OpenSearch's resolve API for indices and data streams, field capabilities for fields and the cluster's description for its name. The built-in `readall` role gives the first two; a user without `cluster:monitor/main` still passes the connection test, which then does not name the release. Indices and data streams whose names start with a dot, hidden and system ones, are listed only when what was typed starts with a dot; the backing indices of data streams are listed by their data stream. Fields are the fields of documents: OpenSearch's own metadata fields, objects and the multi-fields that index a field once more, such as `name.keyword`, are left out, as field level security shows or hides those with their field.

## Resources and access types

| Level | Matching | Access types |
| --- | --- | --- |
| `index` | Wildcards, case-sensitive; the name of an index or data stream | `index`, `delete`, `create_index`, `manage_aliases`, `monitor`, `manage` |
| `field` | Wildcards, case-sensitive; below an index, a field's path such as `address.city` | `read` |
| `cluster` | Wildcards, case-sensitive; the cluster's name | `cluster_monitor`, `manage_snapshots`, `manage_templates`, `manage_pipelines` |

| Access type | The security plugin's action group |
| --- | --- |
| `read` | `read`: searches, gets, counts and the like |
| `index` | `index`: indexing and updating documents, also by bulk |
| `delete` | `delete`: deleting documents |
| `create_index` | `create_index`: creating indices, also when the first write creates one |
| `manage_aliases`, `monitor` | `manage_aliases`, `indices_monitor` |
| `manage` | `manage`: every administrative action of an index, deleting it included; it implies `create_index`, `manage_aliases` and `monitor` |
| `cluster_monitor`, `manage_snapshots` | `cluster_monitor`, `manage_snapshots` |
| `manage_templates`, `manage_pipelines` | `cluster_manage_index_templates`, `cluster_manage_pipelines` |

Reading is decided by field, as field level security shows the fields of documents: a `read` policy names fields, and `*` is every field. A policy can thus allow an index and deny one field of it, and the user reads every other field. In policy values, `*` matches any run of characters and `?` one character. The policy editor suggests indices and data streams, the fields of the indices the policy names, and the cluster.

Using what a policy allows needs more in OpenSearch: multi-search, multi-get and scrolling for reading, and bulk for writing. They are no access types of their own; the agent grants the cluster's composite operations with them.

### Masks

A masking policy of a field shows the user its values masked, as the security plugin's masked fields do, in documents and in aggregations alike:

| Mask | What the user sees |
| --- | --- |
| Redact | Upper-case letters as `X`, lower-case letters as `x` and digits as `n` |
| Partial mask: show last 4 / first 4 | Every other character as `x` |
| Hash (SHA-256) | The value's SHA-256 hash |
| Hash with the cluster's salt (BLAKE2b) | The security plugin's own hash, keyed with the cluster's salt, so that equal values stay equal but cannot be looked up |
| Custom | A hash algorithm, such as `SHA-512`, or regular expressions with their replacements, such as `/[0-9]/::*` |
| Unmasked | The value as it is |

### Row filters

A row filter policy of an index shows the user only the documents a query matches. The query is OpenSearch's query DSL as document level security takes it, such as `{"term": {"region": "EU"}}`, and may use the security plugin's substitutions such as `${user.name}`. As with other services, a user gets the filter of the first policy that applies to it. A row filter can test only fields the user may read: field level security hides the others from the query too.

## Policy examples

Suppose the cluster has the indices `logs-2026.10.26` and `hr`. Create these enabled policies in the same data service:

| Resource | Rule |
| --- | --- |
| `index=logs-*`, `field=*` | Allow group `analysts` to `read` |
| `index=logs-*`, `field=client_ip` | Deny group `analysts` to `read` |
| `index=logs-*` | Allow user `logstash` to `index` and `create_index` |
| `index=hr`, `field=*` | Allow group `hr` to `read` |
| `index=hr`, `field=salary` (masking) | Show group `hr` the hash with the cluster's salt |
| `index=hr` (row filter) | Show user `emea-hr` only documents of `{"term": {"region": "EMEA"}}` |
| `cluster=*` | Allow group `ops` to `cluster_monitor` |

Once the agent applies the new snapshot, members of `analysts` read every log field but the client's address; `logstash` writes tomorrow's `logs-2026.10.27` at once, creating it with its first document; members of `hr` see salaries hashed; `emea-hr` sees only the region's employees; and members of `ops` read the cluster's health and statistics.

## Enforcing policies

The service plugin manages policies and looks up resources; enforcing them needs an OpenSearch agent per cluster. Deployment and checks are in [OpenSearch agent](/en/external/opensearch-agent/).

## Related guides

- [Data services, policies and agents](/en/external/data-services/)
- [OpenSearch agent](/en/external/opensearch-agent/)
- [Plugins and service types](/en/develop/plugins/)
