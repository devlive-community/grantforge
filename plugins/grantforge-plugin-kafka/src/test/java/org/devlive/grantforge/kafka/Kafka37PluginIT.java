// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.kafka;

/** The Kafka plugin against Kafka 3.7.2. */
class Kafka37PluginIT
        extends KafkaPluginContract
{
    @Override
    String image()
    {
        return "apache/kafka:3.7.2";
    }
}
