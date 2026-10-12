// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hdfs;

import org.apache.hadoop.security.authentication.util.KerberosName;
import org.devlive.grantforge.plugin.api.KerberosRealms;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The short-name rules of the plugin API's Kerberos realms, as Hadoop reads them. */
class KerberosRealmsTest
{
    @Test
    void namesTheRealmsUsersAndServicesByTheirShortNames() throws Exception
    {
        KerberosName.setRules(KerberosRealms.shortNames("BETA.EXAMPLE"));
        assertThat(new KerberosName("lookup@BETA.EXAMPLE").getShortName()).isEqualTo("lookup");
        assertThat(new KerberosName("nn/namenode@BETA.EXAMPLE").getShortName()).isEqualTo("nn");
        // Dots are literal: another realm is not mistaken for it.
        assertThatThrownBy(() -> new KerberosName("lookup@BETAXEXAMPLE").getShortName()).isInstanceOf(KerberosName.NoMatchingRule.class);
    }
}
