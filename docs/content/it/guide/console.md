---
title: Tour della console
description: La disposizione della console, i gruppi di menu e perché i menu variano da una persona all’altra.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

![Panoramica dello spazio di lavoro](/screenshots/dashboard.png)

## Disposizione

- Il **menu di sinistra** è organizzato per gruppi: Spazio di lavoro, Controllo degli accessi, Permessi sui dati, Gestione della piattaforma.
- La **barra superiore** contiene l’accesso rapido (⌘K o Ctrl+K, per cercare le pagine in base al nome), il cambio di lingua, il tema chiaro/scuro e il menu personale.
- La pagina **Panoramica** mostra il numero di account, dipartimenti, gruppi e posizioni del tenant attuale, oltre ai membri dello spazio di lavoro e ai passi iniziali.

## Gruppi di menu

| Gruppo | Menu | Descrizione |
| --- | --- | --- |
| Spazio di lavoro | Panoramica, Le mie richieste | Visibile a ogni utente che ha effettuato l’accesso |
| Controllo degli accessi | Gestione utenti, Struttura dell’organizzazione, Gruppi di utenti, Posizioni, Importazione ed esportazione, Gestione dei ruoli, Separazione dei compiti, Approvazione dei permessi, Revisione dei permessi, Sessioni attive, Fonti di identità, Registri di audit | Identità e autorizzazioni di questo tenant |
| Permessi sui dati | Servizi di dati, Policy, Agenti, Audit degli accessi | I permessi sui sistemi di dati esterni (HDFS, Hive, ecc.); vedi [Servizi di dati, policy e agenti](/it/external/data-services/) |
| Gestione della piattaforma | Gestione dei tenant, Catalogo di risorse, Catalogo API, Server di autorizzazione, Ispezione del catalogo, Plug-in | Disponibili solo nel tenant piattaforma |

## Perché il mio menu è diverso da quello degli altri

La console è a sua volta un’applicazione gestita da GrantForge: ogni pagina e ogni pulsante sono risorse del catalogo di risorse, e quali menu e quali pulsanti si vedono dipende interamente dai propri ruoli.

- Chi possiede il ruolo di sistema **amministratore tenant** vede tutti i menu “Controllo degli accessi” e “Permessi sui dati” di questo tenant.
- Chi possiede il ruolo di sistema **amministratore piattaforma** vede anche “Gestione della piattaforma”.
- Gli altri utenti vedono solo le pagine autorizzate dal proprio ruolo; quando in un gruppo non compare alcuna pagina, il gruppo completo non viene mostrato.

> [!NOTE]
> Nascondere i menu è solo una comodità. Il server verifica nuovamente i permessi a ogni chiamata API: anche inserendo direttamente l’indirizzo, un’operazione per cui non si dispone del permesso viene rifiutata.

Dopo una modifica delle autorizzazioni non è necessario accedere di nuovo: ogni risposta riporta il numero di versione dei permessi in vigore e, quando la versione cambia, la console ricarica automaticamente il menu.
