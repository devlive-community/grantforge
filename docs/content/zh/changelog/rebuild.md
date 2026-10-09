---
title: 2026.0.0（重构版）
description: 从零重写的 GrantForge：多租户身份、资源与角色授权、数据与字段权限、标准协议接入、企业治理与插件化的外部系统权限。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

2026.0.0 是一次完整的重写，取代 1.x（AuthX）。它不再是一个后台模板，而是一个独立部署的身份与权限平台。1.x 的账号、角色与菜单可以导入，见 [升级与旧版本迁移](/deploy/upgrade/)。

## 平台

- Spring Boot 4、Java 17 运行时；一个发行包包含服务端与控制台，另提供 Docker 镜像、Compose 与 Helm Chart。
- 支持 H2、PostgreSQL、MySQL、MariaDB、Oracle、SQL Server，迁移由 Liquibase 管理，每次提交在九个数据库版本上测试。
- 首次启动的初始化向导，用服务日志中的一次性令牌创建平台管理员。
- 集群部署：会话保存在数据库，ID 为时间有序的 TSID。
- 全新的控制台：Vue 3、Tailwind CSS，亮色与暗色主题，中文与英文。

## 身份与组织

- 多租户：每个租户的账号、组织与授权完全隔离。
- 用户、部门树、用户组、岗位，CSV 批量导入导出。
- Argon2id 密码、可配置的密码策略与锁定、会话管理、TOTP 两步验证与恢复码、敏感操作的再次验证。
- LDAP / Active Directory 与 OIDC 身份源，支持同步与联合登录。

## 授权

- 资源目录：模块、菜单、页面、标签、按钮、API、数据实体与字段，以及它们之间的依赖。控制台自身的页面、按钮与 API 也在目录中，并接受同样的授权。
- 角色与授权：允许与拒绝、继承、按用户/用户组/部门/岗位分配、有效期。
- 数据权限：按组织范围或结构化条件限定可见的行。
- 字段权限：按角色隐藏、脱敏或只读字段。
- 权限解释、按用户模拟、完整的审计日志，以及失效配置的检查。

## 治理

- 职责分离：互斥角色在分配、继承与申请时被拒绝，并能发现已存在的冲突。
- 权限申请：用户申请可申请的角色，审批人批准后限时生效、到期自动收回。
- 定期权限复核。

## 应用接入

- 内置 OAuth 2.1 / OpenID Connect 授权服务器：授权码 + PKCE、客户端凭证、刷新令牌轮换、签名密钥轮换。
- 权限查询开放 API，带版本与 ETag。
- Spring Boot Starter 与 JavaScript SDK，附带可运行的示例应用。

## 外部系统权限

- 插件化的服务类型：插件定义资源层级、访问类型、脱敏与行过滤；每个插件独立加载。
- 通用策略编辑器、Ed25519 签名的策略快照、代理心跳与访问审计。
- 示例插件、HDFS 服务类型和 Hadoop 3.5.0 NameNode 代理；Hive 插件与其他 Hadoop 版本的代理正在开发中。

## 质量

- 百万账号规模的性能基准每晚运行，超过上限即失败。
- 静态分析（NullAway、Error Prone、Checkstyle、PMD、SpotBugs、ArchUnit）、覆盖率门槛与全栈浏览器测试。
- 文档站改用 Next.js 与 Tailwind CSS 重建。
