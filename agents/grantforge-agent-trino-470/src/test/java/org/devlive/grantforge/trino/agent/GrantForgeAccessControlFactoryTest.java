// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.trino.agent;

import io.trino.spi.security.SystemAccessControl;
import io.trino.spi.security.SystemAccessControlFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class GrantForgeAccessControlFactoryTest
{
    @TempDir
    Path temporary;

    @Test
    void isNamedGrantforgeAndStartsTheAgent() throws Exception
    {
        GrantForgeAccessControlFactory factory = new GrantForgeAccessControlFactory(GrantForgeSystemAccessControl::new);
        assertThat(factory.getName()).isEqualTo("grantforge");
        Map<String, String> settings = TestRuntime.settings(temporary);
        settings.put("grantforge.server.url", "http://127.0.0.1:1/");
        SystemAccessControl control = factory.create(settings, mock(SystemAccessControlFactory.SystemAccessControlContext.class));
        assertThat(control).isInstanceOf(GrantForgeSystemAccessControl.class);
        control.shutdown();
    }

    @Test
    void readsTheVersionTheBuildWrote() throws Exception
    {
        assertThat(GrantForgeAccessControlFactory.version()).isNotBlank().doesNotContain("${");
        assertThat(GrantForgeAccessControlFactory.version(stream("grantforge.version = 2026.2.0 \n"))).isEqualTo("2026.2.0");
        assertThatThrownBy(() -> GrantForgeAccessControlFactory.version(null)).isInstanceOf(IOException.class).hasMessageContaining("has no");
        assertThatThrownBy(() -> GrantForgeAccessControlFactory.version(stream("grantforge.version = \n"))).isInstanceOf(IOException.class)
                .hasMessageContaining("names no grantforge.version");
        assertThatThrownBy(() -> GrantForgeAccessControlFactory.version(stream("other=1\n"))).isInstanceOf(IOException.class);
    }

    private static InputStream stream(String text)
    {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.ISO_8859_1));
    }

    @Test
    void namesSettingsItCannotUse() throws Exception
    {
        GrantForgeAccessControlFactory factory = new GrantForgeAccessControlFactory(GrantForgeSystemAccessControl::new);
        SystemAccessControlFactory.SystemAccessControlContext context = mock(SystemAccessControlFactory.SystemAccessControlContext.class);
        assertThatIllegalArgumentException().isThrownBy(() -> factory.create(Map.of(), context)).withMessageContaining("grantforge.token.file");
        Map<String, String> settings = TestRuntime.settings(temporary);
        settings.put("grantforge.token.file", temporary.resolve("missing").toString());
        assertThatThrownBy(() -> factory.create(settings, context)).isInstanceOf(UncheckedIOException.class).hasMessageContaining("token");
    }
}
