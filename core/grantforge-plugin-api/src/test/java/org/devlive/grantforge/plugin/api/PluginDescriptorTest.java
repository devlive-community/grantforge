// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.plugin.api;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PluginDescriptorTest
{
    private static Map<String, Object> document()
    {
        Map<String, Object> document = new HashMap<>();
        document.put("id", "hdfs");
        document.put("version", "1.2.0-rc1");
        document.put("name", " HDFS ");
        document.put("description", "Paths");
        // How a YAML parser reads "apiVersion: 1.0".
        document.put("apiVersion", 1.0);
        document.put("providers", List.of("org.example.HdfsProvider", "org.example.Hdfs$Inner"));
        return document;
    }

    @Test
    void readsTheParsedDocument()
    {
        PluginDescriptor descriptor = PluginDescriptor.fromMap(document());

        assertThat(descriptor).isEqualTo(new PluginDescriptor("hdfs", "1.2.0-rc1", "HDFS", "Paths", new ApiVersion(1, 0, 0),
                List.of("org.example.HdfsProvider", "org.example.Hdfs$Inner")));
        assertThat(descriptor.isCompatible()).isTrue();
        assertThat(PluginDescriptor.FILE_NAME).isEqualTo("grantforge-plugin.yaml");
    }

    @Test
    void aSingleProviderMayBeAScalar()
    {
        Map<String, Object> document = document();
        document.put("providers", "org.example.HdfsProvider");
        document.remove("description");

        PluginDescriptor descriptor = PluginDescriptor.fromMap(document);

        assertThat(descriptor.providers()).containsExactly("org.example.HdfsProvider");
        assertThat(descriptor.description()).isNull();
    }

    @Test
    void pluginsForAnotherMajorVersionAreIncompatible()
    {
        Map<String, Object> document = document();
        document.put("apiVersion", "2.0");

        assertThat(PluginDescriptor.fromMap(document).isCompatible()).isFalse();
    }

    @Test
    void missingOrMalformedKeysAreRefused()
    {
        assertRefused("id", null, "id is required");
        assertRefused("id", "HDFS", "plugin id");
        assertRefused("version", "1.2", "major.minor.patch");
        assertRefused("version", null, "version is required");
        assertRefused("name", " ", "name is required");
        assertRefused("name", List.of("a"), "single value");
        assertRefused("apiVersion", null, "apiVersion is required");
        assertRefused("apiVersion", "one", "not an API version");
        assertRefused("providers", null, "at least one provider");
        assertRefused("providers", List.of("not a class"), "not a class name");
        assertRefused("description", Map.of("a", "b"), "single value");
    }

    private static void assertRefused(String key, @Nullable Object value, String message)
    {
        Map<String, Object> document = document();
        // HashMap accepts null, which is how YAML reads an empty value.
        document.put(key, value);
        assertThatThrownBy(() -> PluginDescriptor.fromMap(document)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(message);
    }
}
