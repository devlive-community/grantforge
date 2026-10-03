// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.authz;

import org.devlive.grantforge.authz.application.AssignmentView;
import org.devlive.grantforge.authz.application.EffectiveAccess;
import org.devlive.grantforge.authz.application.EffectiveRole;
import org.devlive.grantforge.authz.application.RoleView;
import org.devlive.grantforge.authz.application.Subject;
import org.devlive.grantforge.authz.data.DataAccess;
import org.devlive.grantforge.authz.data.DataRule;
import org.devlive.grantforge.authz.data.DataSubject;
import org.devlive.grantforge.authz.domain.ResourceType;
import org.devlive.grantforge.authz.domain.RoleAssignment;
import org.devlive.grantforge.authz.domain.RoleType;
import org.devlive.grantforge.authz.domain.SubjectType;
import org.devlive.grantforge.persistence.secured.DataAction;
import org.devlive.grantforge.persistence.secured.DataScope;
import org.devlive.grantforge.persistence.secured.FieldMode;
import org.devlive.grantforge.persistence.secured.FieldReadMode;
import org.devlive.grantforge.persistence.secured.FieldView;
import org.devlive.grantforge.persistence.secured.FieldWriteMode;
import org.devlive.grantforge.server.security.FieldModeResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EffectiveAccessResponseTest
{
    @Test
    void putsRolesResourcesDataAndFieldsTogether()
    {
        Subject alice = new Subject(SubjectType.USER, 7, "Alice", "alice");
        AssignmentView assignment = new AssignmentView(1, 2, alice, RoleAssignment.Terms.UNLIMITED, true);
        EffectiveRole editors = new EffectiveRole(new RoleView(2, "editors", "Editors", null, RoleType.CUSTOM, true),
                List.of(assignment, assignment), true);
        EffectiveAccess access = new EffectiveAccess(List.of(editors),
                List.of(new EffectiveAccess.Item("system.user", "Users", null, ResourceType.PAGE, "system")),
                List.of(new EffectiveAccess.Item("system.user.read", "Read users", null, ResourceType.API, "api")));
        DataSubject subject = new DataSubject(7, 1, "alice", List.of(), List.of(), List.of(), List.of());
        DataAccess data = new DataAccess(subject, Map.of(
                new DataAccess.Key("user", DataAction.UPDATE), new DataAccess.Rules(List.of(DataRule.of(DataScope.SELF)), List.of()),
                new DataAccess.Key("user", DataAction.READ), new DataAccess.Rules(List.of(new DataRule(DataScope.CUSTOM_ORGS, null,
                        List.of(1L, 2L))), List.of(DataRule.of(DataScope.ALL)))));

        EffectiveAccessResponse response = EffectiveAccessResponse.from(7, access, data,
                Map.of("user.email", new FieldMode(FieldView.HIDDEN, FieldWriteMode.READONLY)));

        assertThat(response.accountId()).isEqualTo("7");
        assertThat(response.roles()).singleElement().satisfies(role -> assertThat(role.assignedTo()).hasSize(1));
        assertThat(response.resources()).extracting(EffectiveAccessResponse.Item::parentCode).containsExactly("system");
        assertThat(response.data()).extracting(EffectiveAccessResponse.Data::action).containsExactly(DataAction.READ, DataAction.UPDATE);
        assertThat(response.data().get(0).allow()).containsExactly(new EffectiveAccessResponse.Rule(DataScope.CUSTOM_ORGS, false, 2));
        assertThat(response.data().get(0).deny()).containsExactly(new EffectiveAccessResponse.Rule(DataScope.ALL, false, 0));
        assertThat(response.fields()).extractingByKey("user.email").extracting(FieldModeResponse::readMode).isEqualTo(FieldReadMode.HIDDEN);
    }
}
