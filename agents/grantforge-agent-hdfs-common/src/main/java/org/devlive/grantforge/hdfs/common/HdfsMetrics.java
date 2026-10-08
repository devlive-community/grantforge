// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hdfs.common;

import org.devlive.grantforge.agent.AgentDecision;

/** Optional monitoring hooks; Hadoop adapters may expose them through their own metrics system. */
public interface HdfsMetrics
{
    /** A monitor that records nothing. */
    HdfsMetrics NONE = new HdfsMetrics() {};

    /** Records a regular authorization callback. */
    default void callback()
    {
    }

    /** Records a superuser authorization callback. */
    default void superuserCallback()
    {
    }

    /** Records a native HDFS rejection. */
    default void nativeDeny()
    {
    }

    /** Records a callback that failed closed because its context or policy evaluation was invalid. */
    default void failure()
    {
    }

    /**
     * Records one primitive policy decision.
     *
     * @param decision the decision
     */
    default void decision(AgentDecision decision)
    {
    }

    /** Records a callback without a verified snapshot. */
    default void missingSnapshot()
    {
    }

    /**
     * Updates gauges from the runtime used by this callback.
     *
     * @param version the captured snapshot version, or zero without a snapshot
     * @param queuedEvents events awaiting shipment
     * @param droppedEvents events dropped because the queue or spool is full
     * @param reachable whether the last policy-server request succeeded
     */
    default void snapshot(long version, long queuedEvents, long droppedEvents, boolean reachable)
    {
    }
}
