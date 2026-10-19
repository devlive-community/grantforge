// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.trino.agent;

import io.trino.spi.security.AccessDeniedException;
import io.trino.spi.security.SystemAccessControl;
import io.trino.spi.security.SystemAccessControlFactory;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.Properties;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;

/**
 * Creates the GrantForge access control of a coordinator from {@code etc/access-control.properties}, whose
 * {@code access-control.name=grantforge} picks it and whose {@code grantforge.*} settings say where the GrantForge
 * server is and how to sign in to it.
 */
public final class GrantForgeAccessControlFactory
        implements SystemAccessControlFactory
{
    /** The name {@code access-control.name} gives. */
    public static final String NAME = "grantforge";

    private static final String VERSION_FILE = "/META-INF/grantforge/trino-agent-version.properties";

    private final Function<TrinoAccess, SystemAccessControl> controls;

    /**
     * Creates the factory of a Trino line's access control.
     *
     * @param controls creates the access control around the agent's checks
     */
    public GrantForgeAccessControlFactory(Function<TrinoAccess, SystemAccessControl> controls)
    {
        this.controls = requireNonNull(controls, "controls");
    }

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public SystemAccessControl create(Map<String, String> config, SystemAccessControlContext context)
    {
        try {
            return controls.apply(new TrinoAccess(TrinoAgentRuntime.start(config, version()), AccessDeniedException::new));
        }
        catch (IOException broken) {
            throw new UncheckedIOException("GrantForge cannot read the Trino agent's token or signing key", broken);
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
        try (InputStream in = GrantForgeAccessControlFactory.class.getResourceAsStream(VERSION_FILE)) {
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
        if (version == null || version.isBlank()) {
            throw new IOException(VERSION_FILE + " names no grantforge.version");
        }
        return version.strip();
    }
}
