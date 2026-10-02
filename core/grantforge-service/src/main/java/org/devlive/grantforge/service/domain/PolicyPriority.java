// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service.domain;

/** Whether a policy is weighed with the others or before them. */
public enum PolicyPriority
{
    /** Weighed with the other normal policies; a deny among them beats an allow. */
    NORMAL,
    /** Weighed before every normal policy: what it decides stands. */
    OVERRIDE
}
