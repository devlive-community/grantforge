// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.sdk;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Lets a controller method, or every method of a controller, run only for users who hold the API permissions in
 * GrantForge. Without a token GrantForge accepts the call answers 401, without the permissions 403. On a method it
 * replaces the controller's.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface RequirePermission
{
    /**
     * Returns the permission codes, all of which the user must hold.
     *
     * @return the codes, as in GrantForge's resource catalog
     */
    String[] value();
}
