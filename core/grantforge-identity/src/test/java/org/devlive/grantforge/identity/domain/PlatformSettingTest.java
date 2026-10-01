// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlatformSettingTest
{
    @Test
    void blankValuesAreStoredAsNull()
    {
        PlatformSetting setting = PlatformSetting.of(" setup.state ", " ");

        assertThat(setting.getSettingKey()).isEqualTo("setup.state");
        assertThat(setting.getSettingValue()).isNull();

        setting.update(" done ");
        assertThat(setting.getSettingValue()).isEqualTo("done");
    }

    @Test
    void keyIsRequired()
    {
        assertThatThrownBy(() -> PlatformSetting.of(" ", "v")).isInstanceOf(IllegalArgumentException.class);
    }
}
