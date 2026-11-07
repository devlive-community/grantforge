// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.redis;

/** The Redis plugin against Valkey 9.1.3. */
class Valkey91PluginIT
        extends RedisPluginContract
{
    @Override
    String image()
    {
        return "valkey/valkey:9.1.3";
    }

    @Override
    String release()
    {
        return "Valkey 9.1.3";
    }
}
