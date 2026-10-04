// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.sdk.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Orders of the test application. */
public interface TestOrderRepository
        extends JpaRepository<TestOrder, Long>, JpaSpecificationExecutor<TestOrder>
{
}
