// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.common.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.regex.Pattern;

/**
 * Callers need a grant of the named permission. Several endpoints may share one permission, such as the list and
 * the detail of users ({@code system.user.read}); the catalog holds it as the API resource {@code api:<value>}.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface RequirePermission
{
    /** Permission codes: dot-separated lowercase words, such as {@code system.user.read}; at most 120 characters. */
    Pattern CODE = Pattern.compile("(?=.{3,120}$)[a-z][a-z0-9-]*(\\.[a-z][a-z0-9-]*)+");

    /**
     * Returns the permission.
     *
     * @return the permission code; see {@link #CODE}
     */
    String value();
}
