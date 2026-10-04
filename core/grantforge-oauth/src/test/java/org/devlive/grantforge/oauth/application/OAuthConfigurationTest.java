// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.oauth.application;

import org.devlive.grantforge.audit.application.AuditLog;
import org.devlive.grantforge.audit.domain.AuditEventRepository;
import org.devlive.grantforge.authz.application.CatalogAccess;
import org.devlive.grantforge.oauth.domain.SigningKeyRepository;
import org.devlive.grantforge.service.SecretBox;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = "grantforge.oauth.issuer=https://id.example")
@Import({OAuthConfiguration.class, SigningKeys.class, SecretBox.class, AuditLog.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class OAuthConfigurationTest
{
    @Autowired
    private JwtEncoder encoder;

    @Autowired
    private JwtDecoder decoder;

    @Autowired
    private AuthorizationServerSettings settings;

    @Autowired
    private SigningKeyRepository keys;

    @Autowired
    private AuditEventRepository events;

    @MockitoBean
    private CatalogAccess access;

    @AfterEach
    void deleteRows()
    {
        keys.deleteAllInBatch();
        events.deleteAllInBatch();
    }

    @Test
    void signsWithTheActiveKeyAndVerifiesWithThePublishedOnes()
    {
        Instant now = Instant.now();
        Jwt signed = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(),
                JwtClaimsSet.builder().subject("42").issuedAt(now).expiresAt(now.plus(Duration.ofMinutes(5))).build()));

        assertThat(signed.getHeaders()).containsEntry("kid", keys.findAll().get(0).getKeyId());
        assertThat(decoder.decode(signed.getTokenValue()).getSubject()).isEqualTo("42");
        assertThat(settings.getIssuer()).isEqualTo("https://id.example");
    }

    @Test
    void leavesTheIssuerToTheRequestWhenNoneIsConfigured()
    {
        assertThat(new OAuthConfiguration().authorizationServerSettings(new OAuthProperties(null, Duration.ZERO, Duration.ofDays(2)))
                .getIssuer()).isNull();
    }
}
