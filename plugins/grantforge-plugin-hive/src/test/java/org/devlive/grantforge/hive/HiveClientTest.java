// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hive;

import org.devlive.grantforge.plugin.api.ServiceConfig;
import org.junit.jupiter.api.Test;

import javax.security.auth.Subject;
import javax.security.auth.login.LoginException;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class HiveClientTest
{
    private static ServiceConfig config(String... values)
    {
        Map<String, String> map = new HashMap<>();
        map.put(HiveProvider.URL, "jdbc:hive2://hs2:10000/default");
        map.put(HiveProvider.USER, "lookup");
        for (int index = 0; index < values.length; index += 2) {
            map.put(values[index], values[index + 1]);
        }
        return new ServiceConfig("warehouse", map);
    }

    private static HiveClient.Login refusing()
    {
        return (principal, keytab, password, kdcs) -> {
            throw new AssertionError("no Kerberos sign-in expected");
        };
    }

    @Test
    void connectsWithThePasswordAndClosesTheConnection() throws Exception
    {
        Connection connection = mock(Connection.class);
        AtomicReference<String> url = new AtomicReference<>();
        AtomicReference<Properties> properties = new AtomicReference<>();
        HiveClient client = new HiveClient(config(HiveProvider.PASSWORD, "secret", HiveProvider.READ_TIMEOUT, "4000"), (given, props) -> {
            url.set(given);
            properties.set(props);
            return connection;
        }, refusing());

        String result = client.run(open -> open == connection ? "done" : "wrong");
        assertThat(result).isEqualTo("done");
        assertThat(url.get()).isEqualTo("jdbc:hive2://hs2:10000/default;socketTimeout=4000");
        assertThat(properties.get()).containsEntry("user", "lookup").containsEntry("password", "secret");
        verify(connection).close();
        assertThat(new HiveClient(config(), (given, props) -> connection, refusing()).properties()).doesNotContainKey("password");
    }

    @Test
    void connectsAsTheKerberosSubject() throws Exception
    {
        Subject signedIn = new Subject();
        List<Object> seen = new java.util.ArrayList<>();
        HiveClient.Login login = (principal, keytab, password, kdcs) -> {
            seen.add(principal);
            seen.add(keytab);
            seen.add(kdcs);
            return signedIn;
        };
        HiveClient client = new HiveClient(config(HiveProvider.AUTHENTICATION, " Kerberos ", HiveProvider.USER, "lookup@EXAMPLE.COM",
                HiveProvider.URL, "jdbc:hive2://hs2:10000/default;principal=hive/_HOST@EXAMPLE.COM", HiveProvider.KEYTAB, " /etc/lookup.keytab ",
                HiveProvider.KDC, "kdc:88"), (given, props) -> {
                    assertThat(Subjects.current()).isSameAs(signedIn);
                    assertThat(props).isEmpty();
                    assertThat(given).contains(";kerberosAuthType=fromSubject");
                    return mock(Connection.class);
                }, login);
        Integer result = client.run(connection -> 1);
        assertThat(result).isEqualTo(1);
        assertThat(seen).containsExactly("lookup@EXAMPLE.COM", "/etc/lookup.keytab", "kdc:88");
    }

    @Test
    void passesFailuresOnAsTheyCame()
    {
        HiveClient none = new HiveClient(config(), (given, props) -> null, refusing());
        assertThatThrownBy(() -> none.run(connection -> 1)).isInstanceOf(SQLException.class).hasMessageContaining("does not accept");
        HiveClient refused = new HiveClient(config(), (given, props) -> {
            throw new SQLException("refused");
        }, refusing());
        assertThatThrownBy(() -> refused.run(connection -> 1)).isInstanceOf(SQLException.class).hasMessage("refused");
        String kerberos = "jdbc:hive2://hs2:10000/default;principal=hive/_HOST@EXAMPLE.COM";
        HiveClient kdcRefuses = new HiveClient(config(HiveProvider.AUTHENTICATION, "kerberos", HiveProvider.URL, kerberos),
                (given, props) -> mock(Connection.class), (principal, keytab, password, kdcs) -> {
                    throw new LoginException("refused");
                });
        assertThatThrownBy(() -> kdcRefuses.run(connection -> 1)).isInstanceOf(LoginException.class);
        HiveClient workFails = new HiveClient(config(HiveProvider.AUTHENTICATION, "kerberos", HiveProvider.URL, kerberos),
                (given, props) -> mock(Connection.class), (principal, keytab, password, kdcs) -> new Subject());
        assertThatThrownBy(() -> workFails.run(connection -> {
            throw new SQLException("no metastore");
        })).isInstanceOf(SQLException.class).hasMessage("no metastore");
        HiveClient keytabMissing = new HiveClient(config(HiveProvider.AUTHENTICATION, "kerberos", HiveProvider.URL, kerberos),
                (given, props) -> mock(Connection.class), (principal, keytab, password, kdcs) -> {
                    throw new IOException("unreadable");
                });
        assertThatThrownBy(() -> keytabMissing.run(connection -> 1)).isInstanceOf(IOException.class);
    }

    @Test
    void keepsTheReadTimeoutWithinTenMinutes()
    {
        assertThat(HiveClient.timeout(config())).isEqualTo(30_000);
        assertThatIllegalArgumentException().isThrownBy(() -> HiveClient.timeout(config(HiveProvider.READ_TIMEOUT, "0")));
        assertThatIllegalArgumentException().isThrownBy(() -> HiveClient.timeout(config(HiveProvider.READ_TIMEOUT, "600001")));
        assertThat(new HiveClient(config()).url()).endsWith(";socketTimeout=30000");
    }
}
