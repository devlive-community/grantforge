// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.agent;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Runs scheduled jobs, such as purging access events past their retention period. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class SchedulingConfiguration
{
}
