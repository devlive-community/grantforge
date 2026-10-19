// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.trino;

/** The Trino plugin against a Trino 470 coordinator with HTTPS, a password file and JWTs. */
class Trino470SecureIT
        extends TrinoSecureContract
{
    @Override
    String version()
    {
        return "470";
    }
}
