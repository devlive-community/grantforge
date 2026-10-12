// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.zookeeper;

import org.devlive.grantforge.plugin.api.LookupException;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class ZooKeeperLoginExceptionTest
{
    @Test
    void reportsThePrincipalAsAnAuthenticationFailureUsingOnlyTheFirstLine()
    {
        ZooKeeperLoginException refused = new ZooKeeperLoginException(
                "the ensemble refused the sign-in of grantforge@EXAMPLE.COM\ncheck the principal and its keytab");

        LookupException failure = ZooKeeperProvider.failure(refused);

        assertThat(failure.getReason()).isEqualTo(LookupException.Reason.AUTHENTICATION_FAILED);
        assertThat(failure)
                .hasMessage("the ensemble refused the sign-in of grantforge@EXAMPLE.COM")
                .hasCause(refused);
        assertThat(refused)
                .hasMessage("the ensemble refused the sign-in of grantforge@EXAMPLE.COM\ncheck the principal and its keytab");
    }

    @Test
    void recognizesAWrappedLoginFailureAndPreservesItsKeytabContext()
    {
        ZooKeeperLoginException unreadable = new ZooKeeperLoginException(
                "the keytab /etc/security/grantforge.keytab cannot be read by the GrantForge server");
        IOException wrapped = new IOException("cannot sign in to zk1:2181\nconnection details", unreadable);

        LookupException failure = ZooKeeperProvider.failure(wrapped);

        assertThat(failure.getReason()).isEqualTo(LookupException.Reason.AUTHENTICATION_FAILED);
        assertThat(failure).hasMessage("cannot sign in to zk1:2181").hasCause(wrapped);
        assertThat(wrapped).hasCause(unreadable);
        assertThat(unreadable)
                .hasMessage("the keytab /etc/security/grantforge.keytab cannot be read by the GrantForge server");
    }
}
