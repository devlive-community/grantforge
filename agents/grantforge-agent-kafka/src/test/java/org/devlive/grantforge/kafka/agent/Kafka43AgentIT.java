// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.kafka.agent;

/** The agent inside Kafka 4.3.1. */
class Kafka43AgentIT
        extends KafkaAgentContract
{
    @Override
    String image()
    {
        return "apache/kafka:4.3.1";
    }
}
