// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.agent.sync;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/** Collects what a logger writes while a test runs, as {@code LEVEL message} lines. */
final class Logs
        extends Handler
        implements AutoCloseable
{
    private final Logger logger;
    private final Level level;
    private final List<String> lines = new CopyOnWriteArrayList<>();

    private Logs(Logger logger)
    {
        this.logger = logger;
        this.level = logger.getLevel();
        setLevel(Level.ALL);
        logger.setLevel(Level.ALL);
        logger.addHandler(this);
    }

    static Logs of(Logger logger)
    {
        return new Logs(logger);
    }

    List<String> lines()
    {
        return List.copyOf(lines);
    }

    @Override
    public void publish(LogRecord record)
    {
        lines.add(record.getLevel() + " " + record.getMessage());
    }

    @Override
    public void flush()
    {
    }

    @Override
    public void close()
    {
        logger.removeHandler(this);
        logger.setLevel(level);
    }
}
