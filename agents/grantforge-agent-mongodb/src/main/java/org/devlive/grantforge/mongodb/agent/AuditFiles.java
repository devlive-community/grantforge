// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.mongodb.agent;

import org.bson.Document;
import org.bson.json.JsonParseException;
import org.devlive.grantforge.agent.sync.LogTail;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

/**
 * Reports accesses from the audit log files of MongoDB Enterprise and Percona Server, one JSON object a line: the
 * {@code authCheck} events, which say what each user's commands were allowed or refused. MongoDB writes refusals by
 * default, and allowed commands with {@code auditAuthorizationSuccess} on. The agent reads the files it can see, those of
 * the node it runs on or those a shared directory collects.
 */
// A Document is what the driver takes and answers with, not a choice among maps.
@SuppressWarnings("PMD.LooseCoupling")
final class AuditFiles
{
    /** What MongoDB answers a command refused for lack of a privilege. */
    private static final int UNAUTHORIZED = 13;
    private static final Logger LOG = Logger.getLogger(AuditFiles.class.getName());

    private final LogTail tail;
    private final AccessReporter reporter;

    AuditFiles(AgentConfig.Audit settings, Path position, AccessReporter reporter)
    {
        this.tail = new LogTail(settings.files(), settings.batch(), position, LOG);
        this.reporter = reporter;
    }

    /**
     * Reads the lines written since the last read.
     *
     * @return how many lines were read
     */
    int poll()
    {
        return tail.poll(this::line);
    }

    private void line(String line)
    {
        AccessReporter.Command command = command(line);
        if (command != null) {
            reporter.report(command);
        }
    }

    /**
     * Reads one line of the audit log.
     *
     * @param line the line
     * @return the command it tells of, or {@code null} for a line that tells of none
     */
    static AccessReporter.@Nullable Command command(String line)
    {
        Document event;
        try {
            event = Document.parse(line);
        }
        catch (JsonParseException | IllegalArgumentException | ClassCastException notJson) {
            return null;
        }
        if (!"authCheck".equals(event.getString("atype"))) {
            return null;
        }
        Document parameters = event.get("param", Document.class);
        List<Document> users = event.getList("users", Document.class);
        if (parameters == null || users == null || users.isEmpty() || parameters.getString("command") == null) {
            return null;
        }
        Object result = event.get("result");
        boolean allowed = !(result instanceof Number number && number.intValue() == UNAUTHORIZED);
        Document arguments = parameters.get("args", Document.class);
        String command = String.valueOf(parameters.getString("command"));
        String namespace = String.valueOf(parameters.getString("ns"));
        Document remote = event.get("remote", Document.class);
        return new AccessReporter.Command(String.valueOf(users.get(0).getString("user")), command, namespace,
                Accesses.of(command, namespace, arguments == null ? new Document() : arguments), allowed,
                event.get("ts") instanceof Date at ? at.toInstant() : Instant.now(), remote == null ? null : remote.getString("ip"));
    }
}
