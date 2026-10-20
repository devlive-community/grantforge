// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.yarn.agent;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.hadoop.security.authorize.AccessControlList;
import org.apache.hadoop.yarn.exceptions.YarnRuntimeException;
import org.apache.hadoop.yarn.security.AccessRequest;
import org.apache.hadoop.yarn.security.AccessType;
import org.apache.hadoop.yarn.security.Permission;
import org.apache.hadoop.yarn.security.PrivilegedEntity;
import org.apache.hadoop.yarn.security.YarnAuthorizationProvider;
import org.devlive.grantforge.agent.AccessEvent;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/**
 * YARN's authorization provider in GrantForge's terms, set with {@code yarn.authorization-provider} and enabled with
 * {@code yarn.acl.enable}. Submitting an application to a queue needs {@code submit_app} on the queue, or on the queue
 * with the application's name; administering a queue, which killing another user's application or changing its priority
 * is, needs {@code admin_queue}; both are decided at every queue of the queue's path, from the root. YARN's
 * administration, which {@code yarn.admin.acl} grants otherwise, needs {@code admin} on the cluster. Every check goes
 * with the client's address, for the policy items that hold for some addresses only, and is reported. The
 * ResourceManager's own user is not checked, and the queue ACLs of the scheduler's configuration are not used.
 */
@SuppressWarnings("PMD.AvoidUsingVolatile")
public class GrantForgeYarnAuthorizer
        extends YarnAuthorizationProvider
{
    static final String SUBMIT_APP = "submit_app";
    static final String ADMIN_QUEUE = "admin_queue";
    static final String ADMIN = "admin";

    private static final Logger LOG = LoggerFactory.getLogger(GrantForgeYarnAuthorizer.class);
    private static final String VERSION_FILE = "/META-INF/grantforge/yarn-agent-version.properties";

    private final ReentrantLock lifecycle = new ReentrantLock();
    private final AtomicBoolean warnedAcls = new AtomicBoolean();
    private volatile @Nullable YarnAgentRuntime runtime;
    private volatile String daemonUser = "";

    /** Creates the provider YARN loads. */
    public GrantForgeYarnAuthorizer()
    {
        // Started by init.
    }

    GrantForgeYarnAuthorizer(YarnAgentRuntime runtime, String daemonUser)
    {
        this.runtime = runtime;
        this.daemonUser = daemonUser;
    }

    /**
     * Starts the agent from the ResourceManager's {@code grantforge.*} settings; it stops with the ResourceManager's JVM,
     * sending the events it folded.
     */
    // The runtime lives as long as the ResourceManager, which stops it through the shutdown hook; YARN has no instance hook
    // for it, its destroy being static.
    @Override
    @SuppressWarnings({"PMD.CloseResource", "PMD.DoNotUseThreads"})
    public void init(Configuration conf)
    {
        lifecycle.lock();
        try {
            if (runtime != null) {
                return;
            }
            Map<String, String> settings = new HashMap<>();
            for (Map.Entry<String, String> entry : conf) {
                if (entry.getKey().startsWith(YarnAgentRuntime.PREFIX)) {
                    settings.put(entry.getKey(), entry.getValue());
                }
            }
            YarnAgentRuntime started = YarnAgentRuntime.start(settings, version());
            daemonUser = UserGroupInformation.getLoginUser().getShortUserName();
            runtime = started;
            Runtime.getRuntime().addShutdownHook(new Thread(started::close, "grantforge-yarn-agent-stop"));
            LOG.info("GrantForge decides YARN's queue and administration access; {} is not checked", daemonUser);
        }
        catch (IOException | IllegalArgumentException broken) {
            throw new YarnRuntimeException("GrantForge cannot start the YARN agent: " + broken.getMessage(), broken);
        }
        finally {
            lifecycle.unlock();
        }
    }

    /**
     * Returns the agent's version, as the build wrote it.
     *
     * @return such as {@code 2026.2.0}
     * @throws IOException if the jar has no version
     */
    static String version() throws IOException
    {
        try (InputStream in = GrantForgeYarnAuthorizer.class.getResourceAsStream(VERSION_FILE)) {
            return version(in);
        }
    }

    /**
     * Reads the agent's version from the build's properties.
     *
     * @param in the properties, or {@code null} when the jar has none
     * @return the version
     * @throws IOException if there is none
     */
    static String version(@Nullable InputStream in) throws IOException
    {
        if (in == null) {
            throw new IOException("the agent jar has no " + VERSION_FILE);
        }
        Properties properties = new Properties();
        properties.load(in);
        String version = properties.getProperty("grantforge.version");
        String value = version == null ? "" : version.trim();
        if (value.isEmpty()) {
            throw new IOException(VERSION_FILE + " names no grantforge.version");
        }
        return value;
    }

    // The runtime is borrowed; init's shutdown hook closes it.
    @Override
    @SuppressWarnings("PMD.CloseResource")
    public boolean checkPermission(AccessRequest request)
    {
        UserGroupInformation user = request.getUser();
        if (user == null) {
            return false;
        }
        String name = user.getShortUserName();
        YarnAgentRuntime running = runtime;
        if (name.equals(daemonUser)) {
            return true;
        }
        if (running == null) {
            return false;
        }
        String accessType = accessType(request.getAccessType());
        PrivilegedEntity entity = request.getEntity();
        String queue = entity == null ? null : entity.getName();
        if (accessType == null || queue == null || queue.isEmpty()) {
            LOG.warn("GrantForge refuses {} {} on {}: it decides submitting to and administering queues", name, request.getAccessType(),
                    queue);
            return false;
        }
        Policies policies = running.policies();
        if (!policies.enforced()) {
            return true;
        }
        String application = SUBMIT_APP.equals(accessType) ? text(request.getAppName()) : null;
        Caller caller = new Caller(name, groups(user), Caller.client(request.getRemoteAddress(), request.getForwardedAddresses()));
        YarnResource resource = YarnResource.queue(queue, application);
        Policies.Verdict verdict = policies.decide(caller, resource, accessType);
        String what = text(request.getAppId());
        report(running, caller, resource, verdict, SUBMIT_APP.equals(accessType) ? "submit application" : "administer queue",
                what == null ? null : "application " + what + (application == null ? "" : " " + application));
        return verdict.allowed();
    }

    // The runtime is borrowed; init's shutdown hook closes it.
    @Override
    @SuppressWarnings("PMD.CloseResource")
    public boolean isAdmin(UserGroupInformation ugi)
    {
        String name = ugi.getShortUserName();
        YarnAgentRuntime running = runtime;
        if (name.equals(daemonUser)) {
            return true;
        }
        if (running == null) {
            return false;
        }
        Policies policies = running.policies();
        if (!policies.enforced()) {
            return true;
        }
        Caller caller = new Caller(name, groups(ugi), null);
        YarnResource resource = YarnResource.cluster();
        Policies.Verdict verdict = policies.decide(caller, resource, ADMIN);
        report(running, caller, resource, verdict, "administer cluster", null);
        return verdict.allowed();
    }

    /** The queue ACLs of the scheduler's configuration: GrantForge's policies take their place. */
    @Override
    public void setPermission(List<Permission> permissions, UserGroupInformation user)
    {
        if (!permissions.isEmpty() && warnedAcls.compareAndSet(false, true)) {
            LOG.info("GrantForge policies decide queue access; the {} queue ACLs of the scheduler's configuration are not used",
                    permissions.size());
        }
    }

    /** {@code yarn.admin.acl}: GrantForge's policies on the cluster take its place. */
    @Override
    public void setAdmins(AccessControlList acls, UserGroupInformation user)
    {
        // Administration is decided by the cluster level's policies.
    }

    private static @Nullable String accessType(@Nullable AccessType type)
    {
        if (type == null) {
            return null;
        }
        switch (type.name()) {
            case "SUBMIT_APP":
                return SUBMIT_APP;
            case "ADMINISTER_QUEUE":
                return ADMIN_QUEUE;
            default:
                return null;
        }
    }

    private static List<String> groups(UserGroupInformation user)
    {
        try {
            return Arrays.asList(user.getGroupNames());
        }
        catch (RuntimeException unknown) {
            return Collections.emptyList();
        }
    }

    private static @Nullable String text(@Nullable String value)
    {
        String text = value == null ? "" : value.trim();
        return text.isEmpty() ? null : text;
    }

    /** Reports a check; YARN words its own refusal, so the log says which policy refused it. */
    private static void report(YarnAgentRuntime running, Caller caller, YarnResource resource, Policies.Verdict verdict, String action,
            @Nullable String request)
    {
        if (!verdict.allowed()) {
            LOG.info("GrantForge refuses {} to {} {}: {}", caller.user(), action, resource.display(), why(verdict));
        }
        running.audit(AccessEvent.builder(caller.user(), resource.display(), verdict.accessType(), verdict.allowed()).decidedBy(verdict.decision())
                .enforcedByGrantForge().clientIp(caller.clientIp()).resourceType(resource.level()).action(action).request(request).build());
    }

    /**
     * Returns why the policies refuse something, for YARN's own message to go with it.
     *
     * @param verdict the verdict
     * @return such as {@code GrantForge policy 7 denies it}
     */
    static String why(Policies.Verdict verdict)
    {
        Long policy = verdict.decision().policyId();
        return verdict.denied() && policy != null ? "GrantForge policy " + policy + " denies it" : "no GrantForge policy allows it";
    }

    @Override
    public String toString()
    {
        return "GrantForge";
    }
}
