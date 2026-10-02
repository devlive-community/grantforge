// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service.domain;

/** Who decided an access an agent reported. */
public enum Enforcer
{
    /** GrantForge's policies decided. */
    GRANTFORGE,
    /** No policy decided, so the system's own permissions did, such as HDFS ACLs. */
    NATIVE
}
