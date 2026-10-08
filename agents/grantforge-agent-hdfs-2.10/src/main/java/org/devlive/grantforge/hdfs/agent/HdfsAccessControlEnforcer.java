// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hdfs.agent;

import org.apache.hadoop.hdfs.server.namenode.INodeAttributeProvider.AccessControlEnforcer;
import org.devlive.grantforge.hdfs.common.HdfsAuthorizer;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

/** Native permission bridge for Hadoop 2.10, whose SPI does not expose context or superuser callbacks. */
final class HdfsAccessControlEnforcer
        extends HdfsNativeEnforcer
{
    HdfsAccessControlEnforcer(@Nullable AccessControlEnforcer nativeEnforcer, Supplier<HdfsAuthorizer> authorizers)
    {
        super(nativeEnforcer, authorizers);
    }
}
