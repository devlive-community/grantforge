// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.controller;

import lombok.extern.slf4j.Slf4j;
import org.devlive.grantforge.service.entity.common.CommonResponseModel;
import org.devlive.grantforge.service.service.OverviewService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "api/v1/overview")
@Slf4j
public class OverviewController
{

    private final OverviewService service;

    public OverviewController(OverviewService service)
    {
        this.service = service;
    }

    @GetMapping
    public CommonResponseModel getOverviewByCount()
    {
        return this.service.getOverviewByCount();
    }
}
