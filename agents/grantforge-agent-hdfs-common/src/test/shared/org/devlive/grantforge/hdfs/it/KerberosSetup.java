// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hdfs.it;

import java.nio.file.Path;

/**
 * What a Kerberos cluster of {@link HadoopContainers} needs, prepared by the test: a KDC the containers reach on a host
 * port, and a directory with the keytabs and TLS stores. Its files are {@code nn-service.keytab} (nn/namenode),
 * {@code dn-service.keytab} (dn/datanode), {@code http-service.keytab} (HTTP/namenode and HTTP/datanode), one
 * {@code user-<name>.keytab} per user who runs commands, among them {@code nn} as the superuser, and
 * {@code keystore.p12} with {@code truststore.jks} for TLS, both protected with {@link #STORE_PASSWORD}.
 *
 * @param realm the realm
 * @param kdcPort the KDC's TCP port on this host
 * @param security the directory of keytabs and stores
 */
record KerberosSetup(String realm, int kdcPort, Path security)
{
    /** The password of the TLS key store, its key and the truststore. */
    static final String STORE_PASSWORD = "cluster-secret";
}
