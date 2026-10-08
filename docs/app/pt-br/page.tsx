// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import type { Metadata } from 'next'
import Landing from '@/components/Landing'

export const metadata: Metadata = {
  title: { absolute: 'Documentação do GrantForge' },
  description: 'O GrantForge é uma plataforma unificada de permissões para desenvolvedores: gerencie usuários e organização, conceda a papéis permissões funcionais, sobre dados e sobre campos, e deixe que sua aplicação as use por meio de protocolos padrão.',
}

export default function PortugueseHome() {
  return <Landing locale="pt-br" />
}
