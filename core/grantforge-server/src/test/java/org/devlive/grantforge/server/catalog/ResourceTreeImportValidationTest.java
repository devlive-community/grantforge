// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.catalog;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResourceTreeImportValidationTest
{
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    void acceptsTheConfiguredResourceCountAndRejectsTheNextResource()
    {
        var nodes = JSON.createArrayNode();
        for (int i = 0; i < 5000; i++) {
            nodes.addObject().putArray("children");
        }
        assertThatCode(() -> ResourceTreeImportController.validateResources(nodes, 5000)).doesNotThrowAnyException();
        assertThatThrownBy(() -> ResourceTreeImportController.validateResources(nodes, 4999))
                .hasMessageContaining("Resource count exceeds configured limit of 4999");
        nodes.addObject().putArray("children");
        assertThatThrownBy(() -> ResourceTreeImportController.validateResources(nodes, 5000))
                .hasMessageContaining("Resource count exceeds configured limit of 5000");
    }

    @Test
    void reportsInvalidNodesAndDepthSeparately()
    {
        assertThatThrownBy(() -> ResourceTreeImportController.validateResources(JSON.readTree("[42]"), 5000))
                .hasMessageContaining("Resource must be an object");
        assertThatThrownBy(() -> ResourceTreeImportController.validateResources(JSON.readTree("[{}]"), 5000))
                .hasMessageContaining("Children must be an array");
        var root = JSON.createArrayNode();
        var children = root.addObject().putArray("children");
        for (int i = 1; i < 16; i++) {
            children = children.addObject().putArray("children");
        }
        assertThatCode(() -> ResourceTreeImportController.validateResources(root, 5000)).doesNotThrowAnyException();
        children.addObject().putArray("children");
        assertThatThrownBy(() -> ResourceTreeImportController.validateResources(root, 5000))
                .hasMessageContaining("Resource tree exceeds maximum depth of 16 levels");
    }
}
