// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.audit;

import jakarta.persistence.EntityManagerFactory;
import org.devlive.grantforge.audit.domain.AuditAction;
import org.devlive.grantforge.audit.domain.AuditEvent;
import org.devlive.grantforge.audit.domain.AuditEventRepository;
import org.devlive.grantforge.audit.domain.AuditOutcome;
import org.devlive.grantforge.persistence.naming.SchemaNamingVerifier;
import org.devlive.grantforge.testsupport.TestDatabase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Applies the audit changelog to a real database selected with {@code -Dgrantforge.it.database}; context start-up
 * proves that Hibernate validates the migrated schema.
 */
@SpringBootTest(classes = TestAuditApplication.class)
class AuditSchemaIT
{
    private static final TestDatabase DATABASE = TestDatabase.fromSystemProperty();
    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MICROS);

    @Autowired
    private AuditEventRepository events;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry)
    {
        registry.add("spring.datasource.url", DATABASE::url);
        registry.add("spring.datasource.username", DATABASE::username);
        registry.add("spring.datasource.password", DATABASE::password);
    }

    @AfterAll
    static void stopDatabase()
    {
        DATABASE.close();
    }

    @AfterEach
    void deleteRows()
    {
        events.deleteAllInBatch();
    }

    @Test
    void eventsRoundTripWithUnicodeAndTimestamps()
    {
        String agent = "Mozilla/5.0 浏览器 🔐 " + "x".repeat(300);
        events.save(AuditEvent.of(NOW, AuditAction.LOGIN_FAILED, AuditOutcome.FAILURE, null, 42L, "管理员", null,
                "GF-IDENTITY-020", "2001:db8::ffff:192.0.2.1", agent, "req-1"));

        AuditEvent saved = events.findByActorIdAndActionInOrderByOccurredAtDescIdDesc(42L,
                Set.of(AuditAction.LOGIN_FAILED), PageRequest.of(0, 10)).getContent().get(0);
        assertThat(saved.getOccurredAt()).isEqualTo(NOW);
        assertThat(saved.getActorName()).isEqualTo("管理员");
        assertThat(saved.getTenantId()).isNull();
        assertThat(saved.getUserAgent()).hasSize(AuditEvent.MAX_USER_AGENT).startsWith("Mozilla/5.0 浏览器 🔐");
    }

    @Test
    void mappedNamesStayPortable()
    {
        assertThat(SchemaNamingVerifier.verify(entityManagerFactory)).isEmpty();
    }
}
