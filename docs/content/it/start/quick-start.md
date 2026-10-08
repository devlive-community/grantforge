---
title: Configurazione in cinque minuti
description: Avvia GrantForge con il pacchetto di rilascio o con Docker, completa l’inizializzazione, crea un utente e concedi il primo ruolo.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Questa guida avvia GrantForge sulla propria macchina con il database H2 incorporato predefinito. Per la produzione, consulta [Installare il pacchetto di rilascio](/it/deploy/installation/) e [Database](/it/deploy/databases/).

## 1. Avviare il servizio

È richiesto Java 17 o una versione successiva. Scarica il pacchetto di rilascio da [GitHub Releases](https://github.com/devlive-community/grantforge/releases), oppure compilalo tu stesso eseguendo `./mvnw -DskipTests package` nella directory del codice sorgente (l’artefatto viene scritto in `dist/grantforge-release.tar.gz`); quindi estrailo e avvialo:

```bash
tar -xzf grantforge-release.tar.gz
cd grantforge
bin/startup.sh
```

Si può usare anche Docker: costruisci prima l’immagine dal pacchetto di rilascio, quindi avviala con l’esempio Compose:

```bash
docker build -f deploy/docker/Dockerfile -t grantforge dist
docker compose -f deploy/compose/h2.yml up -d
```

Il servizio resta in ascolto sulla porta `9999` per impostazione predefinita. Al primo avvio vengono create le tabelle del database e nel log viene stampato una sola volta il **token di inizializzazione**:

```text
WARN  ... First-run setup is pending. Open the console and enter this setup token: 7rV2...
```

I log del pacchetto di rilascio si trovano in `logs/grantforge.log`; con Docker si consultano con `docker compose logs`.

## 2. Completare l’inizializzazione

Apri http://127.0.0.1:9999/ nel browser: la console entra direttamente nella pagina di inizializzazione. Inserisci il token riportato nel log, il nome dell’organizzazione e il nome utente e la password del primo amministratore (almeno 12 caratteri).

![Pagina di inizializzazione e di accesso](/screenshots/login.png)

> [!TIP]
> Nelle installazioni automatizzate è possibile predefinire il token con la variabile d’ambiente `GRANTFORGE_SETUP_TOKEN`; consulta il [riferimento alla configurazione](/it/reference/configuration/).

Una volta completata l’inizializzazione, questo amministratore detiene sia il ruolo di sistema **amministratore del tenant** sia quello di **amministratore della piattaforma** e può usare tutte le funzioni della console. Da quel momento la pagina di inizializzazione resta chiusa definitivamente.

## 3. Creare un utente

Entra in **Controllo degli accessi → Gestione utenti**, fai clic su «Crea utente» e compila nome utente, password iniziale e dipartimento principale. I nuovi utenti devono modificare la password al primo accesso.

## 4. Creare un ruolo e autorizzarlo

1. Entra in **Controllo degli accessi → Gestione ruoli**, fai clic su «Nuovo ruolo» e assegnagli ad esempio il nome «Revisore in sola lettura».
2. Sulla riga del ruolo fai clic su «Autorizza» e spunta nella matrice delle autorizzazioni la pagina «Registro di audit». La matrice estrae automaticamente le API necessarie a quella pagina.
3. Fai clic su «Assegna» per attribuire il ruolo all’utente appena creato.

![Gestione dei ruoli](/screenshots/roles.png)

## 5. Verificare il risultato

Accedi con il nuovo utente: nel menu di sinistra compare solo «Registro di audit». Torna sull’account amministratore, fai clic sull’icona «Visualizza permessi effettivi» sulla riga dell’utente e potrai vedere l’origine di ciascuno dei suoi permessi.

## Passaggi successivi

- Approfondisci i [concetti fondamentali](/it/start/concepts/).
- Prendi confidenza con ogni menu consultando la [guida all’uso](/it/guide/console/).
- [Integra la tua applicazione](/it/integration/overview/) con GrantForge.
