// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hdfs.agent;

import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.regex.Pattern;

/** The build versions reported in heartbeats, loaded once from metadata embedded in the agent jar. */
final class HdfsAgentVersion
{
    static final String RESOURCE = "/META-INF/grantforge/hdfs-agent-version.properties";
    private static final Pattern VERSION_PART = Pattern.compile("[0-9]+\\.[0-9]+\\.[0-9]+"
            + "(?:-[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?(?:\\+[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?");
    private static final String VERSION = load(HdfsAgentVersion.class.getResourceAsStream(RESOURCE));

    private HdfsAgentVersion()
    {
    }

    static String value()
    {
        return VERSION;
    }

    /** Reads and closes build metadata; absent or unfiltered metadata indicates a broken agent build. */
    static String load(@Nullable InputStream input)
    {
        if (input == null) {
            throw new IllegalStateException("missing HDFS agent build metadata " + RESOURCE + "; rebuild the agent jar with Maven");
        }
        try (InputStream metadata = input) {
            Properties properties = new Properties();
            properties.load(metadata);
            String version = part(properties, "grantforge.version") + "-hadoop-" + part(properties, "hadoop.version");
            // The server's heartbeat DTO accepts at most 64 characters for agentVersion.
            if (version.length() > 64) {
                throw new IllegalStateException("HDFS agent build version exceeds the heartbeat limit of 64 characters");
            }
            return version;
        }
        catch (IOException unreadable) {
            throw new IllegalStateException("cannot read HDFS agent build metadata " + RESOURCE + "; rebuild the agent jar", unreadable);
        }
    }

    private static String part(Properties properties, String name)
    {
        String value = properties.getProperty(name);
        if (value == null || !VERSION_PART.matcher(value).matches()) {
            throw new IllegalStateException("invalid or missing " + name + " in HDFS agent build metadata; rebuild the agent jar with Maven");
        }
        return value;
    }
}
