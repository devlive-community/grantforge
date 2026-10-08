// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import type { Metadata } from 'next'
import Landing from '@/components/Landing'

export const metadata: Metadata = {
  title: { absolute: 'Documentazione di GrantForge' },
  description: 'GrantForge è una piattaforma unificata di permessi per sviluppatori: gestisci utenti e organizzazione, concedi ai ruoli permessi funzionali, sui dati e sui campi, e lascia che la tua applicazione li usi tramite protocolli standard.',
}

export default function ItalianHome() {
  return <Landing locale="it" />
}
