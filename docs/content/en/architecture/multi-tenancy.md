---
title: Multi-tenancy and data isolation
description: How tenants isolate data, which data is shared platform-wide, and the constraints on cross-tenant operations.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

## Isolation approach

Apart from platform-shared data, every business table has a `tenant_id` column. When a request arrives, the filter chain binds the signed-in account's tenant to the current thread; Hibernate's tenant filter automatically adds the tenant condition to queries and fills in `tenant_id` on writes. Business code cannot "forget" to add a tenant condition.

The few scenarios that need cross-tenant access (for example, looking up an account by username at sign-in, counting accounts per tenant, or background scheduled jobs) must explicitly enter the "system context", so they are easy to spot in code review.

## Shared versus tenant-owned

| Shared platform-wide | Owned by each tenant |
| --- | --- |
| Applications and the resource catalog, the API catalog, OAuth clients, plugins | Accounts, departments, user groups, positions, roles, grants, assignments, data and field policies, separation of duties, access requests and reviews, identity sources, data services, auditing |

Usernames are unique across the entire platform, so no tenant needs to be selected at sign-in.

## The platform tenant

The platform tenant is created by initialization and cannot be disabled. Its administrators manage the platform-shared data and the other tenants; only roles in the platform tenant can use the "all tenants" data scope.

## IDs

All primary keys are TSIDs: 64-bit, time-ordered, and guaranteed unique across the cluster by node numbers. They exceed the integers JavaScript can represent exactly, so they are always passed as strings in JSON. Each instance in a cluster needs a distinct `GRANTFORGE_ID_NODE`.
