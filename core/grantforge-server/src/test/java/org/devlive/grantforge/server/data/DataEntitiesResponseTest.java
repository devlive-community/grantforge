// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.data;

import org.devlive.grantforge.persistence.secured.DataField;
import org.devlive.grantforge.persistence.secured.DataFieldType;
import org.devlive.grantforge.persistence.secured.DataScope;
import org.devlive.grantforge.persistence.secured.SecuredEntityDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DataEntitiesResponseTest
{
    @Test
    void describesEntitiesFieldsAndVariables()
    {
        DataEntitiesResponse response = DataEntitiesResponse.from(List.of(new SecuredEntityDefinition("group", "Groups", Object.class,
                List.of(new DataField("admin", "Admin", DataFieldType.BOOLEAN, List.of())), null, null, false, true, null)));
        DataEntitiesResponse.Entity group = response.entities().get(0);
        assertThat(group.scopes()).containsExactly(DataScope.ALL, DataScope.TENANT, DataScope.CONDITION);
        assertThat(group.fields().get(0).operators()).containsExactly("eq", "ne", "is_null", "not_null");
        assertThat(response.variables()).extracting(DataEntitiesResponse.Variable::key).contains("subject.id", "now");
    }
}
