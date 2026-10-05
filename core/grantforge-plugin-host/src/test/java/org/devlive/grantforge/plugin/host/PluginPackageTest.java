// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.plugin.host;

import org.devlive.grantforge.plugin.api.PluginDescriptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PluginPackageTest
{
    @TempDir
    private Path root;

    @Test
    void candidatesAreJarsZipsAndDirectoriesButNotHiddenOnes()
            throws IOException
    {
        assertThat(PluginPackage.candidate(Files.createDirectories(root.resolve("hdfs")))).isTrue();
        assertThat(PluginPackage.candidate(Files.writeString(root.resolve("hive.JAR"), ""))).isTrue();
        assertThat(PluginPackage.candidate(Files.writeString(root.resolve("trino.zip"), ""))).isTrue();
        assertThat(PluginPackage.candidate(Files.writeString(root.resolve("readme.md"), ""))).isFalse();
        assertThat(PluginPackage.candidate(Files.createDirectories(root.resolve(PluginPackage.WORK)))).isFalse();
        // Plugin sources in the repository's plugins folder are no plugins, unless built into one in place.
        Path source = Files.createDirectories(root.resolve("grantforge-plugin-example"));
        Files.writeString(source.resolve("pom.xml"), "<project/>");
        assertThat(PluginPackage.candidate(source)).isFalse();
        Files.writeString(source.resolve(PluginDescriptor.FILE_NAME), "id: example");
        assertThat(PluginPackage.candidate(source)).isTrue();
        // A module counts once its build put the descriptor into target/classes and copied its libraries; the example
        // plugin, whose build copies none, stays a test fixture.
        Path module = Files.createDirectories(root.resolve("grantforge-plugin-hdfs"));
        Files.writeString(module.resolve("pom.xml"), "<project/>");
        assertThat(PluginPackage.candidate(module)).isFalse();
        Files.writeString(Files.createDirectories(module.resolve(PluginPackage.MODULE_CLASSES)).resolve(PluginDescriptor.FILE_NAME), "id: hdfs");
        assertThat(PluginPackage.candidate(module)).isFalse();
        Files.createDirectories(module.resolve(PluginPackage.MODULE_LIB));
        assertThat(PluginPackage.candidate(module)).isTrue();
    }

    @Test
    void readsABuiltModuleWithTheDependenciesItsBuildCopied()
            throws IOException
    {
        Path module = Files.createDirectories(root.resolve("grantforge-plugin-hdfs"));
        Files.writeString(module.resolve("pom.xml"), "<project/>");
        Path classes = Files.createDirectories(module.resolve(PluginPackage.MODULE_CLASSES));
        Files.writeString(classes.resolve(PluginDescriptor.FILE_NAME), """
                id: hdfs
                version: 1.0.0
                name: HDFS
                apiVersion: 1.0
                providers: org.example.HdfsProvider
                """);

        assertThatThrownBy(() -> PluginPackage.read(module, root.resolve(PluginPackage.WORK))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("./mvnw -pl plugins/grantforge-plugin-hdfs -am install -DskipTests");
        Path lib = Files.createDirectories(module.resolve(PluginPackage.MODULE_LIB));
        Files.writeString(lib.resolve("hadoop-client-api.jar"), "");
        Files.writeString(lib.resolve("notes.txt"), "");
        PluginPackage read = PluginPackage.read(module, root.resolve(PluginPackage.WORK));
        assertThat(read.location()).isEqualTo("grantforge-plugin-hdfs");
        assertThat(read.descriptor().id()).isEqualTo("hdfs");
        assertThat(read.urls()).extracting(url -> url.getPath().replaceAll(".*/(?=.)", "")).containsExactly("classes/", "hadoop-client-api.jar");
    }

    @Test
    void readsADirectoryWithItsJarsAndRefusesHugeDescriptors()
            throws IOException
    {
        Path plugin = Files.createDirectories(root.resolve("hdfs"));
        Files.writeString(plugin.resolve("grantforge-plugin.yaml"), """
                id: hdfs
                version: 1.0.0
                name: HDFS
                apiVersion: 1.0
                providers: org.example.HdfsProvider
                """);
        Files.writeString(plugin.resolve("core.jar"), "");
        Files.writeString(Files.createDirectories(plugin.resolve("lib")).resolve("dependency.jar"), "");
        PluginPackage read = PluginPackage.read(plugin, root.resolve(PluginPackage.WORK));
        assertThat(read.location()).isEqualTo("hdfs");
        assertThat(read.descriptor().providers()).containsExactly("org.example.HdfsProvider");
        assertThat(read.urls()).extracting(url -> url.getPath().replaceAll(".*/", "")).containsExactly("core.jar", "dependency.jar");

        Files.writeString(plugin.resolve("grantforge-plugin.yaml"), "x".repeat(70 * 1024));
        assertThatThrownBy(() -> PluginPackage.read(plugin, root)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("larger than 64 KB");
        assertThat(new PluginPackage("x", read.descriptor(), List.of()).urls()).isEmpty();
    }
}
