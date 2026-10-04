// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.sdk.jpa;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.devlive.grantforge.sdk.GrantForgeEntity;
import org.devlive.grantforge.sdk.GrantForgeField;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Instant;

/** An order of a test application, under GrantForge's data permissions. */
@Entity
@Table(name = "test_order")
@GrantForgeEntity(code = "order", name = "Orders", owner = "ownerId", unit = "unitId", tenant = "tenantId")
public class TestOrder
{
    /** Statuses of orders. */
    public enum Status
    {
        /** Not paid yet. */
        OPEN,
        /** Paid. */
        PAID
    }

    @Id
    private Long id = 0L;

    private Long ownerId = 0L;

    private @Nullable Long unitId;

    private String tenantId = "";

    @GrantForgeField("Title")
    private @Nullable String title;

    @GrantForgeField("Status")
    @Enumerated(EnumType.STRING)
    private Status status = Status.OPEN;

    @GrantForgeField("Total")
    private BigDecimal total = BigDecimal.ZERO;

    @GrantForgeField("Paid by card")
    private boolean card;

    @GrantForgeField("Created")
    private Instant createdAt = Instant.EPOCH;

    /** For JPA. */
    protected TestOrder()
    {
    }

    /**
     * Creates an order.
     *
     * @param id its ID
     * @param ownerId its owner
     * @param unitId its department
     * @param tenantId its tenant
     * @param title its title
     * @param status its status
     * @param total its total
     * @param createdAt when it was created
     */
    public TestOrder(long id, long ownerId, @Nullable Long unitId, String tenantId, @Nullable String title, Status status, BigDecimal total,
            Instant createdAt)
    {
        this.id = id;
        this.ownerId = ownerId;
        this.unitId = unitId;
        this.tenantId = tenantId;
        this.title = title;
        this.status = status;
        this.total = total;
        this.card = status == Status.PAID;
        this.createdAt = createdAt;
    }

    /**
     * Returns the ID.
     *
     * @return the ID
     */
    public Long getId()
    {
        return id;
    }
}
