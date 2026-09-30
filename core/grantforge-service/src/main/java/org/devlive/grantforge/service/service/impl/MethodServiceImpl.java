package org.devlive.grantforge.service.service.impl;

import org.devlive.grantforge.service.entity.MethodEntity;
import org.devlive.grantforge.service.repository.MethodRepository;
import org.devlive.grantforge.service.service.MethodService;
import org.springframework.stereotype.Service;

@Service
public class MethodServiceImpl implements MethodService
{

    private final MethodRepository repository;

    public MethodServiceImpl(MethodRepository repository)
    {
        this.repository = repository;
    }

    @Override
    public MethodEntity getByMethod(String method)
    {
        return this.repository.findByMethod(method);
    }
}
