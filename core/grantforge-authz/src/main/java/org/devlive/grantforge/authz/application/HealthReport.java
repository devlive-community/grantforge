// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import java.time.Instant;
import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * The result of a catalog check-up of one application.
 *
 * @param applicationId the application checked
 * @param checkedAt when
 * @param findings what is inconsistent, by issue and resource code
 */
public record HealthReport(long applicationId, Instant checkedAt, List<HealthFinding> findings)
{
    /** Copies the findings. */
    public HealthReport
    {
        requireNonNull(checkedAt, "checkedAt");
        findings = List.copyOf(requireNonNull(findings, "findings"));
    }
}
