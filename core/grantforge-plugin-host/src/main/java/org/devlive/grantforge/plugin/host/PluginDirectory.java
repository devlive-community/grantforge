// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.plugin.host;

import org.jspecify.annotations.Nullable;

import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;

/**
 * Picks the plugins directory (D-89). An explicit {@code grantforge.plugins.directory} wins; otherwise
 * {@code plugins} in the working directory, as a release has it. A server started from the sources, as an IDE starts
 * it from wherever, has no such folder: when its classes come from a build's output folder, the {@code plugins} folder
 * of the repository they were built in is used, whose built plugin modules then load as plugins.
 */
final class PluginDirectory
{
    /** The folder a release keeps its plugins in, relative to the working directory. */
    static final String STANDARD = "plugins";

    private PluginDirectory()
    {
    }

    /**
     * Picks the directory.
     *
     * @param configured the setting; blank when not set
     * @param codeSource where the server's classes come from, or {@code null} if unknown
     * @return the directory to scan
     */
    static Path choose(String configured, @Nullable Path codeSource)
    {
        if (!configured.isBlank()) {
            return Path.of(configured.strip());
        }
        Path standard = Path.of(STANDARD);
        if (Files.isDirectory(standard)) {
            return standard;
        }
        Path sources = sourceTree(codeSource);
        return sources == null ? standard : sources;
    }

    /**
     * Finds the {@code plugins} folder of the repository classes were built in.
     *
     * @param codeSource a build output folder, such as {@code core/grantforge-plugin-host/target/classes}; a jar is a
     *        release, which has no repository
     * @return the folder, or {@code null}
     */
    static @Nullable Path sourceTree(@Nullable Path codeSource)
    {
        if (codeSource == null || !Files.isDirectory(codeSource)) {
            return null;
        }
        for (Path directory = codeSource.toAbsolutePath(); directory != null; directory = directory.getParent()) {
            if (Files.isRegularFile(directory.resolve("pom.xml")) && Files.isDirectory(directory.resolve(STANDARD))
                    && Files.isDirectory(directory.resolve("core"))) {
                return directory.resolve(STANDARD);
            }
        }
        return null;
    }

    /**
     * Where a class was loaded from.
     *
     * @param type the class
     * @return its jar or class folder, or {@code null} if that is not a file
     */
    static @Nullable Path codeSource(Class<?> type)
    {
        CodeSource source = type.getProtectionDomain().getCodeSource();
        if (source == null || source.getLocation() == null) {
            return null;
        }
        try {
            return Path.of(source.getLocation().toURI());
        }
        catch (URISyntaxException | IllegalArgumentException notAFile) {
            return null;
        }
    }
}
