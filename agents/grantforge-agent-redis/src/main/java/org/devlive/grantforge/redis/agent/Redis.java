// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.redis.agent;

import org.jspecify.annotations.Nullable;

/**
 * A deployment as the agent sees it: its nodes, and commands sent to one of them. Redis keeps an ACL on every node, so
 * the agent talks to each.
 */
interface Redis
        extends AutoCloseable
{
    /**
     * Finds the deployment's nodes.
     *
     * @return what the deployment is and its nodes
     * @throws RedisFailure when no node given can be reached or one refuses the sign-in
     */
    Topology topology();

    /**
     * Sends a command to a node.
     *
     * @param node the node as {@code host:port}
     * @param command the command and its arguments
     * @return the answer: text, a number, or a list of answers; {@code null} for none
     * @throws RedisFailure when the node refuses the command or cannot be reached
     */
    @Nullable Object command(String node, String... command);

    /** Lets go of the nodes. */
    @Override
    void close();
}
