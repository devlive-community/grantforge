// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

/**
 * The GrantForge agent for Kafka: an authorizer of Kafka's Authorizer API that checks requests against the service's
 * signed policies, answers whether a user may act on any resource of a type, shows the policies as read-only ACLs and
 * reports access events, repeated ones folded into one.
 */
@NullMarked
package org.devlive.grantforge.kafka.agent;

import org.jspecify.annotations.NullMarked;
