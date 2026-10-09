// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.plugin.host;

import org.devlive.grantforge.plugin.api.ApiVersion;
import org.devlive.grantforge.plugin.api.ServiceTypeProvider;
import org.devlive.grantforge.plugin.api.model.ServiceTypeDefinition;
import org.devlive.grantforge.plugin.host.fixture.Fixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PluginRegistryTest
{
    private static final String FIXTURES = "org/devlive/grantforge/plugin/host/fixture/";

    @TempDir
    private Path plugins;

    @TempDir
    private Path builtins;

    private final Map<String, Boolean> switches = new HashMap<>();
    private final PluginCalls calls = new PluginCalls(Duration.ofSeconds(2));
    private URLClassLoader host;
    private PluginRegistry registry;

    @BeforeEach
    void createRegistry()
            throws IOException
    {
        host = new URLClassLoader(new URL[] {builtins.toUri().toURL()}, PluginRegistryTest.class.getClassLoader());
        PluginSwitches stored = new PluginSwitches()
        {
            @Override
            public boolean enabled(String pluginId)
            {
                return switches.getOrDefault(pluginId, true);
            }

            @Override
            public void set(String pluginId, boolean enabled)
            {
                switches.put(pluginId, enabled);
            }
        };
        registry = new PluginRegistry(plugins, stored, calls, host);
    }

    @AfterEach
    void close()
            throws IOException
    {
        registry.close();
        calls.close();
        host.close();
    }

    private static Path compiled()
    {
        try {
            return Path.of(Fixtures.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        }
        catch (URISyntaxException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static List<Path> fixtureClasses()
            throws IOException
    {
        try (Stream<Path> files = Files.list(compiled().resolve(FIXTURES))) {
            return files.filter(file -> file.getFileName().toString().endsWith(".class")).toList();
        }
    }

    private static String descriptor(String id, String apiVersion, String... providers)
    {
        StringBuilder text = new StringBuilder("id: " + id + "\nversion: 1.2.0\nname: " + id.toUpperCase(Locale.ROOT)
                + "\ndescription: the " + id + " plugin\napiVersion: \"" + apiVersion + "\"\nproviders:\n");
        for (String provider : providers) {
            text.append("  - ").append(Fixtures.class.getName()).append('$').append(provider).append('\n');
        }
        return text.toString();
    }

    private Path directoryPlugin(String name, String descriptor)
            throws IOException
    {
        Path root = Files.createDirectories(plugins.resolve(name));
        Files.writeString(root.resolve("grantforge-plugin.yaml"), descriptor);
        Path classes = Files.createDirectories(root.resolve("classes").resolve(FIXTURES));
        for (Path file : fixtureClasses()) {
            Files.copy(file, classes.resolve(file.getFileName().toString()));
        }
        return root;
    }

    private void archive(Path target, String descriptor, boolean jar, String classesPrefix, Map<String, String> extra)
            throws IOException
    {
        try (OutputStream file = Files.newOutputStream(target);
             ZipOutputStream out = jar ? new JarOutputStream(file) : new ZipOutputStream(file)) {
            out.putNextEntry(new ZipEntry("grantforge-plugin.yaml"));
            out.write(descriptor.getBytes(StandardCharsets.UTF_8));
            for (Path compiledClass : fixtureClasses()) {
                out.putNextEntry(new ZipEntry(classesPrefix + FIXTURES + compiledClass.getFileName()));
                out.write(Files.readAllBytes(compiledClass));
            }
            for (Map.Entry<String, String> entry : extra.entrySet()) {
                out.putNextEntry(new ZipEntry(entry.getKey()));
                out.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    private InstalledPlugin plugin(String id)
    {
        return registry.plugins().stream().filter(plugin -> plugin.id().equals(id)).findFirst()
                .orElseThrow(() -> new AssertionError("no plugin " + id + " in " + registry.plugins()));
    }

    @Test
    void loadsDirectoryJarAndZipPluginsEachInItsOwnClassLoader()
            throws IOException
    {
        directoryPlugin("alpha", descriptor("alpha", "1.0", "Alpha"));
        archive(plugins.resolve("beta.jar"), descriptor("beta", "1.0", "Beta"), true, "", Map.of());
        assertThat(registry.plugins()).isEmpty();

        registry.scan();

        InstalledPlugin alpha = plugin("alpha");
        assertThat(alpha.status()).isEqualTo(PluginStatus.ACTIVE);
        assertThat(alpha.source()).isEqualTo(PluginSource.EXTERNAL);
        assertThat(alpha).extracting(InstalledPlugin::version, InstalledPlugin::name, InstalledPlugin::description,
                InstalledPlugin::apiVersion, InstalledPlugin::location).containsExactly("1.2.0", "ALPHA", "the alpha plugin", "1.0.0",
                "alpha");
        assertThat(alpha.serviceTypes()).extracting(ServiceTypeDefinition::name).containsExactly("alpha");
        // The plugin sees the plugin API but none of the server's own classes.
        assertThat(alpha.serviceTypes().get(0).description()).isEqualTo("isolated");
        ServiceTypeProvider provider = registry.provider("alpha").orElseThrow();
        assertThat(provider.getClass().getClassLoader()).isInstanceOf(PluginClassLoader.class);
        assertThat(provider.getClass()).isNotEqualTo(Fixtures.Alpha.class);
        assertThat(((PluginClassLoader) provider.getClass().getClassLoader()).pluginId()).isEqualTo("alpha");
        assertThat(plugin("beta").status()).isEqualTo(PluginStatus.ACTIVE);
        assertThat(registry.provider("beta")).isPresent();

        // A zip holds its classes in classes/, and is unpacked into the work folder, which is never a plugin itself.
        Files.delete(plugins.resolve("beta.jar"));
        archive(plugins.resolve("gamma.zip"), descriptor("gamma", "1.0", "Beta"), false, "classes/", Map.of());
        registry.scan();
        assertThat(registry.plugins()).extracting(InstalledPlugin::id).containsExactly("alpha", "gamma");
        assertThat(plugin("gamma").status()).isEqualTo(PluginStatus.ACTIVE);
        assertThat(Files.isDirectory(plugins.resolve(".work/gamma"))).isTrue();
        assertThat(registry.provider("missing")).isEmpty();
    }

    @Test
    void setsAsideWhatCannotBeReadIsIncompatibleOrClashes()
            throws IOException
    {
        Files.createDirectories(plugins.resolve("empty"));
        Files.writeString(Files.createDirectories(plugins.resolve("garbled")).resolve("grantforge-plugin.yaml"), "id: [unclosed");
        Files.writeString(Files.createDirectories(plugins.resolve("listed")).resolve("grantforge-plugin.yaml"), "- just a list");
        Files.writeString(plugins.resolve("plain.jar"), "not a jar");
        archive(plugins.resolve("nodescriptor.jar"), descriptor("x", "1.0", "Beta"), true, "", Map.of());
        Files.writeString(plugins.resolve("notes.txt"), "ignored");
        directoryPlugin("old", descriptor("old", "0.9", "Beta"));
        directoryPlugin("future", descriptor("future", "1.99", "Beta"));
        directoryPlugin("alpha-1", descriptor("alpha", "1.0", "Alpha"));
        directoryPlugin("alpha-2", descriptor("alpha", "1.0", "Beta"));
        directoryPlugin("zz-again", descriptor("again", "1.0", "Alpha"));
        archive(plugins.resolve("escape.zip"), descriptor("escape", "1.0", "Beta"), false, "classes/", Map.of("../outside.txt", "x"));

        registry.scan();

        assertThat(plugin("empty").problem()).contains("has no grantforge-plugin.yaml");
        assertThat(plugin("garbled").problem()).contains("not valid YAML");
        assertThat(plugin("listed").problem()).contains("must be a mapping");
        assertThat(plugin("plain.jar").problem()).contains("cannot be read");
        assertThat(registry.plugins()).extracting(InstalledPlugin::id).doesNotContain("notes.txt");
        assertThat(plugin("old")).extracting(InstalledPlugin::status, InstalledPlugin::problem)
                .containsExactly(PluginStatus.INCOMPATIBLE, "built for plugin API 0.9.0, this server provides " + ApiVersion.CURRENT);
        // A plugin needing additions this server lacks is refused when it loads, not when it first calls them.
        assertThat(plugin("future")).extracting(InstalledPlugin::status, InstalledPlugin::problem)
                .containsExactly(PluginStatus.INCOMPATIBLE, "built for plugin API 1.99.0, this server provides " + ApiVersion.CURRENT);
        // The first package with an id wins; a second one with the same id is set aside. Both are built against API 1.0,
        // which a newer 1.x server still loads.
        List<InstalledPlugin> alphas = registry.plugins().stream().filter(plugin -> plugin.id().equals("alpha")).toList();
        assertThat(alphas).extracting(InstalledPlugin::status).containsExactly(PluginStatus.ACTIVE, PluginStatus.FAILED);
        assertThat(alphas.get(1).problem()).isEqualTo("plugin id alpha is already used by alpha-1");
        assertThat(plugin("again").problem()).isEqualTo("service type alpha is already provided by alpha");
        assertThat(plugin("escape.zip").problem()).contains("entry outside the package");
        assertThat(Files.exists(plugins.resolve(".work/outside.txt"))).isFalse();
        assertThat(registry.plugins().stream().filter(plugin -> plugin.status() == PluginStatus.ACTIVE)).hasSize(1);
    }

    @Test
    void providersThatFailTakeTooLongOrAreNoProvidersAreSetAside()
            throws IOException
    {
        directoryPlugin("broken", descriptor("broken", "1.0", "Broken"));
        directoryPlugin("slow", descriptor("slow", "1.0", "Slow"));
        directoryPlugin("odd", descriptor("odd", "1.0", "NotAProvider"));
        directoryPlugin("absent", descriptor("absent", "1.0", "Missing"));

        registry.scan();

        assertThat(plugin("broken").problem()).contains("cannot describe myself");
        assertThat(plugin("slow").problem()).isEqualTo("slow did not answer within 2s");
        assertThat(plugin("odd").problem()).contains("does not implement");
        assertThat(plugin("absent").problem()).contains("ClassNotFoundException");
        assertThat(registry.plugins()).allMatch(plugin -> plugin.status() == PluginStatus.FAILED && plugin.serviceTypes().isEmpty());
    }

    @Test
    void builtInPluginsComeFromTheClasspathAndEveryPluginCanBeSwitchedOff()
            throws IOException
    {
        Path services = Files.createDirectories(builtins.resolve("META-INF/services"));
        Files.writeString(services.resolve(ServiceTypeProvider.class.getName()), Fixtures.Beta.class.getName() + "\n");
        directoryPlugin("alpha", descriptor("alpha", "1.0", "Alpha"));
        directoryPlugin("beta-again", descriptor("beta-again", "1.0", "Beta"));

        registry.scan();
        InstalledPlugin builtin = plugin("builtin-beta");
        assertThat(builtin).extracting(InstalledPlugin::source, InstalledPlugin::status, InstalledPlugin::location)
                .containsExactly(PluginSource.BUILTIN, PluginStatus.ACTIVE, Fixtures.Beta.class.getName());
        assertThat(registry.plugins().get(0).id()).isEqualTo("builtin-beta");
        // Built-in plugins claim their service types first.
        assertThat(plugin("beta-again").problem()).isEqualTo("service type beta is already provided by builtin-beta");

        assertThat(registry.setEnabled("builtin-beta", false).status()).isEqualTo(PluginStatus.DISABLED);
        assertThat(plugin("beta-again").status()).isEqualTo(PluginStatus.ACTIVE);
        assertThat(registry.setEnabled("alpha", false).status()).isEqualTo(PluginStatus.DISABLED);
        assertThat(registry.provider("alpha")).isEmpty();
        assertThat(plugin("alpha").serviceTypes()).isEmpty();
        assertThat(registry.setEnabled("alpha", true).status()).isEqualTo(PluginStatus.ACTIVE);
        assertThatThrownBy(() -> registry.setEnabled("nothing", true)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void offersTheActiveServiceTypesAndCallsTheirProvidersInTheirOwnClassLoader()
            throws IOException
    {
        directoryPlugin("alpha", descriptor("alpha", "1.0", "Alpha"));
        directoryPlugin("broken", descriptor("broken", "1.0", "Broken"));
        registry.scan();

        assertThat(registry.serviceTypes()).extracting(ServiceTypeDefinition::name).containsExactly("alpha");
        assertThat(registry.serviceType("alpha")).isPresent();
        assertThat(registry.serviceType("broken")).isEmpty();
        ClassLoader context = registry.call("alpha", provider -> Thread.currentThread().getContextClassLoader());
        assertThat(context).isInstanceOf(PluginClassLoader.class);
        String description = registry.call("alpha", provider -> provider.definition().description());
        assertThat(description).isEqualTo("isolated");
        assertThatThrownBy(() -> registry.call("alpha", provider -> {
            throw new IllegalStateException("boom");
        })).isInstanceOf(PluginCallException.class);
        assertThatThrownBy(() -> registry.call("missing", provider -> 1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aMissingDirectoryMeansNoExternalPluginsAndABrokenServiceFileIsReported()
            throws IOException
    {
        PluginRegistry nowhere = new PluginRegistry(plugins.resolve("missing"), new PluginSwitches()
        {
            @Override
            public boolean enabled(String pluginId)
            {
                return true;
            }

            @Override
            public void set(String pluginId, boolean enabled)
            {
                // Not needed.
            }
        }, calls, host);
        assertThat(nowhere.scan()).isEmpty();

        Path services = Files.createDirectories(builtins.resolve("META-INF/services"));
        Files.writeString(services.resolve(ServiceTypeProvider.class.getName()), "org.example.DoesNotExist\n");
        assertThat(nowhere.scan()).singleElement().satisfies(plugin -> {
            assertThat(plugin.source()).isEqualTo(PluginSource.BUILTIN);
            assertThat(plugin.status()).isEqualTo(PluginStatus.FAILED);
            assertThat(requireNonNull(plugin.problem())).contains("DoesNotExist");
        });
        nowhere.close();
    }
}
