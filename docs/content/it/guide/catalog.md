---
title: Catalogo di risorse e API
description: Mantenere applicazioni e albero delle risorse, dipendenze delle risorse e client OAuth, consultare le API registrate automaticamente e trovare con l’ispezione del catalogo le configurazioni che non funzionano più.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

## Catalogo di risorse

**Gestione della piattaforma → Catalogo di risorse** mantiene l’albero delle risorse di ogni applicazione. La console stessa (`grantforge-console`) è un’applicazione integrata: le sue pagine, i suoi pulsanti e le sue API si registrano automaticamente all’avvio e non si possono eliminare.

![Catalogo di risorse](/screenshots/resources.png)

- **Applicazioni**: crea, modifica ed elimina applicazioni di business; un’applicazione che ha client o risorse non si può eliminare.
- **Risorse**: moduli, menu, pagine, schede, pulsanti, API, entità di dati e campi. Il tipo decide dove può stare ciascun elemento: un pulsante solo sotto una pagina o una scheda, un campo solo sotto un’entità di dati. Le risorse si possono spostare trascinandole, con un massimo di 15 livelli.
- **Stato**: una risorsa si può nascondere o disattivare; quando manca un permesso si può scegliere fra “Nascondi” e “Disabilita” il pulsante.
- **Dipendenze**: un pulsante “richiede” l’API che chiama e una pagina “richiede” l’API da cui carica i dati; al momento dell’autorizzazione le dipendenze vengono dedotte insieme e la pagina di dettaglio le mostra in un grafico.
- **Client OAuth**: registra client per le applicazioni di business; vedi [OAuth 2.1 e OpenID Connect](/it/integration/oauth/).
- **Campi**: quando si seleziona un campo, mostra in quali interfacce compare (che lo restituiscono o lo ricevono).

## Catalogo API

**Gestione della piattaforma → Catalogo API** elenca tutte le interfacce che il server registra automaticamente all’avvio e i requisiti di accesso di ciascuna: pubbliche, sufficiente aver effettuato l’accesso, oppure necessario un codice di permesso. Le interfacce che richiedono autorizzazione vengono raggruppate nel catalogo di risorse in base al codice di permesso e i ruoli citano questi permessi quando vengono autorizzati. Quando un’interfaccia viene aggiunta, rimossa o cambia il suo codice di permesso, compare come “modifica in attesa di conferma” e il catalogo si aggiorna una volta confermata.

![Catalogo API](/screenshots/apis.png)

## Ispezione del catalogo

**Gestione della piattaforma → Ispezione del catalogo** trova le configurazioni che hanno smesso di funzionare senza che nessuno se ne accorgesse: autorizzazioni senza effetto, pulsanti che non funzionano (manca l’API di cui hanno bisogno), API che nessuno può chiamare e dipendenze interrotte. L’ispezione si limita a leggere i dati e non modifica nulla.

![Ispezione del catalogo](/screenshots/health.png)
