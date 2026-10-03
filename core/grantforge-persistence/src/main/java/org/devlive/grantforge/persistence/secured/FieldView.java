// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.persistence.secured;

import org.jspecify.annotations.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * How a reader sees a secured field.
 *
 * @param mode visible, masked or hidden
 * @param mask how a masked field is masked; {@code null} unless masked
 */
public record FieldView(FieldReadMode mode, @Nullable MaskStrategy mask)
{
    /** The value as it is. */
    public static final FieldView VISIBLE = new FieldView(FieldReadMode.VISIBLE, null);

    /** Nothing of the value. */
    public static final FieldView HIDDEN = new FieldView(FieldReadMode.HIDDEN, null);

    /**
     * Checks that a masked field says how it is masked, and others do not.
     *
     * @throws IllegalArgumentException otherwise
     */
    public FieldView
    {
        requireNonNull(mode, "mode");
        if ((mode == FieldReadMode.MASKED) != (mask != null)) {
            throw new IllegalArgumentException("a mask strategy goes with masked fields, and only with them");
        }
    }

    /**
     * Creates the view of a masked field.
     *
     * @param mask how it is masked
     * @return the view
     */
    public static FieldView masked(MaskStrategy mask)
    {
        return new FieldView(FieldReadMode.MASKED, requireNonNull(mask, "mask"));
    }

    /**
     * Shows a value as the reader may see it. Masking works on text; other masked values, such as times, show nothing.
     *
     * @param value the value
     * @return the value, its masked text, or {@code null} for a hidden field
     */
    public @Nullable Object present(@Nullable Object value)
    {
        if (mode == FieldReadMode.VISIBLE) {
            return value;
        }
        MaskStrategy strategy = mask;
        return strategy != null && value instanceof CharSequence text ? strategy.mask(text.toString()) : null;
    }
}
