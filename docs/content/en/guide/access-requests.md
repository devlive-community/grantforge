---
title: Access Requests and Approvals
description: Users request time-limited roles; approvers grant, reject or revoke early; expired roles are withdrawn automatically.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

## Administrators: make roles requestable

In **Access Control → Access Approvals**, click **Requestable Roles** and choose which custom roles can be requested, and the maximum number of days each role can be requested for (1–365). System roles cannot be requested.

## Users: requesting

Every signed-in user can see the roles available for request in **Workspace → My Requests**. Pick a role, enter a reason and a number of days, and submit; a request can be withdrawn before approval. You cannot request a role you already hold, or one for which you already have a request pending approval.

![My requests](/screenshots/requests.png)

Newly created users must change their initial password before they can use this page.

## Approvers: grant, reject, revoke

**Access Control → Access Approvals** lists pending, granted or all requests.

![Access approvals](/screenshots/access-approvals.png)

- **Grant**: you can shorten the number of days and add a comment. Once granted, the user gets the role immediately, and it expires automatically at the end time.
- **Reject**: you can give a reason.
- **Revoke**: takes back the role of an already granted request ahead of schedule.

Granting a request is equivalent to the approver assigning the role, so the same rules apply:

- Cannot grant a role beyond the approver's own permissions;
- [Separation of duties](/en/guide/sod/) constraints are enforced;
- Nobody can approve their own request;
- Approvers with two-step verification enabled must have verified within the last 10 minutes.

## Automatic expiry

Granted roles stop working immediately at the end time. A background job cleans up expired assignments every 5 minutes and marks the requests as "expired". The whole process (request, withdrawal, grant, rejection, revocation, expiry) is recorded in the audit log.
