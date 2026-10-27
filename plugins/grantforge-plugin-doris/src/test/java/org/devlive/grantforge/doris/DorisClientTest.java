// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.doris;

import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DorisClientTest
{
    @Test
    void triesTheFrontendsInTheirOrder()
    {
        DorisClient client = new DorisClient(new DorisSettings(List.of("fe1:9030", "fe2:19030"), "lookup", null, DorisSettings.DISABLE, null, 5000));
        assertThat(client.url()).isEqualTo("jdbc:mariadb:sequential://fe1:9030,fe2:19030/");
    }

    @Test
    void givesTheDriverTheSignInTlsAndTimeouts()
    {
        Properties properties = new DorisClient(new DorisSettings(List.of("fe1:9030"), "lookup", "secret", DorisSettings.VERIFY_FULL, "/ca.pem", 7000))
                .properties();
        assertThat(properties).containsEntry("user", "lookup").containsEntry("password", "secret").containsEntry("sslMode", "verify-full")
                .containsEntry("serverSslCert", "/ca.pem").containsEntry("connectTimeout", "7000").containsEntry("socketTimeout", "7000")
                .containsEntry("allowLocalInfile", "false").containsEntry("allowMultiQueries", "false").containsEntry("retriesAllDown", "1");
        assertThat(new DorisClient(new DorisSettings(List.of("fe1:9030", "fe2:9030"), "lookup", null, DorisSettings.DISABLE, null, 7000))
                .properties()).as("one try of each frontend").containsEntry("retriesAllDown", "2");
    }

    @Test
    void leavesOutWhatIsNotSet()
    {
        Properties properties = new DorisClient(new DorisSettings(List.of("fe1:9030"), "lookup", null, DorisSettings.DISABLE, null, 7000)).properties();
        assertThat(properties).doesNotContainKeys("password", "serverSslCert").containsEntry("sslMode", "disable");
    }

    @Test
    void reportsAFrontendThatCannotBeReached()
    {
        DorisClient client = new DorisClient(new DorisSettings(List.of("127.0.0.1:1"), "lookup", null, DorisSettings.DISABLE, null, 2000));
        assertThatThrownBy(() -> client.run(connection -> 1)).isInstanceOf(SQLException.class);
    }
}
