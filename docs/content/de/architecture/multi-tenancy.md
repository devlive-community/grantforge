---
title: Mehrmandantenfähigkeit und Datentrennung
description: Wie Mandanten Daten trennen, welche Daten plattformweit geteilt sind und welche Regeln für mandantenübergreifende Aktionen gelten.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

## Art der Trennung

Bis auf die plattformweit geteilten Daten hat jede Geschäftstabelle eine Spalte `tenant_id`. Geht eine Anfrage ein, bindet die Filterkette den Mandanten des angemeldeten Kontos an den aktuellen Thread; der Mandantenfilter von Hibernate ergänzt Abfragen automatisch um die Mandantenbedingung und füllt beim Schreiben `tenant_id` aus. Geschäftscode kann das Mandantenkriterium nicht „vergessen“.

Die wenigen Szenarien, die Mandanten übergreifen (zum Beispiel das Finden eines Kontos über den Benutzernamen bei der Anmeldung, das Zählen der Konten je Mandant, Hintergrundjobs), müssen explizit in den „Systemkontext“ wechseln; im Code Review sind sie sofort zu erkennen.

## Geteilt und getrennt

| Plattformweit geteilt | je Mandant eigen |
| --- | --- |
| Anwendungen und Ressourcenkatalog, API-Katalog, OAuth-Clients, Plug-ins | Konten, Abteilungen, Gruppen, Stellen, Rollen, Berechtigungen, Zuweisungen, Daten- und Feldrichtlinien, Funktionstrennung, Antrag und Berechtigungsprüfung, Identitätsquellen, Datendienste, Audit |

Benutzernamen sind plattformweit eindeutig, deshalb muss bei der Anmeldung kein Mandant gewählt werden.

## Plattform-Mandant

Der Plattform-Mandant wird bei der Initialisierung angelegt und kann nicht deaktiviert werden. Seine Administratoren verwalten die plattformweit geteilten Daten und die anderen Mandanten; nur Rollen des Plattform-Mandanten können den Datenbereich „alle Mandanten“ nutzen.

## ID

Alle Primärschlüssel sind TSIDs: 64 Bit, zeitlich geordnet und im Cluster über die Knotennummer eindeutig. Sie liegen über dem, was JavaScript genau darstellen kann, und werden deshalb in JSON immer als Zeichenkette übergeben. Jede Instanz im Cluster braucht ein eigenes `GRANTFORGE_ID_NODE`.
