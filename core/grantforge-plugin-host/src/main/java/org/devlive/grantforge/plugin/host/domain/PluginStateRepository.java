// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.plugin.host.domain;

import org.springframework.data.jpa.repository.JpaRepository;

/** Stored plugin switches. */
public interface PluginStateRepository
        extends JpaRepository<PluginState, String>
{
}
