---
title: 部门、用户组与岗位
description: 维护部门树、按需建用户组、管理岗位，它们都可以作为角色分配与数据范围的对象。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

## 组织架构

**访问控制 → 组织架构** 维护部门层级。

![组织架构](/screenshots/org.png)

- 部门最多 16 层，可以拖动或“移动到…”调整位置，但不能移到自己的下级之下。
- 每个账号有一个主部门，可以兼职多个部门。
- 部门是数据权限的重要依据：“本部门”“本部门及下级”“指定部门”都按这里的结构计算。
- 只能删除没有下级部门的部门。

## 用户组

**访问控制 → 用户组**：把需要相同权限的账号放进同一个组，之后为组分配角色即可。用户组与组织结构无关，适合“值班组”“项目组”这类跨部门的集合。成员可以批量添加和移除，每次最多 500 人。

![用户组](/screenshots/groups.png)

## 岗位

**访问控制 → 岗位**：维护组织中的岗位（例如“财务经理”），在编辑用户时为其分配一个或多个岗位。岗位同样可以被分配角色，人员调岗时只需改他的岗位，权限随之变化。

![岗位](/screenshots/positions.png)
