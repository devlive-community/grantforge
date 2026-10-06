---
title: Permission model
description: The precise semantics of resources, grant derivation, inheritance, assignment, and data and field rules, plus authorization snapshots and versions.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

This page describes the precise evaluation rules. For a conceptual introduction, see [Core concepts](/en/start/concepts/).

## Resource tree and types

Resources belong to an application and are organized as a tree with at most 15 levels. The type determines where a resource can be placed:

| Type | Allowed parents |
| --- | --- |
| Module | Top level, modules |
| Menus, pages | Top level, modules, menus |
| Tabs | Pages, tabs |
| Buttons | Pages, tabs |
| APIs, data entities | Top level, modules |
| Fields | Data entities |

Resources can declare dependencies on each other: "required" (granting a resource also grants the resources it depends on) or "optional" (only surfaced to administrators, never granted automatically); dependency cycles are not allowed. The console's pages, buttons, and the APIs they need are declared by the frontend's permission manifest and synchronized as built-in resources when the server starts.

## Grant derivation

For an account, the evaluator first determines its **effective roles**:

1. Roles assigned directly to the account;
2. Roles assigned to the account's user groups, departments (including assignments to ancestor departments with "include sub-departments"), and positions it holds;
3. Only assignments whose validity period covers the current time, and only enabled roles;
4. Inheritance is expanded: all ancestor roles of a role (disabled ancestors do not pass grants through).

Their grants are then merged:

- Allowing a resource implies allowing the resource itself, its ancestors in the tree (so it stays visible), and the resources it "required"-depends on (recursively).
- Denying a resource applies to it and all of its descendants, and **takes precedence over any allow**.
- Disabled resources and their descendants have no effect.
- System roles are equivalent to allowing the entire subtree of their module (such as `system`, `data`, `platform`).

The result is the set of available resources, plus the permission codes of the API resources among them.

## Privilege escalation protection

- To grant an "allow", the grantor must be able to use that resource themselves (holders of system roles are exempt for business applications).
- When assigning a role or configuring inheritance, the grantor must themselves cover every resource the role covers in each application.
- The platform administrator role can only be assigned, modified, or removed by its current holders.
- "Deny" is unrestricted: anyone with grant permissions can tighten permissions.

## Data rules

Data policies belong to roles and define scopes by "entity × action × effect": `ALL` (platform tenant only), `TENANT`, `ORG_AND_CHILDREN`, `ORG`, `CUSTOM_ORGS`, `SELF`, `CONDITION`. A row is accessible if and only if it satisfies at least one allow rule and no deny rule.

Conditions are structured JSON, never executed, and validated before being persisted:

```json
{ "and": [
  { "field": "status", "op": "eq", "value": "ACTIVE" },
  { "not": { "field": "orgUnitId", "op": "in", "value": { "var": "subject.orgUnitIds" } } }
] }
```

The only variables are `subject.id`, `subject.username`, `subject.orgUnitIds`, `subject.groupCodes`, `subject.positionCodes`, and `now`; operators are restricted by field type; nesting is limited to 5 levels and 50 nodes. The server translates rules into JPA `Specification`s, and the SDKs translate them with the same semantics inside business applications.

## Field rules

Field policies define how a field can be read (visible, masked, hidden) and written (editable, read-only); with multiple roles the most permissive setting wins, and a field with no settings is fully open. Read rules are enforced during JSON serialization (the same DTO renders differently to different people), write rules are enforced in the service layer, and modifying a read-only field returns `GF-FIELD-001` naming the field.

## Snapshots and versions

The evaluation result is an **authorization snapshot**: available resources, permission codes, data rules, and field rules. Snapshots are cached per account; any change to grants, assignments, inheritance, the resource catalog, policies, or memberships bumps the version of the affected scope (catalog or tenant) and invalidates the cache. Every endpoint that requires permissions returns the current version in the `X-Authorization-Version` response header, which the console uses to decide whether to reload the menu; the Open API expresses the same version with an ETag.
