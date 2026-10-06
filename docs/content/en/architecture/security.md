---
title: Security design
description: The design of sessions, CSRF, passwords and lockouts, two-step verification and re-verification, encrypted storage, tokens, and auditing.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

## Console sessions

- Sessions are stored in the database (Spring Session JDBC); the browser only holds the `GRANTFORGE_SESSION` cookie: HttpOnly, SameSite=Lax, and Secure over HTTPS (or forced with `grantforge.security.cookie-secure`).
- The session ID and CSRF token are rotated at sign-in, on completing two-step verification, and on completing federated sign-in, to prevent session fixation.
- State-changing requests must carry the `X-XSRF-TOKEN` header, whose value comes from the `XSRF-TOKEN` cookie.
- Administrators can list and terminate any session; disabling, locking, or resetting the password immediately terminates all of that account's sessions; changing the password terminates sessions on other devices.

## Passwords

- New passwords are hashed with Argon2id; BCrypt and hashes imported from 1.x can still be verified, and are upgraded to the current algorithm at the next sign-in.
- Policy: length, character classes, history, and expiry are all configurable, and a password may not contain the username.
- The account is locked after the configured number of consecutive failures; an unknown username also performs one hash comparison, so response times do not reveal which usernames exist; disabled accounts and disabled tenants are only reported after the password verifies.

## Two-step verification and re-verification

- TOTP (RFC 6238, SHA-1, 6 digits, 30 seconds, tolerating one time step before and after); a code for the same time step can be used only once; 10 one-time recovery codes are stored as SHA-256 hashes.
- For accounts with two-step verification enabled, once the password is verified the session stays in a "pending" state for 5 minutes and is not signed in until the second step completes.
- Sensitive endpoints are annotated with `@RequireStepUp`: accounts with two-step verification enabled must have re-verified within a configurable time window, otherwise the call returns `GF-SECURITY-006` and the console shows a confirmation dialog before retrying.

## Encrypted storage

Identity source bind passwords and client secrets, authenticator keys, sensitive data service configuration, and the authorization server's signing private keys are all stored AES-GCM encrypted. The key comes from `grantforge.security.encryption-key`; when it is not configured, one is generated automatically and stored in the database (suitable for trials only). Agent tokens and OAuth client secrets are stored as hashes only.

## Tokens

- The authorization server stores hashes of tokens, not the tokens themselves.
- Refresh tokens rotate on every use; replaying an old token revokes the entire authorization.
- A disabled, locked, or password-change-required account, a disabled client, or a disabled tenant all block token renewal; the Open API re-validates the token on every call.
- Policy snapshots are signed with Ed25519, and agents verify the signature before using them.

## Endpoint hardening

- Every endpoint must declare its access mode, and an undeclared one prevents startup; endpoints that require permissions are checked against the latest snapshot on every call.
- Denied calls are written to the audit log (without blocking the request).
- A local account sharing a name with an external identity source account is never linked automatically, preventing account takeover.
- When exporting CSV, cells starting with `=`, `+`, `-`, or `@` are given a prefix to prevent formula injection.

## Auditing

All administrative operations, grant changes, sign-in events, and denied calls are recorded in the audit log; permission-related changes and their audit records commit in the same transaction, so a rolled-back change leaves no audit record behind.
