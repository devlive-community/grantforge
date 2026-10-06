---
title: Funktionstrennung
description: Konfiguriere Rollen, die ein und dieselbe Person nicht gleichzeitig innehaben darf, verhindere Verstöße oder melde sie nur, und sieh dir die aktuellen Konflikte an.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

**Zugriffskontrolle → Funktionstrennung** konfiguriert Rollen, die ein und dieselbe Person nicht gleichzeitig innehaben darf (zum Beispiel Zahlung und Freigabe), und zeigt die Konten an, die aktuell gegen eine Bedingung verstoßen.

![Funktionstrennung](/screenshots/sod.png)

## Bedingungen

| Einstellung | Erläuterung |
| --- | --- |
| Sich ausschließende Rollen | 2–50 Rollen |
| Höchstzahl pro Konto | Standard 1; lässt sich einstellen als „von drei höchstens zwei“ |
| Modus | **Erzwingen**: Zuweisungen und Vererbungen, die einen Konflikt erzeugen, werden abgelehnt; **nur melden**: Änderungen sind erlaubt und erscheinen nur in der Konfliktliste |
| Aktiv | Eine deaktivierte Bedingung lehnt weder ab noch meldet sie |

„Innehaben“ umfasst alle Wege: direkte Zuweisung, den Weg über Gruppe, Abteilung und Stelle sowie über die Rollenvererbung. Zuweisungen außerhalb ihres Gültigkeitszeitraums und deaktivierte Rollen zählen nicht.

## Wann erzwungen wird

Im Modus Erzwingen werden die folgenden Änderungen vor dem Speichern für jedes davon berührte Konto geprüft:

- Rollen zuweisen oder den Gültigkeitszeitraum einer Zuweisung ändern bzw. ob sie Unterabteilungen einschließt;
- die Vererbungsbeziehung einer Rolle ändern;
- Berechtigungsanträge freigeben (siehe [Berechtigungsanträge und Freigaben](/de/guide/access-requests/)).

Abgelehnt werden nur Konflikte, die **durch diese Änderung neu entstehen**; der Hinweis nennt, wen es betrifft, welche Rollen beteiligt sind und welche Bedingung verletzt wird. Konflikte, die bereits vor dem Inkrafttreten der Bedingung bestanden, blockieren keine unzusammenhängenden Änderungen; sie bleiben in der Konfliktliste stehen und müssen manuell bearbeitet werden.

> [!WARNING]
> Wird jemand zu einer Gruppe, Abteilung oder Stelle hinzugefügt, findet diese Prüfung nicht statt. Auf diesem Weg entstandene Konflikte erscheinen in der Konfliktliste; sieh sie dir regelmäßig an.

## Konfliktliste

Auf der rechten Seite sind alle Konten aufgelistet, die gegen aktive Bedingungen verstoßen (unabhängig vom Modus): Konto, Bedingung, innegehabte Rollen und die erlaubte Höchstzahl.
