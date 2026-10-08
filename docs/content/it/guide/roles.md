---
title: Ruoli e autorizzazioni
description: Creare ruoli, concedere pagine, pulsanti e interfacce, configurare l’ereditarietà, assegnare i ruoli a persone, gruppi, dipartimenti o posizioni e vedere l’anteprima dell’impatto prima di modificare.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

**Controllo degli accessi → Gestione dei ruoli**: un ruolo è un insieme di autorizzazioni che produce effetto quando viene assegnato a utenti, gruppi di utenti, dipartimenti o posizioni.

![Gestione dei ruoli](/screenshots/roles.png)

## Ruoli di sistema e ruoli personalizzati

Ogni tenant dispone del ruolo di sistema **amministratore tenant** e il tenant piattaforma dispone inoltre del ruolo **amministratore piattaforma**. I ruoli di sistema possiedono automaticamente tutte le risorse del proprio modulo e non possono essere modificati; quando servono permessi simili ma più limitati, **copia** il ruolo di sistema e modifica la copia.

I ruoli personalizzati hanno un codice (lettere minuscole, cifre, punto, trattino o trattino basso) e un nome, e possono essere disattivati: un ruolo disattivato non concede alcun permesso né trasmette permessi per ereditarietà.

## Autorizzazioni

Fai clic su **Autorizzazioni** sulla riga di un ruolo per aprire la matrice delle autorizzazioni:

![Matrice delle autorizzazioni](/screenshots/role-grants.png)

- Cambia applicazione; le risorse si aprono come un albero di catalogo e per ogni risorsa è possibile scegliere “Consenti” o “Nega”.
- **Il consenso a un pulsante fa derivare automaticamente la pagina in cui si trova e le interfacce di cui ha bisogno**; non occorre selezionarli uno per uno. Le risorse derivate sono contrassegnate nella matrice.
- **La negazione ha la precedenza** e si applica alle risorse subordinate: se neghi una pagina, i pulsanti che contiene non sono disponibili nemmeno se un altro ruolo li consente.
- Puoi concedere solo i permessi che possiedi, per evitare di superarli. Chi possiede un ruolo di sistema può concedere qualsiasi risorsa delle applicazioni di business.

Prima di salvare, GrantForge mostra l’**impatto** della modifica: quali risorse diventeranno disponibili o smetteranno di esserlo e quanti utenti possiedono questo ruolo.

## Ereditarietà

Fai clic su **Ereditarietà** e scegli i ruoli da cui questo ruolo eredita: ottiene tutto ciò che i ruoli ereditati consentono e negano, oltre ai ruoli che questi ereditano a loro volta. L’ereditarietà non può formare cicli ed è possibile ereditare solo permessi che si possiedono. È adatta a relazioni cumulative come “responsabile = dipendente + approvazione”.

## Assegnazione

Fai clic su **Assegna** per assegnare il ruolo a:

| Destinatario | Descrizione |
| --- | --- |
| Utente | Direttamente a un account |
| Gruppo di utenti | Tutti i membri del gruppo ottengono il ruolo |
| Dipartimento | I membri del dipartimento ottengono il ruolo, con l’opzione “Includi dipartimenti subordinati” |
| Posizione | Ottiene il ruolo chi ricopre quella posizione |

Per ogni assegnazione è possibile impostare la **data di inizio** e la **data di fine**: alla scadenza perde automaticamente efficacia, il che è utile per le autorizzazioni temporanee. È anche possibile lasciare che siano gli utenti a richiedere ruoli a tempo limitato tramite le [Richieste di accesso e approvazioni](/it/guide/access-requests/).

L’assegnazione e la concessione sono soggette alla [Separazione dei compiti](/it/guide/sod/): un’assegnazione che porterebbe una persona a possedere contemporaneamente ruoli mutualmente esclusivi viene rifiutata.

## Permessi sui dati e permessi sui campi

Sulla riga del ruolo, **Permessi sui dati** e **Permessi sui campi** determinano rispettivamente quali righe può vedere chi possiede il ruolo e come può vedere e modificare i singoli campi; vedi [Permessi sui dati](/it/guide/data-permissions/) e [Permessi sui campi](/it/guide/field-permissions/).

## Copia ed eliminazione

**Copia** replica anche le autorizzazioni, i permessi sui dati e i permessi sui campi. L’eliminazione di un ruolo rimuove contemporaneamente le sue assegnazioni, le sue autorizzazioni e le sue policy, e non è reversibile.
