// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.trino;

/** The Trino plugin against a Kerberized Trino 484 coordinator. */
class Trino484KerberosIT
        extends TrinoKerberosContract
{
    @Override
    String version()
    {
        return "484";
    }
}
