// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hdfs;

import org.devlive.grantforge.plugin.api.LookupException;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class HdfsLoginExceptionTest
{
    @Test
    void keepsTheSignInFailureAndIsNamedAsAuthentication()
    {
        IOException refused = new IOException("Kerberos refused grantforge@EXAMPLE.COM: pre-authentication failed");
        HdfsLoginException failure = new HdfsLoginException(String.valueOf(refused.getMessage()), refused);

        assertThat(failure).hasMessageContaining("pre-authentication failed").hasCause(refused);
        assertThat(HdfsProvider.failure(failure).getReason()).isEqualTo(LookupException.Reason.AUTHENTICATION_FAILED);
    }
}
