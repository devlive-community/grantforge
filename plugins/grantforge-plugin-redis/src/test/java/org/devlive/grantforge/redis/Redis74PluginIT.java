// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.redis;

/** The Redis plugin against Redis 7.4.12. */
class Redis74PluginIT
        extends RedisPluginContract
{
    @Override
    String image()
    {
        return "redis:7.4.12";
    }

    @Override
    String release()
    {
        return "Redis 7.4.12";
    }
}
