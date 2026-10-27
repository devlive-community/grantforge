// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.opensearch.agent;

import org.devlive.grantforge.agent.sync.Launcher;

/**
 * Runs the agent: {@code java -jar grantforge-agent-opensearch.jar /etc/grantforge/opensearch-agent.properties}. Settings
 * come from the file and may be overridden with {@code -Dgrantforge.*}.
 */
public final class OpenSearchAgentMain
{
    private static final String VERSION = "/META-INF/grantforge/opensearch-agent-version.properties";

    private OpenSearchAgentMain()
    {
    }

    /**
     * Starts the agent and keeps it running until the process is stopped.
     *
     * @param args the settings file
     */
    public static void main(String[] args)
    {
        int status = Launcher.run(args, "OpenSearch", OpenSearchAgentMain.class, VERSION,
                (settings, version) -> OpenSearchAgent.start(AgentConfig.parse(settings, version)));
        if (status != 0) {
            System.exit(status);
        }
    }

    static String version()
    {
        return Launcher.version(OpenSearchAgentMain.class, VERSION);
    }
}
