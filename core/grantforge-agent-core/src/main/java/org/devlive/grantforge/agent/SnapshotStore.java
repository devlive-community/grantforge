// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.agent;

import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

/**
 * Keeps the last good snapshot on disk with its signature and the key that made it, so an agent that starts while the
 * server cannot be reached still decides with the policies it had. The signature is checked again when the snapshot
 * is read back, so a file changed on disk is not used.
 */
final class SnapshotStore
{
    static final String BODY = "snapshot.json";
    static final String META = "snapshot.properties";

    private final Path directory;

    SnapshotStore(Path directory)
    {
        this.directory = directory;
    }

    /**
     * Stores a snapshot, replacing the one stored.
     *
     * @param snapshot the snapshot as downloaded
     * @param key the key whose signature was checked
     * @throws IOException if it cannot be written
     */
    void save(ServerClient.Download snapshot, SigningKey key) throws IOException
    {
        Files.createDirectories(directory);
        Properties meta = new Properties();
        meta.setProperty("etag", snapshot.etag());
        meta.setProperty("keyId", snapshot.keyId());
        meta.setProperty("signature", snapshot.signature());
        meta.setProperty("signingKey", key.encoded());
        Path body = Files.write(directory.resolve(BODY + ".tmp"), snapshot.body());
        Path properties = directory.resolve(META + ".tmp");
        try (OutputStream out = Files.newOutputStream(properties)) {
            meta.store(out, "The policy snapshot in " + BODY);
        }
        move(body, directory.resolve(BODY));
        move(properties, directory.resolve(META));
    }

    /**
     * Reads the stored snapshot back if its signature still holds.
     *
     * @param trusted the key the agent is pinned to, or {@code null} to accept the stored key
     * @return the snapshot and its key, or {@code null} if none is stored, or it was changed or signed by another key
     * @throws IOException if the files exist but cannot be read
     */
    @Nullable Stored load(@Nullable SigningKey trusted) throws IOException
    {
        byte[] body;
        Properties meta = new Properties();
        try {
            body = Files.readAllBytes(directory.resolve(BODY));
            try (InputStream in = Files.newInputStream(directory.resolve(META))) {
                meta.load(in);
            }
        }
        catch (NoSuchFileException missing) {
            return null;
        }
        String etag = meta.getProperty("etag");
        String keyId = meta.getProperty("keyId");
        String signature = meta.getProperty("signature");
        String encoded = meta.getProperty("signingKey");
        if (etag == null || keyId == null || signature == null || encoded == null) {
            return null;
        }
        SigningKey key;
        try {
            key = SigningKey.of(encoded);
        }
        catch (IllegalArgumentException broken) {
            return null;
        }
        if (trusted != null && !trusted.keyId().equals(key.keyId()) || !key.keyId().equals(keyId) || !key.verifies(body, signature)) {
            return null;
        }
        return new Stored(new ServerClient.Download(body, etag, keyId, signature), key);
    }

    private static void move(Path from, Path to) throws IOException
    {
        try {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        }
        catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** A stored snapshot with the key that signed it. */
    static final class Stored
    {
        private final ServerClient.Download snapshot;
        private final SigningKey key;

        Stored(ServerClient.Download snapshot, SigningKey key)
        {
            this.snapshot = snapshot;
            this.key = key;
        }

        ServerClient.Download snapshot()
        {
            return snapshot;
        }

        SigningKey key()
        {
            return key;
        }
    }
}
