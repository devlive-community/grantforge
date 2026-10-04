// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.field;

import org.devlive.grantforge.persistence.secured.FieldReadMode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FieldPoliciesRequestTest
{
    @Test
    @SuppressWarnings("NullAway") // JSON without the list gives null
    void copiesThePoliciesAndTakesNoneForMissing()
    {
        List<FieldPolicyRequest> given = new ArrayList<>(List.of(new FieldPolicyRequest("user", "email", FieldReadMode.HIDDEN, null, null)));
        FieldPoliciesRequest request = new FieldPoliciesRequest(given);
        given.clear();
        assertThat(request.toCommands()).hasSize(1);
        assertThat(new FieldPoliciesRequest(null).toCommands()).isEmpty();
    }
}
