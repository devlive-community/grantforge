---
title: Tenant
description: Creare, modificare, disattivare e riattivare i tenant.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

**Gestione della piattaforma → Gestione dei tenant**: ogni tenant è un’organizzazione isolata dalle altre, con i propri utenti e i propri permessi. Il tenant piattaforma ospita gli amministratori della piattaforma e non si può disattivare.

![Gestione dei tenant](/screenshots/tenants.png)

## Creare un tenant

Compila il codice e il nome del tenant, e il nome utente, il nome visualizzato e la password del suo primo amministratore. Il primo amministratore possiede il ruolo di sistema “amministratore tenant” di quel tenant e deve modificare la password al primo accesso. Il nome utente è unico in tutta la piattaforma.

## Disattivare e riattivare

Dopo aver disattivato un tenant, tutti i suoi account escono immediatamente e non possono più effettuare l’accesso, e i token delle applicazioni già emessi non vengono più rinnovati. I dati non vengono eliminati: basta riattivarlo per recuperarli.

## Tenant piattaforma

Il tenant piattaforma è il primo tenant creato in fase di inizializzazione; i suoi amministratori possono gestire tutti i tenant, il [Catalogo di risorse e API](/it/guide/catalog/), il server di autorizzazione e i plug-in. Le applicazioni di business e le loro risorse sono condivise da tutta la piattaforma, e ogni tenant autorizza queste risorse nei propri ruoli.
