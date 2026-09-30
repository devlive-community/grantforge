<div align="center">

<img src="docs/brand/grantforge-logo.png" width="128" height="128" alt="GrantForge logo" />

# GrantForge

开源权限管理平台 · 用户、角色、菜单与接口授权

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
![Version](https://img.shields.io/badge/version-2026.0.0-4F46E5)

</div>

GrantForge（原 AuthX）基于 Spring Boot、Spring Security 和 Vue 3，提供基于角色的访问控制（RBAC）管理基础。当前版本号为 `2026.0.0`。

## 当前实现

- 用户注册、登录、用户查询和角色分配。
- 角色管理、菜单授权、菜单与 HTTP 方法关联。
- 菜单、图标及菜单类型管理的后端接口。
- 用户数量概览与 JSON 工具。
- MySQL 持久化、Java 服务与 Vue 管理界面。

## 项目结构

| 模块 | 职责 |
| --- | --- |
| `core/grantforge-common` | 通用工具、分页与消息定义 |
| `core/grantforge-param` | 请求参数模型 |
| `core/grantforge-validation` | 业务参数校验 |
| `core/grantforge-service` | JPA 实体、仓储与业务服务 |
| `core/grantforge-security` | 认证、令牌与访问控制 |
| `core/grantforge-aop` | 日志与校验切面 |
| `core/grantforge-server` | REST API 与服务入口 |
| `core/grantforge-web` | Vue 3 / TypeScript / Vite 管理界面 |

Maven 根坐标：`org.devlive.grantforge:grantforge:2026.0.0`。Java 包前缀：`org.devlive.grantforge`。启动类：`org.devlive.grantforge.server.GrantForge`。

## 开发与验证

Java 源码目标为 Java 8。前端使用 pnpm 与仓库中的 `pnpm-lock.yaml`。

```sh
# 只检查服务端及其依赖的 Java 编译，不执行历史前端插件
mvn -pl core/grantforge-server -am clean compiler:compile

# 前端开发与完整检查
cd core/grantforge-web
pnpm install --frozen-lockfile
pnpm dev
pnpm build
pnpm exec eslint src --ext .vue,.ts
```

## 更名兼容

已有数据库仍使用 `authx` 与 `authx_*` 表。OAuth 协议标识和浏览器令牌存储键沿用原有配置。

## 项目链接

- [项目仓库](https://github.com/devlive-community/authx)
- [现有文档站](https://authx.devlive.org)
