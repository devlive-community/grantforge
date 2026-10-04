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
 * Lets data policies' conditions test an attribute of a {@link GrantForgeEntity}. Its kind follows the attribute's type:
 * text, a number, a boolean, an enum (whose constants are the choices) or a moment.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface GrantForgeField
{
    /**
     * Says how people call the attribute, as GrantForge's condition builder shows it.
     *
     * @return the name
     */
    String value();
}
