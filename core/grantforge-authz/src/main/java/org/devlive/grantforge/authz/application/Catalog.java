// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.devlive.grantforge.common.error.CommonErrorCode;
import org.devlive.grantforge.common.error.GrantForgeException;

import java.util.function.Supplier;

/** Helpers shared by the catalog services. */
final class Catalog
{
    private Catalog()
    {
    }

    /** Runs a domain change and reports invalid values as a bad request. */
    static <T> T valid(Supplier<T> change)
    {
        try {
            return change.get();
        }
        catch (IllegalArgumentException invalid) {
            throw new GrantForgeException(CommonErrorCode.BAD_REQUEST, String.valueOf(invalid.getMessage()), invalid);
        }
    }
}
