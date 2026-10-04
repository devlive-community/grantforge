// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.oauth.application;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKMatcher;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.devlive.grantforge.audit.application.AuditLog;
import org.devlive.grantforge.audit.domain.AuditEventRepository;
import org.devlive.grantforge.authz.application.CatalogAccess;
import org.devlive.grantforge.common.error.CommonErrorCode;
import org.devlive.grantforge.common.error.GrantForgeException;
import org.devlive.grantforge.oauth.domain.SigningKeyRecord;
import org.devlive.grantforge.oauth.domain.SigningKeyRepository;
import org.devlive.grantforge.identity.application.SecretBox;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.doThrow;

@DataJpaTest(properties = {"grantforge.oauth.signing-key-rotation=30d", "grantforge.oauth.signing-key-retention=2d"})
@Import({SigningKeys.class, SecretBox.class, AuditLog.class, OAuthConfiguration.class, SigningKeysTest.MovingClock.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SigningKeysTest
{
    static final AtomicReference<Instant> TIME = new AtomicReference<>(OAuthFixture.NOW);

    @Autowired
    private SigningKeys keys;

    @Autowired
    private SigningKeyRepository stored;

    @Autowired
    private AuditEventRepository events;

    @MockitoBean
    private CatalogAccess access;

    @BeforeEach
    void startTheClock()
    {
        TIME.set(OAuthFixture.NOW);
        // The bean outlives each test; what it read of the previous test's keys must go.
        ((AtomicReference<?>) requireNonNull(ReflectionTestUtils.getField(keys, "loaded"))).set(null);
    }

    @AfterEach
    void deleteRows()
    {
        stored.deleteAllInBatch();
        events.deleteAllInBatch();
    }

    @Test
    void createsTheFirstKeyAndKeepsItsPrivateHalfSealed() throws Exception
    {
        List<JWK> signing = select(keys.signing());
        List<JWK> published = select(keys.published());

        assertThat(signing).singleElement().satisfies(key -> assertThat(key.isPrivate()).isTrue());
        assertThat(published).singleElement().satisfies(key -> {
            assertThat(key.isPrivate()).isFalse();
            assertThat(key.getKeyID()).isEqualTo(signing.get(0).getKeyID());
            assertThat(key.getAlgorithm()).isEqualTo(JWSAlgorithm.RS256);
        });
        SigningKeyRecord record = stored.findAll().get(0);
        assertThat(record.getPrivateKey()).startsWith("v1:");
        assertThat(keys.list()).singleElement().satisfies(view -> assertThat(view.active()).isTrue());
        assertThat(events.findAll()).singleElement().satisfies(event -> assertThat(event.getReason()).isEqualTo("first key"));
    }

    @Test
    void rotatesByHandAndOnScheduleAndPublishesRetiredKeysForTheRetentionPeriod() throws Exception
    {
        String first = select(keys.signing()).get(0).getKeyID();
        SigningKeyView second = keys.rotate(1);

        assertThat(second.keyId()).isNotEqualTo(first);
        assertThat(select(keys.signing())).extracting(JWK::getKeyID).containsExactly(second.keyId());
        assertThat(select(keys.published())).extracting(JWK::getKeyID).containsExactlyInAnyOrder(first, second.keyId());
        assertThat(keys.list()).extracting(SigningKeyView::keyId, SigningKeyView::active)
                .containsExactly(tuple(second.keyId(), true), tuple(first, false));

        // Thirty-one days on, the next read rotates on its own; the first key is no longer published.
        TIME.set(OAuthFixture.NOW.plus(Duration.ofDays(31)));
        String third = select(keys.signing()).get(0).getKeyID();
        assertThat(third).isNotIn(first, second.keyId());
        assertThat(select(keys.published())).extracting(JWK::getKeyID).containsExactlyInAnyOrder(second.keyId(), third);
        assertThat(keys.purgeRetired()).isOne();
        assertThat(stored.count()).isEqualTo(2);
    }

    @Test
    void keepsTheNewestWhenTwoNodesCreatedKeysAtOnce() throws Exception
    {
        String first = select(keys.signing()).get(0).getKeyID();
        SigningKeyRecord twin = stored.findAll().get(0);
        stored.save(SigningKeyRecord.create("twin", twin.getAlgorithm(), twin.getPublicKey(), twin.getPrivateKey(),
                OAuthFixture.NOW.plusSeconds(1)));
        TIME.set(OAuthFixture.NOW.plus(SigningKeys.REFRESH).plusSeconds(1));

        assertThat(select(keys.signing())).extracting(JWK::getKeyID).containsExactly("twin");
        assertThat(keys.list()).filteredOn(SigningKeyView::active).extracting(SigningKeyView::keyId).containsExactly("twin");
        assertThat(select(keys.published())).extracting(JWK::getKeyID).contains(first);
    }

    @Test
    void onlyPlatformAdministratorsRotateByHand()
    {
        doThrow(new GrantForgeException(CommonErrorCode.FORBIDDEN, "no")).when(access).requireEditor(9);

        assertThatThrownBy(() -> keys.rotate(9)).isInstanceOf(GrantForgeException.class);
        assertThat(stored.count()).isZero();
    }

    private static List<JWK> select(JWKSource<SecurityContext> source) throws Exception
    {
        return source.get(new JWKSelector(new JWKMatcher.Builder().build()), null);
    }

    /** A clock the tests move. */
    static class MovingClock
    {
        @Bean
        @Primary
        Clock movingClock()
        {
            return new Clock()
            {
                @Override
                public ZoneId getZone()
                {
                    return ZoneOffset.UTC;
                }

                @Override
                public Clock withZone(ZoneId zone)
                {
                    return this;
                }

                @Override
                public Instant instant()
                {
                    return requireNonNull(TIME.get());
                }
            };
        }
    }
}
