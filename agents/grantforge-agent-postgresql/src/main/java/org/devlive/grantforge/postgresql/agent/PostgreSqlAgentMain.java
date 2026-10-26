// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.postgresql.agent;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Runs the agent: {@code java -jar grantforge-agent-postgresql.jar /etc/grantforge/postgresql-agent.properties}. Settings
 * come from the file and may be overridden with {@code -Dgrantforge.*}.
 */
public final class PostgreSqlAgentMain
{
    private static final Logger LOG = Logger.getLogger(PostgreSqlAgentMain.class.getName());

    private PostgreSqlAgentMain()
    {
    }

    /**
     * Starts the agent and keeps it running until the process is stopped.
     *
     * @param args the settings file
     */
    // The process lives as long as the agent; the shutdown hook stops it.
    @SuppressWarnings({"PMD.DoNotUseThreads", "PMD.CloseResource"})
    public static void main(String[] args)
    {
        if (args.length != 1) {
            LOG.severe("usage: java -jar grantforge-agent-postgresql.jar <settings.properties>");
            System.exit(2);
        }
        PostgreSqlAgent agent;
        try {
            agent = PostgreSqlAgent.start(AgentConfig.parse(settings(args[0]), version()));
        }
        catch (IOException | RuntimeException failed) {
            LOG.log(Level.SEVERE, "The GrantForge PostgreSQL agent could not start: " + failed.getMessage(), failed);
            System.exit(1);
            return;
        }
        CountDownLatch stopped = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            agent.close();
            stopped.countDown();
        }, "grantforge-postgresql-stop"));
        try {
            stopped.await();
        }
        catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    static Map<String, String> settings(String file) throws IOException
    {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(Paths.get(file), StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        Map<String, String> values = new HashMap<>();
        for (String name : properties.stringPropertyNames()) {
            values.put(name, properties.getProperty(name));
        }
        for (String name : System.getProperties().stringPropertyNames()) {
            if (name.startsWith(AgentConfig.PREFIX)) {
                values.put(name, System.getProperty(name));
            }
        }
        return values;
    }

    static String version()
    {
        Properties properties = new Properties();
        try (InputStream in = PostgreSqlAgentMain.class.getResourceAsStream("/META-INF/grantforge/postgresql-agent-version.properties")) {
            if (in != null) {
                properties.load(in);
            }
        }
        catch (IOException unreadable) {
            LOG.log(Level.FINE, "The agent version cannot be read", unreadable);
        }
        return properties.getProperty("grantforge.version", "unknown");
    }
}
