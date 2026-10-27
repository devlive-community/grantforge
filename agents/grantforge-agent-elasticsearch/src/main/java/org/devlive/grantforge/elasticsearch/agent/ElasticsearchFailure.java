// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.elasticsearch.agent;

import org.jspecify.annotations.Nullable;

/** A request Elasticsearch refused or failed, or no node that could be reached. */
final class ElasticsearchFailure
        extends RuntimeException
{
    private static final long serialVersionUID = 1L;

    private final int status;

    ElasticsearchFailure(String message, int status, @Nullable Throwable cause)
    {
        super(message, cause);
        this.status = status;
    }

    /**
     * Returns the HTTP status of the refusal.
     *
     * @return the status, or 0 when no node answered
     */
    int status()
    {
        return status;
    }

    /**
     * Returns whether no node could be reached or none could serve, rather than one refusing.
     *
     * @return {@code true} without an answer, or for a node too busy or unavailable
     */
    boolean unreachable()
    {
        return status == 0 || status == 429 || status == 502 || status == 503 || status == 504;
    }
}
