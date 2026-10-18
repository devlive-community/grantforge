// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.kafka;

import org.apache.kafka.common.security.auth.AuthenticateCallbackHandler;
import org.jspecify.annotations.Nullable;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.TextOutputCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.login.AppConfigurationEntry;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Answers the prompts of a Kerberos sign-in with a password: Kafka's login asks Kerberos for a ticket through JAAS, and
 * JAAS asks this handler for the principal and its password, which the service's JAAS entry carries in an option of its
 * own. Kafka creates it by its class, so it is public with a public constructor.
 */
public final class KerberosPasswordHandler
        implements AuthenticateCallbackHandler
{
    private @Nullable String principal;
    private char @Nullable [] password;

    /** Creates a handler; Kafka configures it before the sign-in. */
    public KerberosPasswordHandler()
    {
        // Configured by configure().
    }

    @Override
    public void configure(Map<String, ?> configs, String saslMechanism, List<AppConfigurationEntry> jaasConfigEntries)
    {
        for (AppConfigurationEntry entry : jaasConfigEntries) {
            Object name = entry.getOptions().get("principal");
            Object secret = entry.getOptions().get(KafkaSettings.PASSWORD_OPTION);
            if (name != null) {
                principal = name.toString();
            }
            if (secret != null) {
                password = secret.toString().toCharArray();
            }
        }
    }

    @Override
    public void handle(Callback[] callbacks) throws UnsupportedCallbackException
    {
        for (Callback callback : callbacks) {
            if (callback instanceof NameCallback name) {
                name.setName(principal);
            }
            else if (callback instanceof PasswordCallback secret) {
                char[] value = password;
                if (value == null) {
                    throw new UnsupportedCallbackException(callback, "the service has no Kerberos password");
                }
                secret.setPassword(value);
            }
            else if (!(callback instanceof TextOutputCallback)) {
                throw new UnsupportedCallbackException(callback);
            }
        }
    }

    @Override
    public void close()
    {
        char[] value = password;
        if (value != null) {
            Arrays.fill(value, '\0');
        }
    }
}
