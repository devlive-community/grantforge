// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.sdk;

import org.junit.jupiter.api.Test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static org.assertj.core.api.Assertions.assertThat;

class RequirePermissionTest
{
    @Test
    void isReadAtRunTimeOnMethodsAndTypes()
    {
        assertThat(RequirePermission.class.getAnnotation(Retention.class).value()).isEqualTo(RetentionPolicy.RUNTIME);
        assertThat(RequirePermission.class.getAnnotation(Target.class).value()).containsExactly(ElementType.METHOD, ElementType.TYPE);
    }
}
