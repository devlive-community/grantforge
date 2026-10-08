// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hdfs.agent;

import org.apache.hadoop.hdfs.server.namenode.INodeAttributeProvider.AccessControlEnforcer;
import org.devlive.grantforge.hdfs.common.HdfsAgentRuntime;
import org.devlive.grantforge.hdfs.common.HdfsAuthorizer;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

/** Selects the Hadoop 2.7 callback bridge while reusing the binary native module's lifecycle and policy runtime. */
public final class HdfsAuthorizationProvider
        extends HdfsNativeAuthorizationProvider
{
    /** Creates the versioned provider; Hadoop injects its NameNode configuration before starting it. */
    public HdfsAuthorizationProvider()
    {
        this(HdfsAgentRuntime::new);
    }

    HdfsAuthorizationProvider(Supplier<HdfsAgentRuntime> runtimes)
    {
        // Read this adapter's filtered metadata; the shared native library has no version-specific resource or singleton.
        super(HdfsAgentCompatibility.load(HdfsAuthorizationProvider.class.getResourceAsStream(HdfsAgentCompatibility.RESOURCE)), runtimes);
    }

    @Override
    protected AccessControlEnforcer createEnforcer(@Nullable AccessControlEnforcer defaultEnforcer, Supplier<HdfsAuthorizer> authorizers)
    {
        return new HdfsAccessControlEnforcer(defaultEnforcer, authorizers);
    }
}
