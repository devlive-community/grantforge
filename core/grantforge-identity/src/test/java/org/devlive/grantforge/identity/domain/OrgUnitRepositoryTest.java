// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.domain;

import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class OrgUnitRepositoryTest
{
    @Autowired
    private TenantRepository tenants;

    @Autowired
    private OrgUnitRepository units;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private long tenant;

    @BeforeEach
    void createTenant()
    {
        tenant = tenants.save(Tenant.create("acme", "Acme")).requireId();
    }

    @AfterEach
    void deleteRows()
    {
        // Children first: the parent foreign key forbids deleting a parent before its children.
        TenantContext.runInTenant(tenant, () -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                    List<OrgUnit> all = units.findTree();
                    for (int i = all.size() - 1; i >= 0; i--) {
                        units.delete(all.get(i));
                    }
                }));
        tenants.deleteAllInBatch();
    }

    private <T> T inTenant(Supplier<T> action)
    {
        return TenantContext.callInTenant(tenant, () -> new TransactionTemplate(transactionManager).execute(status ->
                action.get()));
    }

    private OrgUnit save(OrgUnit unit)
    {
        return inTenant(() -> units.save(unit));
    }

    @Test
    void readsTheTreeLevelByLevelAndSiblingsInOrder()
    {
        OrgUnit hq = save(OrgUnit.create(null, "hq", "HQ", 1));
        OrgUnit lab = save(OrgUnit.create(null, "lab", "Lab", 0));
        save(OrgUnit.create(hq, "b", "B", 1));
        save(OrgUnit.create(hq, "a", "A", 0));

        assertThat(inTenant(() -> units.findTree())).extracting(OrgUnit::getCode).containsExactly("lab", "hq", "a", "b");
        assertThat(inTenant(() -> units.findChildren(null))).extracting(OrgUnit::getCode).containsExactly("lab", "hq");
        assertThat(inTenant(() -> units.findChildren(hq.requireId()))).extracting(OrgUnit::getCode).containsExactly("a", "b");
        assertThat(inTenant(() -> units.existsByParentId(hq.requireId()))).isTrue();
        assertThat(inTenant(() -> units.existsByParentId(lab.requireId()))).isFalse();
        assertThat(inTenant(() -> units.findByCode("a"))).isPresent();
        assertThat(TenantContext.callInTenant(tenant + 1, () -> units.findTree())).isEmpty();
    }

    @Test
    void movesAWholeSubtreeInOneStatement()
    {
        OrgUnit hq = save(OrgUnit.create(null, "hq", "HQ", 0));
        OrgUnit sales = save(OrgUnit.create(hq, "sales", "Sales", 0));
        OrgUnit east = save(OrgUnit.create(sales, "east", "East", 0));
        OrgUnit lab = save(OrgUnit.create(null, "lab", "Lab", 1));
        assertThat(inTenant(() -> units.maxDepthBelow(hq.getPath() + "%"))).isEqualTo(2);

        String oldPrefix = sales.getPath();
        String newPrefix = lab.getPath() + sales.requireId() + "/";
        int moved = inTenant(() -> {
            units.reparent(sales.requireId(), lab.requireId());
            return units.moveSubtree(oldPrefix, oldPrefix + "%", newPrefix, oldPrefix.length() + 1, 0);
        });

        assertThat(moved).isEqualTo(2);
        OrgUnit movedSales = inTenant(() -> units.findById(sales.requireId()).orElseThrow());
        OrgUnit movedEast = inTenant(() -> units.findById(east.requireId()).orElseThrow());
        assertThat(movedSales.getParentId()).isEqualTo(lab.requireId());
        assertThat(movedSales.getPath()).isEqualTo(newPrefix);
        assertThat(movedEast.getPath()).isEqualTo(newPrefix + east.requireId() + "/");
        assertThat(movedEast.getDepth()).isEqualTo(2);

        // Making the subtree a root shifts every depth up.
        inTenant(() -> {
            units.reparent(sales.requireId(), null);
            return units.moveSubtree(newPrefix, newPrefix + "%", "/" + sales.requireId() + "/", newPrefix.length() + 1, -1);
        });
        assertThat(inTenant(() -> units.findById(east.requireId()).orElseThrow()))
                .extracting(OrgUnit::getPath, OrgUnit::getDepth)
                .containsExactly("/" + sales.requireId() + "/" + east.requireId() + "/", 1);
    }

    @Test
    void codesAreUniqueWithinATenant()
    {
        save(OrgUnit.create(null, "hq", "HQ", 0));

        assertThatThrownBy(() -> save(OrgUnit.create(null, "hq", "Other", 1)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
