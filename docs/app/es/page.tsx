// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import type { Metadata } from 'next'
import Landing from '@/components/Landing'

export const metadata: Metadata = {
  title: { absolute: 'Documentación de GrantForge' },
  description: 'GrantForge es una plataforma unificada de permisos para desarrolladores: gestiona usuarios y organización, concede a los roles permisos funcionales, sobre datos y sobre campos, y deja que tu aplicación los use mediante protocolos estándar.',
}

export default function SpanishHome() {
  return <Landing locale="es" />
}
