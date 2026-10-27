// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.elasticsearch.agent;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PKCS8Generator;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.openssl.jcajce.JcaPKCS8Generator;
import org.bouncycastle.openssl.jcajce.JceOpenSSLPKCS8EncryptorBuilder;
import org.bouncycastle.openssl.jcajce.JcePEMEncryptorBuilder;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.jspecify.annotations.Nullable;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import java.io.IOException;
import java.io.StringWriter;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A test CA with a server certificate for {@code localhost} only, by name, and a client certificate, {@code CN=client},
 * written as PEM files the way Elasticsearch's tools write them: the server's certificate and key for a node, and the
 * client's key in each PEM form a client may be given.
 */
final class TestCertificates
{
    static final String PASSPHRASE = "key-passphrase";

    private static final AtomicLong SERIAL = new AtomicLong(1);

    private final Path directory;
    private final KeyPair caKeys;
    private final X509Certificate ca;
    private final KeyPair serverKeys;
    private final X509Certificate server;
    private final KeyPair clientKeys;
    private final X509Certificate client;

    /**
     * Makes the certificates and writes them.
     *
     * @param directory where the files go
     */
    TestCertificates(Path directory) throws IOException, GeneralSecurityException, OperatorCreationException
    {
        this.directory = directory;
        caKeys = keys();
        ca = certificate("CN=GrantForge test CA", caKeys, null, null, true);
        serverKeys = keys();
        server = certificate("CN=localhost", serverKeys, caKeys, new GeneralNames(new GeneralName(GeneralName.dNSName, "localhost")), false);
        clientKeys = keys();
        client = certificate("CN=client", clientKeys, caKeys, null, false);
        write("ca.pem", ca);
        write("server.pem", server);
        write("server-key.pem", pem(new JcaPKCS8Generator(serverKeys.getPrivate(), null)));
        write("client.pem", client);
        write("empty.pem", "");
        write("client-pkcs8.pem", pem(new JcaPKCS8Generator(clientKeys.getPrivate(), null)));
        write("client-pkcs1.pem", pem(clientKeys));
        write("client-encrypted.pem", pem(new JcaPKCS8Generator(clientKeys.getPrivate(),
                new JceOpenSSLPKCS8EncryptorBuilder(PKCS8Generator.AES_256_CBC).setProvider(new BouncyCastleProvider()).setPassword(PASSPHRASE.toCharArray()).build())));
        StringWriter legacy = new StringWriter();
        try (JcaPEMWriter writer = new JcaPEMWriter(legacy)) {
            writer.writeObject(clientKeys, new JcePEMEncryptorBuilder("AES-256-CBC").setProvider(new BouncyCastleProvider()).build(PASSPHRASE.toCharArray()));
        }
        write("client-legacy-encrypted.pem", legacy.toString());
    }

    private static KeyPair keys() throws GeneralSecurityException
    {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private X509Certificate certificate(String subject, KeyPair keys, @Nullable KeyPair issuer, @Nullable GeneralNames names, boolean authority)
            throws GeneralSecurityException, IOException
    {
        Instant now = Instant.now();
        X500Name issuerName = new X500Name(issuer == null ? subject : "CN=GrantForge test CA");
        X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(issuerName, BigInteger.valueOf(SERIAL.getAndIncrement()),
                Date.from(now.minus(Duration.ofMinutes(5))), Date.from(now.plus(Duration.ofDays(1))), new X500Name(subject), keys.getPublic());
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(authority));
        if (names != null) {
            builder.addExtension(Extension.subjectAlternativeName, false, names);
        }
        try {
            return new JcaX509CertificateConverter().getCertificate(builder.build(new JcaContentSignerBuilder("SHA256withRSA")
                    .build((issuer == null ? keys : issuer).getPrivate())));
        }
        catch (OperatorCreationException broken) {
            throw new GeneralSecurityException(broken);
        }
    }

    private static String pem(Object value) throws IOException
    {
        StringWriter text = new StringWriter();
        try (JcaPEMWriter writer = new JcaPEMWriter(text)) {
            writer.writeObject(value);
        }
        return text.toString();
    }

    private void write(String name, Object value) throws IOException
    {
        Files.writeString(directory.resolve(name), value instanceof String text ? text : pem(value), StandardCharsets.US_ASCII);
    }

    /**
     * The path of one of the files: ca.pem, server.pem and server-key.pem, client.pem, empty.pem, or the client's key as
     * client-pkcs8.pem and the like.
     */
    String file(String name)
    {
        return directory.resolve(name).toString();
    }

    /** The CA certificate. */
    X509Certificate ca()
    {
        return ca;
    }

    /**
     * The TLS of a server whose certificate is for localhost, trusting client certificates of the CA.
     *
     * @return its context
     */
    SSLContext server() throws GeneralSecurityException, IOException
    {
        KeyStore keys = KeyStore.getInstance("PKCS12");
        keys.load(null, null);
        keys.setKeyEntry("server", serverKeys.getPrivate(), new char[0], new Certificate[] {server, ca});
        KeyManagerFactory keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagers.init(keys, new char[0]);
        KeyStore trusted = KeyStore.getInstance("PKCS12");
        trusted.load(null, null);
        trusted.setCertificateEntry("ca", ca);
        TrustManagerFactory trustManagers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagers.init(trusted);
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(keyManagers.getKeyManagers(), trustManagers.getTrustManagers(), null);
        return context;
    }
}
