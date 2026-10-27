---
title: Identity sources (LDAP and OIDC)
description: Let users sign in with your corporate directory (LDAP/AD) or an OpenID Connect provider, with automatic account creation and synchronization.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

**Access control → Identity sources** configures the directories or identity providers users can sign in with. Passwords for these accounts are stored in the identity source; GrantForge neither stores them nor can change them.

![Identity sources](/screenshots/identity-sources.png)

## LDAP / Active Directory

Click "Add identity source" and choose the type "LDAP directory":

| Setting | Description |
| --- | --- |
| Directory URL | `ldap://` or `ldaps://`; separate multiple addresses with spaces for failover |
| User Base DN | For example `ou=people,dc=example,dc=com` |
| Query account | An account (Bind DN) and its password used to look up users; leave empty for anonymous queries |
| User filter | Defaults to `(&(objectClass=person)(uid={0}))`, where `{0}` is the name the user entered |
| Attributes | Attributes for username, display name, email, and unique identifier; the defaults fit OpenLDAP (`uid`, `cn`, `mail`, `entryUUID`), while Active Directory uses `sAMAccountName` and `objectGUID` |
| Automatically create accounts for new users | When enabled, a user in the directory gets an account on first sign-in |
| Sync interval | Leave empty for manual sync only; minimum 15 minutes |
| Disable accounts of users no longer in the directory | During sync, disable users that no longer exist in the directory |

After saving, click **Test connection** to confirm the configuration is correct.

At sign-in, GrantForge first looks up the user with the query account, then verifies the password by binding to the directory as that user with the password the user entered.

**Sync** creates accounts for new users in the directory, updates the names and emails of existing accounts, and — according to the setting — disables users who have left (ending their sessions at the same time). Sync results are shown on the identity source card.

## OpenID Connect

Choose the type "OpenID Connect" to connect Keycloak, Azure AD, Okta, another GrantForge instance, and more:

| Setting | Description |
| --- | --- |
| Issuer URL | The provider's issuer address; GrantForge discovers endpoints and keys from its discovery document |
| Client ID / secret | The client registered with the provider; without a secret it acts as a public client using PKCE |
| Redirect URI | The `<GrantForge>/api/v1/auth/federated/callback/<编码>` shown on the page; it must be registered on the provider's client |
| Scopes and claims | Defaults to `openid profile email`; username, display name, and email are taken from the `preferred_username`, `name`, and `email` claims respectively |

Once enabled, a "Sign in with <name>" button appears on the sign-in page. After signing in at the provider, the user returns to GrantForge; accounts with two-step verification enabled must also enter a verification code.

## Account rules

- An identity source user's unique identifier (`entryUUID`/`objectGUID` for LDAP, `sub` for OIDC) maps to one GrantForge account; renaming does not affect the mapping.
- If a local account with the same username already exists, it is **not automatically linked** — sign-in is rejected, and an administrator must first rename or delete the local account. This prevents a same-named user in the directory from taking over an existing account.
- An identity source that does not automatically create accounts only lets already-linked users sign in.
- After an identity source is disabled, its users cannot sign in; an identity source still used by accounts cannot be deleted.
- Identity source accounts can be assigned roles and enable two-step verification as usual.

Configure organization synchronization under Data sources, independently of the existing OIDC sign-in settings.

## Data sources and organization synchronization

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
