---
title: Introduction
description: What GrantForge is, the problems it solves, and how it differs from common alternatives.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

GrantForge is an open source (MIT) unified permissions platform. It centralizes "who can do what and which data they can see": you maintain users and organizations in the console, define roles, and grant roles menus, buttons, APIs, data rows, and fields, while your application signs users in through OAuth 2.1 / OpenID Connect and then checks permissions via the open API or an SDK.

![GrantForge console overview](/screenshots/dashboard.png)

## What Problems It Solves

Once a system grows to a certain size, permissions tend to scatter everywhere: menus live in frontend configuration, endpoints each do their own annotation-based checks, and data scope depends on hand-written SQL conditions. When someone leaves or moves to another role, nobody can say for sure what they can still do. GrantForge unifies all of this into a single model:

- **Define once, enforce everywhere**: console pages, buttons, REST endpoints, data entities, and fields are all "resources"; roles are granted on resources, and the same grants drive both frontend visibility and backend enforcement.
- **Visible permissions**: at any moment you can answer "why can this person see that page" or "who is affected if I change this role"; grant changes come with previews and an audit trail.
- **Governance ready**: separation of duties, time-limited access requests, periodic reviews, and two-step verification satisfy the common requirements of MLPS (classified protection) and internal control audits.
- **Standards-based integration**: applications do not need to embed their own user system — sign users in with OIDC and query permissions with the access token.

## Feature Overview

| Area | Features |
| --- | --- |
| Identity and organization | Multi-tenancy, department trees, user groups, positions; CSV bulk import and export; LDAP/AD login and sync, OIDC federated login |
| Account security | Session management, password policies and lockout, TOTP two-step verification and recovery codes, re-verification for sensitive operations |
| Functional authorization | Resource catalog (modules, menus, pages, tabs, buttons, APIs), role inheritance, grant matrix, impact analysis |
| Data permissions | Restrict visible rows by condition (own records, own department and its sub-departments, specified departments, custom conditions), with read and write controlled separately |
| Field permissions | Hide, mask (email, phone number, ID number, etc.), or make fields read-only |
| Explainability and audit | Permission explanations, grant simulation, audit log query and export |
| Governance | Separation-of-duties constraints, access requests and approvals, periodic permission reviews |
| Application integration | OAuth 2.1 / OIDC authorization server, open API for permission queries, Java (Spring Boot) and JavaScript SDKs |
| External systems | Pluggable service types and policy engine (similar to Apache Ranger), data services, access policies, and agents |
| Delivery | Single distribution package, Docker image, Compose examples, Helm chart; H2, PostgreSQL, MySQL, MariaDB, Oracle, SQL Server |

## How It Differs from Common Approaches

> [!NOTE]
> GrantForge is not a library that only does RBAC, nor an IdP that only does single sign-on. It puts identity, authorization, and governance in one model, and makes every grant explainable.

- **Compared with hand-writing permissions in code**: permission rules are maintained in the console, so changing grants does not require releasing the application; you can see the impact before granting, and there is an audit trail afterwards.
- **Compared with authentication-only IdPs (Keycloak and the like)**: GrantForge ships with an authorization model fine-grained down to buttons, data rows, and fields, plus governance features such as separation of duties and periodic reviews; it can also act as an IdP itself, or federate an existing LDAP or OIDC identity source.
- **Compared with Apache Ranger**: GrantForge borrows Ranger's service type, policy, and agent architecture for managing permissions on external data systems, while remaining first and foremost a permissions platform for business applications.

## Next Steps

- [Get started in five minutes](/en/start/quick-start/): download, start, complete initialization, and grant the first role.
- [Core concepts](/en/start/concepts/): how resources, roles, grants, assignments, and evaluation fit together.
- [Integration overview](/en/integration/overview/): put GrantForge to work in your application.
