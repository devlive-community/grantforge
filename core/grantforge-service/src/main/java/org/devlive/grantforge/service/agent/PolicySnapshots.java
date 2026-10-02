// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service.agent;

import org.devlive.grantforge.common.error.CommonErrorCode;
import org.devlive.grantforge.common.error.GrantForgeException;
import org.devlive.grantforge.plugin.api.model.DataMaskDefinition;
import org.devlive.grantforge.plugin.api.model.ServiceTypeDefinition;
import org.devlive.grantforge.plugin.host.PluginRegistry;
import org.devlive.grantforge.service.ServiceErrorCode;
import org.devlive.grantforge.service.domain.ManagedService;
import org.devlive.grantforge.service.domain.ManagedServiceRepository;
import org.devlive.grantforge.service.domain.ServicePolicy;
import org.devlive.grantforge.service.domain.ServicePolicyRepository;
import org.devlive.grantforge.service.policy.PolicyDocument;
import org.devlive.grantforge.service.policy.PolicyItemSpec;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

/**
 * Builds the signed policy snapshots agents download: the service type's matching rules, the enabled policies and the
 * members of the roles and groups they name, as JSON with sorted keys, so equal content gives equal bytes and equal
 * ETags. Must be called with the service's tenant bound.
 */
@Service
public final class PolicySnapshots
{
    private static final JsonMapper JSON = JsonMapper.builder().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).build();

    private final ManagedServiceRepository services;
    private final ServicePolicyRepository policies;
    private final PluginRegistry plugins;
    private final SnapshotSubjects subjects;
    private final SnapshotSigner signer;
    private final TransactionTemplate transactions;
    private final Clock clock;

    /**
     * Creates the builder.
     *
     * @param services the services of the bound tenant
     * @param policies their policies
     * @param plugins the service types
     * @param subjects the members of roles and groups
     * @param signer signs snapshots
     * @param transactionManager opens transactions
     * @param clock the current time, for the validity of role assignments
     */
    public PolicySnapshots(ManagedServiceRepository services, ServicePolicyRepository policies, PluginRegistry plugins,
            SnapshotSubjects subjects, SnapshotSigner signer, PlatformTransactionManager transactionManager, Clock clock)
    {
        this.services = requireNonNull(services, "services");
        this.policies = requireNonNull(policies, "policies");
        this.plugins = requireNonNull(plugins, "plugins");
        this.subjects = requireNonNull(subjects, "subjects");
        this.signer = requireNonNull(signer, "signer");
        this.transactions = new TransactionTemplate(requireNonNull(transactionManager, "transactionManager"));
        this.clock = requireNonNull(clock, "clock");
        transactions.setReadOnly(true);
    }

    /**
     * Builds the snapshot of a service.
     *
     * @param serviceId the service
     * @return the snapshot
     * @throws GrantForgeException with {@link CommonErrorCode#NOT_FOUND}, or {@link ServiceErrorCode#TYPE_UNAVAILABLE} while
     *         no active plugin provides the service's type; agents then keep the snapshot they have
     */
    public PolicySnapshot build(long serviceId)
    {
        return requireNonNull(transactions.execute(status -> {
            ManagedService service = services.findById(serviceId)
                    .orElseThrow(() -> new GrantForgeException(CommonErrorCode.NOT_FOUND, "no service " + serviceId));
            ServiceTypeDefinition type = plugins.serviceType(service.getServiceType()).orElseThrow(() -> new GrantForgeException(
                    ServiceErrorCode.TYPE_UNAVAILABLE, "no active plugin provides " + service.getServiceType(), service.getServiceType()));
            List<PolicySnapshot.SnapshotPolicy> enabled = policies.findByServiceIdAndEnabledTrueOrderByIdAsc(serviceId).stream()
                    .map(PolicySnapshots::policy).toList();
            Set<String> roles = new TreeSet<>();
            Set<String> groups = new TreeSet<>();
            enabled.forEach(policy -> items(policy.document()).forEach(item -> {
                roles.addAll(item.roles());
                item.groups().stream().filter(group -> !PolicyItemSpec.PUBLIC.equals(group)).forEach(groups::add);
            }));
            return new PolicySnapshot(PolicySnapshot.FORMAT, service.getName(), type.name(), type.version(), service.isEnabled(),
                    service.getPolicyVersion(), definition(type), enabled,
                    roles.isEmpty() ? Map.of() : new TreeMap<>(subjects.roleHolders(roles, clock.instant())),
                    groups.isEmpty() ? Map.of() : new TreeMap<>(subjects.groupMembers(groups)));
        }));
    }

    /**
     * Builds and signs the snapshot of a service.
     *
     * @param serviceId the service
     * @return the snapshot as sent to agents
     * @throws GrantForgeException as {@link #build}
     */
    public SignedSnapshot signed(long serviceId)
    {
        PolicySnapshot snapshot = build(serviceId);
        byte[] body = encode(snapshot);
        return new SignedSnapshot(body, etag(body), snapshot.policyVersion(), signer.publicKey().keyId(), signer.sign(body));
    }

    /**
     * Writes a snapshot as JSON with sorted keys.
     *
     * @param snapshot the snapshot
     * @return the JSON
     */
    static byte[] encode(PolicySnapshot snapshot)
    {
        try {
            return JSON.writeValueAsBytes(snapshot);
        }
        catch (JacksonException impossible) {
            throw new IllegalStateException("a snapshot could not be written", impossible);
        }
    }

    /**
     * Reads a snapshot, as agents and tests do.
     *
     * @param body the JSON
     * @return the snapshot
     */
    static PolicySnapshot decode(byte[] body)
    {
        return JSON.readValue(body, PolicySnapshot.class);
    }

    private static String etag(byte[] body)
    {
        try {
            return "\"" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body)).substring(0, 32) + "\"";
        }
        catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is not available", impossible);
        }
    }

    private static PolicySnapshot.SnapshotPolicy policy(ServicePolicy policy)
    {
        PolicyDocument document;
        try {
            document = JSON.readValue(policy.getBody(), PolicyDocument.class);
        }
        catch (JacksonException broken) {
            throw new IllegalStateException("stored policy " + policy.requireId() + " is not valid JSON", broken);
        }
        return new PolicySnapshot.SnapshotPolicy(Long.toString(policy.requireId()), policy.getName(), policy.getPolicyType(), policy.getPriority(),
                document);
    }

    private static Stream<PolicyItemSpec> items(PolicyDocument document)
    {
        return Stream.of(document.allow(), document.allowExceptions(), document.deny(), document.denyExceptions()).flatMap(List::stream);
    }

    private static PolicySnapshot.Definition definition(ServiceTypeDefinition type)
    {
        return new PolicySnapshot.Definition(
                type.resources().stream().map(level -> new PolicySnapshot.Level(level.name(), level.parent(), level.matcher(),
                        level.caseSensitive())).toList(),
                type.accessTypes().stream().map(access -> new PolicySnapshot.Access(access.name(), access.impliedGrants().stream()
                        .sorted().toList())).toList(),
                type.conditions().stream().map(condition -> new PolicySnapshot.Condition(condition.name(), condition.evaluator(),
                        condition.options())).toList(),
                Optional.ofNullable(type.dataMask()).map(DataMaskDefinition::maskTypes).orElse(List.of()).stream()
                        .map(mask -> new PolicySnapshot.Mask(mask.name(), mask.transformer())).toList());
    }
}
