// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ManagedServiceTest
{
    @Test
    void holdsItsDescriptionAndConfiguration()
    {
        ManagedService service = ManagedService.create("hive", "prod", "Prod", "main");
        assertThat(service.isEnabled()).isTrue();
        assertThat(service.getConfig()).isEqualTo("{}");
        service.describe("prod2", "Prod 2", null);
        service.configure("{\"url\":\"x\"}", "{}");
        service.enable(false);
        assertThat(service.getPolicyVersion()).isZero();
        service.policiesChanged();
        service.policiesChanged();
        assertThat(service.getPolicyVersion()).isEqualTo(2);
        assertThat(service).extracting(ManagedService::getName, ManagedService::getLabel, ManagedService::getDescription,
                ManagedService::getServiceType, ManagedService::isEnabled, ManagedService::getConfig, ManagedService::getSecrets)
                .containsExactly("prod2", "Prod 2", null, "hive", false, "{\"url\":\"x\"}", "{}");
    }
}
