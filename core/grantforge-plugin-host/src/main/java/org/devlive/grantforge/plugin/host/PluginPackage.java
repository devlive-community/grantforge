// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.plugin.host;

import org.devlive.grantforge.plugin.api.PluginDescriptor;
import org.jspecify.annotations.Nullable;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.jar.JarFile;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static java.util.Objects.requireNonNull;

/**
 * One external plugin as installed in the plugins directory: a jar with {@value PluginDescriptor#FILE_NAME} at its
 * root; or a directory, or a zip of one, holding the descriptor, its classes in {@code classes/} and its jars (with
 * their dependencies) in {@code lib/} or at the top. Zips are unpacked into the directory's {@code .work}
 * folder.
 *
 * @param location the file or directory name in the plugins directory
 * @param descriptor what the plugin says about itself
 * @param urls what its class loader loads from
 */
public record PluginPackage(String location, PluginDescriptor descriptor, List<URL> urls)
{
    /** The folder of the plugins directory zips are unpacked into; never scanned for plugins itself. */
    public static final String WORK = ".work";

    /** Largest descriptor read, against mistakes. */
    private static final int MAX_DESCRIPTOR = 64 * 1024;

    /** Copies the URLs. */
    public PluginPackage
    {
        requireNonNull(location, "location");
        requireNonNull(descriptor, "descriptor");
        urls = List.copyOf(requireNonNull(urls, "urls"));
    }

    /**
     * Returns whether a path in the plugins directory may be a plugin: a jar, a zip or a directory. A directory with a
     * {@code pom.xml} and no descriptor is the source of a plugin, not a plugin: a server started from the repository
     * root looks into its {@code plugins} source folder.
     *
     * @param path the path
     * @return {@code true} for candidates
     */
    public static boolean candidate(Path path)
    {
        String name = String.valueOf(path.getFileName());
        if (name.startsWith(".")) {
            return false;
        }
        if (Files.isDirectory(path)) {
            return !Files.exists(path.resolve("pom.xml")) || Files.exists(path.resolve(PluginDescriptor.FILE_NAME));
        }
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".jar") || lower.endsWith(".zip");
    }

    /**
     * Reads a plugin package.
     *
     * @param path the jar, zip or directory
     * @param work where zips are unpacked
     * @return the package
     * @throws IllegalArgumentException if it has no valid descriptor or cannot be read
     */
    public static PluginPackage read(Path path, Path work)
    {
        String location = String.valueOf(path.getFileName());
        try {
            if (Files.isDirectory(path)) {
                return directory(location, path);
            }
            if (location.toLowerCase(Locale.ROOT).endsWith(".zip")) {
                return directory(location, unzip(path, work.resolve(location.substring(0, location.length() - 4))));
            }
            try (JarFile jar = new JarFile(path.toFile())) {
                ZipEntry entry = jar.getEntry(PluginDescriptor.FILE_NAME);
                if (entry == null) {
                    throw new IllegalArgumentException(location + " has no " + PluginDescriptor.FILE_NAME);
                }
                try (InputStream in = jar.getInputStream(entry)) {
                    return new PluginPackage(location, descriptor(location, in), List.of(path.toUri().toURL()));
                }
            }
        }
        catch (IOException | UncheckedIOException unreadable) {
            throw new IllegalArgumentException(location + " cannot be read: " + unreadable.getMessage(), unreadable);
        }
    }

    private static PluginPackage directory(String location, Path root)
            throws IOException
    {
        Path file = root.resolve(PluginDescriptor.FILE_NAME);
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException(location + " has no " + PluginDescriptor.FILE_NAME);
        }
        PluginDescriptor descriptor;
        try (InputStream in = Files.newInputStream(file)) {
            descriptor = descriptor(location, in);
        }
        List<URL> urls = new ArrayList<>();
        Path classes = root.resolve("classes");
        if (Files.isDirectory(classes)) {
            urls.add(classes.toUri().toURL());
        }
        urls.addAll(jars(root));
        Path lib = root.resolve("lib");
        if (Files.isDirectory(lib)) {
            urls.addAll(jars(lib));
        }
        return new PluginPackage(location, descriptor, urls);
    }

    private static List<URL> jars(Path folder)
            throws IOException
    {
        try (Stream<Path> files = Files.list(folder)) {
            return files.filter(file -> String.valueOf(file.getFileName()).toLowerCase(Locale.ROOT).endsWith(".jar"))
                    .sorted(Comparator.comparing(Path::toString)).map(PluginPackage::url).toList();
        }
    }

    private static URL url(Path path)
    {
        try {
            return path.toUri().toURL();
        }
        catch (MalformedURLException impossible) {
            throw new UncheckedIOException(impossible);
        }
    }

    /** Unpacks a zip afresh, refusing entries that would land outside the target. */
    private static Path unzip(Path zip, Path target)
            throws IOException
    {
        deleteTree(target);
        Files.createDirectories(target);
        Path root = target.toRealPath();
        try (ZipFile file = new ZipFile(zip.toFile())) {
            for (ZipEntry entry : file.stream().toList()) {
                Path destination = root.resolve(entry.getName()).normalize();
                if (!destination.startsWith(root)) {
                    throw new IllegalArgumentException(zip.getFileName() + ": entry outside the package: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(destination);
                }
                else {
                    Files.createDirectories(requireNonNull(destination.getParent()));
                    try (InputStream in = file.getInputStream(entry)) {
                        Files.copy(in, destination);
                    }
                }
            }
        }
        return root;
    }

    private static void deleteTree(Path root)
            throws IOException
    {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }

    private static PluginDescriptor descriptor(String location, InputStream in)
            throws IOException
    {
        byte[] bytes = in.readNBytes(MAX_DESCRIPTOR + 1);
        if (bytes.length > MAX_DESCRIPTOR) {
            throw new IllegalArgumentException(location + ": " + PluginDescriptor.FILE_NAME + " is larger than 64 KB");
        }
        @Nullable Object document;
        try {
            document = new Yaml(new SafeConstructor(new LoaderOptions())).load(new String(bytes, StandardCharsets.UTF_8));
        }
        catch (YAMLException malformed) {
            throw new IllegalArgumentException(location + ": " + PluginDescriptor.FILE_NAME + " is not valid YAML", malformed);
        }
        if (!(document instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException(location + ": " + PluginDescriptor.FILE_NAME + " must be a mapping");
        }
        @SuppressWarnings("unchecked")
        Map<String, ?> values = (Map<String, ?>) map;
        return PluginDescriptor.fromMap(values);
    }
}
