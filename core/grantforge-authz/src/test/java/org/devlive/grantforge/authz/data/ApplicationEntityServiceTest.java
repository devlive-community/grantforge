// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.data;

import org.devlive.grantforge.audit.application.AuditLog;
import org.devlive.grantforge.audit.domain.AuditAction;
import org.devlive.grantforge.audit.domain.AuditEvent;
import org.devlive.grantforge.audit.domain.AuditEventRepository;
import org.devlive.grantforge.authz.application.AuthzErrorCode;
import org.devlive.grantforge.authz.domain.Application;
import org.devlive.grantforge.authz.domain.ApplicationEntity;
import org.devlive.grantforge.authz.domain.ApplicationEntityRepository;
import org.devlive.grantforge.authz.domain.ApplicationRepository;
import org.devlive.grantforge.common.error.CommonErrorCode;
import org.devlive.grantforge.common.error.FieldIssue;
import org.devlive.grantforge.common.error.GrantForgeException;
import org.devlive.grantforge.persistence.secured.DataField;
import org.devlive.grantforge.persistence.secured.DataFieldType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

@DataJpaTest
@Import({ApplicationEntityService.class, AuditLog.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ApplicationEntityServiceTest
{
    private static final DataField STATUS = new DataField("status", "Status", DataFieldType.CHOICE, List.of("OPEN", "PAID"));

    @Autowired
    private ApplicationEntityService service;

    @Autowired
    private ApplicationEntityRepository entities;

    @Autowired
    private ApplicationRepository applications;

    @Autowired
    private AuditEventRepository events;

    private long shop;

    @BeforeEach
    void createApplication()
    {
        shop = applications.save(Application.create("shop", "Shop", null)).requireId();
    }

    @AfterEach
    void deleteRows()
    {
        entities.deleteAll();
        applications.deleteAllInBatch();
        events.deleteAllInBatch();
    }

    @Test
    void replacesWhatTheApplicationDeclaredAndAuditsOnlyChanges()
    {
        assertThat(service.declare(shop, List.of(new EntityDeclaration("order", " Orders ", true, true, List.of(STATUS)),
                new EntityDeclaration("invoice", "Invoices", false, false, List.of())))).isEqualTo(2);
        assertThat(entities.findByCode("shop:order")).hasValueSatisfying(order -> {
            assertThat(order.getName()).isEqualTo("Orders");
            assertThat(order.getFields()).containsExactly(STATUS);
        });
        service.declare(shop, List.of(new EntityDeclaration("order", "Orders", true, true, List.of(STATUS)),
                new EntityDeclaration("invoice", "Invoices", false, false, List.of())));
        service.declare(shop, List.of(new EntityDeclaration("order", "Orders", false, true, List.of())));

        assertThat(entities.findByApplicationIdOrderByCode(shop)).extracting(ApplicationEntity::getCode).containsExactly("shop:order");
        assertThat(entities.findByCode("shop:order")).hasValueSatisfying(order -> assertThat(order.isOwned()).isFalse());
        assertThat(events.findAll()).extracting(AuditEvent::getAction, AuditEvent::getTargetId)
                .containsOnly(tuple(AuditAction.DATA_ENTITIES_DECLARED, "shop")).hasSize(2);
    }

    @Test
    void refusesWrongDeclarationsNamingEachProblem()
    {
        List<DataField> tooMany = IntStream.range(0, 51).mapToObj(i -> new DataField("f" + i, "F", DataFieldType.TEXT, List.of())).toList();
        List<EntityDeclaration> wrong = List.of(
                new EntityDeclaration("Order", " ", false, false, List.of(new DataField("1x", "", DataFieldType.CHOICE, List.of()))),
                new EntityDeclaration("lead", "Leads", false, false, List.of(new DataField("a", "A", DataFieldType.TEXT, List.of("x")))),
                new EntityDeclaration("lead", "Leads", false, false, tooMany));

        assertThatThrownBy(() -> service.declare(shop, wrong)).isInstanceOfSatisfying(GrantForgeException.class, error -> {
            assertThat(error.getErrorCode()).isEqualTo(AuthzErrorCode.DATA_ENTITIES_INVALID);
            assertThat(error.getFieldIssues()).extracting(FieldIssue::field).containsExactly("entities[0].code", "entities[0].name",
                    "entities[0].fields[0].code", "entities[0].fields[0].name", "entities[0].fields[0].choices",
                    "entities[1].fields[0].choices", "entities[2].code", "entities[2].fields");
        });
        List<EntityDeclaration> hundredAndOne = Collections.nCopies(101, new EntityDeclaration("x1", "X", false, false, List.of()));
        assertThatThrownBy(() -> service.declare(shop, hundredAndOne)).isInstanceOf(GrantForgeException.class);
        long console = applications.save(Application.create(Application.CONSOLE, "Console", null).markBuiltin()).requireId();
        assertThatThrownBy(() -> service.declare(console, List.of())).isInstanceOfSatisfying(GrantForgeException.class,
                error -> assertThat(error.getFieldIssues()).extracting(FieldIssue::field).containsExactly("entities"));
        assertThatThrownBy(() -> service.declare(42, List.of())).isInstanceOfSatisfying(GrantForgeException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(CommonErrorCode.NOT_FOUND));
        assertThat(entities.count()).isZero();
    }
}
