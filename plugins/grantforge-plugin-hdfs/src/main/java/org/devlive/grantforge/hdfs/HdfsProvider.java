// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hdfs;

import org.devlive.grantforge.plugin.api.ConnectionResult;
import org.devlive.grantforge.plugin.api.LookupRequest;
import org.devlive.grantforge.plugin.api.ServiceConfig;
import org.devlive.grantforge.plugin.api.ServiceTypeProvider;
import org.devlive.grantforge.plugin.api.model.AccessTypeDefinition;
import org.devlive.grantforge.plugin.api.model.ConfigField;
import org.devlive.grantforge.plugin.api.model.ConfigFieldType;
import org.devlive.grantforge.plugin.api.model.ConfigProblem;
import org.devlive.grantforge.plugin.api.model.MatcherType;
import org.devlive.grantforge.plugin.api.model.ResourceDefinition;
import org.devlive.grantforge.plugin.api.model.ServiceTypeDefinition;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * The HDFS service type: paths, matched as paths and optionally with everything below them, with the access types the
 * HDFS agent checks, read, write and execute. Connections and path lookups go through WebHDFS or HttpFS.
 */
public final class HdfsProvider
        implements ServiceTypeProvider
{
    /** The service type's name. */
    public static final String TYPE = "hdfs";

    static final String PATH = "path";
    static final String URL = "url";
    static final String USER = "username";
    static final String TIMEOUT = "timeout";
    static final long MAX_TIMEOUT = 120;

    @Override
    public ServiceTypeDefinition definition()
    {
        return ServiceTypeDefinition.builder(TYPE).label("HDFS").description("Paths of the Hadoop Distributed File System")
                .resources(ResourceDefinition.builder(PATH).label("Path").matcher(MatcherType.PATH).caseSensitive(true)
                        .recursiveSupported(true).excludesSupported(true).lookupSupported(true).validLeaf(true).build())
                .accessTypes(AccessTypeDefinition.of("read", "Read"), AccessTypeDefinition.of("write", "Write"),
                        AccessTypeDefinition.of("execute", "Execute"))
                .configFields(ConfigField.builder(URL).label("WebHDFS address").type(ConfigFieldType.STRING).mandatory()
                                .pattern("https?://\\S+(\\s*,\\s*https?://\\S+)*")
                                .description("The NameNodes' HTTP addresses, comma-separated for high availability, or HttpFS;"
                                        + " such as http://namenode:9870").build(),
                        ConfigField.builder(USER).label("User").type(ConfigFieldType.STRING).defaultValue("hdfs")
                                .description("Who lists paths, with simple authentication").build(),
                        ConfigField.builder(TIMEOUT).label("Timeout (seconds)").type(ConfigFieldType.INTEGER).defaultValue("10").build())
                .build();
    }

    @Override
    public List<ConfigProblem> validateConfig(ServiceConfig config)
    {
        List<ConfigProblem> problems = new ArrayList<>();
        String url = config.get(URL);
        if (url != null) {
            try {
                WebHdfs.addresses(url);
            }
            catch (IllegalArgumentException invalid) {
                problems.add(new ConfigProblem(URL, ConfigProblem.Reason.INVALID, String.valueOf(invalid.getMessage())));
            }
        }
        long timeout = config.getLong(TIMEOUT, 10);
        if (timeout < 1 || timeout > MAX_TIMEOUT) {
            problems.add(new ConfigProblem(TIMEOUT, ConfigProblem.Reason.INVALID, "1 to " + MAX_TIMEOUT + " seconds"));
        }
        return problems;
    }

    @Override
    public ConnectionResult testConnection(ServiceConfig config)
    {
        try {
            WebHdfs.Entry root = client(config).status("/");
            return root != null && root.directory() ? ConnectionResult.succeeded()
                    : ConnectionResult.failed("the root of the file system is not a directory");
        }
        catch (IOException | IllegalArgumentException failed) {
            return ConnectionResult.failed(String.valueOf(failed.getMessage()));
        }
    }

    /**
     * Lists the paths that start with what was typed: the entries of the directory typed so far whose names start with
     * the rest, directories first.
     */
    @Override
    public List<String> lookup(LookupRequest request)
    {
        if (!PATH.equals(request.resource())) {
            return List.of();
        }
        String typed = request.userInput().strip();
        String path = typed.startsWith("/") ? typed : "/" + typed;
        int slash = path.lastIndexOf('/');
        String directory = slash == 0 ? "/" : path.substring(0, slash);
        String prefix = path.substring(slash + 1);
        String base = "/".equals(directory) ? "" : directory;
        List<WebHdfs.Entry> entries;
        try {
            entries = client(request.config()).list(directory);
        }
        catch (IOException failed) {
            throw new UncheckedIOException(failed);
        }
        return entries.stream().filter(entry -> entry.name().startsWith(prefix))
                .sorted((left, right) -> left.directory() == right.directory() ? left.name().compareTo(right.name())
                        : left.directory() ? -1 : 1)
                .limit(request.limit()).map(entry -> base + "/" + entry.name()).toList();
    }

    private static WebHdfs client(ServiceConfig config)
    {
        String user = config.get(USER);
        return new WebHdfs(WebHdfs.addresses(config.require(URL)), user == null || user.isBlank() ? "hdfs" : user.strip(),
                Duration.ofSeconds(Math.max(1, Math.min(MAX_TIMEOUT, config.getLong(TIMEOUT, 10)))));
    }
}
