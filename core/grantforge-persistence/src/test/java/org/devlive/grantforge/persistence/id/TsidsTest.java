// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.persistence.id;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class TsidsTest
{
    @BeforeEach
    @AfterEach
    void resetGenerator()
    {
        Tsids.reset();
    }

    @Test
    void systemPropertyWinsOverEnvironment()
    {
        assertThat(Tsids.resolveNode("12", "34", () -> 99)).isEqualTo(12);
        assertThat(Tsids.resolveNode(" ", "34", () -> 99)).isEqualTo(34);
        assertThat(Tsids.resolveNode(null, null, () -> 99)).isEqualTo(99);
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "1024", "abc", "1.5"})
    void rejectsInvalidConfiguredNodes(String value)
    {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Tsids.resolveNode(value, null, () -> 0))
                .withMessageContaining(Tsids.NODE_PROPERTY);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Tsids.resolveNode(null, value, () -> 0))
                .withMessageContaining(Tsids.NODE_ENV);
    }

    @Test
    void configuredNodeIsUsedForEveryId()
    {
        Tsids.configure(77);

        long first = Tsids.next();
        long second = Tsids.next();

        assertThat(TsidGenerator.nodeOf(first)).isEqualTo(77);
        assertThat(second).isGreaterThan(first);
    }

    @Test
    void reconfiguringWithTheSameNodeIsAllowedButNotWithAnother()
    {
        Tsids.configure(5);
        Tsids.configure(5);

        assertThatIllegalStateException().isThrownBy(() -> Tsids.configure(6));
    }

    @Test
    void generatorIsCreatedOnceOnFirstUse()
    {
        TsidGenerator first = Tsids.generator();

        assertThat(Tsids.generator()).isSameAs(first);
    }
}
