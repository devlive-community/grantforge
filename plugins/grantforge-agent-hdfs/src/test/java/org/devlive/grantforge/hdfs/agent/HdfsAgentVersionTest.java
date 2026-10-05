// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hdfs.agent;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HdfsAgentVersionTest
{
    private static String load(String metadata)
    {
        return HdfsAgentVersion.load(new ByteArrayInputStream(metadata.getBytes(StandardCharsets.ISO_8859_1)));
    }

    @Test
    void reportsBothVersionsFromTheFilteredResourceAndCachesTheLabel() throws IOException
    {
        Properties filtered = new Properties();
        try (InputStream resource = requireNonNull(HdfsAgentVersion.class.getResourceAsStream(HdfsAgentVersion.RESOURCE))) {
            filtered.load(resource);
        }
        String grantforge = requireNonNull(filtered.getProperty("grantforge.version"));
        String hadoop = requireNonNull(filtered.getProperty("hadoop.version"));
        assertThat(grantforge).doesNotContain("${");
        assertThat(hadoop).doesNotContain("${");
        assertThat(HdfsAgentVersion.value()).isEqualTo(grantforge + "-hadoop-" + hadoop).hasSizeLessThanOrEqualTo(64);
        assertThat(HdfsAgentVersion.value()).isSameAs(HdfsAgentVersion.value());
    }

    @Test
    void acceptsReleaseAndSnapshotBuildsWithoutFixingEitherVersionInCode()
    {
        assertThat(load("grantforge.version=2027.2.3\nhadoop.version=3.6.1\n")).isEqualTo("2027.2.3-hadoop-3.6.1");
        assertThat(load("grantforge.version=2027.2.3-SNAPSHOT\nhadoop.version=3.6.1-beta.2+build.4\n"))
                .isEqualTo("2027.2.3-SNAPSHOT-hadoop-3.6.1-beta.2+build.4");
    }

    @Test
    void rejectsAbsentMissingMalformedAndUnfilteredMetadata()
    {
        assertThatThrownBy(() -> HdfsAgentVersion.load(null)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(HdfsAgentVersion.RESOURCE).hasMessageContaining("rebuild");
        assertThatThrownBy(() -> load("hadoop.version=3.5.0\n")).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("grantforge.version");
        for (String invalid : new String[] {"", "unknown", "3.5", "3.5.0 beta", "${hadoop.version}"}) {
            assertThatThrownBy(() -> load("grantforge.version=2027.2.3\nhadoop.version=" + invalid + "\n"))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("hadoop.version");
        }
        assertThatThrownBy(() -> load("grantforge.version=${project.version}\nhadoop.version=3.5.0\n"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("grantforge.version");
        assertThatThrownBy(() -> load("grantforge.version=2027.2.3-" + "x".repeat(64) + "\nhadoop.version=3.5.0\n"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("64 characters");
    }

    @Test
    void explainsUnreadableBuildMetadata()
    {
        InputStream unreadable = new InputStream()
        {
            @Override
            public int read() throws IOException
            {
                throw new IOException("broken metadata stream");
            }
        };
        assertThatThrownBy(() -> HdfsAgentVersion.load(unreadable)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot read HDFS agent build metadata").hasCauseInstanceOf(IOException.class);
    }
}
