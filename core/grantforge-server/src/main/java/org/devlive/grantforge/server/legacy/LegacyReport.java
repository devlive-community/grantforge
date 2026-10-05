// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.legacy;

import org.jspecify.annotations.Nullable;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

/**
 * What an import of the old database did, or would do in a dry run: per kind of object how many were created and how
 * many existed already, every object left out or changed on the way and why, the API patterns that came from wildcard
 * URLs and need a look, and which new ID each old one got.
 */
public final class LegacyReport
{
    /** The kinds of objects, in the order the import handles them. */
    public enum Kind
    {
        /** Accounts. */
        USERS,
        /** Roles. */
        ROLES,
        /** Menus, pages and buttons, as resources. */
        MENUS,
        /** URLs with their HTTP methods, as API resources. */
        APIS,
        /** Roles' grants of menus and APIs. */
        GRANTS,
        /** Accounts' roles. */
        ASSIGNMENTS
    }

    /** The outcome of an object left out. */
    public static final String SKIPPED = "skipped";

    private static final JsonMapper JSON = JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();

    private final boolean applied;
    private final String source;
    private final String tenant;
    private final String application;
    private final Map<Kind, int[]> counts = new LinkedHashMap<>();
    private final List<Issue> issues = new ArrayList<>();
    private final List<Wildcard> wildcards = new ArrayList<>();
    private final Map<Kind, Map<Long, Long>> mapping = new LinkedHashMap<>();

    /**
     * Starts a report.
     *
     * @param applied whether the import writes, rather than a dry run
     * @param source the old database, without credentials
     * @param tenant the tenant the import fills
     * @param application the catalog application the menus go to
     */
    // One counter per kind.
    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    public LegacyReport(boolean applied, String source, String tenant, String application)
    {
        this.applied = applied;
        this.source = requireNonNull(source, "source");
        this.tenant = requireNonNull(tenant, "tenant");
        this.application = requireNonNull(application, "application");
        for (Kind kind : Kind.values()) {
            counts.put(kind, new int[2]);
        }
    }

    /**
     * Counts an object created.
     *
     * @param kind its kind
     */
    void created(Kind kind)
    {
        requireNonNull(counts.get(kind))[0]++;
    }

    /**
     * Counts an object that existed already and was kept as it was.
     *
     * @param kind its kind
     */
    void existing(Kind kind)
    {
        requireNonNull(counts.get(kind))[1]++;
    }

    /**
     * Notes an object left out.
     *
     * @param kind its kind
     * @param legacyId its ID in the old database, or {@code null} for a relation
     * @param label what it is, such as its name
     * @param reason why
     */
    void skipped(Kind kind, @Nullable Long legacyId, @Nullable String label, String reason)
    {
        issues.add(new Issue(kind, legacyId, label, SKIPPED, reason));
    }

    /**
     * Notes something changed on the way, such as an address that was dropped.
     *
     * @param kind its kind
     * @param legacyId its ID in the old database
     * @param label what it is
     * @param note what changed
     */
    void noted(Kind kind, long legacyId, @Nullable String label, String note)
    {
        issues.add(new Issue(kind, legacyId, label, "note", note));
    }

    /**
     * Notes an API pattern made from a wildcard URL: the old server matched by prefix, the new pattern by segments.
     *
     * @param menuId the menu
     * @param url the old URL
     * @param pattern the new pattern
     */
    void wildcard(long menuId, String url, String pattern)
    {
        wildcards.add(new Wildcard(menuId, url, pattern));
    }

    /**
     * Records the new ID of an old object.
     *
     * @param kind its kind
     * @param legacyId its ID in the old database
     * @param newId its ID now
     */
    void mapped(Kind kind, long legacyId, long newId)
    {
        mapping.computeIfAbsent(kind, key -> new TreeMap<>()).put(legacyId, newId);
    }

    /**
     * Returns how many objects of a kind were created.
     *
     * @param kind the kind
     * @return the count
     */
    public int createdCount(Kind kind)
    {
        return requireNonNull(counts.get(kind))[0];
    }

    /**
     * Returns how many objects of a kind existed already.
     *
     * @param kind the kind
     * @return the count
     */
    public int existingCount(Kind kind)
    {
        return requireNonNull(counts.get(kind))[1];
    }

    /**
     * Returns how many objects of all kinds were created.
     *
     * @return the count
     */
    public int createdCount()
    {
        return counts.values().stream().mapToInt(count -> count[0]).sum();
    }

    /**
     * Returns how many objects and relations were left out.
     *
     * @return the count
     */
    public long skippedCount()
    {
        return issues.stream().filter(issue -> SKIPPED.equals(issue.outcome())).count();
    }

    /**
     * Returns everything left out or changed.
     *
     * @return the issues, in the order they were found
     */
    public List<Issue> issues()
    {
        return List.copyOf(issues);
    }

    /**
     * Returns the patterns made from wildcard URLs.
     *
     * @return the patterns, by menu
     */
    public List<Wildcard> wildcards()
    {
        return List.copyOf(wildcards);
    }

    /**
     * Returns the new ID of an old object.
     *
     * @param kind its kind
     * @param legacyId its ID in the old database
     * @return the new ID, or {@code null} if it was left out
     */
    public @Nullable Long newId(Kind kind, long legacyId)
    {
        Map<Long, Long> ids = mapping.get(kind);
        return ids == null ? null : ids.get(legacyId);
    }

    /**
     * Sums the report up in one line per kind.
     *
     * @return the summary
     */
    public String summary()
    {
        String counted = counts.entrySet().stream().map(entry -> String.format(Locale.ROOT, "%s: %d created, %d existing", entry.getKey(),
                entry.getValue()[0], entry.getValue()[1])).collect(Collectors.joining(System.lineSeparator()));
        return (applied ? "Imported" : "Dry run, nothing written") + System.lineSeparator() + counted + System.lineSeparator()
                + String.format(Locale.ROOT, "%d left out, %d wildcard URLs to check", skippedCount(), wildcards.size());
    }

    /**
     * Writes the report as JSON.
     *
     * @return the document
     */
    public String json()
    {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("mode", applied ? "applied" : "dry-run");
        document.put("source", source);
        document.put("tenant", tenant);
        document.put("application", application);
        Map<String, Object> totals = new LinkedHashMap<>();
        counts.forEach((kind, count) -> {
            Map<String, Integer> total = new LinkedHashMap<>();
            total.put("created", count[0]);
            total.put("existing", count[1]);
            totals.put(name(kind), total);
        });
        document.put("counts", totals);
        document.put("issues", issues.stream().map(issue -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("kind", name(issue.kind()));
            row.put("legacyId", issue.legacyId());
            row.put("label", issue.label());
            row.put("outcome", issue.outcome());
            row.put("reason", issue.reason());
            return row;
        }).toList());
        document.put("wildcards", wildcards);
        Map<String, Object> ids = new LinkedHashMap<>();
        // IDs as strings: they exceed what JavaScript numbers hold exactly.
        mapping.forEach((kind, found) -> ids.put(name(kind), found.entrySet().stream()
                .collect(Collectors.toMap(entry -> Long.toString(entry.getKey()), entry -> Long.toString(entry.getValue()), (first, second) -> first,
                        LinkedHashMap::new))));
        document.put("mapping", ids);
        return JSON.writeValueAsString(document);
    }

    private static String name(Kind kind)
    {
        return kind.name().toLowerCase(Locale.ROOT);
    }

    /**
     * Something left out or changed.
     *
     * @param kind the kind of object
     * @param legacyId its ID in the old database, or {@code null} for a relation
     * @param label what it is
     * @param outcome {@code skipped} or {@code note}
     * @param reason why, or what changed
     */
    public record Issue(Kind kind, @Nullable Long legacyId, @Nullable String label, String outcome, String reason)
    {
    }

    /**
     * An API pattern made from a wildcard URL.
     *
     * @param menuId the menu
     * @param url the old URL
     * @param pattern the new pattern
     */
    public record Wildcard(long menuId, String url, String pattern)
    {
    }
}
