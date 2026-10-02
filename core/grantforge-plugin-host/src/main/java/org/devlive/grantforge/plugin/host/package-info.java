// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

/**
 * Loads service type plugins: built-in ones from the classpath, external ones from the plugins directory, each in a
 * class loader of its own that sees only the plugin API and the JDK. A plugin that fails is set aside; the server
 * keeps running.
 */
@NullMarked
package org.devlive.grantforge.plugin.host;

import org.jspecify.annotations.NullMarked;
