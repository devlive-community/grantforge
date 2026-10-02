// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service;

import org.devlive.grantforge.common.error.FieldIssue;
import org.devlive.grantforge.plugin.api.model.ConfigField;
import org.devlive.grantforge.plugin.api.model.ConfigFieldType;
import org.devlive.grantforge.plugin.api.model.ConfigProblem;
import org.devlive.grantforge.plugin.api.model.ServiceTypeDefinition;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;

/** Checks a configuration against a service type's fields and works out what to store and what plugins see. */
final class ServiceConfigs
{
    private ServiceConfigs()
    {
    }

    /**
     * A configuration worked out.
     *
     * @param plain the values that are not secret, as given, to store
     * @param secrets the secrets, as they are to be stored (sealed)
     * @param effective what plugins see: plain values with defaults applied and secrets opened
     * @param issues what is wrong, by field
     */
    record Resolved(Map<String, String> plain, Map<String, String> secrets, Map<String, String> effective, List<FieldIssue> issues)
    {
    }

    /**
     * Works out a configuration.
     *
     * @param definition the service type
     * @param given the values given; a blank secret keeps the stored one
     * @param stored the sealed secrets stored so far
     * @param seal seals a new secret
     * @param open opens a stored secret
     * @return the configuration, with its issues
     */
    // A setting without value, given or stored, is null until a default fills it in.
    @SuppressWarnings("PMD.NullAssignment")
    static Resolved resolve(ServiceTypeDefinition definition, Map<String, String> given, Map<String, String> stored,
            UnaryOperator<String> seal, UnaryOperator<String> open)
    {
        Map<String, String> plain = new LinkedHashMap<>();
        Map<String, String> secrets = new LinkedHashMap<>();
        Map<String, String> effective = new LinkedHashMap<>();
        List<FieldIssue> issues = new ArrayList<>();
        for (String name : given.keySet()) {
            if (definition.configFields().stream().noneMatch(field -> field.name().equals(name))) {
                issues.add(FieldIssue.of(name, "error.service.config.unknown-field"));
            }
        }
        for (ConfigField field : definition.configFields()) {
            String value = blankToNull(given.get(field.name()));
            String current;
            if (field.type() == ConfigFieldType.SECRET) {
                String kept = stored.get(field.name());
                if (value != null) {
                    secrets.put(field.name(), seal.apply(value));
                }
                else if (kept != null) {
                    secrets.put(field.name(), kept);
                }
                current = value != null ? value : kept == null ? null : open.apply(kept);
            }
            else {
                if (value != null) {
                    plain.put(field.name(), value);
                }
                current = value != null ? value : field.defaultValue();
            }
            if (current == null) {
                if (field.mandatory()) {
                    issues.add(FieldIssue.of(field.name(), "error.service.config.required"));
                }
                continue;
            }
            Optional<ConfigProblem.Reason> problem = field.check(current);
            if (problem.isPresent()) {
                issues.add(FieldIssue.of(field.name(), messageKey(problem.get())));
            }
            effective.put(field.name(), current);
        }
        return new Resolved(plain, secrets, effective, issues);
    }

    /**
     * Turns a plugin's own problems with a configuration into field issues.
     *
     * @param problems what the plugin found
     * @return the issues
     */
    static List<FieldIssue> issues(List<ConfigProblem> problems)
    {
        List<FieldIssue> issues = new ArrayList<>();
        for (ConfigProblem problem : problems) {
            String detail = problem.detail();
            issues.add(problem.reason() == ConfigProblem.Reason.INVALID
                    ? FieldIssue.of(problem.field(), "error.service.config.invalid", detail == null ? "" : detail)
                    : FieldIssue.of(problem.field(), messageKey(problem.reason())));
        }
        return issues;
    }

    static String messageKey(ConfigProblem.Reason reason)
    {
        return "error.service.config." + reason.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private static @Nullable String blankToNull(@Nullable String value)
    {
        return value == null || value.isBlank() ? null : value;
    }
}
