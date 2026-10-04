// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.persistence.secured;

import org.junit.jupiter.api.Test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static org.assertj.core.api.Assertions.assertThat;

class SecuredFieldTest
{
    private record Sample(@SecuredField(entity = "user", field = "email", name = "E-mail") String email)
    {
    }

    @Test
    void marksRecordComponentsAtRunTime() throws Exception
    {
        assertThat(SecuredField.class.getAnnotation(Retention.class).value()).isEqualTo(RetentionPolicy.RUNTIME);
        assertThat(SecuredField.class.getAnnotation(Target.class).value()).containsExactly(ElementType.RECORD_COMPONENT);
        SecuredField declared = Sample.class.getRecordComponents()[0].getAnnotation(SecuredField.class);
        assertThat(declared.entity()).isEqualTo("user");
        assertThat(declared.field()).isEqualTo("email");
        assertThat(declared.name()).isEqualTo("E-mail");
    }
}
