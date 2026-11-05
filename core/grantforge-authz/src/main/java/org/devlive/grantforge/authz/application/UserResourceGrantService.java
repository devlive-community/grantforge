// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.devlive.grantforge.audit.application.AuditLog;
import org.devlive.grantforge.audit.application.AuditRecord;
import org.devlive.grantforge.audit.domain.AuditAction;
import org.devlive.grantforge.audit.domain.AuditOutcome;
import org.devlive.grantforge.authz.domain.Application;
import org.devlive.grantforge.authz.domain.ApplicationRepository;
import org.devlive.grantforge.authz.domain.GrantEffect;
import org.devlive.grantforge.authz.domain.Resource;
import org.devlive.grantforge.authz.domain.ResourceRepository;
import org.devlive.grantforge.authz.domain.RoleGrant;
import org.devlive.grantforge.authz.domain.UserResourceGrant;
import org.devlive.grantforge.authz.domain.UserResourceGrantRepository;
import org.devlive.grantforge.common.error.CommonErrorCode;
import org.devlive.grantforge.common.error.GrantForgeException;
import org.devlive.grantforge.identity.domain.UserAccount;
import org.devlive.grantforge.identity.domain.UserAccountRepository;
import org.devlive.grantforge.persistence.secured.DataAction;
import org.devlive.grantforge.persistence.secured.RowScopes;
import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

/** Manages direct resource grants to accounts of the bound tenant. */
@Service
public final class UserResourceGrantService
{
    private final UserResourceGrantRepository grants;
    private final ResourceRepository resources;
    private final ApplicationRepository applications;
    private final UserAccountRepository accounts;
    private final AuthorizationEvaluator evaluator;
    private final RowScopes scopes;
    private final AuditLog audit;
    private final TransactionTemplate transactions;
    private final Clock clock;

    /**
     * Creates the direct grant service.
     *
     * @param grants direct grants
     * @param resources resource catalog
     * @param applications applications
     * @param accounts tenant accounts
     * @param evaluator permission evaluator
     * @param scopes account row scopes
     * @param audit audit log
     * @param manager transaction manager
     * @param clock current time
     */
    public UserResourceGrantService(UserResourceGrantRepository grants, ResourceRepository resources, ApplicationRepository applications,
            UserAccountRepository accounts, AuthorizationEvaluator evaluator, RowScopes scopes, AuditLog audit,
            PlatformTransactionManager manager, Clock clock)
    {
        this.grants = requireNonNull(grants);
        this.resources = requireNonNull(resources);
        this.applications = requireNonNull(applications);
        this.accounts = requireNonNull(accounts);
        this.evaluator = requireNonNull(evaluator);
        this.scopes = requireNonNull(scopes);
        this.audit = requireNonNull(audit);
        this.transactions = new TransactionTemplate(manager);
        this.clock = requireNonNull(clock);
    }

    /**
     * Lists direct grants in an application.
     *
     * @param actor administrator
     * @param account target account
     * @param application target application
     * @return direct grants
     */
    public List<UserResourceGrant> list(long actor, long account, long application)
    {
        return requireNonNull(transactions.execute(status -> {
            scopes.requireWithin(actor, accounts, UserAccount.class, DataAction.READ, account);
            requireApplication(application);
            Set<Long> ids = resources.findTree(application).stream().map(Resource::requireId).collect(Collectors.toSet());
            return grants.findByAccountId(account).stream().filter(grant -> ids.contains(grant.getResourceId())).toList();
        }));
    }

    /**
     * Lists resources the account can use through its roles alone.
     *
     * @param actor administrator
     * @param account target account
     * @param application target application
     * @return role-derived resource IDs
     */
    public Set<Long> roleResources(long actor, long account, long application)
    {
        return requireNonNull(transactions.execute(status -> {
            scopes.requireWithin(actor, accounts, UserAccount.class, DataAction.READ, account);
            requireApplication(application);
            return evaluator.usableResources(account, application);
        }));
    }

    /**
     * Applies direct allowances and revocations atomically.
     *
     * @param actor administrator
     * @param account target account
     * @param application target application
     * @param changes requested changes
     * @return direct grants after the changes
     * @throws GrantForgeException if the changes are invalid or conflict with another update
     */
    public List<UserResourceGrant> apply(long actor, long account, long application, List<GrantChange> changes)
    {
        try {
            return requireNonNull(transactions.execute(status -> {
                scopes.requireWithin(actor, accounts, UserAccount.class, DataAction.UPDATE, account);
                Application app = requireApplication(application);
                if (Application.CONSOLE.equals(app.getCode())) {
                    throw new GrantForgeException(CommonErrorCode.FORBIDDEN, "direct grants to the console are not supported");
                }
                Map<Long, Resource> tree = resources.findTree(application).stream().collect(Collectors.toMap(Resource::requireId, Function.identity()));
                Map<Long, UserResourceGrant> existing = grants.findByAccountId(account).stream()
                        .filter(grant -> tree.containsKey(grant.getResourceId()))
                        .collect(Collectors.toMap(UserResourceGrant::getResourceId, Function.identity()));
                Set<Long> grantable = evaluator.grantableResources(actor, application);
                var catalog = evaluator.catalog(application);
                Map<Long, GrantChange> unique = new LinkedHashMap<>();
                changes.forEach(change -> unique.put(change.resourceId(), change));
                for (GrantChange change : unique.values()) {
                    Resource resource = tree.get(change.resourceId());
                    if (resource == null) {
                        throw new GrantForgeException(CommonErrorCode.NOT_FOUND, "resource not found in application");
                    }
                    if (change.effect() != null && change.effect() != GrantEffect.ALLOW) {
                        throw new GrantForgeException(CommonErrorCode.BAD_REQUEST, "only direct allowances are supported");
                    }
                    if (change.effect() == GrantEffect.ALLOW) {
                        if (!RoleGrant.GRANTABLE.contains(resource.getType())) {
                            throw new GrantForgeException(AuthzErrorCode.GRANT_TYPE_UNSUPPORTED, "resource type cannot be granted");
                        }
                        UserResourceGrant proposed = UserResourceGrant.create(account, resource, change.expiresAt(), actor);
                        Set<Long> implied = evaluator.usableWithGrants(List.of(), catalog, List.of(proposed), clock.instant()).keySet();
                        if (!grantable.contains(resource.requireId()) || !grantable.containsAll(implied)) {
                            throw new GrantForgeException(AuthzErrorCode.GRANT_EXCEEDS_ACTOR, "resource cannot be granted", resource.getCode());
                        }
                    }
                }
                for (GrantChange change : unique.values()) {
                    Resource resource = requireNonNull(tree.get(change.resourceId()));
                    UserResourceGrant stored = existing.get(change.resourceId());
                    if (change.effect() == null) {
                        if (stored != null) {
                            grants.delete(stored);
                            existing.remove(change.resourceId());
                        }
                    }
                    else if (stored == null) {
                        UserResourceGrant created = UserResourceGrant.create(account, resource, change.expiresAt(), actor);
                        grants.save(created);
                        existing.put(change.resourceId(), created);
                    }
                    else {
                        stored.update(change.expiresAt(), actor);
                    }
                }
                grants.flush();
                audit.recordWithChange(new AuditRecord(AuditAction.USER_RESOURCE_GRANTS_CHANGED, AuditOutcome.SUCCESS,
                        TenantContext.requireTenantId(), actor, null, Long.toString(account), Integer.toString(changes.size())));
                return existing.values().stream().toList();
            }));
        }
        catch (DataIntegrityViolationException race) {
            throw new GrantForgeException(CommonErrorCode.CONFLICT, "direct grants changed concurrently", race);
        }
    }

    private Application requireApplication(long id)
    {
        return applications.findById(id).orElseThrow(() -> new GrantForgeException(CommonErrorCode.NOT_FOUND, "application not found"));
    }
}
