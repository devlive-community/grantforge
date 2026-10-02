// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ApplicationRepositoryTest
{
    @Autowired
    private ApplicationRepository applications;

    @AfterEach
    void deleteRows()
    {
        applications.deleteAllInBatch();
    }

    @Test
    void listsBuiltInApplicationsFirstAndFindsThemByCode()
    {
        applications.save(Application.create("billing", "Billing", null));
        applications.save(Application.create("crm", "Accounts", null));
        applications.save(Application.create(Application.CONSOLE, "Zeta console", null).markBuiltin());

        assertThat(applications.findOrdered()).extracting(Application::getCode).containsExactly(Application.CONSOLE, "crm",
                "billing");
        assertThat(applications.findByCode("crm")).map(Application::getName).contains("Accounts");
        assertThat(applications.findByCode("nope")).isEmpty();
    }

    @Test
    void codesAreUnique()
    {
        applications.save(Application.create("crm", "CRM", null));

        assertThatThrownBy(() -> applications.saveAndFlush(Application.create("crm", "Other", null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
