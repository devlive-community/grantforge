// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.catalog;

import org.devlive.grantforge.authz.application.ConsoleManifest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;

import static java.util.Objects.requireNonNull;

/** Reads the console's permission manifest, which the build copies from the web console to the classpath. */
@Component
public final class ConsoleManifestLoader
{
    /** Where the manifest sits on the classpath. */
    static final String LOCATION = "permissions/manifest.json";

    private final JsonMapper json;

    /**
     * Creates the loader.
     *
     * @param json parses the manifest
     */
    public ConsoleManifestLoader(JsonMapper json)
    {
        this.json = requireNonNull(json, "json");
    }

    /**
     * Reads the manifest.
     *
     * @return the manifest
     * @throws IllegalStateException if it is missing or not a valid manifest
     */
    public ConsoleManifest load()
    {
        return load(LOCATION);
    }

    ConsoleManifest load(String location)
    {
        ClassPathResource manifest = new ClassPathResource(location);
        if (!manifest.exists()) {
            throw new IllegalStateException("the console manifest " + location + " is missing from the classpath");
        }
        try (InputStream stream = manifest.getInputStream()) {
            return json.readValue(stream, ConsoleManifest.class);
        }
        catch (IOException | JacksonException invalid) {
            throw new IllegalStateException("the console manifest " + location + " cannot be read: " + invalid.getMessage(),
                    invalid);
        }
    }
}
