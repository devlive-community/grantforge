// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import type { Metadata } from 'next'
import Landing from '@/components/Landing'

export const metadata: Metadata = {
  title: { absolute: 'Документация GrantForge' },
  description: 'GrantForge — это открытая платформа единого управления доступом: пользователи и организация, роли, функциональная авторизация, права на данные и поля, интеграция приложений по стандартным протоколам.',
}

export default function RussianHome() {
  return <Landing locale="ru" />
}
