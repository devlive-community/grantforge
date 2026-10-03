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
 * Puts a component of a record an API returns or accepts under field permissions, such as the e-mail address of a user: roles
 * may then see it, see it masked or not see it, and change it or not. The same field of an entity can appear in many
 * APIs; each appearance is marked, and the catalog lists the field once with every API it appears in.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.RECORD_COMPONENT)
public @interface SecuredField
{
    /**
     * Names the {@link SecuredEntity} the field belongs to, such as {@code user}.
     *
     * @return the entity's code
     */
    String entity();

    /**
     * Names the field within its entity, such as {@code email}; its catalog resource is the entity's followed by a dot
     * and this code, such as {@code entity:user.email}.
     *
     * @return 1 to 64 letters or digits, starting with a lowercase letter
     */
    String field();

    /**
     * Says what the field is, as the catalog shows it, such as {@code E-mail}; every appearance of a field gives the
     * same name.
     *
     * @return the name
     */
    String name();
}
