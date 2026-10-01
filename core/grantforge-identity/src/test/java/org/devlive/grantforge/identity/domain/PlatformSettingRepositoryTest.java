// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.domain;

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
class PlatformSettingRepositoryTest
{
    @Autowired
    private PlatformSettingRepository settings;

    @AfterEach
    void deleteRows()
    {
        settings.deleteAllInBatch();
    }

    @Test
    void findsByKey()
    {
        settings.save(PlatformSetting.of("setup.state", "pending"));

        assertThat(settings.findBySettingKey("setup.state")).get()
                .extracting(PlatformSetting::getSettingValue).isEqualTo("pending");
        assertThat(settings.findBySettingKey("missing")).isEmpty();
    }

    @Test
    void keysAreUnique()
    {
        settings.saveAndFlush(PlatformSetting.of("k", "a"));

        assertThatThrownBy(() -> settings.saveAndFlush(PlatformSetting.of("k", "b")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
