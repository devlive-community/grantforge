// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

import org.devlive.grantforge.identity.domain.IdentitySource;
import org.devlive.grantforge.identity.domain.IdentitySourceType;
import org.devlive.grantforge.identity.domain.PlatformSettingRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class IdentitySourceSettingsTest
{
    @Autowired
    private PlatformSettingRepository platformSettings;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void readsTheSettingsOfEachTypeAndOpensTheSecret()
    {
        IdentitySourceSettings settings = new IdentitySourceSettings(new SecretBox(Base64.getEncoder().encodeToString(new byte[32]),
                platformSettings, transactionManager));
        IdentitySource ldap = IdentitySource.create("corp", IdentitySourceType.LDAP);
        LdapSettings directory = LdapSettings.of("ldap://a", "dc=example");
        ldap.configure("Corp", true, true, IdentitySourceSettings.write(directory), null);
        ldap.storeSecret(settings.seal("bind-secret"));
        IdentitySource oidc = IdentitySource.create("okta", IdentitySourceType.OIDC);
        OidcSettings provider = new OidcSettings("https://login.example.com", "c", "", "", "", "");
        oidc.configure("Okta", true, true, IdentitySourceSettings.write(provider), null);

        assertThat(settings.ldap(ldap)).isEqualTo(directory);
        assertThat(settings.oidc(oidc)).isEqualTo(provider);
        assertThat(settings.secret(ldap)).isEqualTo("bind-secret");
        assertThat(settings.secret(oidc)).isNull();
        assertThatThrownBy(() -> settings.oidc(ldap)).isInstanceOf(IllegalArgumentException.class);
    }
}
