// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.redis.agent;

import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
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

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.List;

/**
 * The TLS of a client of Redis, from the PEM files {@code redis-cli} takes as {@code --cacert}, {@code --cert} and
 * {@code --key}: the CA certificates that verify the servers, and a client certificate with its private key, in one file
 * or two, the key PKCS #8 or the older OpenSSL form, encrypted or not. Bouncy Castle reads the files.
 */
final class Tls
{
    /** The in-memory key store's password; the key never leaves the process. */
    private static final char[] IN_MEMORY = new char[0];
    /**
     * Bouncy Castle's own ciphers decrypt keys, as the JDK's lack the padding Bouncy Castle names; the provider is used
     * directly, not installed.
     */
    private static final Provider CIPHERS = new BouncyCastleProvider();

    private Tls()
    {
    }

    /** What a PEM file holds. */
    private record Pem(List<Certificate> certificates, @Nullable PrivateKey key)
    {
    }

    /**
     * Builds the TLS context of a client.
     *
     * @param authorities the PEM file of the CAs that verify the servers, or {@code null} for the JVM's
     * @param certificate the PEM file of the client certificate, which may hold its key too, or {@code null} without one
     * @param key the PEM file of the client certificate's key, or {@code null} when the certificate's file holds it
     * @param password the password of an encrypted key, or {@code null}
     * @return the context
     * @throws IOException when a file cannot be read
     * @throws GeneralSecurityException when a file holds no certificate or key that can be used
     */
    static SSLContext context(@Nullable Path authorities, @Nullable Path certificate, @Nullable Path key, @Nullable String password)
            throws IOException, GeneralSecurityException
    {
        TrustManagerFactory trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trust.init(authorities == null ? null : trustStore(authorities));
        KeyManagerFactory keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keys.init(certificate == null ? null : keyStore(certificate, key == null ? certificate : key, password), IN_MEMORY);
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(certificate == null ? null : keys.getKeyManagers(), trust.getTrustManagers(), null);
        return context;
    }

    /**
     * Reads the certificates of a PEM file into a trust store.
     *
     * @param file the file
     * @return the trust store
     * @throws IOException when it cannot be read
     * @throws GeneralSecurityException when it holds no certificate
     */
    static KeyStore trustStore(Path file) throws IOException, GeneralSecurityException
    {
        List<Certificate> certificates = read(file, null, false).certificates();
        if (certificates.isEmpty()) {
            throw new GeneralSecurityException(file + " holds no PEM certificate");
        }
        KeyStore store = KeyStore.getInstance("PKCS12");
        store.load(null, null);
        int index = 0;
        for (Certificate authority : certificates) {
            store.setCertificateEntry("ca-" + index, authority);
            index++;
        }
        return store;
    }

    /**
     * Reads a client certificate and its key into a key store.
     *
     * @param certificateFile the PEM file of the certificate and its chain
     * @param keyFile the PEM file of the key, which may be the certificate's
     * @param password the password of an encrypted key, or {@code null}
     * @return the key store
     * @throws IOException when a file cannot be read
     * @throws GeneralSecurityException when they lack the certificate or the key
     */
    static KeyStore keyStore(Path certificateFile, Path keyFile, @Nullable String password) throws IOException, GeneralSecurityException
    {
        Pem pem = read(certificateFile, null, false);
        PrivateKey key = read(keyFile, password, true).key();
        if (key == null) {
            throw new GeneralSecurityException(keyFile + " holds no PEM private key");
        }
        if (pem.certificates().isEmpty()) {
            throw new GeneralSecurityException(certificateFile + " holds no PEM certificate");
        }
        KeyStore store = KeyStore.getInstance("PKCS12");
        store.load(null, null);
        store.setKeyEntry("client", key, IN_MEMORY, pem.certificates().toArray(new Certificate[0]));
        return store;
    }

    private static Pem read(Path file, @Nullable String password, boolean withKey) throws IOException, GeneralSecurityException
    {
        JcaPEMKeyConverter converter = new JcaPEMKeyConverter();
        JcaX509CertificateConverter certificates = new JcaX509CertificateConverter();
        List<Certificate> found = new ArrayList<>();
        PrivateKey key = null;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.US_ASCII); PEMParser pem = new PEMParser(reader)) {
            for (Object read = pem.readObject(); read != null; read = pem.readObject()) {
                if (read instanceof X509CertificateHolder certificate) {
                    found.add(certificates.getCertificate(certificate));
                }
                else if (withKey && key == null) {
                    key = key(file, read, password, converter);
                }
            }
        }
        catch (PEMException | PKCSException | OperatorCreationException unreadable) {
            throw new GeneralSecurityException(file + " holds no private key that can be read with the password given: "
                    + unreadable.getMessage(), unreadable);
        }
        return new Pem(found, key);
    }

    /** The key a PEM object is; parameters before a key, such as an EC curve's, are none. */
    private static @Nullable PrivateKey key(Path file, Object read, @Nullable String password, JcaPEMKeyConverter converter)
            throws IOException, GeneralSecurityException, PKCSException, OperatorCreationException
    {
        if (read instanceof PrivateKeyInfo info) {
            return converter.getPrivateKey(info);
        }
        if (read instanceof PEMKeyPair pair) {
            return converter.getKeyPair(pair).getPrivate();
        }
        if (read instanceof PKCS8EncryptedPrivateKeyInfo encrypted) {
            return converter.getPrivateKey(encrypted.decryptPrivateKeyInfo(new JceOpenSSLPKCS8DecryptorProviderBuilder().setProvider(CIPHERS)
                    .build(password(file, password))));
        }
        if (read instanceof PEMEncryptedKeyPair encrypted) {
            return converter.getKeyPair(encrypted.decryptKeyPair(new JcePEMDecryptorProviderBuilder().setProvider(CIPHERS)
                    .build(password(file, password)))).getPrivate();
        }
        return null;
    }

    private static char[] password(Path file, @Nullable String password) throws GeneralSecurityException
    {
        if (password == null || password.isEmpty()) {
            throw new GeneralSecurityException(file + " holds an encrypted private key; give its password");
        }
        return password.toCharArray();
    }
}
