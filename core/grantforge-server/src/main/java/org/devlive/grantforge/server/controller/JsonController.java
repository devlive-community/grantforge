package org.devlive.grantforge.server.controller;

import lombok.extern.slf4j.Slf4j;
import org.devlive.grantforge.service.entity.common.CommonResponseModel;
import org.devlive.grantforge.service.service.JsonService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "api/v1/json")
@Slf4j
public class JsonController
{
    private final JsonService service;

    public JsonController(JsonService service)
    {
        this.service = service;
    }

    @PostMapping(value = "pretty")
    public CommonResponseModel doPretty(@RequestBody String body)
    {
        return service.formatPretty(body);
    }

    @PostMapping(value = "compression")
    public CommonResponseModel doCompression(@RequestBody String body)
    {
        return service.compression(body);
    }
}
