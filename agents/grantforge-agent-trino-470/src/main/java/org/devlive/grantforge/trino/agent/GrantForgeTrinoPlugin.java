// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.trino.agent;

import io.trino.spi.Plugin;
import io.trino.spi.security.SystemAccessControlFactory;

import java.util.List;

/** The GrantForge plugin for Trino 470 to 478: one system access control, named {@code grantforge}. */
public final class GrantForgeTrinoPlugin
        implements Plugin
{
    @Override
    public Iterable<SystemAccessControlFactory> getSystemAccessControlFactories()
    {
        return List.of(new GrantForgeAccessControlFactory(GrantForgeSystemAccessControl::new));
    }
}
