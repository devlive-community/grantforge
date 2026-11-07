// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.mongodb.agent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class ActionsTest
{
    @Test
    void namesTheActionsOfEachAccessType()
    {
        assertThat(Actions.of(Actions.READ)).containsExactly("find", "listIndexes", "collStats", "killCursors");
        assertThat(Actions.of(Actions.REMOVE)).containsExactly("remove");
        assertThat(Actions.of(Actions.DROP)).containsExactly("dropCollection");
        assertThat(Actions.of(Actions.DROP_DATABASE)).containsExactly("dropDatabase");
        assertThat(Actions.of(Actions.MONITOR)).contains("serverStatus");
        for (String type : Actions.COLLECTION_TYPES) {
            assertThat(Actions.of(type)).isNotEmpty();
        }
        for (String type : Actions.DEPLOYMENT_TYPES) {
            assertThat(Actions.of(type)).isNotEmpty();
        }
        assertThatIllegalArgumentException().isThrownBy(() -> Actions.of("grant"));
    }

    @Test
    void readsAnActionBackIntoItsAccessType()
    {
        assertThat(Actions.type("find")).isEqualTo(Actions.READ);
        assertThat(Actions.type("listIndexes")).as("it reads no documents").isNull();
        assertThat(Actions.type("insert")).isEqualTo(Actions.INSERT);
        assertThat(Actions.type("collMod")).isEqualTo(Actions.ALTER);
        assertThat(Actions.type("createCollection")).isEqualTo(Actions.CREATE);
        assertThat(Actions.type("listDatabases")).isEqualTo(Actions.LIST_DATABASES);
        assertThat(Actions.type("shutdown")).isNull();
    }
}
