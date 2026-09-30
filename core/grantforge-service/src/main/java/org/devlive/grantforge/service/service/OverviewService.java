// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service.service;

import org.devlive.grantforge.service.entity.common.CommonResponseModel;

public interface OverviewService
{

    /**
     * 获取数据概览信息(统计)
     *
     * @return 概览信息(统计)
     */
    CommonResponseModel getOverviewByCount();
}
