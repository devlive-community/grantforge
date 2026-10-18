// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.kafka;

/** The Kafka plugin against Kafka 4.3.1. */
class Kafka43PluginIT
        extends KafkaPluginContract
{
    @Override
    String image()
    {
        return "apache/kafka:4.3.1";
    }
}
