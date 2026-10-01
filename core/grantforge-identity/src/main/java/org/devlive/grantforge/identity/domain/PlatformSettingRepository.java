// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** Persistence of {@link PlatformSetting}s. */
public interface PlatformSettingRepository
        extends JpaRepository<PlatformSetting, Long>
{
    /**
     * Finds a setting by key.
     *
     * @param settingKey the key
     * @return the setting, if any
     */
    Optional<PlatformSetting> findBySettingKey(String settingKey);
}
