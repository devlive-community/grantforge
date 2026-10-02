// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service.agent;

/**
 * The public key agents check snapshot signatures with.
 *
 * @param keyId names the key: the first 16 hexadecimal digits of the SHA-256 hash of the encoded public key
 * @param algorithm the signature algorithm, {@code Ed25519}
 * @param publicKey the public key, X.509-encoded, Base64
 */
public record SigningKey(String keyId, String algorithm, String publicKey)
{
}
