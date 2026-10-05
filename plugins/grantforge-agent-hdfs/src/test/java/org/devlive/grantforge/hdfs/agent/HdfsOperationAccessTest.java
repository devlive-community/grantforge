// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hdfs.agent;

import org.apache.hadoop.fs.permission.FsAction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class HdfsOperationAccessTest
{
    @ParameterizedTest
    @ValueSource(strings = {"open", "getfileinfo", "getAclStatus", "getStoragePolicy", "getXAttrs", "quotaUsage"})
    void fileAndMetadataReadsDoNotRequireWriteOrExecute(String operation)
    {
        assertThat(HdfsOperationAccess.superuserAccess(operation)).isEqualTo(FsAction.READ);
    }

    @ParameterizedTest
    @ValueSource(strings = {"listStatus", "contentSummary", "computeSnapshotDiff", "ListSnapshot"})
    void directoryEnumerationRequiresReadAndTraversalWithoutWrite(String operation)
    {
        assertThat(HdfsOperationAccess.superuserAccess(operation)).isEqualTo(FsAction.READ_EXECUTE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"create", "append", "delete", "setAcl", "getAdditionalBlock", "getAdditionalDatanode", "setQuota"})
    void writesIncludingBlockAllocationCannotUseAReadGrant(String operation)
    {
        assertThat(HdfsOperationAccess.superuserAccess(operation)).isEqualTo(FsAction.WRITE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "checkAccess", "concat", "getFutureOperation", "getFileInfo", "listSnapshot", "OPEN"})
    void unknownAmbiguousAndIncorrectlyCasedNamesKeepTheConservativeAccess(String operation)
    {
        assertThat(HdfsOperationAccess.superuserAccess(operation)).isEqualTo(FsAction.ALL);
    }

    @Test
    void missingOperationNameKeepsTheConservativeAccess()
    {
        assertThat(HdfsOperationAccess.superuserAccess(null)).isEqualTo(FsAction.ALL);
    }
}
