// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.agent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;

import static org.assertj.core.api.Assertions.assertThat;

class SnapshotStoreTest
{
    @TempDir
    Path directory;

    private final KeyPair keys = FakeServer.keyPair();
    private final SigningKey key = SigningKey.of(FakeServer.publicKey(keys));

    private ServerClient.Download download(String json)
    {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        return new ServerClient.Download(body, "\"e1\"", key.keyId(), FakeServer.sign(keys, body));
    }

    @Test
    void keepsTheLastSnapshotWithItsKey() throws IOException
    {
        SnapshotStore store = new SnapshotStore(directory.resolve("nested"));
        assertThat(store.load(null)).isNull();

        store.save(download("{\"v\":1}"), key);
        store.save(download("{\"v\":2}"), key);
        SnapshotStore.Stored stored = store.load(null);

        assertThat(stored).isNotNull();
        assertThat(new String(stored.snapshot().body(), StandardCharsets.UTF_8)).isEqualTo("{\"v\":2}");
        assertThat(stored.snapshot().etag()).isEqualTo("\"e1\"");
        assertThat(stored.key().keyId()).isEqualTo(key.keyId());
        assertThat(store.load(key)).isNotNull();
        assertThat(directory.resolve("nested")).isDirectoryNotContaining("glob:**.tmp");
    }

    @Test
    void ignoresASnapshotThatWasChangedOrSignedByAnotherKey() throws IOException
    {
        SnapshotStore store = new SnapshotStore(directory);
        store.save(download("{\"v\":1}"), key);

        assertThat(store.load(SigningKey.of(FakeServer.publicKey(FakeServer.keyPair())))).isNull();
        Files.write(directory.resolve(SnapshotStore.BODY), "{\"v\":9}".getBytes(StandardCharsets.UTF_8));
        assertThat(store.load(null)).isNull();
    }

    @Test
    void ignoresBrokenOrIncompleteMetadata() throws IOException
    {
        SnapshotStore store = new SnapshotStore(directory);
        store.save(download("{\"v\":1}"), key);
        Path meta = directory.resolve(SnapshotStore.META);
        String original = Files.readString(meta);

        Files.writeString(meta, original.replaceAll("(?m)^signingKey=.*$", "signingKey=AAAA"));
        assertThat(store.load(null)).isNull();
        Files.writeString(meta, original.replaceAll("(?m)^keyId=.*$", "keyId=0000000000000000"));
        assertThat(store.load(null)).isNull();
        Files.writeString(meta, original.replaceAll("(?m)^etag=.*$", ""));
        assertThat(store.load(null)).isNull();
        Files.delete(meta);
        assertThat(store.load(null)).isNull();
    }
}
