// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hdfs.agent;

import org.apache.hadoop.fs.permission.FsAction;
import org.jspecify.annotations.Nullable;

import java.util.Set;

/** Infers the path access omitted from Hadoop 3.5.0's superuser authorization callback. */
final class HdfsOperationAccess
{
    private static final Set<String> READS = Set.of("open", "getfileinfo", "isFileClosed", "getAclStatus", "getXAttrs", "listXAttrs",
            "getStoragePolicy", "getPreferredBlockSize", "quotaUsage", "getEZForPath", "getErasureCodingPolicy");
    private static final Set<String> ENUMERATIONS = Set.of("listStatus", "listOpenFiles", "contentSummary", "computeSnapshotDiff",
            "ListSnapshot", "listCorruptFileBlocks", "listEncryptionZones", "listReencryptionStatus");
    private static final Set<String> WRITES = Set.of("create", "append", "truncate", "delete", "rename", "mkdirs", "createSymlink",
            "recoverLease", "getAdditionalBlock", "getAdditionalDatanode", "abandonBlock", "completeFile", "fsync", "setPermission",
            "setOwner", "setTimes", "setReplication", "setStoragePolicy", "unsetStoragePolicy", "satisfyStoragePolicy", "modifyAclEntries",
            "removeAclEntries", "removeDefaultAcl", "removeAcl", "setAcl", "setXAttr", "removeXAttr", "setErasureCodingPolicy",
            "unsetErasureCodingPolicy", "allowSnapshot", "disallowSnapshot", "createSnapshot", "renameSnapshot", "deleteSnapshot",
            "createEncryptionZone", "reencryptEncryptionZone", "setQuota", "setSpaceQuota", "clearQuota", "clearSpaceQuota");

    private HdfsOperationAccess()
    {
    }

    /**
     * Uses the exact operation names assigned by Hadoop 3.5.0's {@code FSNamesystem}; these are operation names,
     * not necessarily RPC method names (for example {@code getfileinfo}, {@code contentSummary} and {@code ListSnapshot}).
     * The native superuser gate must pass before this inferred access is applied.
     *
     * @param operation the NameNode's operation name, or {@code null} when it did not supply one
     * @return the inferred access, or all access for operations whose requirements cannot safely be inferred
     */
    static FsAction superuserAccess(@Nullable String operation)
    {
        if (operation == null) {
            return FsAction.ALL;
        }
        // OperationCategory.READ is not sufficient: checkAccess accepts an arbitrary mode, while concat combines
        // source reads and target writes. Likewise, getAdditionalBlock/getAdditionalDatanode are writes despite
        // their names. An explicit allowlist avoids granting a new or ambiguous operation only read access.
        if (READS.contains(operation)) {
            return FsAction.READ;
        }
        if (ENUMERATIONS.contains(operation)) {
            return FsAction.READ_EXECUTE;
        }
        if (WRITES.contains(operation)) {
            return FsAction.WRITE;
        }
        return FsAction.ALL;
    }
}
