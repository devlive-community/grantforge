// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.devlive.grantforge.common.lang.Strings;
import org.devlive.grantforge.persistence.entity.BaseEntity;
import org.jspecify.annotations.Nullable;

/** A platform-wide key/value setting, such as first-run setup state. Keys are unique. */
@Entity
@Table(name = "gf_platform_setting")
public class PlatformSetting
        extends BaseEntity
{
    @Column(name = "setting_key", nullable = false, length = 64)
    private String settingKey = "";

    @Column(name = "setting_value", length = 1000)
    private @Nullable String settingValue;

    /** For JPA. */
    protected PlatformSetting()
    {
    }

    /**
     * Creates a setting.
     *
     * @param key the unique key
     * @param value the value; blank is stored as {@code null}
     * @return the setting
     */
    public static PlatformSetting of(String key, @Nullable String value)
    {
        PlatformSetting setting = new PlatformSetting();
        setting.settingKey = Strings.requireNonBlank(key, "key");
        setting.settingValue = Strings.blankToNull(value);
        return setting;
    }

    /**
     * Returns the key.
     *
     * @return the key
     */
    public String getSettingKey()
    {
        return settingKey;
    }

    /**
     * Returns the value.
     *
     * @return the value, or {@code null}
     */
    public @Nullable String getSettingValue()
    {
        return settingValue;
    }

    /**
     * Replaces the value.
     *
     * @param value the new value; blank is stored as {@code null}
     */
    public void update(@Nullable String value)
    {
        settingValue = Strings.blankToNull(value);
    }
}
