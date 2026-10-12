// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hive;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * HiveServer2 JDBC URLs, as Hive's driver reads them: {@code jdbc:hive2://<hosts>/<database>;<session variables>
 * ?<Hive settings>#<Hive variables>}. The hosts may be several, for ZooKeeper discovery; the session variables carry
 * TLS, the HTTP transport and the server's Kerberos principal. Variables that carry credentials, read files or tokens on
 * the GrantForge server, or sign in through a browser are the service's to refuse; the lookup user signs in through
 * the service's own fields.
 */
final class HiveUrl
{
    /** The scheme of HiveServer2 URLs. */
    static final String PREFIX = "jdbc:hive2://";

    private static final Pattern KEY = Pattern.compile("[A-Za-z][A-Za-z0-9_.:-]{0,127}");

    /** Variables a service may not set, compared without case. */
    private static final Set<String> RESERVED = Set.of("user", "password", "initfile", "kerberosauthtype", "delegationtoken",
            "storepasswordpath", "jwt", "browserresponseport", "browserresponsetimeout", "browserdisablesslcheck");

    /** The authentication modes the URL may name; Kerberos and passwords are the service's own fields. */
    private static final Set<String> AUTH = Set.of("nosasl");

    private HiveUrl()
    {
    }

    /**
     * Checks a service's URL.
     *
     * @param value the URL as configured
     * @param kerberos whether the service signs in with Kerberos
     * @return the session variables, in their order
     * @throws IllegalArgumentException when it is no HiveServer2 URL, names no host, or sets a variable it may not
     */
    static Map<String, String> check(String value, boolean kerberos)
    {
        String url = value.strip();
        if (!url.startsWith(PREFIX)) {
            throw new IllegalArgumentException("use jdbc:hive2://host:10000/default, or ZooKeeper discovery such as"
                    + " jdbc:hive2://zk1:2181,zk2:2181/;serviceDiscoveryMode=zooKeeper;zooKeeperNamespace=hiveserver2");
        }
        String rest = url.substring(PREFIX.length());
        int hostsEnd = indexOfAny(rest, "/;?#");
        String hosts = hostsEnd < 0 ? rest : rest.substring(0, hostsEnd);
        if (hosts.isBlank() || hosts.indexOf('@') >= 0) {
            throw new IllegalArgumentException("name the HiveServer2 hosts, without credentials; embedded Hive (jdbc:hive2://) is not supported");
        }
        int settings = indexOfAny(rest, "?#");
        String session = settings < 0 ? rest : rest.substring(0, settings);
        Map<String, String> variables = new LinkedHashMap<>();
        String[] parts = session.split(";", -1);
        for (int index = 1; index < parts.length; index++) {
            String part = parts[index].strip();
            if (part.isEmpty()) {
                continue;
            }
            int equals = part.indexOf('=');
            String key = equals < 0 ? part : part.substring(0, equals).strip();
            if (!KEY.matcher(key).matches()) {
                throw new IllegalArgumentException("the session variable " + key + " is no variable name");
            }
            String lower = key.toLowerCase(Locale.ROOT);
            if (RESERVED.contains(lower) || lower.startsWith("browser")) {
                throw new IllegalArgumentException("the session variable " + key + " cannot be set for a service");
            }
            String setting = equals < 0 ? "" : part.substring(equals + 1).strip();
            if ("auth".equals(lower) && (kerberos || !AUTH.contains(setting.toLowerCase(Locale.ROOT)))) {
                throw new IllegalArgumentException(kerberos ? "a Kerberos service signs in with the principal variable, without auth"
                        : "auth may only be noSasl; passwords and Kerberos are the service's own settings");
            }
            if (variables.keySet().stream().anyMatch(known -> known.equalsIgnoreCase(key))) {
                throw new IllegalArgumentException("the session variable " + key + " is given twice");
            }
            variables.put(key, setting);
        }
        boolean principal = variables.keySet().stream().anyMatch("principal"::equalsIgnoreCase);
        if (kerberos && !principal) {
            throw new IllegalArgumentException("a Kerberos service needs the server's principal in the URL, such as ;principal=hive/_HOST@EXAMPLE.COM");
        }
        if (!kerberos && principal) {
            throw new IllegalArgumentException("the principal variable is for Kerberos; choose kerberos authentication");
        }
        return variables;
    }

    /**
     * Builds the URL the driver is given: the service's, with the variables of the sign-in and the read timeout.
     *
     * @param value the URL as configured, checked
     * @param kerberos whether the sign-in uses Kerberos, which the driver then takes from the calling subject
     * @param readTimeout the read timeout in milliseconds, unless the URL sets one
     * @return the URL
     */
    static String connect(String value, boolean kerberos, int readTimeout)
    {
        String url = value.strip();
        Map<String, String> variables = check(url, kerberos);
        StringBuilder extra = new StringBuilder(64);
        if (kerberos) {
            extra.append(";kerberosAuthType=fromSubject");
        }
        if (variables.keySet().stream().noneMatch("socketTimeout"::equalsIgnoreCase)) {
            extra.append(";socketTimeout=").append(readTimeout);
        }
        int settings = indexOfAny(url.substring(PREFIX.length()), "?#");
        if (settings < 0) {
            return url + extra;
        }
        int at = PREFIX.length() + settings;
        return url.substring(0, at) + extra + url.substring(at);
    }

    private static int indexOfAny(String text, String characters)
    {
        for (int index = 0; index < text.length(); index++) {
            if (characters.indexOf(text.charAt(index)) >= 0) {
                return index;
            }
        }
        return -1;
    }
}
