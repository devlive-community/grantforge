// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

/** What an access question is about. */
public enum AccessKind
{
    /** A console resource, such as the page {@code system.user} or the button {@code system.user.btn.edit}. */
    RESOURCE,

    /** An API permission, such as {@code system.user.update}. */
    PERMISSION
}
