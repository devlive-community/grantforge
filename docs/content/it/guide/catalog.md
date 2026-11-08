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

Nel catalogo di un’applicazione con risorse, fai clic su **Esporta** per scaricare un file JSON di riferimento per lo sviluppo o per strumenti di IA. Contiene le informazioni dell’applicazione, tutte le risorse organizzate per gerarchia e le loro dipendenze, con i codici delle risorse alle due estremità; ricerca e filtri non cambiano il contenuto esportato. `formatVersion` indica la versione del formato di esportazione.

Scegli l’applicazione di destinazione, fai clic su **Importa**, carica un file di esportazione della versione 1 ed esegui prima la verifica. L’importazione riusa le risorse con lo stesso codice nella stessa applicazione solo se tipo e padre coincidono, senza sovrascrivere le impostazioni esistenti; un conflitto di tipo, padre o tipo di dipendenza annulla l’intera importazione. Risorse e dipendenze nuove vengono scritte insieme dopo la conferma, e ripetere un’importazione non crea duplicati. Le applicazioni integrate non supportano l’importazione; le dipendenze dichiarate diventano manuali dopo l’importazione. Importare risorse richiede il permesso di creare risorse, e importare dipendenze richiede anche quello di modificarle.

L’esportazione contiene solo ciò che è registrato in GrantForge: `route` di pagine e risorse simili è la route del frontend, `code` delle risorse API è il codice di permesso. Metodi HTTP e percorsi delle API di business esterne non vengono dedotti se non sono registrati. Il JSON è un’istantanea al momento dell’esportazione e non contiene le autorizzazioni di alcun utente; esporta di nuovo quando le risorse cambiano.

## Catalogo API

**Gestione della piattaforma → Catalogo API** elenca tutte le interfacce che il server registra automaticamente all’avvio e i requisiti di accesso di ciascuna: pubbliche, sufficiente aver effettuato l’accesso, oppure necessario un codice di permesso. Le interfacce che richiedono autorizzazione vengono raggruppate nel catalogo di risorse in base al codice di permesso e i ruoli citano questi permessi quando vengono autorizzati. Quando un’interfaccia viene aggiunta, rimossa o cambia il suo codice di permesso, compare come “modifica in attesa di conferma” e il catalogo si aggiorna una volta confermata.

![Catalogo API](/screenshots/apis.png)

## Ispezione del catalogo

**Gestione della piattaforma → Ispezione del catalogo** trova le configurazioni che hanno smesso di funzionare senza che nessuno se ne accorgesse: autorizzazioni senza effetto, pulsanti che non funzionano (manca l’API di cui hanno bisogno), API che nessuno può chiamare e dipendenze interrotte. L’ispezione si limita a leggere i dati e non modifica nulla.

![Ispezione del catalogo](/screenshots/health.png)
