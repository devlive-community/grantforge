// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.open;

import org.devlive.grantforge.sdk.GrantForgeEntity;
import org.devlive.grantforge.sdk.GrantForgeField;

/** An entity of a shop the open API tests declare through the Java SDK, as the shop would at start-up. */
@GrantForgeEntity(code = "order", name = "Orders", owner = "ownerId", unit = "unitId")
final class ShopOrder
{
    /** Statuses of orders. */
    enum Status
    {
        /** Not paid yet. */
        OPEN,
        /** Paid. */
        PAID
    }

    @GrantForgeField("Status")
    private Status status = Status.OPEN;

    @GrantForgeField("Total")
    private long total;

    private ShopOrder()
    {
    }
}
