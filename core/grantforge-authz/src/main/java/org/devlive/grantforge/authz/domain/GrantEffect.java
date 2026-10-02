// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

/** Whether a grant allows or denies a resource. */
public enum GrantEffect
{
    /** The role allows the resource. */
    ALLOW,

    /** The role denies the resource and everything below it; denials win over every allowance. */
    DENY
}
