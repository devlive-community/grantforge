// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.agent.sync;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Runs an agent from its settings file: {@code java -jar grantforge-agent-<system>.jar <settings.properties>}. Settings
 * come from the file, read as UTF-8, and {@code -Dgrantforge.*} system properties override them. The agent runs until
 * the process is stopped, when a shutdown hook closes it.
 */
public final class Launcher
{
    /** Exit status when the agent was started without its settings file. */
    public static final int USAGE = 2;
    /** Exit status when the agent could not start. */
    public static final int FAILED = 1;

    /** Starts an agent from its settings. */
    @FunctionalInterface
    public interface Starter
    {
        /**
         * Starts the agent.
         *
         * @param settings the settings by their full names
         * @param version the agent's version
         * @return the running agent
         * @throws IOException when a file the settings name cannot be read
         */
        AutoCloseable start(Map<String, String> settings, String version) throws IOException;
    }

    private Launcher()
    {
    }

    /**
     * Starts an agent and waits until the process is stopped.
     *
     * @param args the command line: the settings file
     * @param name the protected system's name, such as {@code ClickHouse}
     * @param anchor a class of the agent, whose jar holds its version
     * @param versionResource where the jar holds its version, such as {@code /META-INF/grantforge/clickhouse-agent-version.properties}
     * @param starter what starts the agent
     * @return the exit status: {@code 0} once stopped, {@link #USAGE} or {@link #FAILED}
     */
    public static int run(String[] args, String name, Class<?> anchor, String versionResource, Starter starter)
    {
        return run(args, name, anchor, versionResource, starter, hook -> Runtime.getRuntime().addShutdownHook(hook));
    }

    // The process lives as long as the agent; the shutdown hook stops it.
    @SuppressWarnings({"PMD.DoNotUseThreads", "PMD.CloseResource"})
    static int run(String[] args, String name, Class<?> anchor, String versionResource, Starter starter, Consumer<Thread> hooks)
    {
        Logger log = Logger.getLogger(anchor.getName());
        String system = name.toLowerCase(Locale.ROOT);
        if (args.length != 1) {
            log.severe("usage: java -jar grantforge-agent-" + system + ".jar <settings.properties>");
            return USAGE;
        }
        AutoCloseable agent;
        try {
            agent = starter.start(settings(args[0]), version(anchor, versionResource));
        }
        catch (IOException | RuntimeException failed) {
            log.log(Level.SEVERE, "The GrantForge " + name + " agent could not start: " + failed.getMessage(), failed);
            return FAILED;
        }
        CountDownLatch stopped = new CountDownLatch(1);
        hooks.accept(new Thread(() -> {
            try {
                agent.close();
            }
            catch (Exception failed) {
                log.log(Level.WARNING, "The GrantForge " + name + " agent did not stop cleanly", failed);
            }
            stopped.countDown();
        }, "grantforge-" + system + "-stop"));
        try {
            stopped.await();
        }
        catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        return 0;
    }

    /**
     * Reads the settings: the file's, then the {@code grantforge.*} system properties over them.
     *
     * @param file the settings file
     * @return the settings by their full names
     * @throws IOException when the file cannot be read
     */
    public static Map<String, String> settings(String file) throws IOException
    {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(Paths.get(file), StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        Map<String, String> values = new HashMap<>();
        for (String key : properties.stringPropertyNames()) {
            values.put(key, properties.getProperty(key));
        }
        for (String key : System.getProperties().stringPropertyNames()) {
            if (key.startsWith(Settings.PREFIX)) {
                values.put(key, System.getProperty(key));
            }
        }
        return values;
    }

    /**
     * Reads the version the build wrote into the agent's jar.
     *
     * @param anchor a class of the agent
     * @param resource where the jar holds it
     * @return the version, {@code unknown} when the jar holds none
     */
    public static String version(Class<?> anchor, String resource)
    {
        Properties properties = new Properties();
        try (InputStream in = anchor.getResourceAsStream(resource)) {
            if (in != null) {
                properties.load(in);
            }
        }
        catch (IOException unreadable) {
            Logger.getLogger(anchor.getName()).log(Level.FINE, "The agent version cannot be read", unreadable);
        }
        return properties.getProperty("grantforge.version", "unknown");
    }
}
