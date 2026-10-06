// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import type { Metadata } from 'next'
import Landing from '@/components/Landing'

export const metadata: Metadata = {
  title: { absolute: 'Documentation GrantForge' },
  description: 'GrantForge est une plateforme unifiée d’autorisations pour les développeurs : gérez les utilisateurs et l’organisation, attribuez aux rôles des droits fonctionnels, sur les données et sur les champs, et laissez vos applications les utiliser via des protocoles standard.',
}

export default function FrenchHome() {
  return <Landing locale="fr" />
}
