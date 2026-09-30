package org.devlive.grantforge.validation.menu;

import lombok.extern.slf4j.Slf4j;
import org.devlive.grantforge.service.repository.MenuRepository;
import org.devlive.grantforge.service.service.MenuService;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

@Slf4j
public class MenuRequireValidationValidator implements ConstraintValidator<MenuRequireValidation, String>
{

    private final MenuRepository repository;
    private final MenuService service;

    public MenuRequireValidationValidator(MenuRepository repository, MenuService service)
    {
        this.repository = repository;
        this.service = service;
    }

    @Override
    public void initialize(MenuRequireValidation validation)
    {
    }

    @Override
    public boolean isValid(String s, ConstraintValidatorContext context)
    {
        log.info("validation role id is exists, role id is {}", s);
        return !StringUtils.isEmpty(s) && !ObjectUtils.isEmpty(this.service.findById(repository, Long.valueOf(s)));
    }
}
