// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.devlive.grantforge.authz.domain.Role;
import org.devlive.grantforge.authz.domain.RoleAssignment;
import org.devlive.grantforge.authz.domain.RoleAssignmentRepository;
import org.devlive.grantforge.authz.domain.RoleRepository;
import org.devlive.grantforge.authz.domain.SubjectType;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

/**
 * Works out which roles an account of the bound tenant has and through which assignments. Must be called within a
 * transaction with the tenant bound.
 */
@Component
public final class EffectiveRoles
{
    private final RoleAssignmentRepository assignments;
    private final RoleRepository roles;
    private final SubjectDirectory subjects;

    /**
     * Creates the component.
     *
     * @param assignments assignments of the bound tenant
     * @param roles roles of the bound tenant
     * @param subjects names subjects and finds an account's memberships
     */
    public EffectiveRoles(RoleAssignmentRepository assignments, RoleRepository roles, SubjectDirectory subjects)
    {
        this.assignments = requireNonNull(assignments, "assignments");
        this.roles = requireNonNull(roles, "roles");
        this.subjects = requireNonNull(subjects, "subjects");
    }

    /**
     * Returns the roles an account has, with every assignment that gives each: direct ones and those through its
     * groups, departments (and parent departments whose assignments include sub-departments) and positions.
     *
     * @param accountId the account
     * @param now the current time, for validity
     * @return the roles, active ones first, then by name
     */
    public List<EffectiveRole> of(long accountId, Instant now)
    {
        SubjectDirectory.Memberships memberships = subjects.memberships(accountId);
        List<RoleAssignment> found = new ArrayList<>(assignments.findBySubjects(SubjectType.USER, List.of(accountId)));
        if (!memberships.groups().isEmpty()) {
            found.addAll(assignments.findBySubjects(SubjectType.GROUP, memberships.groups()));
        }
        if (!memberships.units().isEmpty()) {
            found.addAll(assignments.findBySubjects(SubjectType.ORG_UNIT, memberships.units()));
        }
        if (!memberships.parentUnits().isEmpty()) {
            assignments.findBySubjects(SubjectType.ORG_UNIT, memberships.parentUnits()).stream()
                    .filter(assignment -> assignment.getTerms().includeSubUnits()).forEach(found::add);
        }
        if (!memberships.positions().isEmpty()) {
            found.addAll(assignments.findBySubjects(SubjectType.POSITION, memberships.positions()));
        }
        Map<Long, Role> byId = roles.findAllById(found.stream().map(RoleAssignment::getRoleId).distinct().toList()).stream()
                .collect(Collectors.toMap(Role::requireId, role -> role));
        Map<Long, List<AssignmentView>> sources = views(found, now).stream()
                .collect(Collectors.groupingBy(AssignmentView::roleId, LinkedHashMap::new, Collectors.toList()));
        List<EffectiveRole> result = new ArrayList<>();
        for (Map.Entry<Long, List<AssignmentView>> entry : sources.entrySet()) {
            Role role = byId.get(entry.getKey());
            if (role != null) {
                result.add(effectiveRole(role, entry.getValue()));
            }
        }
        result.sort(Comparator.comparing((EffectiveRole role) -> !role.active()).thenComparing(role -> role.role().name()));
        return result;
    }

    private static EffectiveRole effectiveRole(Role role, List<AssignmentView> sources)
    {
        return new EffectiveRole(RoleView.from(role), sources, role.isEnabled() && sources.stream().anyMatch(AssignmentView::valid));
    }

    /**
     * Names the subjects of assignments; assignments whose subject no longer exists are left out.
     *
     * @param rows the assignments
     * @param now the current time, for validity
     * @return the views
     */
    List<AssignmentView> views(List<RoleAssignment> rows, Instant now)
    {
        Map<SubjectType, Map<Long, Subject>> names = new EnumMap<>(SubjectType.class);
        rows.stream().collect(Collectors.groupingBy(RoleAssignment::getSubjectType)).forEach((type, ofType) ->
                names.put(type, subjects.names(type, ofType.stream().map(RoleAssignment::getSubjectId).toList())));
        List<AssignmentView> result = new ArrayList<>();
        for (RoleAssignment row : rows) {
            Subject subject = names.getOrDefault(row.getSubjectType(), Map.of()).get(row.getSubjectId());
            if (subject != null) {
                result.add(view(row, subject, now));
            }
        }
        return result;
    }

    static AssignmentView view(RoleAssignment assignment, Subject subject, Instant now)
    {
        return new AssignmentView(assignment.requireId(), assignment.getRoleId(), subject, assignment.getTerms(),
                assignment.isValidAt(now));
    }
}
