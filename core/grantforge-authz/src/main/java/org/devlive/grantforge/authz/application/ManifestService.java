// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.devlive.grantforge.authz.domain.DenyMode;
import org.devlive.grantforge.authz.domain.DependencyGraph;
import org.devlive.grantforge.authz.domain.DependencyKind;
import org.devlive.grantforge.authz.domain.DependencySource;
import org.devlive.grantforge.authz.domain.Resource;
import org.devlive.grantforge.authz.domain.ResourceDependency;
import org.devlive.grantforge.authz.domain.ResourceDependencyRepository;
import org.devlive.grantforge.authz.domain.ResourceDetails;
import org.devlive.grantforge.authz.domain.ResourceRepository;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.Objects.requireNonNull;

/**
 * Keeps the console's own resources in step with its permission manifest at every start-up: creates the declared
 * modules, menus, pages and buttons as built-in resources, refreshes their names and routes, and keeps their
 * declared dependencies exactly as the manifest lists them. Runs after {@link ApiCatalogService#synchronize}, whose
 * API resources the manifest refers to by permission code.
 */
@Service
public final class ManifestService
{
    private final ResourceRepository resources;
    private final ResourceDependencyRepository dependencies;
    private final ApplicationService applications;
    private final TransactionTemplate transactions;

    /**
     * Creates the service.
     *
     * @param resources resources
     * @param dependencies dependencies
     * @param applications applications, for the console's own
     * @param transactionManager opens transactions
     */
    public ManifestService(ResourceRepository resources, ResourceDependencyRepository dependencies,
            ApplicationService applications, PlatformTransactionManager transactionManager)
    {
        this.resources = requireNonNull(resources, "resources");
        this.dependencies = requireNonNull(dependencies, "dependencies");
        this.applications = requireNonNull(applications, "applications");
        this.transactions = new TransactionTemplate(requireNonNull(transactionManager, "transactionManager"));
    }

    /**
     * Brings the console's resources in step with the manifest.
     *
     * @param manifest the manifest
     * @return what changed
     * @throws IllegalStateException if the manifest repeats a code, refers to an unknown permission or resource,
     *         places a resource where its type does not fit, contradicts an existing resource's type, or declares a
     *         dependency cycle
     */
    public ManifestReport synchronize(ConsoleManifest manifest)
    {
        List<ManifestEntry> entries = manifest.flatten();
        check(entries);
        long console = applications.registerConsole();
        return requireNonNull(transactions.execute(status -> {
            Tally tally = new Tally();
            Map<String, Resource> declared = new HashMap<>();
            for (ManifestEntry top : manifest.resources()) {
                place(console, null, top, declared, tally);
            }
            List<ResourceDependency> all = new ArrayList<>(dependencies.findByApplicationId(console));
            for (ManifestEntry entry : entries) {
                link(console, entry, declared, all, tally);
            }
            new DependencyGraph(all).cycle().ifPresent(edge -> {
                throw new IllegalStateException("manifest dependencies close a cycle through " + edge.getResourceId()
                        + " -> " + edge.getDependsOnId());
            });
            return new ManifestReport(entries.size(), tally.created, tally.updated, tally.added, tally.removed);
        }));
    }

    private void place(long console, @Nullable Resource parent, ManifestEntry entry, Map<String, Resource> declared, Tally tally)
    {
        Resource resource = resources.findByApplicationIdAndCode(console, entry.code()).orElse(null);
        try {
            if (resource == null) {
                Long parentId = parent == null ? null : parent.requireId();
                resource = Resource.create(console, parent, entry.type(), entry.code(),
                        new ResourceDetails(entry.name(), null, entry.route(), true, true, DenyMode.HIDE),
                        resources.findChildren(console, parentId).size()).markBuiltin();
                resource.declare(entry.name(), entry.nameKey(), entry.route());
                resource = resources.saveAndFlush(resource);
                tally.created++;
            }
            else if (resource.getType() != entry.type()) {
                throw new IllegalStateException("resource " + entry.code() + " exists as " + resource.getType()
                        + " but the manifest declares " + entry.type());
            }
            else {
                boolean changed = resource.declare(entry.name(), entry.nameKey(), entry.route()) || !resource.isBuiltin();
                resource.markBuiltin();
                if (changed) {
                    tally.updated++;
                }
            }
        }
        catch (IllegalArgumentException invalid) {
            throw new IllegalStateException("manifest entry " + entry.code() + ": " + invalid.getMessage(), invalid);
        }
        declared.put(entry.code(), resource);
        for (ManifestEntry child : entry.children()) {
            place(console, resource, child, declared, tally);
        }
    }

    /** Makes an entry's declared dependencies exactly what the manifest lists; {@code all} follows every change. */
    private void link(long console, ManifestEntry entry, Map<String, Resource> declared, List<ResourceDependency> all,
            Tally tally)
    {
        Resource resource = requireNonNull(declared.get(entry.code()));
        Map<Long, Resource> wanted = new HashMap<>();
        for (String permission : entry.apis()) {
            Resource api = resources.findByApplicationIdAndCode(console, ApiCatalogService.RESOURCE_PREFIX + permission)
                    .orElseThrow(() -> unknownPermission(entry, permission));
            wanted.put(api.requireId(), api);
        }
        for (String code : entry.requires()) {
            Resource needed = requireNonNull(declared.get(code));
            wanted.put(needed.requireId(), needed);
        }
        for (ResourceDependency existing : List.copyOf(all)) {
            if (existing.getResourceId() != resource.requireId()) {
                continue;
            }
            boolean keep = wanted.containsKey(existing.getDependsOnId());
            if (keep && existing.getSource() == DependencySource.DECLARED) {
                existing.changeKind(DependencyKind.REQUIRED);
                wanted.remove(existing.getDependsOnId());
            }
            else if (keep || existing.getSource() == DependencySource.DECLARED) {
                // Gone from the manifest, or added by hand before the manifest declared it: the manifest owns it.
                dependencies.delete(existing);
                dependencies.flush();
                all.remove(existing);
                tally.removed += keep ? 0 : 1;
            }
        }
        for (Resource target : wanted.values()) {
            all.add(dependencies.save(declaredDependency(entry, resource, target)));
            tally.added++;
        }
    }

    private static ResourceDependency declaredDependency(ManifestEntry entry, Resource resource, Resource target)
    {
        try {
            return ResourceDependency.create(resource, target, DependencyKind.REQUIRED, DependencySource.DECLARED);
        }
        catch (IllegalArgumentException invalid) {
            throw new IllegalStateException("manifest entry " + entry.code() + ": " + invalid.getMessage(), invalid);
        }
    }

    private static IllegalStateException unknownPermission(ManifestEntry entry, String permission)
    {
        return new IllegalStateException("manifest entry " + entry.code() + " needs the unknown permission " + permission);
    }

    private static void check(List<ManifestEntry> entries)
    {
        Set<String> codes = new HashSet<>();
        Set<String> problems = new LinkedHashSet<>();
        for (ManifestEntry entry : entries) {
            if (!codes.add(entry.code())) {
                problems.add(entry.code() + " is declared twice");
            }
        }
        for (ManifestEntry entry : entries) {
            for (String code : entry.requires()) {
                if (!codes.contains(code)) {
                    problems.add(entry.code() + " requires the undeclared resource " + code);
                }
            }
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException("invalid console manifest: " + String.join("; ", problems));
        }
    }

    /** What one synchronization changed. */
    private static final class Tally
    {
        private int created;
        private int updated;
        private int added;
        private int removed;
    }
}
