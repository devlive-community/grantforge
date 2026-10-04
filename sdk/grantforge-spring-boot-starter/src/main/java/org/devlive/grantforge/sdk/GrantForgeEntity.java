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
 * Puts a JPA entity of the application under GrantForge's data permissions. The starter declares it to GrantForge at
 * start-up, where roles get data policies on it; {@link GrantForgeDataScopes} turns the user's rules into a query
 * {@code Specification}. What rows belong to decides which scopes apply.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface GrantForgeEntity
{
    /**
     * Names the entity in GrantForge, such as {@code order}.
     *
     * @return 2 to 40 lowercase letters, digits or hyphens, starting with a letter
     */
    String code();

    /**
     * Says what the rows are, as GrantForge's console shows it.
     *
     * @return the name
     */
    String name();

    /**
     * Names the attribute holding the GrantForge account a row belongs to, for "own rows"; empty if none.
     *
     * @return the attribute, a number or text
     */
    String owner() default "";

    /**
     * Names the attribute holding the GrantForge department a row belongs to, for department scopes; empty if none.
     *
     * @return the attribute, a number or text
     */
    String unit() default "";

    /**
     * Names the attribute holding the GrantForge tenant of a row, which every rule but "all rows" then requires to be
     * the user's; empty if the application serves one tenant or keeps tenants apart itself.
     *
     * @return the attribute, a number or text
     */
    String tenant() default "";
}
