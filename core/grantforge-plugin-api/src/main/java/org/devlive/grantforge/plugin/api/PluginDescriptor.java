// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.plugin.api;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static java.util.Objects.requireNonNull;

/**
 * What a plugin package says about itself, in {@value #FILE_NAME} at its root. For example:
 * <pre>{@code
 * id: hdfs
 * version: 1.2.0
 * name: HDFS
 * description: Paths in the Hadoop file system
 * apiVersion: 1.0
 * providers:
 *   - org.example.grantforge.hdfs.HdfsServiceTypeProvider
 * }</pre>
 *
 * @param id the plugin's id, unique among installed plugins
 * @param version the plugin's own version ({@code major.minor.patch}, optionally with a suffix such as
 *        {@code -rc1})
 * @param name what the console shows
 * @param description a longer explanation, or {@code null}
 * @param apiVersion the plugin API version the plugin was built against
 * @param providers the fully qualified names of its {@link ServiceTypeProvider} classes; at least one
 */
public record PluginDescriptor(String id, String version, String name, @Nullable String description,
        ApiVersion apiVersion, List<String> providers)
{
    /** Where the descriptor sits inside a plugin package. */
    public static final String FILE_NAME = "grantforge-plugin.yaml";

    /** Plugin ids. */
    public static final Pattern ID = Pattern.compile("[a-z][a-z0-9-]{1,63}");

    private static final Pattern VERSION = Pattern.compile("\\d{1,6}\\.\\d{1,6}\\.\\d{1,6}(?:[-+][0-9A-Za-z.-]{1,64})?");

    private static final Pattern CLASS_NAME = Pattern.compile(
            "[A-Za-z_$][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)*");

    /**
     * Checks and copies the values.
     *
     * @throws IllegalArgumentException if a value is missing or malformed
     */
    public PluginDescriptor
    {
        if (id == null || !ID.matcher(id).matches()) {
            throw new IllegalArgumentException("plugin id must be 2-64 lowercase letters, digits or '-': " + id);
        }
        if (version == null || !VERSION.matcher(version).matches()) {
            throw new IllegalArgumentException("plugin " + id + ": version must be major.minor.patch: " + version);
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("plugin " + id + ": name is required");
        }
        name = name.strip();
        description = optional(description);
        requireNonNull(apiVersion, "apiVersion");
        providers = List.copyOf(requireNonNull(providers, "providers"));
        if (providers.isEmpty()) {
            throw new IllegalArgumentException("plugin " + id + ": at least one provider is required");
        }
        for (String provider : providers) {
            if (!CLASS_NAME.matcher(provider).matches()) {
                throw new IllegalArgumentException("plugin " + id + ": not a class name: " + provider);
            }
        }
    }

    private static @Nullable String optional(@Nullable String value)
    {
        return value == null || value.isBlank() ? null : value.strip();
    }

    /**
     * Reads a descriptor from the parsed YAML document; keeps the API free of a YAML library.
     *
     * @param document the document's top-level mapping
     * @return the descriptor
     * @throws IllegalArgumentException if a key is missing or has the wrong kind of value
     */
    public static PluginDescriptor fromMap(Map<String, ?> document)
    {
        requireNonNull(document, "document");
        Object providers = document.get("providers");
        List<String> classes = new ArrayList<>();
        if (providers instanceof List<?> list) {
            list.forEach(entry -> classes.add(String.valueOf(entry)));
        }
        else if (providers != null) {
            classes.add(String.valueOf(providers));
        }
        String apiVersion = text(document, "apiVersion");
        if (apiVersion == null) {
            throw new IllegalArgumentException("plugin descriptor: apiVersion is required");
        }
        return new PluginDescriptor(requireText(document, "id"), requireText(document, "version"),
                requireText(document, "name"), text(document, "description"), ApiVersion.parse(apiVersion), classes);
    }

    private static @Nullable String text(Map<String, ?> document, String key)
    {
        Object value = document.get(key);
        if (value instanceof Map<?, ?> || value instanceof List<?>) {
            throw new IllegalArgumentException("plugin descriptor: " + key + " must be a single value");
        }
        // YAML reads "1.0" as a number, so numbers are taken as written.
        return value == null ? null : value.toString();
    }

    private static String requireText(Map<String, ?> document, String key)
    {
        String value = text(document, key);
        if (value == null) {
            throw new IllegalArgumentException("plugin descriptor: " + key + " is required");
        }
        return value;
    }

    /**
     * Returns whether this server's plugin API can run the plugin.
     *
     * @return {@code true} when {@link ApiVersion#CURRENT} supports {@link #apiVersion()}
     */
    public boolean isCompatible()
    {
        return ApiVersion.CURRENT.supports(apiVersion);
    }
}
