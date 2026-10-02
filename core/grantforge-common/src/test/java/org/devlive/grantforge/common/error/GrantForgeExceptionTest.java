// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.common.error;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GrantForgeExceptionTest
{
    @Test
    void carriesCodeDetailAndArguments()
    {
        GrantForgeException error = new GrantForgeException(CommonErrorCode.NOT_FOUND, "user 7 not found", "user", 7);

        assertThat(error.getErrorCode()).isEqualTo(CommonErrorCode.NOT_FOUND);
        assertThat(error.getMessage()).isEqualTo("user 7 not found");
        assertThat(error.getArguments()).containsExactly("user", 7);
        assertThat(error.getCause()).isNull();
    }

    @Test
    void canNameTheInputsAtFault()
    {
        IllegalStateException cause = new IllegalStateException("x");
        GrantForgeException plain = new GrantForgeException(CommonErrorCode.BAD_REQUEST, "bad", cause, "a");
        GrantForgeException named = plain.withFieldIssues(List.of(FieldIssue.of("url", "error.required", 3)));

        assertThat(plain.getFieldIssues()).isEmpty();
        assertThat(named.getFieldIssues()).containsExactly(new FieldIssue("url", "error.required", List.of(3)));
        assertThat(named).hasMessage("bad").hasCause(cause);
        assertThat(named.getErrorCode()).isEqualTo(CommonErrorCode.BAD_REQUEST);
        assertThat(named.getArguments()).containsExactly("a");
    }

    @Test
    void keepsTheCause()
    {
        IOException cause = new IOException("disk");

        assertThat(new GrantForgeException(CommonErrorCode.INTERNAL, "failed", cause).getCause()).isSameAs(cause);
    }

    @Test
    void argumentsAreACopyAndImmutable()
    {
        Object[] arguments = {"a"};
        GrantForgeException error = new GrantForgeException(CommonErrorCode.CONFLICT, "dup", arguments);
        arguments[0] = "changed";

        assertThat(error.getArguments()).containsExactly("a");
        assertThatThrownBy(() -> error.getArguments().add("b")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @SuppressWarnings("NullAway") // deliberately violates the non-null contract to test the guards
    void rejectsNullInputs()
    {
        assertThatNullPointerException().isThrownBy(() -> new GrantForgeException(null, "detail"));
        assertThatNullPointerException().isThrownBy(() -> new GrantForgeException(CommonErrorCode.INTERNAL, null));
        assertThatNullPointerException()
                .isThrownBy(() -> new GrantForgeException(CommonErrorCode.INTERNAL, "detail", "a", null));
    }
}
