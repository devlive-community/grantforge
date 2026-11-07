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
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;

class LogTailTest
{
    private static final Logger LOG = Logger.getLogger(LogTailTest.class.getName());

    @TempDir
    Path directory;

    private final List<String> lines = new ArrayList<>();

    private static void append(Path file, String text) throws IOException
    {
        Files.writeString(file, text, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    @Test
    void readsAFileFromItsEndAtFirstThenWhatIsWrittenAfter() throws IOException
    {
        Path file = directory.resolve("audit.json");
        Path position = directory.resolve("cache/audit.position");
        Files.writeString(file, "old\n");
        LogTail tail = new LogTail(List.of(file), 10, position, LOG);
        assertThat(tail.poll(lines::add)).isZero();
        assertThat(Files.readString(position)).contains("\"offset\":4");
        append(file, "new\n\nnewer\n");
        assertThat(tail.poll(lines::add)).as("an empty line counts, but is not handed on").isEqualTo(3);
        assertThat(lines).containsExactly("new", "newer");
        assertThat(tail.poll(lines::add)).isZero();
    }

    @Test
    void takesAtMostSoManyLinesAndALineNotYetEndedWaits() throws IOException
    {
        Path file = directory.resolve("audit.json");
        Files.writeString(file, "");
        LogTail tail = new LogTail(List.of(file), 2, directory.resolve("audit.position"), LOG);
        tail.poll(lines::add);
        append(file, "one\ntwo\nthree\nfou");
        assertThat(tail.poll(lines::add)).isEqualTo(2);
        assertThat(tail.poll(lines::add)).isEqualTo(1);
        assertThat(tail.poll(lines::add)).isZero();
        append(file, "r\n");
        assertThat(tail.poll(lines::add)).isEqualTo(1);
        assertThat(lines).containsExactly("one", "two", "three", "four");
    }

    @Test
    void readsAFileRolledOverFromItsStart() throws IOException
    {
        Path file = directory.resolve("audit.json");
        Files.writeString(file, "first\n");
        LogTail tail = new LogTail(List.of(file), 10, directory.resolve("audit.position"), LOG);
        tail.poll(lines::add);
        Files.move(file, directory.resolve("audit.json.1"));
        Files.writeString(file, "second\n");
        assertThat(tail.poll(lines::add)).isEqualTo(1);
        assertThat(lines).containsExactly("second");
    }

    @Test
    void readsAFileThatShrankFromItsStart() throws IOException
    {
        Path file = directory.resolve("audit.json");
        Files.writeString(file, "a long first line\n");
        LogTail tail = new LogTail(List.of(file), 10, directory.resolve("audit.position"), LOG);
        tail.poll(lines::add);
        Files.writeString(file, "short\n");
        assertThat(tail.poll(lines::add)).isEqualTo(1);
        assertThat(lines).containsExactly("short");
    }

    @Test
    void aRestartReadsOnFromWhereTheLastReadEnded() throws IOException
    {
        Path file = directory.resolve("audit.json");
        Path position = directory.resolve("audit.position");
        Files.writeString(file, "old\n");
        new LogTail(List.of(file), 10, position, LOG).poll(lines::add);
        append(file, "new\n");
        assertThat(new LogTail(List.of(file), 10, position, LOG).poll(lines::add)).isEqualTo(1);
        assertThat(lines).containsExactly("new");
    }

    @Test
    void aMissingFileLeavesTheOthersBeing() throws IOException
    {
        Path file = directory.resolve("audit.json");
        Files.writeString(file, "");
        LogTail tail = new LogTail(List.of(directory.resolve("missing.json"), file), 10, directory.resolve("audit.position"), LOG);
        tail.poll(lines::add);
        append(file, "one\n");
        assertThat(tail.poll(lines::add)).isEqualTo(1);
    }

    @Test
    void anUnreadablePositionReadsOnFromThePresent() throws IOException
    {
        Path file = directory.resolve("audit.json");
        Path position = directory.resolve("audit.position");
        Files.writeString(file, "old\n");
        Files.writeString(position, "not json");
        LogTail tail = new LogTail(List.of(file), 10, position, LOG);
        assertThat(tail.poll(lines::add)).isZero();
        assertThat(lines).isEmpty();
        assertThat(Files.readString(position)).contains("\"offset\":4");
    }

    @Test
    void aPositionThatCannotBeWrittenIsNotFatal() throws IOException
    {
        Path file = directory.resolve("audit.json");
        Path blocked = Files.writeString(directory.resolve("blocked"), "");
        Files.writeString(file, "old\n");
        LogTail tail = new LogTail(List.of(file), 10, blocked.resolve("audit.position"), LOG);
        assertThat(tail.poll(lines::add)).isZero();
    }
}
