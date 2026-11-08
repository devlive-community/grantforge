// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.mongodb;

/** The MongoDB plugin against MongoDB 7.0.45. */
class MongoDb70PluginIT
        extends MongoDbPluginContract
{
    @Override
    String version()
    {
        return "7.0.45";
    }
}
