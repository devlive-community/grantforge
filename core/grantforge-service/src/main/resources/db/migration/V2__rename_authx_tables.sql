-- MySQL renames all core tables in one statement and preserves their data.
RENAME TABLE
    `authx_menu` TO `grantforge_menu`,
    `authx_menu_method_relation` TO `grantforge_menu_method_relation`,
    `authx_method` TO `grantforge_method`,
    `authx_role` TO `grantforge_role`,
    `authx_role_menu_relation` TO `grantforge_role_menu_relation`,
    `authx_user` TO `grantforge_user`,
    `authx_user_role_relation` TO `grantforge_user_role_relation`;
