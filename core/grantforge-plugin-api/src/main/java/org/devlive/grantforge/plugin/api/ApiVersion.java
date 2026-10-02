// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.plugin.api;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Objects.requireNonNull;

/**
 * A semantic version of this plugin API. Additions raise the minor version and fixes the patch version; a
 * breaking change raises the major version. A plugin built against one version runs on any later version with the
 * same major version.
 *
 * @param major incompatible changes
 * @param minor compatible additions
 * @param patch compatible fixes
 */
// Records generate equals and hashCode from all components, which agrees with compareTo.
@SuppressWarnings("PMD.OverrideBothEqualsAndHashCodeOnComparable")
public record ApiVersion(int major, int minor, int patch)
        implements Comparable<ApiVersion>
{
    /** The version of the API in this jar. Raise it with every change; CI compares the API with the last release. */
    public static final ApiVersion CURRENT = new ApiVersion(1, 0, 0);

    private static final Pattern FORMAT = Pattern.compile("(\\d{1,4})\\.(\\d{1,4})(?:\\.(\\d{1,4}))?");

    /**
     * Checks the numbers.
     *
     * @throws IllegalArgumentException if a number is negative
     */
    public ApiVersion
    {
        if (major < 0 || minor < 0 || patch < 0) {
            throw new IllegalArgumentException("version numbers must not be negative");
        }
    }

    /**
     * Parses {@code major.minor} or {@code major.minor.patch}.
     *
     * @param text the version
     * @return the version; patch 0 when omitted
     * @throws IllegalArgumentException if the text is not a version
     */
    public static ApiVersion parse(String text)
    {
        Matcher matcher = FORMAT.matcher(requireNonNull(text, "text").strip());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("not an API version (major.minor[.patch]): " + text);
        }
        String patch = matcher.group(3);
        return new ApiVersion(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)),
                patch == null ? 0 : Integer.parseInt(patch));
    }

    /**
     * Returns whether a plugin that needs {@code required} runs on this version.
     *
     * @param required the version the plugin was built against
     * @return {@code true} for the same major version and this version at least as new
     */
    public boolean supports(ApiVersion required)
    {
        return major == required.major && compareTo(required) >= 0;
    }

    @Override
    public int compareTo(ApiVersion other)
    {
        int result = Integer.compare(major, other.major);
        if (result == 0) {
            result = Integer.compare(minor, other.minor);
        }
        return result == 0 ? Integer.compare(patch, other.patch) : result;
    }

    @Override
    public String toString()
    {
        return major + "." + minor + "." + patch;
    }
}
