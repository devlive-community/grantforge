// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hdfs.agent;

import org.apache.hadoop.hdfs.server.namenode.INodeAttributeProvider;
import org.apache.hadoop.util.VersionInfo;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Build identity and a fail-closed native compatibility gate shared by the numbered adapters. */
public final class HdfsAgentCompatibility
{
    /** Metadata supplied by each numbered adapter, never loaded implicitly by this shared module. */
    public static final String RESOURCE = "/META-INF/grantforge/hdfs-agent-version.properties";
    private static final Pattern HADOOP_VERSION = Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)(?:[-.+].*)?$");

    private HdfsAgentCompatibility()
    {
    }

    /**
     * Returns the numbered adapter's heartbeat version label.
     *
     * @param build the adapter's validated metadata
     * @return the product and native Hadoop build versions
     * @throws IllegalStateException if versions are missing, unfiltered or too long for the heartbeat API
     */
    public static String agentVersion(Properties build)
    {
        String label = required(build, "grantforge.version") + "-hadoop-" + required(build, "hadoop.version");
        if (label.length() > 64) {
            throw new IllegalStateException("HDFS agent version exceeds the heartbeat limit of 64 characters");
        }
        return label;
    }

    /**
     * Checks the adapter's declared Hadoop line, JVM minimum and callback family against the loaded native runtime.
     *
     * @param build metadata loaded by the numbered adapter
     * @throws IllegalStateException if the current NameNode cannot load that adapter's contract
     */
    // Verify the SPI that actually defines Hadoop's classes, irrespective of a caller's context class loader.
    @SuppressWarnings("PMD.UseProperClassLoader")
    public static void verify(Properties build)
    {
        requireLine(required(build, "hadoop.line"), VersionInfo.getVersion());
        requireJava(Integer.parseInt(required(build, "java.minimum")), System.getProperty("java.specification.version", ""));
        String family = required(build, "spi.family");
        if (!"parameters".equals(family)) {
            try {
                Class<?> context = Class.forName("org.apache.hadoop.hdfs.server.namenode.INodeAttributeProvider$AuthorizationContext",
                        false, INodeAttributeProvider.class.getClassLoader());
                Class<?> enforcer = INodeAttributeProvider.AccessControlEnforcer.class;
                enforcer.getMethod("checkPermissionWithContext", context);
                if ("superuser".equals(family)) {
                    enforcer.getMethod("checkSuperUserPermissionWithContext", context);
                    enforcer.getMethod("denyUserAccess", context, String.class);
                }
                else if (!"context".equals(family)) {
                    throw new IllegalStateException("unknown HDFS agent SPI family " + family);
                }
            }
            catch (ReflectiveOperationException incompatible) {
                throw new IllegalStateException("this HDFS agent needs native " + family + " callbacks; install the jar for Hadoop "
                        + required(build, "hadoop.line"), incompatible);
            }
        }
    }

    static void requireJava(int minimum, String specification)
    {
        String major = specification.startsWith("1.") ? specification.substring(2) : specification;
        try {
            if (Integer.parseInt(major) >= minimum) {
                return;
            }
        }
        catch (NumberFormatException invalid) {
            throw new IllegalStateException("cannot determine the NameNode Java version: " + specification, invalid);
        }
        throw new IllegalStateException("this HDFS agent requires Java " + minimum + " or later; NameNode uses Java " + specification);
    }

    static void requireLine(String expected, String actual)
    {
        Matcher version = HADOOP_VERSION.matcher(actual);
        if (!version.matches() || !expected.equals(version.group(1) + "." + version.group(2))) {
            throw new IllegalStateException("HDFS agent for Hadoop " + expected + " cannot run on " + actual
                    + "; install the matching numbered agent jar");
        }
    }

    /**
     * Reads and closes the numbered adapter's metadata resource, leaving resource lookup to that adapter's class.
     *
     * @param input the resource stream, or null if it was absent
     * @return metadata independent of the source stream
     * @throws IllegalStateException if metadata is missing, unfiltered, unreadable or exceeds the heartbeat size limit
     */
    public static Properties load(@Nullable InputStream input)
    {
        if (input == null) {
            throw new IllegalStateException("missing HDFS compatibility metadata; rebuild this agent with Maven");
        }
        try (InputStream metadata = input) {
            Properties properties = new Properties();
            properties.load(metadata);
            for (String name : new String[] {"grantforge.version", "hadoop.version", "hadoop.line", "java.minimum", "spi.family"}) {
                required(properties, name);
            }
            agentVersion(properties);
            return properties;
        }
        catch (IOException unreadable) {
            throw new IllegalStateException("cannot read HDFS compatibility metadata", unreadable);
        }
    }

    private static String required(Properties properties, String name)
    {
        String value = properties.getProperty(name);
        if (value == null || value.chars().allMatch(character -> character <= ' ') || value.contains("${")) {
            throw new IllegalStateException("missing or unfiltered " + name + " in HDFS compatibility metadata");
        }
        return value;
    }
}
