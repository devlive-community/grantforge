---
title: Справочник REST API
description: Все REST-конечные точки и требования к доступу к ним, сгенерированные из контракта OpenAPI.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Эта страница генерируется при сборке документации из контракта OpenAPI в репозитории (`core/grantforge-web/src/api/openapi.json`) и остаётся согласованной с сервером. Работающий сервер отдаёт тот же контракт по `/v3/api-docs`.

- **Публичные**: вход не требуется.
- **Доступные авторизованным**: любая вошедшая учётная запись.
- Остальные конечные точки перечисляют требуемые им коды прав; коды прав регистрируются как ресурсы API в каталоге ресурсов.

Конечные точки консоли используют сессии и CSRF-токены; см. [Проектирование безопасности](/ru/architecture/security/). Для Open API, используемого бизнес-приложениями, см. [Open API](/ru/integration/open-api/).

{{generated:api}}
