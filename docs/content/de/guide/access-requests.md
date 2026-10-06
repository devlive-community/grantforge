---
title: Berechtigungsanträge und Freigaben
description: Benutzer beantragen zeitlich begrenzte Rollen; Freigebende genehmigen, lehnen ab oder entziehen vorzeitig; zum Ablauf wird automatisch entzogen.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

## Administratoren: Rollen zur Beantragung freigeben

Klicke unter **Zugriffskontrolle → Berechtigungsfreigaben** auf **Beantragbare Rollen** und wähle, welche benutzerdefinierten Rollen beantragt werden können und wie viele Tage (1–365) eine Rolle höchstens beantragt werden kann. Systemrollen können nicht beantragt werden.

## Benutzer: Antrag stellen

Jeder angemeldete Benutzer sieht unter **Arbeitsbereich → Meine Anträge** die Rollen, die er beantragen kann. Rolle auswählen, Begründung und Anzahl der Tage eintragen und absenden; vor der Freigabe kann der Antrag zurückgezogen werden. Wer die Rolle bereits hat oder für sie schon einen offenen Antrag hat, kann nicht erneut beantragen.

![Meine Anträge](/screenshots/requests.png)

Neu angelegte Benutzer müssen zuerst ihr initiales Passwort ändern, bevor sie diese Seite nutzen können.

## Freigebende: genehmigen, ablehnen, entziehen

Unter **Zugriffskontrolle → Berechtigungsfreigaben** werden offene, erteilte oder alle Anträge aufgelistet.

![Berechtigungsfreigaben](/screenshots/access-approvals.png)

- **Genehmigen**: Die Anzahl der Tage kann verkürzt und eine Bemerkung eingetragen werden. Nach der Genehmigung erhält der Benutzer die Rolle sofort; zum Ablaufdatum wird sie automatisch entzogen.
- **Ablehnen**: Eine Begründung kann eingetragen werden.
- **Entziehen**: Bei einem bereits erteilten Antrag wird die Rolle vorzeitig entzogen.

Eine Genehmigung entspricht einer Rollenzuweisung durch die freigebende Person, deshalb gelten dieselben Regeln:

- Es kann keine Rolle erteilt werden, die über die eigenen Berechtigungen der freigebenden Person hinausgeht;
- die Regeln der [Funktionstrennung](/de/guide/sod/) müssen eingehalten werden;
- niemand darf seinen eigenen Antrag freigeben;
- bei freigebenden Personen mit aktiver Zwei-Faktor-Authentifizierung muss in den letzten 10 Minuten eine Verifizierung erfolgt sein.

## Entzug zum Ablauf

Erteilte Rollen enden zum Stichtag sofort. Im Hintergrund werden alle 5 Minuten abgelaufene Zuweisungen bereinigt und die Anträge als „abgelaufen“ markiert. Der gesamte Vorgang (Antrag, Rücknahme, Genehmigung, Ablehnung, Entzug, Ablauf) wird im Audit-Protokoll festgehalten.
