// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

/** What a separation-of-duties constraint does about a conflict. */
public enum SodMode
{
    /** Refuses role assignments and inheritance links that would create a conflict. */
    ENFORCE,

    /** Lets them through and lists the conflicts in the report only. */
    REPORT
}
