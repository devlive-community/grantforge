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
