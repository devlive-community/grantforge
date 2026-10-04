// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.security;

import org.devlive.grantforge.persistence.secured.FieldMode;
import org.devlive.grantforge.persistence.secured.FieldReadMode;
import org.devlive.grantforge.persistence.secured.FieldView;
import org.devlive.grantforge.persistence.secured.FieldWriteMode;
import org.devlive.grantforge.persistence.secured.MaskStrategy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FieldModeResponseTest
{
    @Test
    void tellsHowAFieldIsSeenAndChanged()
    {
        assertThat(FieldModeResponse.from(new FieldMode(FieldView.masked(MaskStrategy.PHONE), FieldWriteMode.READONLY)))
                .isEqualTo(new FieldModeResponse(FieldReadMode.MASKED, MaskStrategy.PHONE, FieldWriteMode.READONLY));
        assertThat(FieldModeResponse.from(FieldMode.OPEN)).isEqualTo(new FieldModeResponse(FieldReadMode.VISIBLE, null,
                FieldWriteMode.EDITABLE));
    }
}
