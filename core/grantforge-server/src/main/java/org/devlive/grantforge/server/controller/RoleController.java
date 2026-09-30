package org.devlive.grantforge.server.controller;

import lombok.extern.slf4j.Slf4j;
import org.devlive.grantforge.param.RoleSetMenuParam;
import org.devlive.grantforge.param.system.role.SystemRoleMenuParam;
import org.devlive.grantforge.service.entity.MenuEntity;
import org.devlive.grantforge.service.entity.RoleEntity;
import org.devlive.grantforge.service.entity.UserEntity;
import org.devlive.grantforge.service.entity.common.CommonResponseModel;
import org.devlive.grantforge.service.entity.system.menu.SystemMenuTypeModel;
import org.devlive.grantforge.service.repository.MenuRepository;
import org.devlive.grantforge.service.repository.RoleRepository;
import org.devlive.grantforge.service.service.MenuService;
import org.devlive.grantforge.service.service.RoleService;
import org.devlive.grantforge.service.service.UserIService;
import org.devlive.grantforge.service.service.system.role.SystemRoleSeniorService;
import org.springframework.util.ObjectUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping(value = "api/v1/role")
@Slf4j
public class RoleController
        extends BaseController<RoleEntity>
{

    private final RoleRepository repository;
    private final MenuRepository menuRepository;
    private final RoleService service;
    private final SystemRoleSeniorService systemRoleSeniorService;
    private final UserIService userService;
    private final MenuService menuService;

    public RoleController(RoleRepository repository, MenuRepository menuRepository, RoleService service, SystemRoleSeniorService systemRoleSeniorService, UserIService userService, MenuService systemMenuService)
    {
        super(repository, service);
        this.repository = repository;
        this.menuRepository = menuRepository;
        this.service = service;
        this.systemRoleSeniorService = systemRoleSeniorService;
        this.userService = userService;
        this.menuService = systemMenuService;
    }

    @PutMapping(value = ControllerSupport.CONTROLLER_DEFAULT_SET + "/menu")
    public CommonResponseModel setMenus(@RequestBody @Validated RoleSetMenuParam param)
    {
        RoleEntity systemRole = this.service.getModelById(Long.valueOf(param.getId()));
        List<MenuEntity> menuList = new ArrayList<>();
        Arrays.asList(param.getMenu().split(","))
                .forEach(v -> menuList.add(new MenuEntity(Long.valueOf(v))));
        systemRole.setMenus(menuList);
        return CommonResponseModel.success(this.service.saveOrUpdate(repository, systemRole));
    }

    /**
     * Assign system permissions menu
     *
     * @param param System permissions and menu information
     * @return Distribution state
     */
    @PutMapping(value = "menus")
    public CommonResponseModel setMenu(@RequestBody SystemRoleMenuParam param)
    {
        List<MenuEntity> menus = new ArrayList<>();
        RoleEntity model = this.service.getModelById(param.getRoleId());
        if (!ObjectUtils.isEmpty(param.getMenus())) {
            param.getMenus()
                    .forEach(v -> {
                        MenuEntity menu = new MenuEntity();
                        menu.setId(v);
                        menus.add(menu);
                    });
        }
        model.setMenus(menus);
        return CommonResponseModel.success(this.service.saveOrUpdate(repository, model));
    }

    /**
     * Assign system permissions menu list
     *
     * @return 菜单列表
     */
    @GetMapping(value = "menus")
    public CommonResponseModel findAllByRoleAndMenuType(@RequestParam Long id)
    {
        SystemMenuTypeModel menuTypeModel = new SystemMenuTypeModel();
        menuTypeModel.setId(0L);
        RoleEntity roleModel = new RoleEntity();
        roleModel.setId(id);
        return CommonResponseModel.success(this.systemRoleSeniorService.findTreeMenuById(roleModel, menuTypeModel));
    }

    /**
     * 获取系统左侧导航菜单
     *
     * @param id 权限标识
     * @return 导航菜单
     */
    @GetMapping(value = "menu")
    public CommonResponseModel getMenu(@RequestParam Long id)
    {
        UserEntity user = this.userService.getDistinctById(id);
        // TODO: 判断权限的等级
        return CommonResponseModel.success(this.systemRoleSeniorService.findMenuByIds(user.getRoles()));
    }
}
