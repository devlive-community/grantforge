// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service.agent;

import org.jspecify.annotations.Nullable;

import java.util.Arrays;

/**
 * A snapshot as sent to agents.
 *
 * @param body the snapshot as JSON, exactly as signed
 * @param etag a hash of the body, so unchanged snapshots need not be sent again
 * @param policyVersion the service's policy version
 * @param keyId the key the body was signed with
 * @param signature the Ed25519 signature of the body, Base64
 */
public record SignedSnapshot(byte[] body, String etag, long policyVersion, String keyId, String signature)
{
    /** Copies the body. */
    public SignedSnapshot
    {
        body = body.clone();
    }

    @Override
    public byte[] body()
    {
        return body.clone();
    }

    @Override
    public boolean equals(@Nullable Object other)
    {
        return other instanceof SignedSnapshot that && Arrays.equals(body, that.body) && etag.equals(that.etag)
                && policyVersion == that.policyVersion && keyId.equals(that.keyId) && signature.equals(that.signature);
    }

    @Override
    public int hashCode()
    {
        return Arrays.hashCode(body) * 31 + etag.hashCode();
    }

    @Override
    public String toString()
    {
        return "SignedSnapshot[etag=" + etag + ", policyVersion=" + policyVersion + ", bytes=" + body.length + "]";
    }
}
