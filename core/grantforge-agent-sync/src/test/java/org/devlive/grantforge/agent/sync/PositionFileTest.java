// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.agent.sync;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PositionFileTest
{
    @TempDir
    Path directory;

    @Test
    void readsNothingBeforeTheFirstWrite() throws IOException
    {
        PositionFile position = new PositionFile(directory.resolve("audit.position"));
        assertThat(position.read()).isNull();
        assertThat(position.path()).isEqualTo(directory.resolve("audit.position"));
        assertThat(new PositionFile(directory).read()).as("a directory is no position").isNull();
    }

    @Test
    void replacesThePositionWholeAndCreatesItsDirectory() throws IOException
    {
        PositionFile position = new PositionFile(directory.resolve("cache/agent/audit.position"));
        position.write("1730000000\tquery-1\n");
        assertThat(position.read()).isEqualTo("1730000000\tquery-1");
        position.write("1730000001\tquery-2");
        assertThat(position.read()).isEqualTo("1730000001\tquery-2");
        try (var files = Files.list(directory.resolve("cache/agent"))) {
            assertThat(files).as("no temporary file is left behind").containsExactly(directory.resolve("cache/agent/audit.position"));
        }
    }

    @Test
    void reportsAPositionItCannotKeep() throws IOException
    {
        Path file = Files.writeString(directory.resolve("cache"), "a file, not a directory");
        assertThatThrownBy(() -> new PositionFile(file.resolve("audit.position")).write("1")).isInstanceOf(IOException.class);
    }
}
