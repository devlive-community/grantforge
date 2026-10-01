// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

/** Tells platform administrators apart: system accounts of the platform tenant (D-34). */
@FunctionalInterface
public interface PlatformAdministrators
{
    /**
     * Returns whether an account administers the platform.
     *
     * @param accountId the account
     * @return {@code true} for a system account of the platform tenant
     */
    boolean isPlatformAdministrator(long accountId);
}
