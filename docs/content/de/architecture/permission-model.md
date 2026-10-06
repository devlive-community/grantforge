---
title: Berechtigungsmodell
description: Die genaue Semantik von Ressourcen, Berechtigungsableitung, Vererbung und Zuweisung sowie von Daten- und Feldregeln, dazu Berechtigungs-Snapshots und Versionen.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Dieser Artikel beschreibt die genauen Regeln der Auswertung. Eine Einführung in die Begriffe findest du unter [Grundbegriffe](/de/start/concepts/).

## Ressourcenbaum und -typen

Ressourcen gehören zu einer Anwendung, sind als Baum organisiert und höchstens 15 Ebenen tief. Der Typ bestimmt, wo sie liegen dürfen:

| Typ | Erlaubte Eltern |
| --- | --- |
| Modul | oberste Ebene, Modul |
| Menü, Seite | oberste Ebene, Modul, Menü |
| Registerkarte | Seite, Registerkarte |
| Schaltfläche | Seite, Registerkarte |
| API, Datenentität | oberste Ebene, Modul |
| Feld | Datenentität |

Zwischen Ressourcen können Abhängigkeiten deklariert werden: „erforderlich“ (beim Berechtigen der Ressource wird die abhängige Ressource mit berechtigt) oder „optional“ (der Administrator wird nur hingewiesen, automatisch berechtigt wird nichts); Abhängigkeiten dürfen keine Zyklen bilden. Die Seiten und Schaltflächen der Konsole sowie die APIs, die sie benötigen, werden durch das Berechtigungsmanifest des Frontends deklariert und beim Start des Servers als eingebaute Ressourcen synchronisiert.

## Berechtigungsableitung

Für ein Konto bestimmt die Auswertung zuerst die **effektiven Rollen**:

1. direkt dem Konto zugewiesene Rollen;
2. Rollen, die den Gruppen und Abteilungen des Kontos (einschließlich der Zuweisungen übergeordneter Abteilungen mit „inklusive Unterabteilungen“) sowie den bekleideten Stellen des Kontos zugewiesen sind;
3. dabei zählen nur Zuweisungen innerhalb ihres Gültigkeitszeitraums zum aktuellen Zeitpunkt sowie aktivierte Rollen;
4. und es wird die Vererbung aufgelöst: alle Vorfahrenrollen einer Rolle (deaktivierte Vorfahren werden nicht weitergegeben).

Danach werden die Berechtigungen dieser Rollen zusammengeführt:

- Eine Ressource erlauben bedeutet, sie selbst, ihre Vorfahren im Baum (damit sie sichtbar ist) sowie ihre „erforderlich“ abhängigen Ressourcen (rekursiv) zu erlauben.
- Eine Ressource verweigern wirkt auf sie und alle ihre Unterelemente und hat **Vorrang vor jedem Erlauben**.
- Deaktivierte Ressourcen und ihre Unterelemente wirken nicht.
- Systemrollen entsprechen dem Erlauben des gesamten Teilbaums ihres Moduls (zum Beispiel `system`, `data`, `platform`).

Das Ergebnis ist die Menge der verfügbaren Ressourcen sowie die zu den API-Ressourcen darin gehörenden Berechtigungscodes.

## Schutz gegen Berechtigungsausweitung

- Beim Erteilen eines „Erlaubens“ muss der Erteilende die Ressource selbst nutzen können (Inhaber einer Systemrolle sind bei Geschäftsanwendungen davon ausgenommen).
- Beim Zuweisen von Rollen oder beim Festlegen der Vererbung muss der Erteilende alle Ressourcen abdecken, die die Rolle in den einzelnen Anwendungen abdeckt.
- Die Plattformadministrator-Rolle kann nur von ihrem aktuellen Inhaber zugewiesen, geändert oder entfernt werden.
- „Verweigern“ unterliegt keiner Einschränkung: Jeder, der Berechtigungen erteilen darf, kann Berechtigungen damit verschärfen.

## Datenregeln

Datenrichtlinien gehören zu Rollen und legen den Bereich nach „Entität × Aktion × Wirkung“ fest: `ALL` (nur Plattform-Mandant), `TENANT`, `ORG_AND_CHILDREN`, `ORG`, `CUSTOM_ORGS`, `SELF`, `CONDITION`. Eine Zeile ist verfügbar, wenn sie mindestens eine Erlauben-Regel erfüllt und keine Verweigern-Regel erfüllt.

Bedingungen sind strukturiertes JSON, nicht ausführbar, und werden vor dem Speichern validiert:

```json
{ "and": [
  { "field": "status", "op": "eq", "value": "ACTIVE" },
  { "not": { "field": "orgUnitId", "op": "in", "value": { "var": "subject.orgUnitIds" } } }
] }
```

Als Variablen stehen nur `subject.id`, `subject.username`, `subject.orgUnitIds`, `subject.groupCodes`, `subject.positionCodes` und `now` zur Verfügung; die Vergleichsoperatoren sind auf den Feldtyp beschränkt; die Verschachtelung umfasst höchstens 5 Ebenen und höchstens 50 Knoten. Der Server übersetzt die Regeln in ein JPA-`Specification`, und das SDK übersetzt sie in der Geschäftsanwendung mit derselben Semantik.

## Feldregeln

Feldrichtlinien legen die Leseart (sichtbar, maskiert, ausgeblendet) und die Schreibart (bearbeitbar, schreibgeschützt) fest; bei mehreren Rollen gilt die lockerste Einstellung, und ohne jede Einstellung ist das Feld vollständig offen. Leseregeln werden bei der JSON-Serialisierung angewendet (dasselbe DTO erscheint bei verschiedenen Personen unterschiedlich), Schreibregeln in der Serviceschicht; das Ändern eines schreibgeschützten Feldes gibt `GF-FIELD-001` zurück und nennt das Feld.

## Snapshot und Versionen

Das Ergebnis der Auswertung ist ein **Berechtigungs-Snapshot**: verfügbare Ressourcen, Berechtigungscodes, Datenregeln und Feldregeln. Der Snapshot wird pro Konto zwischengespeichert; jede Änderung an Berechtigungen, Zuweisungen, Vererbung, Ressourcenkatalog, Richtlinien oder Mitgliedschaften erhöht die Versionsnummer des betroffenen Bereichs (Katalog oder Mandant) und macht den Cache ungültig. Jede berechtigungsabhängige Schnittstelle gibt die aktuelle Version im Antwortheader `X-Authorization-Version` zurück; die Konsole entscheidet daran, ob die Menüs neu geladen werden müssen; die offene API drückt dieselbe Version per ETag aus.
