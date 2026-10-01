// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity;

import org.devlive.grantforge.identity.application.RecordingSessionTerminator;
import org.devlive.grantforge.identity.application.SessionTerminator;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * Minimal Spring Boot configuration so slice tests can bootstrap this module; entities and repositories of the
 * audit trail it writes are found too.
 */
@SpringBootApplication(scanBasePackages = {"org.devlive.grantforge.identity", "org.devlive.grantforge.audit"})
@AutoConfigurationPackage(basePackages = {"org.devlive.grantforge.identity", "org.devlive.grantforge.audit"})
class TestIdentityApplication
{
    /**
     * Stands in for the server's Spring Session terminator.
     *
     * @return a terminator that only records what it ended
     */
    @Bean
    SessionTerminator sessionTerminator()
    {
        return new RecordingSessionTerminator();
    }
}
