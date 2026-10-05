// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/** Access requests of the bound tenant; call as system to look across tenants. */
public interface AccessRequestRepository
        extends JpaRepository<AccessRequest, Long>
{
    /**
     * Lists an account's requests, newest first.
     *
     * @param requesterId the account
     * @param limit how many at most
     * @return the requests
     */
    List<AccessRequest> findByRequesterIdOrderByCreatedAtDescIdDesc(long requesterId, Limit limit);

    /**
     * Lists requests in some states, newest first.
     *
     * @param statuses the states
     * @param limit how many at most
     * @return the requests
     */
    List<AccessRequest> findByStatusInOrderByCreatedAtDescIdDesc(Collection<AccessRequestStatus> statuses, Limit limit);

    /**
     * Tells whether an account asks for a role already.
     *
     * @param requesterId the account
     * @param roleId the role
     * @param status the state, pending
     * @return whether such a request exists
     */
    boolean existsByRequesterIdAndRoleIdAndStatus(long requesterId, long roleId, AccessRequestStatus status);

    /**
     * Lists approved grants whose period is over.
     *
     * @param status approved
     * @param now the current time
     * @return the grants to take back
     */
    List<AccessRequest> findByStatusAndValidUntilLessThanEqual(AccessRequestStatus status, Instant now);

    /**
     * Counts requests in a state.
     *
     * @param status the state
     * @return how many
     */
    long countByStatus(AccessRequestStatus status);
}
