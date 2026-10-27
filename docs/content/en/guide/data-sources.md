---
title: Data sources
description: Synchronize organization directories independently of SSO authentication.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Identity sources continue to authenticate OIDC and LDAP accounts. Data sources synchronize organizations independently; disabling one does not disable OIDC sign-in.

1. Configure OpenID Connect under **Access control → Identity sources**.
2. Add a DingTalk source under **Access control → Data sources** and select the linked OIDC source.
3. Enter CorpId, AppKey, AppSecret and the root department ID (usually `1`). The credential is encrypted separately, never returned by the API, and preserved when an edit leaves it blank.
4. Optionally mount under a local department. The default schedule is 30 minutes; supported intervals are 15–10080 minutes. Leave it blank for manual synchronization.
5. Test the connection and perform the first sync. The application's directory read permissions must cover the selected department subtree.
6. Sign in using SSO to fill department memberships and a primary department. SSO emails must be administrator-managed, unavailable for employee impersonation, and match DingTalk emails. Enterprise email is used when employee email is absent.

Sign-in uses only the local directory snapshot. With MFA, membership updates wait for the second factor. Accounts signing in before the first sync retain the trusted OIDC email separately and are linked when synchronization completes. Editable profile email never establishes bindings. Missing, ambiguous or already bound mailboxes do not grant new memberships.

Bindings follow stable external employee IDs across mailbox changes. Sync preserves local memberships, groups, positions and direct role grants. A valid existing primary department is retained; otherwise one is selected in stable department-ID order. Existing department role inheritance and authorization version invalidation apply to organization changes.

Once directory data or pending accounts exist, the linked identity source, enterprise, application, root and mount cannot be retargeted. Managed departments and memberships are read-only in organization and account administration. Missing departments are retained to preserve existing grants; departed employees lose this source's memberships without their accounts being automatically disabled. Sources owning departments can be disabled but not directly deleted.

All remote pages must succeed before a snapshot is applied atomically. Fetch errors, invalid hierarchies or changed authorization scope preserve the previous snapshot. Restore the approved directory scope before retrying a scope change. Sync summaries, diagnostics and audit events describe attempts.

DingTalk access uses the [official server SDK](https://open.dingtalk.com/document/development/download-the-server-side-sdk). Provider adapters emit neutral department and employee snapshots, so future providers can reuse organization reconciliation, account binding and permission updates.
