// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hive;

import org.devlive.grantforge.plugin.api.KerberosRealms;
import org.jspecify.annotations.Nullable;

import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.login.AppConfigurationEntry;
import javax.security.auth.login.Configuration;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static java.util.Objects.requireNonNull;

/**
 * Signs a lookup principal in to Kerberos with the JDK's own login module, from a keytab or with a password, and
 * returns the subject that holds its ticket. The JAAS configuration is the sign-in's own, so the process's JAAS
 * settings, which belong to others, are neither read nor changed. A service that names its KDCs has them declared
 * through the plugin API's {@link KerberosRealms}, shared by every plugin.
 */
final class KerberosLogin
{
    private static final String MODULE = "com.sun.security.auth.module.Krb5LoginModule";

    private KerberosLogin()
    {
    }

    /**
     * Signs in.
     *
     * @param principal such as {@code grantforge@EXAMPLE.COM}
     * @param keytab the keytab on the server, or {@code null} to use the password
     * @param password the password, used without a keytab
     * @param kdcs the realm's KDCs as the service names them, or {@code null} for the server's krb5.conf
     * @return the subject with the ticket
     * @throws LoginException when the KDC refuses the principal, or the keytab or password does not fit
     * @throws IOException when the keytab cannot be read or the realm cannot be declared
     */
    static Subject login(String principal, @Nullable String keytab, @Nullable String password, @Nullable String kdcs)
            throws LoginException, IOException
    {
        requireNonNull(principal, "principal");
        if (kdcs != null) {
            KerberosRealms.declare(KerberosRealms.realm(principal), KerberosRealms.kdcs(kdcs));
        }
        Map<String, String> options = new HashMap<>();
        options.put("principal", principal);
        options.put("refreshKrb5Config", "true");
        options.put("storeKey", "true");
        options.put("isInitiator", "true");
        options.put("useTicketCache", "false");
        if (keytab != null) {
            if (!Files.isReadable(Path.of(keytab))) {
                throw new IOException("the keytab " + keytab + " cannot be read by the GrantForge server");
            }
            options.put("useKeyTab", "true");
            options.put("keyTab", keytab);
            options.put("doNotPrompt", "true");
        }
        else if (password == null) {
            throw new LoginException("Kerberos needs a password or a keytab");
        }
        Configuration jaas = new Configuration()
        {
            @Override
            public AppConfigurationEntry[] getAppConfigurationEntry(String name)
            {
                return new AppConfigurationEntry[] {new AppConfigurationEntry(MODULE, AppConfigurationEntry.LoginModuleControlFlag.REQUIRED,
                        options)};
            }
        };
        Subject subject = new Subject();
        LoginContext login = new LoginContext("grantforge-hive", subject, callbacks -> answer(callbacks, principal, password), jaas);
        login.login();
        return subject;
    }

    private static void answer(Callback[] callbacks, String principal, @Nullable String password) throws UnsupportedCallbackException
    {
        for (Callback callback : callbacks) {
            if (callback instanceof NameCallback name) {
                name.setName(principal);
            }
            else if (callback instanceof PasswordCallback secret && password != null) {
                secret.setPassword(password.toCharArray());
            }
            else {
                throw new UnsupportedCallbackException(callback);
            }
        }
    }
}
