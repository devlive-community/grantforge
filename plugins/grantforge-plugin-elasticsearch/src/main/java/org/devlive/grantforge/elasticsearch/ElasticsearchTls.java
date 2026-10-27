// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.elasticsearch;

import org.apache.hc.client5.http.ssl.DefaultHostnameVerifier;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.TrustAllStrategy;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMEncryptedKeyPair;
import org.bouncycastle.openssl.PEMException;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JceOpenSSLPKCS8DecryptorProviderBuilder;
import org.bouncycastle.openssl.jcajce.JcePEMDecryptorProviderBuilder;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.pkcs.PKCS8EncryptedPrivateKeyInfo;
import org.bouncycastle.pkcs.PKCSException;
import org.jspecify.annotations.Nullable;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.Collection;

/**
 * The TLS of a client of Elasticsearch, from the PEM files Elasticsearch's own tools write: the CA certificates that verify
 * the nodes, and a client certificate with its private key, which may be PKCS #8 or the older OpenSSL form, encrypted
 * or not. The JDK reads the certificates and Bouncy Castle the keys.
 *
 * @param context the TLS context
 * @param verifier how a node's name is checked against its certificate
 */
record ElasticsearchTls(SSLContext context, HostnameVerifier verifier)
{
    /** The in-memory key store's password; the key never leaves the process. */
    private static final char[] IN_MEMORY = new char[0];
    /**
     * Bouncy Castle's own ciphers decrypt keys, as the JDK's lack the padding Bouncy Castle names; the provider is used
     * directly, not installed.
     */
    private static final Provider CIPHERS = new BouncyCastleProvider();

    /**
     * Builds the TLS of a client.
     *
     * @param mode full checks the nodes' certificates and names, certificate their certificates only, none nothing
     * @param authorities the PEM file of the CAs that verify the nodes, or {@code null} for the JVM's
     * @param certificate the PEM file of the client certificate and its chain, or {@code null} without one
     * @param key the client certificate's PEM private key, or {@code null} without one
     * @param passphrase the passphrase of an encrypted key, or {@code null}
     * @return the TLS
     * @throws IOException when a file cannot be read
     * @throws GeneralSecurityException when a file holds no certificate or key that can be used
     */
    static ElasticsearchTls of(String mode, @Nullable Path authorities, @Nullable Path certificate, @Nullable Path key, @Nullable String passphrase)
            throws IOException, GeneralSecurityException
    {
        SSLContextBuilder builder = SSLContextBuilder.create();
        if (ElasticsearchSettings.NONE.equals(mode)) {
            builder.loadTrustMaterial(TrustAllStrategy.INSTANCE);
        }
        else if (authorities != null) {
            builder.loadTrustMaterial(trustStore(authorities), null);
        }
        if (certificate != null && key != null) {
            KeyStore keys = KeyStore.getInstance("PKCS12");
            keys.load(null, null);
            keys.setKeyEntry("client", privateKey(key, passphrase), IN_MEMORY, certificates(certificate).toArray(new Certificate[0]));
            builder.loadKeyMaterial(keys, IN_MEMORY);
        }
        return new ElasticsearchTls(builder.build(), ElasticsearchSettings.FULL.equals(mode) ? new DefaultHostnameVerifier() : NoopHostnameVerifier.INSTANCE);
    }

    /**
     * Reads the CA certificates of a PEM file into a trust store.
     *
     * @param file the file
     * @return the trust store
     * @throws IOException when it cannot be read
     * @throws GeneralSecurityException when it holds no certificate
     */
    static KeyStore trustStore(Path file) throws IOException, GeneralSecurityException
    {
        KeyStore store = KeyStore.getInstance("PKCS12");
        store.load(null, null);
        int index = 0;
        for (Certificate authority : certificates(file)) {
            store.setCertificateEntry("ca-" + index, authority);
            index++;
        }
        return store;
    }

    /**
     * Reads the certificates of a PEM file.
     *
     * @param file the file
     * @return its certificates, in their order
     * @throws IOException when it cannot be read
     * @throws GeneralSecurityException when it holds none
     */
    static Collection<? extends Certificate> certificates(Path file) throws IOException, GeneralSecurityException
    {
        Collection<? extends Certificate> certificates;
        try (InputStream in = Files.newInputStream(file)) {
            certificates = CertificateFactory.getInstance("X.509").generateCertificates(in);
        }
        if (certificates.isEmpty()) {
            throw new GeneralSecurityException(file + " holds no PEM certificate");
        }
        return certificates;
    }

    /**
     * Reads the private key of a PEM file: PKCS #8, encrypted PKCS #8, or the OpenSSL form of an RSA, EC or DSA key,
     * encrypted or not. Parameters before the key, such as an EC curve's, are skipped.
     *
     * @param file the file
     * @param passphrase the passphrase of an encrypted key, or {@code null}
     * @return the key
     * @throws IOException when it cannot be read
     * @throws GeneralSecurityException when it holds no key, or an encrypted one without the right passphrase
     */
    static PrivateKey privateKey(Path file, @Nullable String passphrase) throws IOException, GeneralSecurityException
    {
        JcaPEMKeyConverter converter = new JcaPEMKeyConverter();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.US_ASCII); PEMParser pem = new PEMParser(reader)) {
            for (Object read = pem.readObject(); read != null; read = pem.readObject()) {
                if (read instanceof PrivateKeyInfo info) {
                    return converter.getPrivateKey(info);
                }
                if (read instanceof PEMKeyPair pair) {
                    return converter.getKeyPair(pair).getPrivate();
                }
                if (read instanceof PKCS8EncryptedPrivateKeyInfo encrypted) {
                    return converter.getPrivateKey(decrypted(passphrase(file, passphrase), encrypted));
                }
                if (read instanceof PEMEncryptedKeyPair encrypted) {
                    return converter.getKeyPair(decrypted(passphrase(file, passphrase), encrypted)).getPrivate();
                }
            }
        }
        catch (PEMException | PKCSException | OperatorCreationException unreadable) {
            throw new GeneralSecurityException(file + " holds no private key that can be read with the passphrase given: "
                    + unreadable.getMessage(), unreadable);
        }
        throw new GeneralSecurityException(file + " holds no PEM private key");
    }

    private static PrivateKeyInfo decrypted(char[] passphrase, PKCS8EncryptedPrivateKeyInfo encrypted) throws PKCSException, OperatorCreationException
    {
        return encrypted.decryptPrivateKeyInfo(new JceOpenSSLPKCS8DecryptorProviderBuilder().setProvider(CIPHERS).build(passphrase));
    }

    private static PEMKeyPair decrypted(char[] passphrase, PEMEncryptedKeyPair encrypted) throws IOException
    {
        return encrypted.decryptKeyPair(new JcePEMDecryptorProviderBuilder().setProvider(CIPHERS).build(passphrase));
    }

    private static char[] passphrase(Path file, @Nullable String passphrase) throws GeneralSecurityException
    {
        if (passphrase == null || passphrase.isEmpty()) {
            throw new GeneralSecurityException(file + " holds an encrypted private key; give its passphrase");
        }
        return passphrase.toCharArray();
    }
}
