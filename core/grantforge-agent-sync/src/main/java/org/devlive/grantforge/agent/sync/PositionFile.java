// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.agent.sync;

import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Where an agent read a system's audit to, kept in a file of its cache directory so that a restart reads on from
 * there. The text is the agent's own; a write replaces the whole file at once, so a crash leaves the last position or
 * the new one, never part of one.
 */
public final class PositionFile
{
    private final Path file;

    /**
     * Names the file.
     *
     * @param file the file, which need not exist yet
     */
    public PositionFile(Path file)
    {
        this.file = file;
    }

    /**
     * Returns the file.
     *
     * @return it
     */
    public Path path()
    {
        return file;
    }

    /**
     * Reads the position.
     *
     * @return the text last written, without surrounding white space, or {@code null} when nothing was written yet
     * @throws IOException when the file is there but cannot be read
     */
    public @Nullable String read() throws IOException
    {
        if (!Files.isRegularFile(file)) {
            return null;
        }
        return Files.readString(file, StandardCharsets.UTF_8).strip();
    }

    /**
     * Writes a position in place of the last one, creating the directory when it is missing. Where the file system
     * cannot move a file atomically, the new file still replaces the old one whole.
     *
     * @param position the text
     * @throws IOException when the file cannot be written
     */
    public void write(String position) throws IOException
    {
        Path directory = file.toAbsolutePath().getParent();
        if (directory != null) {
            Files.createDirectories(directory);
        }
        Path written = Files.writeString(file.resolveSibling(file.getFileName() + ".tmp"), position, StandardCharsets.UTF_8);
        try {
            Files.move(written, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        }
        catch (AtomicMoveNotSupportedException plain) {
            Files.move(written, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
