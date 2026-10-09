---
title: 配置参考
description: 所有配置项、默认值与对应的环境变量。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

配置可以写在 `configure/application.properties` 里，也可以用环境变量覆盖。Spring Boot 的松散绑定规则同样适用：`grantforge.security.mfa.step-up-window` 可以写成环境变量 `GRANTFORGE_SECURITY_MFA_STEP_UP_WINDOW`。时长写成 `30m`、`12h`、`90d` 这样的形式。

## 服务与数据库

| 配置项 | 环境变量 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `server.port` | `GRANTFORGE_SERVER_PORT` | `9999` | HTTP 端口 |
| `spring.datasource.url` | `GRANTFORGE_DB_URL` | 内置 H2 文件库 | JDBC 地址，见 [数据库](/deploy/databases/) |
| `spring.datasource.username` | `GRANTFORGE_DB_USER` | `sa` | 数据库用户 |
| `spring.datasource.password` | `GRANTFORGE_DB_PASSWORD` | 空 | 数据库密码 |
| — | `GRANTFORGE_HOME` | 安装目录 | H2 数据与日志所在目录 |
| — | `GRANTFORGE_ID_NODE` | 自动 | 集群中每个实例唯一的节点号（0–1023） |
| `spring.servlet.multipart.max-file-size` | `GRANTFORGE_UPLOAD_MAX` | `2MB` | CSV 导入文件大小上限 |

## 初始化与注册

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `grantforge.setup.token` | 空 | 固定的初始化令牌（`GRANTFORGE_SETUP_TOKEN`），空时随机生成并打印在日志里 |
| `grantforge.security.registration-enabled` | `false` | 是否允许访客自助注册 |
| `grantforge.security.registration-tenant` | `default` | 自助注册的账号所属租户 |

## 密码与锁定

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `grantforge.security.password.min-length` | `12` | 最短长度，至少 8 |
| `grantforge.security.password.max-length` | `128` | 最长长度，最多 1024 |
| `grantforge.security.password.required-character-classes` | `1` | 需要混合的字符类别数（小写、大写、数字、其他），1–4 |
| `grantforge.security.password.history-size` | `0` | 新密码不能与最近几次相同，0–24 |
| `grantforge.security.password.max-age` | 不过期 | 密码有效期，到期后登录时必须修改 |
| `grantforge.security.lockout.max-attempts` | `5` | 连续失败几次后锁定 |
| `grantforge.security.lockout.duration` | `15m` | 锁定时长 |

密码不能包含用户名。

## 会话与 Cookie

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `spring.session.timeout` | `30m` | 会话空闲超时（`GRANTFORGE_SESSION_TIMEOUT`） |
| `grantforge.security.sessions.max-per-account` | `0` | 每个账号同时在线的会话数上限，0 为不限 |
| `grantforge.security.sessions.activity-interval` | `1m` | 记录会话最近活动的间隔 |
| `grantforge.security.cookie-secure` | `false` | TLS 在代理处终止时设为 `true`（`GRANTFORGE_COOKIE_SECURE`） |

## 两步验证

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `grantforge.security.mfa.step-up-window` | `10m` | 一次两步验证在多长时间内覆盖敏感操作，1 分钟到 12 小时 |
| `grantforge.security.mfa.required-for-sensitive` | `false` | 敏感操作是否要求账号必须开启两步验证 |

## 加密与授权服务器

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `grantforge.security.encryption-key` | 自动生成 | 加密存储密钥的 32 字节 Base64 密钥，生产环境务必设置 |
| `grantforge.oauth.issuer` | 请求地址 | OIDC 签发者，例如 `https://auth.example.com` |
| `grantforge.oauth.signing-key-rotation` | `90d` | 签名密钥自动轮换周期，0 关闭 |
| `grantforge.oauth.signing-key-retention` | `2d` | 旧密钥继续公开的时长，需长于任何令牌的有效期 |

## 审计、插件与代理

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `grantforge.audit.retention` | `365d` | 审计日志保留时长 |
| `grantforge.audit.archive-directory` | 空 | 过期审计在删除前归档到的目录 |
| `grantforge.access-audit.retention` | `90d` | 代理上报的访问审计保留时长 |
| `grantforge.plugins.directory` | `plugins` | 插件目录 |
| `grantforge.plugins.call-timeout` | `10s` | 调用插件（测试连接、资源查找）的超时 |
| `grantforge.agents.refresh-interval` | `30s` | 建议代理拉取策略的间隔 |

## 可观测性

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `grantforge.observability.prometheus-public` | `false` | `/actuator/prometheus` 是否无需登录（`GRANTFORGE_PROMETHEUS_PUBLIC`） |
| — | `LOGGING_STRUCTURED_FORMAT_CONSOLE` | 设为 `ecs` 或 `logstash` 输出 JSON 日志 |
