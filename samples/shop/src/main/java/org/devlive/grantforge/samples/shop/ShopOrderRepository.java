// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.samples.shop;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** The shop's orders. */
public interface ShopOrderRepository
        extends JpaRepository<ShopOrder, Long>, JpaSpecificationExecutor<ShopOrder>
{
}
