// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hdfs.common;

import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Infers omitted superuser path actions from known NameNode operation names, conservatively checking all other actions. */
public final class HdfsOperationAccess
{
    private static final Set<String> READS = names("open", "getfileinfo", "isFileClosed", "getAclStatus", "getXAttrs", "listXAttrs",
            "getStoragePolicy", "getPreferredBlockSize", "quotaUsage", "getEZForPath", "getErasureCodingPolicy");
    private static final Set<String> ENUMERATIONS = names("listStatus", "listOpenFiles", "contentSummary", "computeSnapshotDiff",
            "ListSnapshot", "listCorruptFileBlocks", "listEncryptionZones", "listReencryptionStatus");
    private static final Set<String> WRITES = names("create", "append", "truncate", "delete", "rename", "mkdirs", "createSymlink",
            "recoverLease", "getAdditionalBlock", "getAdditionalDatanode", "abandonBlock", "completeFile", "fsync", "setPermission",
            "setOwner", "setTimes", "setReplication", "setStoragePolicy", "unsetStoragePolicy", "satisfyStoragePolicy", "modifyAclEntries",
            "removeAclEntries", "removeDefaultAcl", "removeAcl", "setAcl", "setXAttr", "removeXAttr", "setErasureCodingPolicy",
            "unsetErasureCodingPolicy", "allowSnapshot", "disallowSnapshot", "createSnapshot", "renameSnapshot", "deleteSnapshot",
            "createEncryptionZone", "reencryptEncryptionZone", "setQuota", "setSpaceQuota", "clearQuota", "clearSpaceQuota");

    private HdfsOperationAccess()
    {
    }

    private static Set<String> names(String... values)
    {
        return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(values)));
    }

    /**
     * Maps known operation names to read 4, write 2 or read-and-execute 5; ambiguous or unavailable names require all bits 7.
     *
     * @param operation the NameNode's operation name, or null on SPI versions that omit it
     * @return the inferred native action mask
     */
    public static int superuserAccess(@Nullable String operation)
    {
        if (operation != null) {
            if (READS.contains(operation)) {
                return 4;
            }
            if (ENUMERATIONS.contains(operation)) {
                return 5;
            }
            if (WRITES.contains(operation)) {
                return 2;
            }
        }
        return 7;
    }
}
