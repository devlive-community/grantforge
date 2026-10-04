// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

import org.devlive.grantforge.identity.domain.PlatformSettingRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SecretBoxTest
{
    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

    @Autowired
    private PlatformSettingRepository settings;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @AfterEach
    void deleteSettings()
    {
        settings.deleteAll();
    }

    @Test
    void sealsSoThatOnlyTheSameKeyOpensAndChangesAreNoticed()
    {
        SecretBox box = new SecretBox(KEY, settings, transactionManager);
        String sealed = box.seal("s3cret");
        assertThat(sealed).startsWith("v1:").doesNotContain("s3cret").isNotEqualTo(box.seal("s3cret"));
        assertThat(box.open(sealed)).isEqualTo("s3cret");

        byte[] other = new byte[32];
        other[0] = 1;
        SecretBox elsewhere = new SecretBox(Base64.getEncoder().encodeToString(other), settings, transactionManager);
        assertThatThrownBy(() -> elsewhere.open(sealed)).isInstanceOf(IllegalStateException.class);
        String tampered = sealed.substring(0, sealed.length() - 2) + (sealed.endsWith("A") ? "BB" : "AA");
        assertThatThrownBy(() -> box.open(tampered)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> box.open("plain")).hasMessage("not a sealed secret");
        assertThatThrownBy(() -> new SecretBox("c2hvcnQ=", settings, transactionManager).seal("x"))
                .hasMessageContaining("32 bytes");
        assertThat(settings.count()).isZero();
    }

    @Test
    void withoutAConfiguredKeyOneIsGeneratedOnceAndShared()
    {
        SecretBox first = new SecretBox(" ", settings, transactionManager);
        String sealed = first.seal("s3cret");
        assertThat(settings.findBySettingKey(SecretBox.SETTING)).isPresent();
        // Another node, starting later, finds the same key.
        assertThat(new SecretBox(null, settings, transactionManager).open(sealed)).isEqualTo("s3cret");
        assertThat(settings.count()).isEqualTo(1);
    }
}
