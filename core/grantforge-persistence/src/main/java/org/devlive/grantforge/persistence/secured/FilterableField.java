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
 * Lets data permission conditions test a field of a {@link SecuredEntity}, such as {@code status = ACTIVE}. Only marked
 * fields can be tested, so conditions never reach secrets such as password hashes.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface FilterableField
{
    /**
     * Says what the field is, as conditions show it, such as {@code Status}.
     *
     * @return the name
     */
    String value();
}
