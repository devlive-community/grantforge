// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class SetupTokenAnnouncerTest
{
    private final SetupService setup = mock(SetupService.class);
    private final SetupTokenAnnouncer announcer = new SetupTokenAnnouncer(setup);

    @Test
    void generatedTokensAreLogged(CapturedOutput output)
    {
        when(setup.isRequired()).thenReturn(true);
        when(setup.issueToken()).thenReturn(Optional.of("generated-token"));

        announcer.announce();

        assertThat(output.getOut()).contains("setup token: generated-token");
    }

    @Test
    void configuredTokensAreNotRepeated(CapturedOutput output)
    {
        when(setup.isRequired()).thenReturn(true);
        when(setup.issueToken()).thenReturn(Optional.empty());

        announcer.announce();

        assertThat(output.getOut()).contains("grantforge.setup.token");
    }

    @Test
    void nothingHappensAfterSetup()
    {
        when(setup.isRequired()).thenReturn(false);

        announcer.announce();

        verify(setup, never()).issueToken();
    }
}
