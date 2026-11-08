// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.mongodb.agent;

/** The audit log of Percona Server for MongoDB 7.0.45. */
class Percona70AuditFileIT
        extends MongoDbAuditFileContract
{
    @Override
    String version()
    {
        return "7.0.45";
    }
}
