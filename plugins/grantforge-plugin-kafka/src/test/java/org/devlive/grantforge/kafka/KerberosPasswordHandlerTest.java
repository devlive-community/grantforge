// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.kafka;

import org.junit.jupiter.api.Test;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.ChoiceCallback;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.TextOutputCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.login.AppConfigurationEntry;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KerberosPasswordHandlerTest
{
    private static AppConfigurationEntry entry(Map<String, ?> options)
    {
        return new AppConfigurationEntry("com.sun.security.auth.module.Krb5LoginModule", AppConfigurationEntry.LoginModuleControlFlag.REQUIRED,
                options);
    }

    @Test
    void answersWithThePrincipalAndPasswordOfTheEntry() throws UnsupportedCallbackException
    {
        KerberosPasswordHandler handler = new KerberosPasswordHandler();
        handler.configure(Map.of(), "GSSAPI", List.of(entry(Map.of("principal", "grantforge@EXAMPLE.COM", KafkaSettings.PASSWORD_OPTION,
                "s3cret"))));
        NameCallback name = new NameCallback("name");
        PasswordCallback password = new PasswordCallback("password", false);
        handler.handle(new Callback[] {name, password, new TextOutputCallback(TextOutputCallback.INFORMATION, "hello")});
        assertThat(name.getName()).isEqualTo("grantforge@EXAMPLE.COM");
        assertThat(password.getPassword()).containsExactly('s', '3', 'c', 'r', 'e', 't');
        handler.close();
        assertThatThrownBy(() -> handler.handle(new Callback[] {new ChoiceCallback("pick", new String[] {"a"}, 0, false)}))
                .isInstanceOf(UnsupportedCallbackException.class);
    }

    @Test
    void refusesAPasswordPromptWithoutAPassword()
    {
        KerberosPasswordHandler handler = new KerberosPasswordHandler();
        handler.configure(Map.of(), "GSSAPI", List.of(entry(Map.of("useKeyTab", "true"))));
        assertThatThrownBy(() -> handler.handle(new Callback[] {new PasswordCallback("password", false)}))
                .isInstanceOf(UnsupportedCallbackException.class).hasMessageContaining("no Kerberos password");
        handler.close();
    }
}
