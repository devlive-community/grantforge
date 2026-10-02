// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.plugin.host;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * The class loader of one external plugin. It sees the JDK, the plugin API (taken from the server, so the plugin's
 * classes and the server's agree on it) and the plugin's own jars, and nothing else of the server: the plugin's
 * dependencies, such as a Hadoop client, cannot clash with the server's or another plugin's.
 */
public final class PluginClassLoader
        extends URLClassLoader
{
    /** Packages taken from the server rather than the plugin. */
    static final List<String> SHARED = List.of("org.devlive.grantforge.plugin.api.", "org.jspecify.annotations.");

    private final ClassLoader host;
    private final String pluginId;

    /**
     * Creates the loader.
     *
     * @param pluginId the plugin, for diagnostics
     * @param urls the plugin's jars and class directories
     * @param host the server's class loader, which provides the plugin API
     */
    public PluginClassLoader(String pluginId, List<URL> urls, ClassLoader host)
    {
        super("plugin-" + pluginId, urls.toArray(URL[]::new), ClassLoader.getPlatformClassLoader());
        this.pluginId = requireNonNull(pluginId, "pluginId");
        this.host = requireNonNull(host, "host");
    }

    /**
     * Returns the plugin this loader belongs to.
     *
     * @return the plugin id
     */
    public String pluginId()
    {
        return pluginId;
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve)
            throws ClassNotFoundException
    {
        if (SHARED.stream().anyMatch(name::startsWith)) {
            return host.loadClass(name);
        }
        return super.loadClass(name, resolve);
    }
}
