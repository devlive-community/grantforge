// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.zookeeper.agent;

import org.devlive.grantforge.agent.AgentDecision;
import org.devlive.grantforge.policy.engine.AccessRequest;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.util.Objects.requireNonNull;

/**
 * Decides whether the ACL entry {@code sasl:grantforge} matches a signed-in principal. Policies use the server's
 * absolute operation path, including the target node for create/delete even though the ACL belongs to its parent.
 * Sequential create uses the requested prefix before ZooKeeper adds its sequence suffix.
 * Eager ACL checks must be disabled: that preliminary check supplies a different path for these operations.
 * Composite permission checks use OR, just like native ZooKeeper ACLs (notably READ or ADMIN for getACL).
 *
 * <p>This callback cannot observe the final request result: another ACL entry can allow access, and getACL may probe
 * ADMIN only to decide whether to redact credentials. It therefore does not emit final access audit events.
 */
public final class ZooKeeperAuthorizer
{
    /** Permission bits from {@code ZooDefs.Perms}. */
    static final int READ = 1;
    static final int WRITE = 1 << 1;
    static final int CREATE = 1 << 2;
    static final int DELETE = 1 << 3;
    static final int ADMIN = 1 << 4;

    private static final Logger LOG = Logger.getLogger(ZooKeeperAuthorizer.class.getName());
    private static final int ALL = READ | WRITE | CREATE | DELETE | ADMIN;
    private static final int[] BITS = {READ, WRITE, CREATE, DELETE, ADMIN};
    private static final String[] ACCESS = {"read", "write", "create", "delete", "admin"};

    private final Supplier<Function<AccessRequest, AgentDecision>> sessions;
    private final boolean shortNames;

    /**
     * Creates an authorizer.
     *
     * @param sessions the decision function of one captured policy snapshot per ACL check
     * @param shortNames whether to map {@code kafka/host@REALM} to {@code kafka}, rather than the full principal
     */
    public ZooKeeperAuthorizer(Supplier<Function<AccessRequest, AgentDecision>> sessions, boolean shortNames)
    {
        this.sessions = requireNonNull(sessions, "sessions");
        this.shortNames = shortNames;
    }

    /**
     * Checks an ACL marker, without inferring the eventual ZooKeeper request result.
     *
     * @param principal the client's authenticated SASL identity
     * @param path the absolute server operation path (not the client's chroot-relative path)
     * @param perm the requested permission mask
     * @param clientIp the client address supplied to policy conditions
     * @return whether a policy allows any of the requested permissions; false on missing or broken policy state
     */
    public boolean authorize(String principal, String path, int perm, @Nullable String clientIp)
    {
        List<String> wanted = accessTypes(perm);
        if (wanted.isEmpty()) {
            return false;
        }
        try {
            Function<AccessRequest, AgentDecision> decide = requireNonNull(sessions.get(), "policy session");
            String user = user(principal);
            for (String access : wanted) {
                AccessRequest.Builder request = AccessRequest.builder(user, access).resource("path", path);
                if (clientIp != null) {
                    request.context(Collections.singletonMap("clientAddress", clientIp));
                }
                AgentDecision decision = requireNonNull(decide.apply(request.build()), "policy decision");
                if (decision.allowed()) {
                    return true;
                }
            }
        }
        catch (RuntimeException broken) {
            LOG.log(Level.WARNING, "GrantForge cannot match the ZooKeeper ACL marker; the marker refuses", broken);
        }
        return false;
    }

    /**
     * Maps a principal to the service's user name.
     *
     * @param principal the SASL identity
     * @return its first component with short names, otherwise the entire principal
     */
    String user(String principal)
    {
        if (!shortNames) {
            return principal;
        }
        int end = principal.length();
        int slash = principal.indexOf('/');
        int at = principal.indexOf('@');
        if (slash > 0) {
            end = slash;
        }
        if (at > 0 && at < end) {
            end = at;
        }
        return principal.substring(0, end);
    }

    /**
     * Maps a valid permission mask to ordered access types.
     *
     * @param perm the mask
     * @return access types; empty for zero or unknown permission bits
     */
    static List<String> accessTypes(int perm)
    {
        List<String> types = new ArrayList<>();
        if (perm <= 0 || (perm & ~ALL) != 0) {
            return types;
        }
        for (int index = 0; index < BITS.length; index++) {
            if ((perm & BITS[index]) != 0) {
                types.add(ACCESS[index]);
            }
        }
        return types;
    }
}
