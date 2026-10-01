// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.audit.domain;

/** Whether the audited action took place. */
public enum AuditOutcome
{
    /** The action happened. */
    SUCCESS,
    /** The action was refused. */
    FAILURE
}
