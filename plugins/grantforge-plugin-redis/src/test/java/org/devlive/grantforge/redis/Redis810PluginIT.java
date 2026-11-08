// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.redis;

/** The Redis plugin against Redis 8.10.3. */
class Redis810PluginIT
        extends RedisPluginContract
{
    @Override
    String image()
    {
        return "redis:8.10.3";
    }

    @Override
    String release()
    {
        return "Redis 8.10.3";
    }
}
