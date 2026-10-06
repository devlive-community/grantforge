---
title: Separation of Duties
description: Configure roles that must not be held by the same person, enforce or report only, and review current conflicts.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

**Access Control → Separation of Duties** configures roles that must not be held by the same person (such as payment and approval) and shows the accounts that currently violate a constraint.

![Separation of duties](/screenshots/sod.png)

## Constraints

| Setting | Description |
| --- | --- |
| Mutually exclusive roles | 2–50 roles |
| Maximum held per account | Default 1; can be set to "at most two out of three" |
| Mode | **Enforce**: rejects role assignments and inheritance that would create a conflict; **Report only**: allows the change but shows it in the conflict list |
| Enabled | A disabled constraint neither rejects nor reports |

"Holding" covers every path: direct assignment, obtaining the role through a user group, department or position, and obtaining it through role inheritance. Assignments outside their validity period and disabled roles do not count.

## When enforcement happens

In enforce mode, the following changes are checked against every account they touch before being saved:

- Assigning a role, or changing an assignment's validity period or "include sub-departments" flag;
- Changing a role's inheritance;
- Approving an access request (see [access requests and approvals](/en/guide/access-requests/)).

Only conflicts **newly created by this change** are rejected, with a message saying who, which roles, and which constraint was violated. Conflicts that already existed before the constraint took effect do not block other unrelated changes; they stay visible in the conflict list and have to be resolved manually.

> [!WARNING]
> Adding people to user groups, departments or positions does not perform this check. Conflicts arising through those paths appear in the conflict list — review it regularly.

## Conflict list

The right-hand side lists every account that violates an enabled constraint (in either mode): the account, the constraint, the roles held and the allowed maximum.
