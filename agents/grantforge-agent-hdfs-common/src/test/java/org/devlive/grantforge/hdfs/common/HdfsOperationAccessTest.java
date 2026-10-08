// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hdfs.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HdfsOperationAccessTest
{
    @Test
    void preservesReadEnumerationAndWriteOperationMasks()
    {
        for (String operation : new String[] {"open", "getfileinfo", "getAclStatus", "getXAttrs", "quotaUsage"}) {
            assertThat(HdfsOperationAccess.superuserAccess(operation)).isEqualTo(4);
        }
        for (String operation : new String[] {"listStatus", "contentSummary", "computeSnapshotDiff", "ListSnapshot"}) {
            assertThat(HdfsOperationAccess.superuserAccess(operation)).isEqualTo(5);
        }
        for (String operation : new String[] {"create", "append", "delete", "rename", "setPermission", "getAdditionalBlock"}) {
            assertThat(HdfsOperationAccess.superuserAccess(operation)).isEqualTo(2);
        }
    }

    @Test
    void ambiguousUnknownAndAbsentLegacyOperationNamesRequireAllPermissions()
    {
        for (String operation : new String[] {"checkAccess", "concat", "NEW_OPERATION", "OPEN", ""}) {
            assertThat(HdfsOperationAccess.superuserAccess(operation)).isEqualTo(7);
        }
        assertThat(HdfsOperationAccess.superuserAccess(null)).isEqualTo(7);
    }
}
