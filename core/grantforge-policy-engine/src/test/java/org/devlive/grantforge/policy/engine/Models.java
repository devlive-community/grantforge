// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.policy.engine;

/** Service models the tests share: Hive-like (database → table → column, url) and HDFS-like (path). */
final class Models
{
    static final ServiceModel HIVE = ServiceModel.builder()
            .level(ResourceLevel.of("database", null, MatcherKind.WILDCARD, false))
            .level(ResourceLevel.of("table", "database", MatcherKind.WILDCARD, false))
            .level(ResourceLevel.of("column", "table", MatcherKind.WILDCARD, false))
            .level(ResourceLevel.of("url", null, MatcherKind.REGEX, true))
            .accessType("select").accessType("update").accessType("drop")
            .accessType("all", "select", "update", "drop")
            .build();

    static final ServiceModel HDFS = ServiceModel.builder()
            .level(ResourceLevel.of("path", null, MatcherKind.PATH, true))
            .accessType("read").accessType("write").accessType("execute")
            .build();

    static final ServiceModel EXACT = ServiceModel.builder()
            .level(ResourceLevel.of("topic", null, MatcherKind.EXACT, true))
            .accessType("publish").accessType("consume")
            .build();

    private Models()
    {
    }
}
