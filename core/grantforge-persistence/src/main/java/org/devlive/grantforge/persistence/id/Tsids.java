// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.persistence.id;

import org.devlive.grantforge.common.lang.Strings;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntSupplier;

import static java.util.Objects.requireNonNull;

/**
 * The process-wide {@link TsidGenerator} used for entity IDs.
 *
 * <p>JPA instantiates entities itself, so the generator cannot be injected; it is resolved once per JVM.
 * The node comes from the system property {@value #NODE_PROPERTY}, then the environment variable
 * {@value #NODE_ENV}, and otherwise a random node is chosen with a warning. Clustered deployments must set
 * a distinct node per instance.
 */
public final class Tsids
{
    /** System property holding this instance's node (0-1023). */
    public static final String NODE_PROPERTY = "grantforge.id.node";
    /** Environment variable holding this instance's node (0-1023). */
    public static final String NODE_ENV = "GRANTFORGE_ID_NODE";

    private static final Logger LOG = LoggerFactory.getLogger(Tsids.class);
    private static final AtomicReference<@Nullable TsidGenerator> GENERATOR = new AtomicReference<>();

    private Tsids()
    {
    }

    /**
     * Returns the next ID from the process-wide generator.
     *
     * @return a new, strictly increasing ID
     */
    public static long next()
    {
        return generator().next();
    }

    /**
     * Fixes the node before the first ID is generated.
     *
     * @param node the node, 0-{@value TsidGenerator#MAX_NODE}
     * @throws IllegalArgumentException if {@code node} is out of range
     * @throws IllegalStateException if IDs are already being generated with a different node
     */
    public static void configure(int node)
    {
        TsidGenerator candidate = new TsidGenerator(node);
        if (!GENERATOR.compareAndSet(null, candidate) && generator().node() != node) {
            throw new IllegalStateException("TSID node is already set to a different value; configure it once at startup");
        }
    }

    static TsidGenerator generator()
    {
        TsidGenerator current = GENERATOR.get();
        if (current != null) {
            return current;
        }
        int node = resolveNode(System.getProperty(NODE_PROPERTY), System.getenv(NODE_ENV),
                // Not security sensitive: the node only needs to differ between instances.
                () -> ThreadLocalRandom.current().nextInt(TsidGenerator.MAX_NODE + 1));
        // Another thread may win the race; everyone then uses the instance that was stored first.
        GENERATOR.compareAndSet(null, new TsidGenerator(node));
        return requireNonNull(GENERATOR.get(), "generator");
    }

    /**
     * Resolves the node from the configured sources.
     *
     * @param property system property value; may be {@code null}
     * @param environment environment variable value; may be {@code null}
     * @param random fallback when neither is set
     * @return the node, 0-{@value TsidGenerator#MAX_NODE}
     * @throws IllegalArgumentException if a configured value is not a valid node
     */
    static int resolveNode(@Nullable String property, @Nullable String environment, IntSupplier random)
    {
        String configured = Strings.blankToNull(property);
        String source = NODE_PROPERTY;
        if (configured == null) {
            configured = Strings.blankToNull(environment);
            source = NODE_ENV;
        }
        if (configured == null) {
            int node = random.getAsInt();
            LOG.warn("No TSID node configured ({} / {}); using random node {}. Set a distinct node per instance "
                    + "in clustered deployments.", NODE_PROPERTY, NODE_ENV, node);
            return node;
        }
        try {
            int node = Integer.parseInt(configured);
            if (node >= 0 && node <= TsidGenerator.MAX_NODE) {
                return node;
            }
        }
        catch (NumberFormatException ignored) {
            // Reported below together with the range error.
        }
        throw new IllegalArgumentException(source + " must be an integer between 0 and " + TsidGenerator.MAX_NODE
                + " but was '" + configured + "'");
    }

    /** Forgets the process-wide generator; for tests only. */
    static void reset()
    {
        GENERATOR.set(null);
    }
}
