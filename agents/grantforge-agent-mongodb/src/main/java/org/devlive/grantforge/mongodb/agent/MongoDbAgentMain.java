// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.mongodb.agent;

import org.devlive.grantforge.agent.sync.Launcher;

/**
 * Runs the agent: {@code java -jar grantforge-agent-mongodb.jar /etc/grantforge/mongodb-agent.properties}. Settings
 * come from the file and may be overridden with {@code -Dgrantforge.*}.
 */
public final class MongoDbAgentMain
{
    private static final String VERSION = "/META-INF/grantforge/mongodb-agent-version.properties";

    private MongoDbAgentMain()
    {
    }

    /**
     * Starts the agent and keeps it running until the process is stopped.
     *
     * @param args the settings file
     */
    public static void main(String[] args)
    {
        int status = Launcher.run(args, "MongoDB", MongoDbAgentMain.class, VERSION,
                (settings, version) -> MongoDbAgent.start(AgentConfig.parse(settings, version)));
        if (status != 0) {
            System.exit(status);
        }
    }

    static String version()
    {
        return Launcher.version(MongoDbAgentMain.class, VERSION);
    }
}
