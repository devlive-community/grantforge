// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.catalog;

import org.devlive.grantforge.authz.application.AuthorizationEvaluator;
import org.devlive.grantforge.authz.application.CatalogAccess;
import org.devlive.grantforge.authz.application.DependencyService;
import org.devlive.grantforge.authz.application.DependencyView;
import org.devlive.grantforge.authz.application.ResourceService;
import org.devlive.grantforge.authz.application.ResourceView;
import org.devlive.grantforge.authz.domain.ApplicationRepository;
import org.devlive.grantforge.authz.domain.DenyMode;
import org.devlive.grantforge.authz.domain.DependencyKind;
import org.devlive.grantforge.authz.domain.ResourceDetails;
import org.devlive.grantforge.authz.domain.ResourceType;
import org.devlive.grantforge.authz.domain.SystemRole;
import org.devlive.grantforge.common.error.CommonErrorCode;
import org.devlive.grantforge.common.error.GrantForgeException;
import org.devlive.grantforge.common.security.RequirePermission;
import org.devlive.grantforge.identity.application.PlatformAdministrators;
import org.devlive.grantforge.server.security.SecurityErrorCode;
import org.devlive.grantforge.server.security.SessionUser;
import org.devlive.grantforge.server.web.PathIds;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static java.util.Objects.requireNonNull;

/** Imports an exported resource tree into an application, with transactional preview. */
@RestController
@RequestMapping("/api/v1/applications/{id}/resource-tree")
public final class ResourceTreeImportController
{
    private static final int MAX_RESOURCES = 1000;
    private static final int MAX_DEPENDENCIES = 5000;
    private final ResourceService resources;
    private final DependencyService dependencies;
    private final ApplicationRepository applications;
    private final CatalogAccess access;
    private final AuthorizationEvaluator authorization;
    private final PlatformAdministrators administrators;
    private final TransactionTemplate transactions;

    /**
     * Creates the importer.
     *
     * @param resources resource service
     * @param dependencies dependency service
     * @param applications application repository
     * @param access catalog access checks
     * @param authorization permission evaluator
     * @param administrators platform administrator check
     * @param transactionManager transaction manager
     */
    public ResourceTreeImportController(ResourceService resources, DependencyService dependencies, ApplicationRepository applications,
            CatalogAccess access, AuthorizationEvaluator authorization, PlatformAdministrators administrators, PlatformTransactionManager transactionManager)
    {
        this.resources = requireNonNull(resources, "resources");
        this.dependencies = requireNonNull(dependencies, "dependencies");
        this.applications = requireNonNull(applications, "applications");
        this.access = requireNonNull(access, "access");
        this.authorization = requireNonNull(authorization, "authorization");
        this.administrators = requireNonNull(administrators, "administrators");
        this.transactions = new TransactionTemplate(requireNonNull(transactionManager, "transactionManager"));
    }

    /**
     * Clears an application's resource tree atomically, retaining existing deletion safeguards.
     *
     * @param user current user
     * @param id target application ID
     * @throws GrantForgeException if the user is not a platform administrator or a resource cannot be deleted
     */
    @RequirePermission("platform.resource.delete")
    @DeleteMapping
    @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void clear(@AuthenticationPrincipal SessionUser user, @PathVariable String id)
    {
        if (!administrators.isPlatformAdministrator(user.accountId())
                && !authorization.snapshot(user.accountId()).roles().contains(SystemRole.PLATFORM_ADMIN.code())
                && !access.scoped(user.accountId())) {
            throw new GrantForgeException(CommonErrorCode.FORBIDDEN, "platform administrator required");
        }
        long applicationId = PathIds.parse(id, "application");
        access.requireApplication(user.accountId(), applicationId);
        transactions.executeWithoutResult(status -> {
            var application = applications.findById(applicationId)
                    .orElseThrow(() -> new GrantForgeException(CommonErrorCode.NOT_FOUND, "application not found"));
            if (application.isBuiltin()) {
                throw new GrantForgeException(CommonErrorCode.FORBIDDEN, "built-in application");
            }
            var tree = new ArrayList<>(resources.tree(user.accountId(), applicationId));
            resources.requireNoGrants(tree.stream().map(ResourceView::id).toList());
            for (DependencyView dependency : dependencies.graph(user.accountId(), applicationId)) {
                dependencies.remove(user.accountId(), dependency.id());
            }
            Collections.reverse(tree);
            for (ResourceView resource : tree) {
                resources.delete(user.accountId(), resource.id());
            }
        });
    }

    /**
     * Clears the descendants of a top-level resource without removing the resource itself.
     *
     * @param user current user
     * @param id target application ID
     * @param rootId top-level resource ID
     * @throws GrantForgeException if access is denied or the descendants cannot be cleared
     */
    @RequirePermission("platform.resource.delete")
    @DeleteMapping("/{rootId}/children")
    @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void clearChildren(@AuthenticationPrincipal SessionUser user, @PathVariable String id, @PathVariable String rootId)
    {
        if (!administrators.isPlatformAdministrator(user.accountId())
                && !authorization.snapshot(user.accountId()).roles().contains(SystemRole.PLATFORM_ADMIN.code())
                && !access.scoped(user.accountId())) {
            throw new GrantForgeException(CommonErrorCode.FORBIDDEN, "platform administrator required");
        }
        long applicationId = PathIds.parse(id, "application");
        access.requireApplication(user.accountId(), applicationId);
        long root = PathIds.parse(rootId, "resource");
        transactions.executeWithoutResult(status -> {
            var application = applications.findById(applicationId)
                    .orElseThrow(() -> new GrantForgeException(CommonErrorCode.NOT_FOUND, "application not found"));
            if (application.isBuiltin()) {
                throw new GrantForgeException(CommonErrorCode.FORBIDDEN, "built-in application");
            }
            var tree = resources.tree(user.accountId(), applicationId);
            var parent = tree.stream().filter(resource -> resource.id() == root && resource.parentId() == null).findFirst()
                    .orElseThrow(() -> new GrantForgeException(CommonErrorCode.NOT_FOUND, "top-level resource not found"));
            Set<Long> subtree = new HashSet<>();
            subtree.add(parent.id());
            boolean changed;
            do {
                changed = false;
                for (ResourceView resource : tree) {
                    if (resource.parentId() != null && subtree.contains(resource.parentId())) {
                        changed |= subtree.add(resource.id());
                    }
                }
            }
            while (changed);
            subtree.remove(root);
            resources.requireNoGrants(subtree);
            for (DependencyView dependency : dependencies.graph(user.accountId(), applicationId)) {
                boolean source = subtree.contains(dependency.resourceId());
                boolean target = subtree.contains(dependency.dependsOnId());
                if (source != target) {
                    throw new GrantForgeException(CommonErrorCode.CONFLICT, "dependency crosses the selected resource subtree");
                }
                if (source) {
                    dependencies.remove(user.accountId(), dependency.id());
                }
            }
            var descendants = new ArrayList<>(tree.stream().filter(resource -> subtree.contains(resource.id())).toList());
            Collections.reverse(descendants);
            for (ResourceView resource : descendants) {
                resources.delete(user.accountId(), resource.id());
            }
        });
    }

    /**
     * Validates or applies a version 1 resource tree.
     *
     * @param user current user
     * @param id target application ID
     * @param apply whether to commit the import
     * @param document exported tree
     * @return import counts or validation problems
     * @throws GrantForgeException if the user lacks permission to import dependencies
     */
    @RequirePermission("platform.resource.create")
    @PostMapping("/import")
    public Report importTree(@AuthenticationPrincipal SessionUser user, @PathVariable String id,
            @RequestParam(defaultValue = "false") boolean apply, @RequestBody JsonNode document)
    {
        long applicationId = PathIds.parse(id, "application");
        access.requireEditor(user.accountId());
        access.requireApplication(user.accountId(), applicationId);
        if (document.path("dependencies").isArray() && !document.path("dependencies").isEmpty()
                && !authorization.snapshot(user.accountId()).holds("platform.resource.update")) {
            throw new GrantForgeException(SecurityErrorCode.PERMISSION_DENIED, "dependency permission required");
        }
        try {
            return requireNonNull(transactions.execute(status -> {
                var application = applications.findById(applicationId)
                        .orElseThrow(() -> new GrantForgeException(CommonErrorCode.NOT_FOUND, "application not found"));
                if (application.isBuiltin()) {
                    throw new ImportFailure("application", "Import into a built-in application is not allowed");
                }
                if (!document.isObject() || !document.path("formatVersion").isInt() || document.path("formatVersion").asInt() != 1) {
                    throw new ImportFailure("formatVersion", "Unsupported resource tree format version");
                }
                JsonNode nodes = document.path("resources");
                JsonNode edges = document.path("dependencies");
                if (!nodes.isArray() || !edges.isArray() || edges.size() > MAX_DEPENDENCIES) {
                    throw new ImportFailure("document", "Resources and dependencies must be arrays within the limits");
                }
                State state = new State();
                for (ResourceView item : resources.tree(user.accountId(), applicationId)) {
                    state.known.put(item.code(), item);
                }
                for (DependencyView edge : dependencies.graph(user.accountId(), applicationId)) {
                    state.edges.put(edge.resourceId() + ":" + edge.dependsOnId(), edge.kind());
                }
                for (JsonNode node : nodes) {
                    addNode(user.accountId(), applicationId, node, null, 0, state);
                }
                int index = 0;
                for (JsonNode edge : edges) {
                    addDependency(user.accountId(), edge, index, state);
                    index++;
                }
                if (!apply) {
                    status.setRollbackOnly();
                }
                return new Report(state.created, state.reused, state.addedDependencies, state.reusedDependencies,
                        state.declaredDependencies, apply, List.of());
            }));
        }
        catch (ImportFailure failure) {
            return new Report(0, 0, 0, 0, 0, false, List.of(new Problem(failure.path, String.valueOf(failure.getMessage()))));
        }
        catch (IllegalArgumentException | GrantForgeException failure) {
            return new Report(0, 0, 0, 0, 0, false, List.of(new Problem("document", String.valueOf(failure.getMessage()))));
        }
    }

    private void addNode(long actor, long application, JsonNode node, @Nullable ResourceView parent, int depth, State state)
    {
        state.count++;
        if (!node.isObject() || state.count > MAX_RESOURCES || depth > 15) {
            throw new ImportFailure("resources", "Invalid resource, too many resources or tree too deep");
        }
        String code = text(node, "code");
        String path = "resources/" + code;
        if (!state.seen.add(code)) {
            throw new ImportFailure(path, "Duplicate resource code in the document");
        }
        ResourceType type = ResourceType.valueOf(text(node, "type"));
        ResourceDetails details = new ResourceDetails(text(node, "name"), optional(node, "description"), optional(node, "route"),
                bool(node, "visible", true), bool(node, "enabled", true), mode(node));
        ResourceView existing = state.known.get(code);
        Long parentId = parent == null ? null : parent.id();
        ResourceView current;
        if (existing == null) {
            current = resources.create(actor, application, parentId, type, code, details);
            state.known.put(code, current);
            state.created++;
        }
        else {
            if (existing.type() != type || !Objects.equals(existing.parentId(), parentId)) {
                throw new ImportFailure(path, "Existing resource has a different type or parent");
            }
            current = existing;
            state.reused++;
        }
        JsonNode children = node.path("children");
        if (!children.isArray()) {
            throw new ImportFailure(path + "/children", "Children must be an array");
        }
        for (JsonNode child : children) {
            addNode(actor, application, child, current, depth + 1, state);
        }
    }

    private void addDependency(long actor, JsonNode edge, int index, State state)
    {
        String path = "dependencies/" + index;
        if (!edge.isObject()) {
            throw new ImportFailure(path, "Dependency must be an object");
        }
        String source = text(edge, "resourceCode");
        String target = text(edge, "dependsOnCode");
        ResourceView from = state.known.get(source);
        ResourceView to = state.known.get(target);
        if (from == null || to == null || !state.seen.contains(source) || !state.seen.contains(target)) {
            throw new ImportFailure(path, "Dependency refers to a resource missing from the document");
        }
        DependencyKind kind = DependencyKind.valueOf(text(edge, "kind"));
        String key = from.id() + ":" + to.id();
        DependencyKind existing = state.edges.get(key);
        if (existing == null) {
            dependencies.add(actor, from.id(), to.id(), kind);
            state.edges.put(key, kind);
            state.addedDependencies++;
            if ("DECLARED".equals(optional(edge, "source"))) {
                state.declaredDependencies++;
            }
        }
        else if (existing != kind) {
            throw new ImportFailure(path, "Existing dependency has a different kind");
        }
        else {
            state.reusedDependencies++;
        }
    }

    private static String text(JsonNode node, String key)
    {
        JsonNode value = node.path(key);
        if (!value.isTextual() || value.asText().isBlank()) {
            throw new ImportFailure(key, "A non-empty string is required");
        }
        return value.asText();
    }

    private static @Nullable String optional(JsonNode node, String key)
    {
        JsonNode value = node.path(key);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        if (!value.isTextual()) {
            throw new ImportFailure(key, "Expected a string");
        }
        return value.asText();
    }

    private static boolean bool(JsonNode node, String key, boolean fallback)
    {
        JsonNode value = node.path(key);
        if (value.isMissingNode() || value.isNull()) {
            return fallback;
        }
        if (!value.isBoolean()) {
            throw new ImportFailure(key, "Expected a boolean");
        }
        return value.asBoolean();
    }

    private static DenyMode mode(JsonNode node)
    {
        String value = optional(node, "denyMode");
        return value == null ? DenyMode.HIDE : DenyMode.valueOf(value);
    }

    private static final class State
    {
        private final Map<String, ResourceView> known = new HashMap<>();
        private final Map<String, DependencyKind> edges = new HashMap<>();
        private final Set<String> seen = new HashSet<>();
        private int count;
        private int created;
        private int reused;
        private int addedDependencies;
        private int reusedDependencies;
        private int declaredDependencies;
    }

    private static final class ImportFailure extends RuntimeException
    {
        private static final long serialVersionUID = 1L;
        private final String path;

        private ImportFailure(String path, String message)
        {
            super(message);
            this.path = path;
        }
    }

    /** One import error. */
    public record Problem(String path, String message) {}

    /** Counts and errors from a preview or import. */
    public record Report(int created, int reused, int addedDependencies, int reusedDependencies, int declaredDependencies,
            boolean applied, List<Problem> problems)
    {
        /** Copies the problems to keep the report immutable. */
        public Report
        {
            problems = List.copyOf(problems);
        }
    }
}
