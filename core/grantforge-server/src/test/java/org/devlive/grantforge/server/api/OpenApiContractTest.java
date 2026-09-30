// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fails when the server's OpenAPI document differs from the committed contract.
 *
 * <p>After an intentional API change, regenerate the contract and the console types:
 * <pre>
 * ./mvnw -DskipFrontend -pl core/grantforge-server -am test -Dtest=OpenApiContractTest \
 *     -Dsurefire.failIfNoSpecifiedTests=false -Dgrantforge.openapi.update=true
 * cd core/grantforge-web &amp;&amp; pnpm api:generate
 * </pre>
 */
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiContractTest
{
    /** Committed contract, relative to the server module (Surefire's working directory). */
    static final Path CONTRACT = Path.of("../grantforge-web/src/api/openapi.json");

    // Sorted keys and fixed indentation keep the committed file stable and its diffs reviewable.
    private static final JsonMapper MAPPER = JsonMapper.builder()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .build();

    @Autowired
    private MockMvc mvc;

    @Test
    void publishedApiMatchesTheCommittedContract() throws Exception
    {
        String body = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String actual = canonical(body);

        if (Boolean.getBoolean("grantforge.openapi.update")) {
            Files.writeString(CONTRACT, actual, StandardCharsets.UTF_8);
        }

        assertThat(CONTRACT).as("committed contract %s (see the class Javadoc to regenerate)", CONTRACT).exists();
        assertThat(Files.readString(CONTRACT, StandardCharsets.UTF_8)).isEqualTo(actual);
    }

    /** Re-serializes JSON with sorted object keys and a trailing newline. */
    static String canonical(String json)
    {
        JsonNode tree = MAPPER.readTree(json);
        Object sorted = MAPPER.convertValue(tree, Object.class);
        return MAPPER.writeValueAsString(sorted) + "\n";
    }
}
