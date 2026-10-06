---
title: Periodic Access Reviews
description: Periodically review who holds which roles; reviewers keep or revoke item by item; revoked assignments are removed when the round completes.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

**Access Control → Access Reviews**: periodically review who holds which roles. Reviewers decide item by item to keep or revoke, and revoked assignments are removed when the round completes.

![Access reviews](/screenshots/access-reviews.png)

## Review plans

| Setting | Description |
| --- | --- |
| Name, description | For example "Quarterly review of finance roles" |
| Roles | The roles to review; each round lists all of their current assignments |
| Round duration | 1–90 days; the round completes automatically when it ends |
| Repeat interval | Leave empty to start rounds only manually; otherwise the next round starts automatically at the interval |
| Unreviewed items | Items without a decision when the round ends: **keep** or **revoke** |
| Enabled | Only controls whether rounds start automatically on schedule |

## A review round

1. Click **Start Now**, or wait for the plan to start a round automatically. GrantForge creates one review item per assignment (except system roles of system accounts).
2. During the round, reviewers choose **Keep** or **Revoke** item by item or in batches; a revoke can include a note, and decisions can be withdrawn before the round ends.
3. You cannot review roles you hold yourself through direct assignment, a user group, a department or a position.
4. An administrator can **Complete Round** (apply all decisions, with undecided items handled according to the plan settings) or **Cancel Round** (no changes are made). Rounds not finished by the deadline complete automatically.

Revoked assignments are deleted when the round completes. Every step is recorded in the audit log, providing an audit trail for internal-control audits.
