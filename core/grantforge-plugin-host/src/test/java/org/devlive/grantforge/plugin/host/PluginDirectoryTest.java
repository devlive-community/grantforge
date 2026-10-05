// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.plugin.host;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PluginDirectoryTest
{
    @TempDir
    private Path root;

    @Test
    void anExplicitDirectoryWins()
    {
        assertThat(PluginDirectory.choose(" /opt/plugins ", root)).isEqualTo(Path.of("/opt/plugins"));
    }

    @Test
    void aServerRunFromTheSourcesUsesTheRepositorysPluginModules() throws IOException
    {
        Files.writeString(root.resolve("pom.xml"), "<project/>");
        Files.createDirectories(root.resolve("core"));
        Path plugins = Files.createDirectories(root.resolve("plugins"));
        Path classes = Files.createDirectories(root.resolve("core/grantforge-plugin-host/target/classes"));

        assertThat(PluginDirectory.sourceTree(classes)).isEqualTo(plugins);
        // The working directory of the tests has no plugins folder, as an IDE's run of the server may not.
        assertThat(PluginDirectory.choose("", classes)).isEqualTo(plugins);
    }

    @Test
    void aReleaseRunsFromJarsAndKeepsThePluginsFolder() throws IOException
    {
        Path jar = Files.writeString(root.resolve("grantforge-plugin-host.jar"), "");

        assertThat(PluginDirectory.sourceTree(jar)).isNull();
        assertThat(PluginDirectory.sourceTree(null)).isNull();
        assertThat(PluginDirectory.sourceTree(Files.createDirectories(root.resolve("elsewhere")))).isNull();
        assertThat(PluginDirectory.choose("", jar)).isEqualTo(Path.of(PluginDirectory.STANDARD));
    }

    @Test
    void findsWhereClassesCameFrom()
    {
        assertThat(PluginDirectory.codeSource(PluginDirectory.class)).isDirectory();
        assertThat(PluginDirectory.codeSource(String.class)).isNull();
    }
}
