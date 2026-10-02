// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.persistence.secured;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Puts an entity under data permissions: roles get to see, change or export only some of its rows. What the rows belong
 * to decides which scopes apply: an owning account allows "own rows only", an owning department allows department
 * scopes, and fields marked {@link FilterableField} allow conditions.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface SecuredEntity
{
    /**
     * Names the entity in permissions, such as {@code user}; its catalog resource is {@code entity:} followed by the code.
     *
     * @return 2 to 41 lowercase letters, digits or '-', starting with a letter
     */
    String code();

    /**
     * Says what the rows are, as the catalog shows it, such as {@code Users}.
     *
     * @return the name
     */
    String name();

    /**
     * Names the attribute holding the account a row belongs to, such as {@code id} for accounts themselves or
     * {@code actorId} for audit events; empty if rows belong to no account.
     *
     * @return the attribute, of type {@code Long}
     */
    String owner() default "";

    /**
     * Names the attribute holding the department a row belongs to, such as {@code id} for departments themselves;
     * empty if rows belong to no department or to their owner's departments ({@link #unitFromOwner()}).
     *
     * @return the attribute, of type {@code Long}
     */
    String unit() default "";

    /**
     * Says that a row belongs to the departments its owner is a member of, such as an account to its own.
     *
     * @return {@code true} to take the departments from the owner; needs {@link #owner()} and no {@link #unit()}
     */
    boolean unitFromOwner() default false;

    /**
     * Names the attribute holding the tenant, for entities that are not tenant-scoped entities but carry a tenant, such
     * as audit events; empty otherwise.
     *
     * @return the attribute, of type {@code Long}
     */
    String tenant() default "";
}
