---
title: 2026.0.0 (rebuild)
description: "GrantForge rewritten from the ground up: multi-tenant identity, resource and role authorization, data and field permissions, standard protocol integration, enterprise governance, and plugin-based external system permissions."
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

2026.0.0 is a complete rewrite that supersedes 1.x (AuthX). It is no longer an admin template but a standalone identity and permission platform. Accounts, roles, and menus from 1.x can be imported; see [Upgrading and legacy version migration](/en/deploy/upgrade/).

## Platform

- Spring Boot 4 and Java 17 runtime; a single release package contains the server and the console, with Docker images, Compose, and a Helm Chart also provided.
- Supports H2, PostgreSQL, MySQL, MariaDB, Oracle, and SQL Server; migrations are managed by Liquibase and every commit is tested against nine database versions.
- A first-start initialization wizard that creates the platform administrator using a one-time token from the service logs.
- Cluster deployment: sessions are stored in the database and IDs are time-ordered TSIDs.
- A brand-new console: Vue 3 and Tailwind CSS, light and dark themes, Chinese and English.

## Identity and Organization

- Multi-tenancy: accounts, organizations, and authorizations are fully isolated per tenant.
- Users, department trees, user groups, and positions, with CSV bulk import and export.
- Argon2id passwords, configurable password policies and lockout, session management, TOTP two-step verification with recovery codes, and re-verification for sensitive operations.
- LDAP / Active Directory and OIDC identity sources with synchronization and federated login.

## Authorization

- Resource catalog: modules, menus, pages, tabs, buttons, APIs, data entities, and fields, along with the dependencies among them. The console's own pages, buttons, and APIs are also part of the catalog and subject to the same authorization.
- Roles and authorization: allow and deny, inheritance, assignment by user / user group / department / position, and validity periods.
- Data permissions: restrict visible rows by organization scope or structured conditions.
- Field permissions: hide, mask, or make fields read-only per role.
- Permission explanations, per-user simulation, complete audit logs, and checks for invalid configurations.

## Governance

- Separation of duties: mutually exclusive roles are rejected during assignment, inheritance, and requests, and existing conflicts can be detected.
- Permission requests: users request eligible roles; once approved, they take effect for a limited time and are automatically revoked on expiry.
- Periodic permission reviews.

## Application Integration

- Built-in OAuth 2.1 / OpenID Connect authorization server: authorization code + PKCE, client credentials, refresh token rotation, and signing key rotation.
- Open API for permission queries, with versioning and ETags.
- A Spring Boot Starter and a JavaScript SDK, with runnable sample applications.

## External System Permissions

- Pluggable service types: plugins define resource hierarchies, access types, masking, and row filtering; each plugin is loaded independently.
- A universal policy editor, Ed25519-signed policy snapshots, agent heartbeats, and access auditing.
- Sample plugins, an HDFS service type, and a Hadoop 3.5.0 NameNode agent; the Hive plugin and agents for other Hadoop versions are under development.

## Quality

- Performance benchmarks at the scale of one million accounts run nightly and fail if limits are exceeded.
- Static analysis (NullAway, Error Prone, Checkstyle, PMD, SpotBugs, ArchUnit), coverage thresholds, and full-stack browser tests.
- The documentation site was rebuilt with Next.js and Tailwind CSS.
