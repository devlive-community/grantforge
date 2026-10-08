// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hdfs.agent;

import org.apache.hadoop.hdfs.server.namenode.INodeAttributeProvider.AccessControlEnforcer;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class HdfsAccessControlEnforcerTest
{
    @Test
    void compilesOnlyAgainstTheRuntimeSupportedBaselineCallbacks()
    {
        Set<String> methods = Arrays.stream(AccessControlEnforcer.class.getMethods()).map(Method::getName).collect(Collectors.toSet());
        assertThat(methods).contains("checkPermission").doesNotContain("checkPermissionWithContext", "checkSuperUserPermissionWithContext",
                "denyUserAccess");
        assertThat(Arrays.stream(HdfsAccessControlEnforcer.class.getDeclaredMethods()).map(Method::getName).collect(Collectors.toSet()))
                .doesNotContain("checkPermissionWithContext", "checkSuperUserPermissionWithContext", "denyUserAccess");
    }
}
