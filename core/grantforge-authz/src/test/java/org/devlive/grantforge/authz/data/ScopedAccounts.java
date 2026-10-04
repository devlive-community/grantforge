// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.data;

import org.devlive.grantforge.identity.domain.UserAccount;
import org.devlive.grantforge.persistence.secured.ScopedRepository;

/** Accounts through a scoped repository, as the business modules will read them. */
public interface ScopedAccounts
        extends ScopedRepository<UserAccount, Long>
{
}
