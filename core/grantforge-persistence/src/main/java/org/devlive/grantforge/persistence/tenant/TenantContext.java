// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.persistence.tenant;

import org.jspecify.annotations.Nullable;

import java.util.OptionalLong;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

/**
 * The tenant the current thread works for.
 *
 * <p>A binding exists only for the duration of a callback ({@link #callInTenant}, {@link #callAsSystem}) and
 * the previous binding is always restored afterwards, even when the callback throws or bindings are
 * nested. There is deliberately no public set/clear pair, which could leak a tenant to the next request
 * served by a pooled thread.
 *
 * <p>Three states exist:
 * <ul>
 *   <li><b>tenant</b> - tenant-scoped entities are filtered to that tenant and new rows belong to it;</li>
 *   <li><b>system</b> - platform code working across tenants; filtering is disabled and must be used only
 *       for explicit cross-tenant operations such as tenant provisioning;</li>
 *   <li><b>unbound</b> - fail closed: tenant-scoped queries match nothing and inserts are rejected.</li>
 * </ul>
 */
public final class TenantContext
{
    private static final ThreadLocal<@Nullable Binding> CURRENT = new ThreadLocal<>();

    private TenantContext()
    {
    }

    /**
     * Runs an action for one tenant.
     *
     * @param tenantId the tenant, a positive ID
     * @param action the work to run; must not be {@code null}
     * @param <T> result type
     * @return the action's result
     * @throws IllegalArgumentException if {@code tenantId} is not positive
     */
    public static <T> T callInTenant(long tenantId, Supplier<T> action)
    {
        if (tenantId <= 0) {
            throw new IllegalArgumentException("tenantId must be positive but was " + tenantId);
        }
        return call(new Binding(tenantId, false), action);
    }

    /**
     * Runs an action for one tenant without a result.
     *
     * @param tenantId the tenant, a positive ID
     * @param action the work to run; must not be {@code null}
     */
    public static void runInTenant(long tenantId, Runnable action)
    {
        requireNonNull(action, "action");
        callInTenant(tenantId, () -> {
            action.run();
            return null;
        });
    }

    /**
     * Runs cross-tenant platform work with tenant filtering disabled.
     *
     * @param action the work to run; must not be {@code null}
     * @param <T> result type
     * @return the action's result
     */
    public static <T> T callAsSystem(Supplier<T> action)
    {
        return call(new Binding(0L, true), action);
    }

    /**
     * Returns the bound tenant.
     *
     * @return the tenant ID, or empty when unbound or in system context
     */
    public static OptionalLong currentTenantId()
    {
        Binding binding = CURRENT.get();
        return binding == null || binding.system() ? OptionalLong.empty() : OptionalLong.of(binding.tenantId());
    }

    /**
     * Returns the bound tenant, failing when there is none.
     *
     * @return the tenant ID
     * @throws IllegalStateException when unbound or in system context
     */
    public static long requireTenantId()
    {
        return currentTenantId().orElseThrow(
                () -> new IllegalStateException("no tenant is bound to the current thread"));
    }

    /**
     * Returns whether the current thread runs cross-tenant platform work.
     *
     * @return {@code true} inside {@link #callAsSystem}
     */
    public static boolean isSystem()
    {
        Binding binding = CURRENT.get();
        return binding != null && binding.system();
    }

    private static <T> T call(Binding binding, Supplier<T> action)
    {
        requireNonNull(action, "action");
        Binding previous = CURRENT.get();
        CURRENT.set(binding);
        try {
            return action.get();
        }
        finally {
            // Restore rather than clear so nested bindings unwind correctly.
            if (previous == null) {
                CURRENT.remove();
            }
            else {
                CURRENT.set(previous);
            }
        }
    }

    private record Binding(long tenantId, boolean system)
    {
    }
}
