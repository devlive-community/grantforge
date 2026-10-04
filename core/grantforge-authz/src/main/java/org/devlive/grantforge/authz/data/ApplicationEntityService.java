// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.data;

import org.devlive.grantforge.audit.application.AuditLog;
import org.devlive.grantforge.audit.application.AuditRecord;
import org.devlive.grantforge.audit.domain.AuditAction;
import org.devlive.grantforge.audit.domain.AuditOutcome;
import org.devlive.grantforge.authz.application.AuthzErrorCode;
import org.devlive.grantforge.authz.domain.Application;
import org.devlive.grantforge.authz.domain.ApplicationEntity;
import org.devlive.grantforge.authz.domain.ApplicationEntityField;
import org.devlive.grantforge.authz.domain.ApplicationEntityRepository;
import org.devlive.grantforge.authz.domain.ApplicationRepository;
import org.devlive.grantforge.common.error.CommonErrorCode;
import org.devlive.grantforge.common.error.FieldIssue;
import org.devlive.grantforge.common.error.GrantForgeException;
import org.devlive.grantforge.persistence.secured.DataField;
import org.devlive.grantforge.persistence.secured.DataFieldType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

/**
 * Takes in the data entities an application declares, as it does at start-up through the open API: what it declares
 * replaces what it declared before, and entities it no longer declares go (policies naming them then apply to nothing).
 * The console's entities come from its code, never from here.
 */
@Service
public final class ApplicationEntityService
{
    /** Most entities an application declares. */
    public static final int MAX_ENTITIES = 100;

    /** Most fields of an entity. */
    public static final int MAX_FIELDS = 50;

    /** Most choices of a field. */
    public static final int MAX_CHOICES = 50;

    private static final Pattern ENTITY = Pattern.compile("[a-z][a-z0-9-]{1,39}");
    private static final Pattern FIELD = Pattern.compile("[a-zA-Z][a-zA-Z0-9_.]{0,63}");
    private static final int NAME_MAX = 128;
    private static final int CHOICE_MAX = 64;
    private static final int CHOICES_MAX = 1000;
    private static final int CODE_MAX = 64;

    private final ApplicationEntityRepository entities;
    private final ApplicationRepository applications;
    private final AuditLog audit;
    private final TransactionTemplate transactions;

    /**
     * Creates the service.
     *
     * @param entities the declared entities
     * @param applications the applications declaring them
     * @param audit records declarations that change something
     * @param transactionManager opens transactions
     */
    public ApplicationEntityService(ApplicationEntityRepository entities, ApplicationRepository applications, AuditLog audit,
            PlatformTransactionManager transactionManager)
    {
        this.entities = requireNonNull(entities, "entities");
        this.applications = requireNonNull(applications, "applications");
        this.audit = requireNonNull(audit, "audit");
        this.transactions = new TransactionTemplate(requireNonNull(transactionManager, "transactionManager"));
    }

    /**
     * Replaces an application's entities with those it declares now.
     *
     * @param applicationId the application
     * @param declared the entities
     * @return how many entities the application has now
     * @throws GrantForgeException with {@link AuthzErrorCode#DATA_ENTITIES_INVALID} naming every problem, or
     *         {@link CommonErrorCode#NOT_FOUND} for an unknown application
     */
    public int declare(long applicationId, List<EntityDeclaration> declared)
    {
        return requireNonNull(transactions.execute(status -> {
            Application application = applications.findById(applicationId)
                    .orElseThrow(() -> new GrantForgeException(CommonErrorCode.NOT_FOUND, "no application " + applicationId));
            check(application, declared);
            Map<String, ApplicationEntity> existing = entities.findByApplicationIdOrderByCode(applicationId).stream()
                    .collect(Collectors.toMap(ApplicationEntity::getCode, Function.identity()));
            boolean changed = false;
            Set<String> kept = new HashSet<>();
            for (EntityDeclaration entity : declared) {
                String code = application.getCode() + ApplicationEntity.SEPARATOR + entity.code();
                kept.add(code);
                ApplicationEntity stored = existing.get(code);
                if (stored == null) {
                    stored = ApplicationEntity.create(applicationId, code);
                    changed = true;
                }
                else if (!same(stored, entity)) {
                    changed = true;
                }
                stored.describe(entity.name().strip(), entity.owned(), entity.unitBased(), entity.fields());
                entities.save(stored);
            }
            for (ApplicationEntity gone : existing.values()) {
                if (!kept.contains(gone.getCode())) {
                    entities.delete(gone);
                    changed = true;
                }
            }
            if (changed) {
                audit.recordWithChange(new AuditRecord(AuditAction.DATA_ENTITIES_DECLARED, AuditOutcome.SUCCESS, null, null, null,
                        application.getCode(), declared.size() + " entities"));
            }
            return declared.size();
        }));
    }

    private static boolean same(ApplicationEntity stored, EntityDeclaration declared)
    {
        return stored.getName().equals(declared.name().strip()) && stored.isOwned() == declared.owned()
                && stored.isUnitBased() == declared.unitBased() && stored.getFields().equals(declared.fields());
    }

    private static void check(Application application, List<EntityDeclaration> declared)
    {
        List<FieldIssue> issues = new ArrayList<>();
        if (Application.CONSOLE.equals(application.getCode())) {
            issues.add(FieldIssue.of("entities", "error.data.entities-console"));
        }
        if (declared.size() > MAX_ENTITIES) {
            issues.add(FieldIssue.of("entities", "error.data.entities-too-many", MAX_ENTITIES));
        }
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < declared.size(); i++) {
            EntityDeclaration entity = declared.get(i);
            String path = "entities[" + i + "]";
            if (!ENTITY.matcher(entity.code()).matches()
                    || application.getCode().length() + ApplicationEntity.SEPARATOR.length() + entity.code().length() > CODE_MAX) {
                issues.add(FieldIssue.of(path + ".code", "error.data.entity-code"));
            }
            else if (!codes.add(entity.code())) {
                issues.add(FieldIssue.of(path + ".code", "error.data.entity-duplicate"));
            }
            if (entity.name().isBlank() || entity.name().strip().length() > NAME_MAX) {
                issues.add(FieldIssue.of(path + ".name", "error.data.entity-name", NAME_MAX));
            }
            checkFields(entity.fields(), path, issues);
        }
        if (!issues.isEmpty()) {
            throw new GrantForgeException(AuthzErrorCode.DATA_ENTITIES_INVALID, issues.size() + " entity issues").withFieldIssues(issues);
        }
    }

    private static void checkFields(List<DataField> fields, String path, List<FieldIssue> issues)
    {
        if (fields.size() > MAX_FIELDS) {
            issues.add(FieldIssue.of(path + ".fields", "error.data.fields-too-many", MAX_FIELDS));
        }
        Set<String> codes = new HashSet<>();
        for (int j = 0; j < fields.size(); j++) {
            DataField field = fields.get(j);
            String at = path + ".fields[" + j + "]";
            if (!FIELD.matcher(field.code()).matches() || !codes.add(field.code())) {
                issues.add(FieldIssue.of(at + ".code", "error.data.field-code"));
            }
            if (field.name().isBlank() || field.name().length() > NAME_MAX) {
                issues.add(FieldIssue.of(at + ".name", "error.data.entity-name", NAME_MAX));
            }
            boolean choice = field.type() == DataFieldType.CHOICE;
            List<String> choices = field.choices();
            if (choice != !choices.isEmpty() || choices.size() > MAX_CHOICES
                    || choices.stream().anyMatch(value -> value.isBlank() || value.length() > CHOICE_MAX
                            || value.contains(ApplicationEntityField.CHOICE_SEPARATOR))
                    || String.join(ApplicationEntityField.CHOICE_SEPARATOR, choices).length() > CHOICES_MAX) {
                issues.add(FieldIssue.of(at + ".choices", "error.data.field-choices", MAX_CHOICES));
            }
        }
    }
}
