// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.redis.agent;

import org.devlive.grantforge.agent.sync.Launcher;

/**
 * Runs the agent: {@code java -jar grantforge-agent-redis.jar /etc/grantforge/redis-agent.properties}. Settings
 * come from the file and may be overridden with {@code -Dgrantforge.*}.
 */
public final class RedisAgentMain
{
    private static final String VERSION = "/META-INF/grantforge/redis-agent-version.properties";

    private RedisAgentMain()
    {
    }

    /**
     * Starts the agent and keeps it running until the process is stopped.
     *
     * @param args the settings file
     */
    public static void main(String[] args)
    {
        int status = Launcher.run(args, "Redis", RedisAgentMain.class, VERSION,
                (settings, version) -> RedisAgent.start(AgentConfig.parse(settings, version)));
        if (status != 0) {
            System.exit(status);
        }
    }

    static String version()
    {
        return Launcher.version(RedisAgentMain.class, VERSION);
    }
}
