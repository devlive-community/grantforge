// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.redis;

/** The Redis plugin against Valkey 8.1.11. */
class Valkey81PluginIT
        extends RedisPluginContract
{
    @Override
    String image()
    {
        return "valkey/valkey:8.1.11";
    }

    @Override
    String release()
    {
        return "Valkey 8.1.11";
    }
}
