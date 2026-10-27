// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.opensearch.agent;

import com.fasterxml.jackson.databind.JsonNode;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/** What the agent asks of OpenSearch: requests of its REST API with JSON bodies and answers. */
interface OpenSearch
        extends AutoCloseable
{
    /**
     * Sends a request.
     *
     * @param method the HTTP method
     * @param path the path, its names already encoded
     * @param parameters the query parameters
     * @param body the JSON body, or {@code null} for none
     * @return the answer's JSON
     * @throws OpenSearchFailure when OpenSearch refuses or fails it, or cannot be reached
     */
    JsonNode send(String method, String path, Map<String, String> parameters, @Nullable JsonNode body);

    /**
     * Reads a resource.
     *
     * @param path the path, its names already encoded
     * @param parameters the query parameters
     * @return the answer's JSON
     * @throws OpenSearchFailure when OpenSearch refuses or fails it, or cannot be reached
     */
    default JsonNode get(String path, Map<String, String> parameters)
    {
        return send("GET", path, parameters, null);
    }

    @Override
    void close();
}
