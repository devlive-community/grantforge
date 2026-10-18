// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.kafka;

/** The Kafka plugin against Kafka 4.1.2. */
class Kafka41PluginIT
        extends KafkaPluginContract
{
    @Override
    String image()
    {
        return "apache/kafka:4.1.2";
    }
}
