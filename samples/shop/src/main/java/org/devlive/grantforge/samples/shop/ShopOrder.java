// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.samples.shop;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.devlive.grantforge.sdk.GrantForgeEntity;
import org.devlive.grantforge.sdk.GrantForgeField;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * An order of the shop. GrantForge's data policies decide whose orders a user sees, changes or deletes: own orders (the
 * owner is the GrantForge account that placed it), all, or those a condition on status or total selects.
 */
@Entity
@Table(name = "shop_order")
@GrantForgeEntity(code = "order", name = "Orders", owner = "ownerId")
public class ShopOrder
{
    /** Where an order stands. */
    public enum Status
    {
        /** Placed, not paid yet. */
        OPEN,
        /** Paid. */
        PAID
    }

    @Id
    @GeneratedValue
    private @Nullable Long id;

    private long ownerId;

    private String ownerName = "";

    @GrantForgeField("Title")
    private String title = "";

    @GrantForgeField("Status")
    @Enumerated(EnumType.STRING)
    private Status status = Status.OPEN;

    @GrantForgeField("Total")
    private long total;

    private Instant placedAt = Instant.EPOCH;

    /** For JPA. */
    protected ShopOrder()
    {
    }

    /**
     * Places an order.
     *
     * @param ownerId the GrantForge account placing it
     * @param ownerName its login name
     * @param title what is ordered
     * @param total the price
     * @param placedAt when
     */
    public ShopOrder(long ownerId, String ownerName, String title, long total, Instant placedAt)
    {
        this.ownerId = ownerId;
        this.ownerName = ownerName;
        this.title = title;
        this.total = total;
        this.placedAt = placedAt;
    }

    /**
     * Returns the ID.
     *
     * @return the ID, once saved
     */
    public @Nullable Long getId()
    {
        return id;
    }

    /**
     * Returns the owner's account.
     *
     * @return the account ID
     */
    public long getOwnerId()
    {
        return ownerId;
    }

    /**
     * Returns the owner's login name.
     *
     * @return the name
     */
    public String getOwnerName()
    {
        return ownerName;
    }

    /**
     * Returns what is ordered.
     *
     * @return the title
     */
    public String getTitle()
    {
        return title;
    }

    /**
     * Returns where the order stands.
     *
     * @return the status
     */
    public Status getStatus()
    {
        return status;
    }

    /**
     * Returns the price.
     *
     * @return the total
     */
    public long getTotal()
    {
        return total;
    }

    /**
     * Returns when the order was placed.
     *
     * @return the time
     */
    public Instant getPlacedAt()
    {
        return placedAt;
    }
}
