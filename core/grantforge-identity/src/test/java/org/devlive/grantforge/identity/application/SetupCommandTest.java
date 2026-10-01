// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SetupCommandTest
{
    @Test
    void toStringHidesSecrets()
    {
        String text = new SetupCommand("the-token", "Acme", "admin", "the-password", null).toString();

        assertThat(text).contains("Acme", "admin").doesNotContain("the-token", "the-password");
    }
}
