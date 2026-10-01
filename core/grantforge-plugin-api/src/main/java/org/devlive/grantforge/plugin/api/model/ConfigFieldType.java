// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.plugin.api.model;

/** The kind of value a configuration field holds; the console renders an input to match. */
public enum ConfigFieldType
{
    /** One line of text. */
    STRING,

    /** Several lines of text, such as a certificate. */
    TEXT,

    /** A whole number. */
    INTEGER,

    /** {@code true} or {@code false}. */
    BOOLEAN,

    /** A password, key or keytab: stored encrypted and never shown again. */
    SECRET,

    /** One of the field's options. */
    ENUM
}
