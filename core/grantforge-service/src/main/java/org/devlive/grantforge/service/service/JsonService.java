// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service.service;

import org.devlive.grantforge.service.entity.common.CommonResponseModel;

public interface JsonService
{

    /**
     * 格式化json数据
     *
     * @param source json元数据
     * @return 格式化后的json数据
     */
    CommonResponseModel formatPretty(String source);

    /**
     * 压缩json数据
     *
     * @param source json元数据
     * @return 压缩后的json数据
     */
    CommonResponseModel compression(String source);
}
