---
title: Elasticsearch
description: Install and configure the Elasticsearch service plugin, reach one node or several, sign in with a password, an API key or a TLS client certificate, look up indices, data streams, fields and the cluster, and manage who may read, write and administer what with Elasticsearch's own privileges, down to fields and documents.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

The Elasticsearch plugin looks up indices, data streams, fields and the cluster, and manages access policies in Elasticsearch's own terms: reading fields, indexing, deleting, creating, deleting and administering indices, and the cluster's own operations, with row filters of indices. Its plugin ID and service type are both `elasticsearch`. The service plugin runs in the GrantForge server; the [Elasticsearch agent](/en/external/elasticsearch-agent/), a service beside the cluster, enforces the policies with Elasticsearch's own roles, field level security and document level security.

The repository's Testcontainers integration tests cover Elasticsearch `8.19`, `9.3`, `9.4` and `9.5` with security on; this does not certify every other release.

## Installation

The distribution includes the plugin in `plugins/elasticsearch`. Confirm that `elasticsearch` is enabled under **Platform management → Plugins**; rescan the plugin directory after updating it. The plugin talks to the nodes through Elasticsearch's low-level REST client, in a class loader of its own.

## Add a data service

1. Open **Data permissions → Data services**, add a service and select Elasticsearch (`elasticsearch`).
2. Enter a node as a URL, or several nodes.
3. Choose how the lookup user signs in, enter its credentials, test the connection and save the service. The test signs in and describes the cluster: its Elasticsearch release and name, its license and whether that license has document and field level security, the user it signed in as and how many indices and data streams it has. A cluster without security fails the test, as the agent enforces policies with Elasticsearch's roles.

The lookup user lets GrantForge discover resources; it is separate from the users who read and write. A successful connection test does not mean access is already subject to policies.

| Setting | Purpose |
| --- | --- |
| `elasticsearch.hosts` | Required: a node as a URL, such as `https://es1.example.com:9200`; several separated by commas, tried in turn when one cannot be reached. Without a scheme a node is `https`, and the port defaults to `9200`; the nodes of one service are all `http` or all `https` |
| `authentication` | `password` (default), `api_key`, or `certificate`: a TLS client certificate that a PKI realm maps to a user |
| `username`, `password` | password: the lookup user and its password, stored encrypted |
| `apiKey` | api_key: the encoded API key, as Elasticsearch returns it when it creates one, stored encrypted |
| `ssl.verificationMode` | As Kibana names it: `full` (default) checks the nodes' certificates and names, `certificate` their certificates only, `none` nothing |
| `ssl.certificateAuthorities` | Path on the **GrantForge server** of the PEM CA certificates the nodes are checked against, such as Elasticsearch's `http_ca.crt`; empty uses the JVM's |
| `ssl.certificate`, `ssl.key` | certificate: paths on the GrantForge server of the PEM client certificate and its private key, PKCS #8 or the older OpenSSL form; they need `https` nodes |
| `ssl.keyPassphrase` | certificate: the passphrase of an encrypted private key, stored encrypted |
| `timeout` | How long connecting and a request may take, default `30000`, range `1..600000` milliseconds |

Settings of one sign-in method are refused for the others, so the service says exactly how it signs in. Refused sign-ins, revoked API keys, missing privileges, nodes that cannot be reached and certificates that cannot be verified are reported as such.

### What the lookup user sees

The lookup user needs to read what it lists: Elasticsearch's resolve API for indices and data streams, field capabilities for fields and the cluster's description for its name. A role with `read` and `view_index_metadata` on the indices gives the first two; a user without the `monitor` cluster privilege still passes the connection test, which then names neither the release nor the license. Indices and data streams whose names start with a dot, hidden and system ones, are listed only when what was typed starts with a dot; the backing indices of data streams are listed by their data stream. Fields are the fields of documents: Elasticsearch's own metadata fields, objects and the multi-fields that index a field once more, such as `name.keyword`, are left out, as the agent grants those with their field.

## Resources and access types

| Level | Matching | Access types |
| --- | --- | --- |
| `index` | Wildcards, case-sensitive; the name of an index or data stream | `index`, `delete`, `create_index`, `delete_index`, `view_index_metadata`, `monitor`, `manage` |
| `field` | Wildcards, case-sensitive; below an index, a field's path such as `address.city` | `read` |
| `cluster` | Wildcards, case-sensitive; the cluster's name | `cluster_monitor`, `create_snapshot`, `manage_slm`, `manage_index_templates`, `manage_ingest_pipelines`, `manage_ilm` |

Every access type is the Elasticsearch privilege of the same name, but `cluster_monitor`, which is the `monitor` cluster privilege:

| Access type | What it allows |
| --- | --- |
| `read` | Searches, gets, counts, multi-search and scrolling |
| `index`, `delete` | Indexing and updating documents, also by bulk; deleting documents |
| `create_index`, `delete_index` | Creating indices, also when the first write creates one; deleting indices and data streams |
| `view_index_metadata`, `monitor` | Reading mappings, settings and aliases; reading statistics and recovery |
| `manage` | Every administrative action of an index; it implies `create_index`, `delete_index`, `view_index_metadata` and `monitor` |
| `cluster_monitor` | Reading the cluster's health, state and statistics |
| `create_snapshot`, `manage_slm` | Taking snapshots; managing snapshot lifecycle policies, which implies `create_snapshot` |
| `manage_index_templates`, `manage_ingest_pipelines`, `manage_ilm` | Managing index templates, ingest pipelines and index lifecycle policies |

Reading is decided by field, as field level security shows the fields of documents: a `read` policy names fields, and `*` is every field. A policy can thus allow an index and deny one field of it, and the user reads every other field. In policy values, `*` matches any run of characters and `?` one character. The policy editor suggests indices and data streams, the fields of the indices the policy names, and the cluster.

### Row filters

A row filter policy of an index shows the user only the documents a query matches. The query is Elasticsearch's query DSL as document level security takes it, such as `{"term": {"region": "EU"}}`, and may be a template, such as one of `{{_user.username}}`. As with other services, a user gets the filter of the first policy that applies to it. A row filter can test only fields the user may read: field level security hides the others from the query too.

Elasticsearch masks no values, so the service type offers no masking.

### The license

Elasticsearch has document and field level security from a Platinum license on. Below it, a policy that lets a user read only some fields or some documents of an index cannot be enforced, so the agent grants that user no read of the index rather than more than the policies allow, and says so. Policies that allow whole indices, writing and administering work with every license.

## Policy examples

Suppose the cluster, with a Platinum license, has the indices `logs-2026.10.26` and `hr`. Create these enabled policies in the same data service:

| Resource | Rule |
| --- | --- |
| `index=logs-*`, `field=*` | Allow group `analysts` to `read` |
| `index=logs-*`, `field=client_ip` | Deny group `analysts` to `read` |
| `index=logs-*` | Allow user `logstash` to `index` and `create_index` |
| `index=hr`, `field=*` | Allow group `hr` to `read` |
| `index=hr` (row filter) | Show user `emea-hr` only documents of `{"term": {"region": "EMEA"}}` |
| `cluster=*` | Allow group `ops` to `cluster_monitor` |

Once the agent applies the new snapshot, members of `analysts` read every log field but the client's address; `logstash` writes tomorrow's `logs-2026.10.27` at once, creating it with its first document; members of `hr` read the employees; `emea-hr` sees only the region's employees; and members of `ops` read the cluster's health and statistics.

## Enforcing policies

The service plugin manages policies and looks up resources; enforcing them needs an Elasticsearch agent per cluster. Deployment and checks are in [Elasticsearch agent](/en/external/elasticsearch-agent/).

## Related guides

- [Data services, policies and agents](/en/external/data-services/)
- [Elasticsearch agent](/en/external/elasticsearch-agent/)
- [Plugins and service types](/en/develop/plugins/)
