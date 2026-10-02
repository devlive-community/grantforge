// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

/** A kind of inconsistency the catalog check-up finds. */
public enum HealthIssue
{
    /** A grant of a disabled resource: it gives nothing. */
    GRANT_ON_DISABLED,

    /** A grant of an API no server endpoint offers any more. */
    GRANT_ON_RETIRED_API,

    /** A grant past its expiry: it gives nothing and can go. */
    GRANT_EXPIRED,

    /** A button whose dependencies reach no API in service: it cannot do anything. */
    ACTION_WITHOUT_API,

    /** An API in service that no resource needs and no role is granted: nobody can call it. */
    UNUSED_API,

    /** A dependency on a disabled resource: the dependent cannot work. */
    DEPENDENCY_ON_DISABLED,

    /** A dependency on an API no server endpoint offers any more. */
    DEPENDENCY_ON_RETIRED_API
}
