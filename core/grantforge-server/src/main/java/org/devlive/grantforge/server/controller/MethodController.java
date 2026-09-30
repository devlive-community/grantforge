package org.devlive.grantforge.server.controller;

import lombok.extern.slf4j.Slf4j;
import org.devlive.grantforge.service.entity.MethodEntity;
import org.devlive.grantforge.service.repository.MethodRepository;
import org.devlive.grantforge.service.service.MethodService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "api/v1/method")
@Slf4j
public class MethodController
        extends BaseController<MethodEntity>
{
    private final MethodRepository repository;
    private final MethodService service;

    protected MethodController(MethodRepository repository, MethodService service)
    {
        super(repository, service);
        this.repository = repository;
        this.service = service;
    }
}
