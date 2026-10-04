// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.open;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.devlive.grantforge.authz.data.EntityDeclaration;
import org.devlive.grantforge.persistence.secured.DataField;
import org.devlive.grantforge.persistence.secured.DataFieldType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OpenEntitiesRequestTest
{
    @Test
    void convertsAndChecksTheShapeOfDeclarations()
    {
        OpenEntitiesRequest request = new OpenEntitiesRequest(List.of(new OpenEntitiesRequest.OpenEntity("order", "Orders", true, false,
                List.of(new OpenEntitiesRequest.OpenEntityField("status", "Status", DataFieldType.CHOICE, List.of("OPEN")))),
                new OpenEntitiesRequest.OpenEntity("lead", "Leads", false, true, null)));

        assertThat(request.declarations()).containsExactly(
                new EntityDeclaration("order", "Orders", true, false, List.of(new DataField("status", "Status", DataFieldType.CHOICE, List.of("OPEN")))),
                new EntityDeclaration("lead", "Leads", false, true, List.of()));
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(request)).isEmpty();
            assertThat(factory.getValidator().validate(new OpenEntitiesRequest(null))).hasSize(1);
            assertThat(factory.getValidator().validate(new OpenEntitiesRequest(List.of(new OpenEntitiesRequest.OpenEntity(" ", null, null, null,
                    List.of(new OpenEntitiesRequest.OpenEntityField(null, "x", null, null))))))).hasSize(4);
        }
        assertThat(new OpenEntitiesRequest(null).declarations()).isEmpty();
        assertThat(new OpenEntitiesRequest.OpenEntityField(null, null, null, null).field().type()).isEqualTo(DataFieldType.TEXT);
    }
}
