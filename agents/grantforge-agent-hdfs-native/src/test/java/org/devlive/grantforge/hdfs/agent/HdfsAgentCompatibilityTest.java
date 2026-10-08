// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hdfs.agent;

import org.apache.hadoop.util.VersionInfo;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HdfsAgentCompatibilityTest
{
    @Test
    void matchesExplicitAdapterMetadataAgainstTheBaselineNativeRuntime()
    {
        Properties build = metadata();
        HdfsAgentCompatibility.verify(build);
        assertThat(HdfsAgentCompatibility.agentVersion(build)).isEqualTo("2026.1.0-hadoop-2.7.7")
                .endsWith("-hadoop-" + VersionInfo.getVersion()).doesNotContain("${").hasSizeLessThanOrEqualTo(64);
    }

    @Test
    void checksBothJavaEightAndModernRuntimeVersionLabels()
    {
        HdfsAgentCompatibility.requireJava(8, "1.8");
        HdfsAgentCompatibility.requireJava(8, "11");
        HdfsAgentCompatibility.requireJava(17, "21");
        assertThatThrownBy(() -> HdfsAgentCompatibility.requireJava(17, "11"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("requires Java 17");
        assertThatThrownBy(() -> HdfsAgentCompatibility.requireJava(8, "unknown"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("cannot determine");
    }

    @Test
    void permitsPatchAndVendorLabelsOnlyWithinTheSameNumberedLine()
    {
        HdfsAgentCompatibility.requireLine("3.4", "3.4.3");
        HdfsAgentCompatibility.requireLine("3.4", "3.4.3-vendor.5");
        assertThatThrownBy(() -> HdfsAgentCompatibility.requireLine("3.4", "3.5.0"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("matching numbered agent");
        assertThatThrownBy(() -> HdfsAgentCompatibility.requireLine("2.7", "unknown"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> HdfsAgentCompatibility.requireLine("2.7", "2.70.1"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsMissingUnfilteredUnreadableAndOversizedMetadata()
    {
        assertThatThrownBy(() -> HdfsAgentCompatibility.load(null)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> HdfsAgentCompatibility.load(stream("grantforge.version=${project.version}\n")))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> HdfsAgentCompatibility.load(stream("grantforge.version=2026.1.0\n")))
                .isInstanceOf(IllegalStateException.class);
        String metadata = "grantforge.version=" + new String(new char[65]).replace('\0', 'x')
                + "\nhadoop.version=3.5.0\nhadoop.line=3.5\njava.minimum=17\nspi.family=superuser\n";
        assertThatThrownBy(() -> HdfsAgentCompatibility.load(stream(metadata))).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("64 characters");
        InputStream broken = new InputStream()
        {
            @Override
            public int read() throws IOException
            {
                throw new IOException("unreadable");
            }
        };
        assertThatThrownBy(() -> HdfsAgentCompatibility.load(broken)).isInstanceOf(IllegalStateException.class)
                .hasCauseInstanceOf(IOException.class);
    }

    @Test
    void loadingExplicitMetadataDoesNotDependOnAnyVersionResourceInTheSharedArtifact()
    {
        Properties loaded = HdfsAgentCompatibility.load(stream("grantforge.version=2026.1.0\nhadoop.version=2.7.7\n"
                + "hadoop.line=2.7\njava.minimum=8\nspi.family=parameters\n"));
        assertThat(loaded).containsEntry("hadoop.version", "2.7.7");
        HdfsAgentCompatibility.verify(loaded);
        loaded.setProperty("hadoop.line", "3.5");
        assertThatThrownBy(() -> HdfsAgentCompatibility.verify(loaded)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("matching numbered agent");
        loaded.setProperty("hadoop.line", "2.7");
        loaded.setProperty("spi.family", "context");
        assertThatThrownBy(() -> HdfsAgentCompatibility.verify(loaded)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("native context callbacks");
    }

    static Properties metadata()
    {
        Properties build = new Properties();
        build.setProperty("grantforge.version", "2026.1.0");
        build.setProperty("hadoop.version", "2.7.7");
        build.setProperty("hadoop.line", "2.7");
        build.setProperty("java.minimum", "8");
        build.setProperty("spi.family", "parameters");
        return build;
    }

    private static InputStream stream(String value)
    {
        return new ByteArrayInputStream(value.getBytes(StandardCharsets.ISO_8859_1));
    }
}
