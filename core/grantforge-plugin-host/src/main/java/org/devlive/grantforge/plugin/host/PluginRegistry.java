// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.plugin.host;

import org.devlive.grantforge.plugin.api.ApiVersion;
import org.devlive.grantforge.plugin.api.PluginDescriptor;
import org.devlive.grantforge.plugin.api.ServiceTypeProvider;
import org.devlive.grantforge.plugin.api.model.ServiceTypeDefinition;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

/**
 * The installed plugins and the service types they provide. A scan finds the built-in plugins (providers on the
 * server's classpath, one plugin each) and the external ones in the plugins directory, each loaded in its own
 * {@link PluginClassLoader}. A plugin that cannot be read, was built for another plugin API, clashes with another
 * plugin or whose provider fails is set aside with the reason; the others are used. Thread-safe.
 */
public final class PluginRegistry
        implements AutoCloseable
{
    private static final Logger LOG = LoggerFactory.getLogger(PluginRegistry.class);
    private static final String BUILTIN_PREFIX = "builtin-";

    private final Path directory;
    private final PluginSwitches switches;
    private final PluginCalls calls;
    private final ClassLoader host;
    private final ReentrantLock lock = new ReentrantLock();
    private final AtomicReference<Scan> current = new AtomicReference<>(new Scan(List.of(), Map.of(), List.of()));

    /**
     * Creates the registry; nothing is loaded until {@link #scan()}.
     *
     * @param directory the plugins directory; it need not exist
     * @param switches which plugins are switched off
     * @param calls calls plugin code with a time limit
     * @param host the server's class loader: built-in providers and the plugin API come from it
     */
    public PluginRegistry(Path directory, PluginSwitches switches, PluginCalls calls, ClassLoader host)
    {
        this.directory = requireNonNull(directory, "directory");
        this.switches = requireNonNull(switches, "switches");
        this.calls = requireNonNull(calls, "calls");
        this.host = requireNonNull(host, "host");
    }

    /**
     * Returns the installed plugins, built-in ones first, then by id.
     *
     * @return the plugins as of the last scan
     */
    public List<InstalledPlugin> plugins()
    {
        return latest().plugins();
    }

    /**
     * Returns the provider of an active service type.
     *
     * @param serviceType the service type's name
     * @return its provider, if a loaded plugin provides it
     */
    public Optional<ServiceTypeProvider> provider(String serviceType)
    {
        return Optional.ofNullable(latest().providers().get(serviceType));
    }

    /**
     * Returns the service types of the active plugins.
     *
     * @return their definitions, by plugin and then as each plugin lists them
     */
    public List<ServiceTypeDefinition> serviceTypes()
    {
        return latest().plugins().stream().filter(plugin -> plugin.status() == PluginStatus.ACTIVE)
                .flatMap(plugin -> plugin.serviceTypes().stream()).toList();
    }

    /**
     * Returns an active service type.
     *
     * @param name the service type's name
     * @return its definition, if a loaded plugin provides it
     */
    public Optional<ServiceTypeDefinition> serviceType(String name)
    {
        return serviceTypes().stream().filter(definition -> definition.name().equals(name)).findFirst();
    }

    /**
     * Calls the provider of an active service type, with the time limit and the plugin's class loader.
     *
     * @param serviceType the service type's name
     * @param call what to ask the provider
     * @param <T> what the call returns
     * @return what the provider answered
     * @throws IllegalArgumentException if no loaded plugin provides the service type
     * @throws PluginCallException if the provider fails or does not answer in time
     */
    // The provider's own loader is meant: plugin code runs with the loader of the plugin it comes from.
    @SuppressWarnings("PMD.UseProperClassLoader")
    public <T> T call(String serviceType, Function<ServiceTypeProvider, T> call)
    {
        ServiceTypeProvider provider = provider(serviceType)
                .orElseThrow(() -> new IllegalArgumentException("no active plugin provides service type " + serviceType));
        ClassLoader loader = provider.getClass().getClassLoader();
        return calls.call(serviceType, loader == null ? host : loader, () -> call.apply(provider));
    }

    /**
     * Looks every plugin up again: unloads the external ones and loads what the plugins directory holds now.
     *
     * @return the plugins found
     */
    public List<InstalledPlugin> scan()
    {
        lock.lock();
        try {
            return rescan();
        }
        finally {
            lock.unlock();
        }
    }

    private List<InstalledPlugin> rescan()
    {
        List<InstalledPlugin> found = new ArrayList<>();
        Map<String, ServiceTypeProvider> providers = new LinkedHashMap<>();
        Map<String, String> owners = new LinkedHashMap<>();
        List<PluginClassLoader> loaders = new ArrayList<>();
        scanBuiltins(found, providers, owners);
        scanDirectory(found, providers, owners, loaders);
        found.sort(Comparator.comparing(InstalledPlugin::source).thenComparing(InstalledPlugin::id));
        requireNonNull(current.getAndSet(new Scan(List.copyOf(found), Map.copyOf(providers), List.copyOf(loaders)))).close();
        found.forEach(plugin -> {
            if (plugin.problem() != null) {
                LOG.warn("Plugin {} ({}) is {}: {}", plugin.id(), plugin.location(), plugin.status(), plugin.problem());
            }
        });
        return latest().plugins();
    }

    /**
     * Switches a plugin on or off and scans again.
     *
     * @param pluginId the plugin
     * @param enabled whether it is on
     * @return the plugin after the scan
     * @throws IllegalArgumentException if no such plugin is installed
     */
    public InstalledPlugin setEnabled(String pluginId, boolean enabled)
    {
        lock.lock();
        try {
            if (latest().plugins().stream().noneMatch(plugin -> plugin.id().equals(pluginId))) {
                throw new IllegalArgumentException("no plugin " + pluginId);
            }
            switches.set(pluginId, enabled);
            return rescan().stream().filter(plugin -> plugin.id().equals(pluginId)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("plugin " + pluginId + " is gone"));
        }
        finally {
            lock.unlock();
        }
    }

    private void scanBuiltins(List<InstalledPlugin> found, Map<String, ServiceTypeProvider> providers, Map<String, String> owners)
    {
        List<ServiceLoader.Provider<ServiceTypeProvider>> candidates;
        try {
            candidates = ServiceLoader.load(ServiceTypeProvider.class, host).stream().toList();
        }
        catch (ServiceConfigurationError broken) {
            found.add(failed(BUILTIN_PREFIX + "classpath", PluginSource.BUILTIN, "classpath", broken.getMessage()));
            return;
        }
        for (ServiceLoader.Provider<ServiceTypeProvider> candidate : candidates) {
            String location = candidate.type().getName();
            try {
                ServiceTypeProvider provider = calls.call(location, host, candidate::get);
                ServiceTypeDefinition definition = calls.call(location, host, provider::definition);
                String id = BUILTIN_PREFIX + definition.name();
                String version = ApiVersion.CURRENT.toString();
                if (!switches.enabled(id)) {
                    found.add(new InstalledPlugin(id, version, definition.label(), definition.description(), version,
                            PluginSource.BUILTIN, location, PluginStatus.DISABLED, null, List.of()));
                    continue;
                }
                String clash = claim(owners, id, List.of(definition));
                if (clash != null) {
                    found.add(failed(id, PluginSource.BUILTIN, location, clash));
                    continue;
                }
                providers.put(definition.name(), provider);
                found.add(new InstalledPlugin(id, version, definition.label(), definition.description(), version,
                        PluginSource.BUILTIN, location, PluginStatus.ACTIVE, null, List.of(definition)));
            }
            catch (PluginCallException | ServiceConfigurationError | IllegalArgumentException failure) {
                found.add(failed(BUILTIN_PREFIX + location, PluginSource.BUILTIN, location, failure.getMessage()));
            }
        }
    }

    private void scanDirectory(List<InstalledPlugin> found, Map<String, ServiceTypeProvider> providers, Map<String, String> owners,
            List<PluginClassLoader> loaders)
    {
        if (!Files.isDirectory(directory)) {
            return;
        }
        List<Path> candidates;
        try (Stream<Path> entries = Files.list(directory)) {
            candidates = entries.filter(PluginPackage::candidate).sorted().toList();
        }
        catch (IOException unreadable) {
            found.add(failed("plugins-directory", PluginSource.EXTERNAL, directory.toString(), unreadable.getMessage()));
            return;
        }
        Map<String, String> seen = new LinkedHashMap<>();
        for (Path path : candidates) {
            String location = String.valueOf(path.getFileName());
            PluginPackage plugin;
            try {
                plugin = PluginPackage.read(path, directory.resolve(PluginPackage.WORK));
            }
            catch (IllegalArgumentException unreadable) {
                found.add(failed(location, PluginSource.EXTERNAL, location, unreadable.getMessage()));
                continue;
            }
            PluginDescriptor descriptor = plugin.descriptor();
            String other = seen.putIfAbsent(descriptor.id(), location);
            if (other != null) {
                found.add(described(plugin, PluginStatus.FAILED, "plugin id " + descriptor.id() + " is already used by " + other,
                        List.of()));
            }
            else if (!descriptor.isCompatible()) {
                found.add(described(plugin, PluginStatus.INCOMPATIBLE, "built for plugin API " + descriptor.apiVersion()
                        + ", this server provides " + ApiVersion.CURRENT, List.of()));
            }
            else if (!switches.enabled(descriptor.id())) {
                found.add(described(plugin, PluginStatus.DISABLED, null, List.of()));
            }
            else {
                found.add(load(plugin, providers, owners, loaders));
            }
        }
    }

    /** Loads an external plugin in its own class loader; on any failure nothing of it stays loaded. */
    // A loaded plugin's class loader stays open while the plugin is in use; the next scan or close() closes it.
    @SuppressWarnings("PMD.CloseResource")
    private InstalledPlugin load(PluginPackage plugin, Map<String, ServiceTypeProvider> providers, Map<String, String> owners,
            List<PluginClassLoader> loaders)
    {
        String id = plugin.descriptor().id();
        PluginClassLoader loader = new PluginClassLoader(id, plugin.urls(), host);
        try {
            Map<String, ServiceTypeProvider> mine = new LinkedHashMap<>();
            List<ServiceTypeDefinition> definitions = new ArrayList<>();
            for (String className : plugin.descriptor().providers()) {
                ServiceTypeProvider provider = calls.call(id, loader, () -> instantiate(loader, className));
                ServiceTypeDefinition definition = calls.call(id, loader, provider::definition);
                mine.put(definition.name(), provider);
                definitions.add(definition);
            }
            String clash = claim(owners, id, definitions);
            if (clash != null) {
                close(loader);
                return described(plugin, PluginStatus.FAILED, clash, List.of());
            }
            providers.putAll(mine);
            loaders.add(loader);
            return described(plugin, PluginStatus.ACTIVE, null, definitions);
        }
        catch (PluginCallException | IllegalArgumentException failure) {
            close(loader);
            return described(plugin, PluginStatus.FAILED, failure.getMessage(), List.of());
        }
    }

    private Scan latest()
    {
        return requireNonNull(current.get());
    }

    private static ServiceTypeProvider instantiate(ClassLoader loader, String className)
            throws ReflectiveOperationException
    {
        Class<?> type = Class.forName(className, true, loader);
        if (!ServiceTypeProvider.class.isAssignableFrom(type)) {
            throw new IllegalArgumentException(className + " does not implement " + ServiceTypeProvider.class.getName());
        }
        return (ServiceTypeProvider) type.getConstructor().newInstance();
    }

    /** Claims the service type names for a plugin; returns the clash if another plugin provides one already. */
    private static @Nullable String claim(Map<String, String> owners, String pluginId, List<ServiceTypeDefinition> definitions)
    {
        for (ServiceTypeDefinition definition : definitions) {
            String owner = owners.get(definition.name());
            if (owner != null) {
                return "service type " + definition.name() + " is already provided by " + owner;
            }
        }
        definitions.forEach(definition -> owners.put(definition.name(), pluginId));
        return null;
    }

    private static InstalledPlugin described(PluginPackage plugin, PluginStatus status, @Nullable String problem,
            List<ServiceTypeDefinition> serviceTypes)
    {
        PluginDescriptor descriptor = plugin.descriptor();
        return new InstalledPlugin(descriptor.id(), descriptor.version(), descriptor.name(), descriptor.description(),
                descriptor.apiVersion().toString(), PluginSource.EXTERNAL, plugin.location(), status, problem, serviceTypes);
    }

    private static InstalledPlugin failed(String id, PluginSource source, String location, @Nullable String problem)
    {
        return new InstalledPlugin(id, null, id, null, null, source, location, PluginStatus.FAILED,
                problem == null ? "failed" : problem, List.of());
    }

    private static void close(PluginClassLoader loader)
    {
        try {
            loader.close();
        }
        catch (IOException ignored) {
            LOG.debug("Could not close the class loader of plugin {}", loader.pluginId(), ignored);
        }
    }

    @Override
    public void close()
    {
        lock.lock();
        try {
            requireNonNull(current.getAndSet(new Scan(List.of(), Map.of(), List.of()))).close();
        }
        finally {
            lock.unlock();
        }
    }

    /** One scan's result and the class loaders it opened. */
    private record Scan(List<InstalledPlugin> plugins, Map<String, ServiceTypeProvider> providers, List<PluginClassLoader> loaders)
    {
        void close()
        {
            loaders.forEach(PluginRegistry::close);
        }
    }
}
