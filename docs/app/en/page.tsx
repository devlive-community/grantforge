// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import type { Metadata } from 'next'
import Landing from '@/components/Landing'

export const metadata: Metadata = {
  title: { absolute: 'GrantForge Docs' },
  description: 'GrantForge is an open-source unified permission platform: users and organization, roles and functional authorization, data and field permissions, application integration over standard protocols.',
}

export default function EnglishHome() {
  return <Landing locale="en" />
}
