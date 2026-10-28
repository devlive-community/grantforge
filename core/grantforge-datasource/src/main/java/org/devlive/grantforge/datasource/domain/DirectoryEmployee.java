// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.datasource.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.devlive.grantforge.identity.domain.UserAccount;
import org.devlive.grantforge.persistence.tenant.TenantScopedEntity;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * A directory snapshot and stable binding, independent of the account's OIDC subject and editable profile.
 */
@Entity
@Table(name = "gf_directory_employee")
public class DirectoryEmployee extends TenantScopedEntity
{
    @Column(name = "source_id", nullable = false, updatable = false)
    private long sourceId;
    @Column(name = "user_id", nullable = false, updatable = false, length = 255)
    private String userId = "";
    @Column(name = "email", length = 254)
    private @Nullable String email;
    @Column(name = "department_ids", nullable = false, length = 4000)
    private String departmentIds = "[]";

    private static final JsonMapper JSON = JsonMapper.builder().build();
    @Column(name = "present_in_directory", nullable = false)
    private boolean presentInDirectory;

    /**
     * Creates an employee.
     */
    protected DirectoryEmployee()
    {
    }

    /**
     * Creates an unbound employee.
     *
     * @param sourceId the owning identity source
     * @param userId   the DingTalk employee ID
     * @return the result
     */
    public static DirectoryEmployee create(long sourceId, String userId)
    {
        DirectoryEmployee user = new DirectoryEmployee();
        user.sourceId = sourceId;
        user.userId = userId;
        return user;
    }

    /**
     * Updates a snapshot without changing its binding. Returns whether anything changed.
     *
     * @param address     the employee email, or null
     * @param departments the department IDs in the configured subtree
     * @param present     whether the employee is in the complete snapshot
     * @return whether the condition holds
     * @throws IllegalArgumentException if the snapshot exceeds the local model limits
     */
    public boolean refresh(@Nullable String address, List<String> departments, boolean present)
    {
        String ids = JSON.writeValueAsString(departments.stream().distinct().sorted().toList());
        String normalized = normalizeEmail(address);
        if (ids.length() > 4000) {
            throw new IllegalArgumentException("too many departments for an employee");
        }
        boolean changed = !Objects.equals(email, normalized) || !departmentIds.equals(ids) || presentInDirectory != present;
        email = normalized;
        departmentIds = ids;
        presentInDirectory = present;
        return changed;
    }

    /**
     * Only canonicalizes case and surrounding space; does not merge mailbox aliases.
     *
     * @param address the employee email, or null
     * @return the result
     */
    public static @Nullable String normalizeEmail(@Nullable String address)
    {
        if (address == null || address.isBlank()) {
            return null;
        }
        String normalized = address.strip().toLowerCase(Locale.ROOT);
        return normalized.length() <= 254 && UserAccount.EMAIL.matcher(normalized).matches() ? normalized : null;
    }

    /**
     * Returns getSourceId.
     *
     * @return the result
     */
    public long getSourceId()
    {
        return sourceId;
    }

    /**
     * Returns getUserId.
     *
     * @return the result
     */
    public String getUserId()
    {
        return userId;
    }

    /**
     * Returns getEmail.
     *
     * @return the result
     */
    public @Nullable String getEmail()
    {
        return email;
    }

    /**
     * Returns isPresentInDirectory.
     *
     * @return whether the condition holds
     */
    public boolean isPresentInDirectory()
    {
        return presentInDirectory;
    }

    /**
     * Returns departments.
     *
     * @return the result
     */
    public List<String> departments()
    {
        return JSON.readValue(departmentIds, new TypeReference<List<String>>()
        {
        });
    }
}
