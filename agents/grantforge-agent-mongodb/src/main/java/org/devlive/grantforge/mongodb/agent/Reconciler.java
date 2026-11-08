// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.mongodb.agent;

import org.bson.Document;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Brings the agent's roles in step with the plans: each user with something to hold has one role, granted to every
 * MongoDB user of its name, and no other. {@code updateRole} replaces a role's privileges in one step, so a user never
 * holds both the old and the new, or neither; what was changed by hand, a role's privileges, roles it was made to inherit,
 * or a grant of it to someone else, is set right the same way. A user whose change fails is tried again at the next sync,
 * and does not hold back the others. A role nobody needs is dropped, which takes it from whoever holds it.
 */
// A Document is what the driver takes and answers with, not a choice among maps.
@SuppressWarnings("PMD.LooseCoupling")
final class Reconciler
{
    private static final Logger LOG = Logger.getLogger(Reconciler.class.getName());

    private final MongoDb mongo;
    private final String prefix;

    /**
     * What one sync changed.
     *
     * @param users the users planned
     * @param created the roles created
     * @param updated the roles whose privileges were replaced
     * @param dropped the roles dropped
     * @param grants the grants of roles made or taken back
     * @param failed the users whose changes failed
     */
    record Outcome(int users, int created, int updated, int dropped, int grants, int failed)
    {
        boolean changed()
        {
            return created + updated + dropped + grants + failed > 0;
        }
    }

    Reconciler(MongoDb mongo, String prefix)
    {
        this.mongo = mongo;
        this.prefix = prefix;
    }

    /**
     * Returns the role the agent keeps for a user: the prefix and the user name, when it holds letters, digits, underscores
     * and hyphens only and stays within 64 characters; otherwise the name's other characters become underscores and a hash
     * of the name keeps roles of different users apart, as for the subject of a client certificate.
     *
     * @param user the user name
     * @return the role's name
     */
    String role(String user)
    {
        String plain = prefix + user;
        if (plain.length() <= 64 && plain.matches("[A-Za-z][A-Za-z0-9_-]*")) {
            return plain;
        }
        String hash = String.format(Locale.ROOT, "%08x", user.hashCode());
        String cleaned = user.replaceAll("[^A-Za-z0-9_-]", "_");
        int room = 64 - prefix.length() - hash.length() - 1;
        return prefix + (cleaned.length() > room ? cleaned.substring(0, room) : cleaned) + "_" + hash;
    }

    /**
     * Brings the roles in step.
     *
     * @param state what the deployment holds
     * @param plans what each user is planned to hold, or {@code null} for nothing at all
     * @return what changed
     * @throws MongoFailure when the deployment cannot be reached
     */
    // A command per role nobody needs.
    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    Outcome reconcile(ServerState state, @Nullable Function<Account, Plan> plans)
    {
        Counts counts = new Counts();
        Set<String> kept = new HashSet<>();
        for (Account account : state.accounts()) {
            Plan plan = plans == null ? Plan.NOTHING : plans.apply(account);
            String role = role(account.name());
            if (!plan.empty()) {
                kept.add(role);
            }
            try {
                apply(account, role, plan, state.managed().get(role), counts);
            }
            catch (MongoFailure failed) {
                failed(failed, "user " + account.name(), counts);
                kept.add(role);
            }
        }
        // Roles of users left alone are the agent's too: taking them keeps such a user from holding what it held before.
        for (String role : state.managed().keySet()) {
            if (!kept.contains(role)) {
                try {
                    mongo.command(ServerState.ROLE_DATABASE, new Document("dropRole", role));
                    counts.dropped++;
                }
                catch (MongoFailure failed) {
                    failed(failed, "role " + role, counts);
                }
            }
        }
        return new Outcome(state.accounts().size(), counts.created, counts.updated, counts.dropped, counts.grants, counts.failed);
    }

    private static void failed(MongoFailure failed, String what, Counts counts)
    {
        if (failed.unreachable()) {
            throw failed;
        }
        LOG.log(Level.WARNING, "The GrantForge agent could not bring " + what + " in step: " + failed.getMessage());
        counts.failed++;
    }

    private void apply(Account account, String role, Plan plan, ServerState.@Nullable ManagedRole existing, Counts counts)
    {
        if (!plan.empty()) {
            if (existing == null) {
                mongo.command(ServerState.ROLE_DATABASE, new Document("createRole", role).append("privileges", plan.documents()).append("roles", List.of()));
                counts.created++;
            }
            else if (existing.inherited() || !existing.comparable() || !existing.privileges().equals(plan)) {
                mongo.command(ServerState.ROLE_DATABASE, new Document("updateRole", role).append("privileges", plan.documents()).append("roles", List.of()));
                counts.updated++;
            }
        }
        for (Account.Identity identity : account.identities()) {
            grants(identity, plan.empty() ? null : role, counts);
        }
    }

    /** Grants a MongoDB user the role it should hold and takes back any other role of the agent it holds. */
    private void grants(Account.Identity identity, @Nullable String role, Counts counts)
    {
        List<Document> taken = new ArrayList<>();
        boolean holds = false;
        for (Account.RoleRef held : identity.roles()) {
            if (ServerState.ROLE_DATABASE.equals(held.database()) && held.role().startsWith(prefix)) {
                if (held.role().equals(role)) {
                    holds = true;
                }
                else {
                    taken.add(reference(held.role()));
                }
            }
        }
        if (!taken.isEmpty()) {
            mongo.command(identity.database(), new Document("revokeRolesFromUser", identity.user()).append("roles", taken));
            counts.grants += taken.size();
        }
        if (role != null && !holds) {
            mongo.command(identity.database(), new Document("grantRolesToUser", identity.user()).append("roles", List.of(reference(role))));
            counts.grants++;
        }
    }

    private static Document reference(String role)
    {
        return new Document("role", role).append("db", ServerState.ROLE_DATABASE);
    }

    /** What one sync has changed so far. */
    private static final class Counts
    {
        private int created;
        private int updated;
        private int dropped;
        private int grants;
        private int failed;
    }
}
