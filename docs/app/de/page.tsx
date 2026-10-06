// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import type { Metadata } from 'next'
import Landing from '@/components/Landing'

export const metadata: Metadata = {
  title: { absolute: 'GrantForge Dokumentation' },
  description: 'GrantForge ist eine einheitliche Berechtigungsplattform für Entwickler: Benutzer und Organisation verwalten, Rollen Funktions-, Daten- und Feldrechte zuweisen und die eigene Anwendung diese Rechte über Standardprotokolle nutzen lassen.',
}

export default function GermanHome() {
  return <Landing locale="de" />
}
