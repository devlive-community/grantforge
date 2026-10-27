// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.datasource.provider.dingtalk;

import org.devlive.grantforge.datasource.application.DataSourceErrors;

import java.util.Map;

/**
 * Public settings for the official DingTalk SDK adapter.
 */
public record DingTalkSettings(String corpId, String appKey, long rootDepartmentId)
{
    /**
     * Validates the selected enterprise, application and subtree.
     */
    public DingTalkSettings
    {
        if (corpId.isBlank() || appKey.isBlank() || corpId.length() > 128 || appKey.length() > 128 || rootDepartmentId < 1) {
            throw DataSourceErrors.invalid("DingTalk corpId, AppKey and a positive root department ID are required");
        }
    }

    /**
     * Parses supplier settings without admitting credentials into public configuration.
     *
     * @param settings public configuration
     * @return the SDK settings
     */
    public static DingTalkSettings from(Map<String, String> settings)
    {
        if (settings.values().stream().anyMatch(java.util.Objects::isNull) || !settings.keySet().stream().allMatch(key -> java.util.Set.of("corpId", "appKey", "rootDepartmentId").contains(key))) {
            throw DataSourceErrors.invalid("unsupported DingTalk settings; store AppSecret in the credential field");
        }
        try {
            return new DingTalkSettings(settings.getOrDefault("corpId", "").strip(), settings.getOrDefault("appKey", "").strip(),
                Long.parseLong(settings.getOrDefault("rootDepartmentId", "1")));
        }
        catch (NumberFormatException failed) {
            throw DataSourceErrors.invalid("invalid DingTalk root department ID");
        }
    }
}
