// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.elasticsearch.agent;

import org.devlive.grantforge.agent.sync.Launcher;

/**
 * Runs the agent: {@code java -jar grantforge-agent-elasticsearch.jar /etc/grantforge/elasticsearch-agent.properties}. Settings
 * come from the file and may be overridden with {@code -Dgrantforge.*}.
 */
public final class ElasticsearchAgentMain
{
    private static final String VERSION = "/META-INF/grantforge/elasticsearch-agent-version.properties";

    private ElasticsearchAgentMain()
    {
    }

    /**
     * Starts the agent and keeps it running until the process is stopped.
     *
     * @param args the settings file
     */
    public static void main(String[] args)
    {
        int status = Launcher.run(args, "Elasticsearch", ElasticsearchAgentMain.class, VERSION,
                (settings, version) -> ElasticsearchAgent.start(AgentConfig.parse(settings, version)));
        if (status != 0) {
            System.exit(status);
        }
    }

    static String version()
    {
        return Launcher.version(ElasticsearchAgentMain.class, VERSION);
    }
}
