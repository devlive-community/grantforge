// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.plugin.api.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigFieldTypeTest
{
    @Test
    void namesArePartOfThePluginApi()
    {
        // Stored in the database and sent to the console; renaming one is a breaking API change.
        assertThat(ConfigFieldType.values()).extracting(Enum::name).containsExactly("STRING", "TEXT", "INTEGER", "BOOLEAN", "SECRET", "ENUM");
    }
}
