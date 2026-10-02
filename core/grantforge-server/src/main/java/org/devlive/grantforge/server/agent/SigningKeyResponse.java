// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.agent;

import org.devlive.grantforge.service.agent.SigningKey;

/**
 * The public key policy snapshots are signed with.
 *
 * @param keyId names the key, as the {@value AgentController#KEY_HEADER} header of snapshots does
 * @param algorithm the signature algorithm
 * @param publicKey the public key, X.509-encoded, Base64
 */
public record SigningKeyResponse(String keyId, String algorithm, String publicKey)
{
    /**
     * Converts a key.
     *
     * @param key the key
     * @return the response
     */
    public static SigningKeyResponse from(SigningKey key)
    {
        return new SigningKeyResponse(key.keyId(), key.algorithm(), key.publicKey());
    }
}
