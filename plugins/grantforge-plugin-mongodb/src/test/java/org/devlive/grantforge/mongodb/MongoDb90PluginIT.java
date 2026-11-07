// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.mongodb;

/** The MongoDB plugin against MongoDB 9.0.4. */
class MongoDb90PluginIT
        extends MongoDbPluginContract
{
    @Override
    String version()
    {
        return "9.0.4";
    }
}
