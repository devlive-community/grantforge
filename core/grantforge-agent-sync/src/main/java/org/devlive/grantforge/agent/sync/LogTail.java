// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.agent.sync;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Follows the audit log files of a system that writes one line per event, those of the node an agent runs on or those a
 * shared directory collects, from where the last read ended. A file read for the first time is read from its end, and a
 * file rolled over, so that another file now has its name, is read from its start; a line not yet ended waits for the
 * next read. Where each file was read to is kept in a {@link PositionFile}, so that a restart reads on from there.
 */
public final class LogTail
{
    /** The most bytes one read takes of a file. */
    static final int MAX_BYTES = 8 * 1024 * 1024;

    private static final ObjectMapper JSON = new ObjectMapper();

    private final List<Path> files;
    private final int maxLines;
    private final PositionFile position;
    private final String log;
    private final Notices notices;

    /**
     * Names what is followed.
     *
     * @param files the files, which need not exist yet
     * @param maxLines the most lines one read takes of each file
     * @param position the file that keeps where each was read to
     * @param log the agent's logger
     */
    public LogTail(List<Path> files, int maxLines, Path position, Logger log)
    {
        this.files = List.copyOf(files);
        this.maxLines = maxLines;
        this.position = new PositionFile(position);
        this.log = log.getName();
        this.notices = new Notices(log);
    }

    /**
     * Where the read of one file ended.
     *
     * @param file what tells the file apart from one that takes its name later, such as its inode
     * @param offset the bytes read
     */
    private record Cursor(String file, long offset)
    {
    }

    /** Where a read of one file ended, and how many lines it took. */
    private record Read(String file, long offset, int lines)
    {
        boolean same(@Nullable Cursor cursor)
        {
            return cursor != null && file.equals(cursor.file()) && offset == cursor.offset();
        }
    }

    /**
     * Reads the lines written since the last read, at most as many of each file as was said.
     *
     * @param lines what takes each line
     * @return how many lines were read
     */
    // A cursor per file read.
    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    public int poll(Consumer<String> lines)
    {
        Map<String, Cursor> cursors = load();
        int read = 0;
        boolean moved = false;
        for (Path file : files) {
            String key = file.toAbsolutePath().toString();
            Read at;
            try {
                at = read(file, cursors.get(key), lines);
            }
            catch (IOException unreadable) {
                notices.once(Level.WARNING, "The GrantForge agent cannot read the audit log " + file + ": " + unreadable.getMessage());
                continue;
            }
            if (!at.same(cursors.get(key))) {
                read += at.lines();
                cursors.put(key, new Cursor(at.file(), at.offset()));
                moved = true;
            }
        }
        if (moved) {
            save(cursors);
        }
        return read;
    }

    // A string per line.
    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    private Read read(Path file, @Nullable Cursor cursor, Consumer<String> lines) throws IOException
    {
        BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class);
        Object fileKey = attributes.fileKey();
        String identity = fileKey == null ? attributes.creationTime().toString() : fileKey.toString();
        long size = attributes.size();
        if (cursor == null) {
            return new Read(identity, size, 0);
        }
        long offset = identity.equals(cursor.file()) && cursor.offset() <= size ? cursor.offset() : 0;
        if (offset == size) {
            return new Read(identity, offset, 0);
        }
        ByteBuffer buffer = ByteBuffer.allocate((int) Math.min(size - offset, MAX_BYTES));
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            channel.position(offset);
            int count;
            do {
                count = channel.read(buffer);
            }
            while (count > 0 && buffer.hasRemaining());
        }
        byte[] bytes = buffer.array();
        int length = buffer.position();
        int start = 0;
        int taken = 0;
        for (int index = 0; index < length && taken < maxLines; index++) {
            if (bytes[index] == '\n') {
                String line = new String(bytes, start, index - start, StandardCharsets.UTF_8).strip();
                if (!line.isEmpty()) {
                    lines.accept(line);
                }
                taken++;
                start = index + 1;
            }
        }
        return new Read(identity, offset + start, taken);
    }

    private Map<String, Cursor> load()
    {
        Map<String, Cursor> cursors = new LinkedHashMap<>();
        try {
            String text = position.read();
            if (text != null) {
                JSON.readTree(text).properties().forEach(entry -> cursors.put(entry.getKey(),
                        new Cursor(entry.getValue().path("file").asText(""), entry.getValue().path("offset").asLong(0))));
            }
        }
        catch (IOException unreadable) {
            notices.once(Level.WARNING, "The GrantForge agent cannot read where it stopped reading the audit log, " + position.path()
                    + ", and reads on from the present: " + unreadable.getMessage());
        }
        return cursors;
    }

    private void save(Map<String, Cursor> cursors)
    {
        ObjectNode saved = JSON.createObjectNode();
        cursors.forEach((file, cursor) -> saved.putObject(file).put("file", cursor.file()).put("offset", cursor.offset()));
        try {
            position.write(saved.toString());
        }
        catch (IOException unwritable) {
            Logger.getLogger(log).log(Level.WARNING, "The GrantForge agent cannot keep where it stopped reading the audit log, " + position.path(), unwritable);
        }
    }
}
