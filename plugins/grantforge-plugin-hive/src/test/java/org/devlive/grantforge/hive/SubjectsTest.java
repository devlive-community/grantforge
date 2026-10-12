// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hive;

import org.junit.jupiter.api.Test;

import javax.security.auth.Subject;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubjectsTest
{
    @Test
    void runsWorkAsTheSubject() throws Exception
    {
        Subject subject = new Subject();
        assertThat(Subjects.current()).isNull();
        assertThat(Subjects.callAs(subject, Subjects::current)).isSameAs(subject);
        Subject inner = new Subject();
        assertThat(Subjects.callAs(subject, () -> Subjects.callAs(inner, Subjects::current))).isSameAs(inner);
        assertThat(Subjects.current()).isNull();
    }

    @Test
    void throwsWhatTheWorkThrew()
    {
        Subject subject = new Subject();
        assertThatThrownBy(() -> Subjects.callAs(subject, () -> {
            throw new IOException("checked");
        })).isInstanceOf(IOException.class).hasMessage("checked");
        assertThatThrownBy(() -> Subjects.callAs(subject, () -> {
            throw new IllegalStateException("unchecked");
        })).isInstanceOf(IllegalStateException.class).hasMessage("unchecked");
        assertThatThrownBy(() -> Subjects.callAs(subject, () -> {
            throw new AssertionError("error");
        })).isInstanceOf(AssertionError.class).hasMessage("error");
    }
}
