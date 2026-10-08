---
title: Permessi sui campi
description: Nascondere, mascherare o impostare in sola lettura i campi controllati in base al ruolo, per esempio l’indirizzo e-mail dell’utente e la data dell’ultimo accesso.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

I permessi sui campi determinano come chi possiede un ruolo **vede e modifica** ogni campo controllato. Fai clic su **Permessi sui campi** sulla riga del ruolo per configurarli.

![Permessi sui campi](/screenshots/role-fields.png)

## Modalità di visualizzazione

| Modalità | Effetto |
| --- | --- |
| Visibile | Mostra il valore originale |
| Maschera | Nasconde in parte secondo la modalità di mascheramento: e-mail (conserva la prima lettera e il dominio), telefono (138\*\*\*5678), documento di identità (conserva i 6 caratteri iniziali e i 4 finali), conserva il primo e l’ultimo carattere, nasconde tutto |
| Nascosto | Non restituisce questo campo e la colonna non compare negli elenchi |

## Modalità di modifica

| Modalità | Effetto |
| --- | --- |
| Modificabile | Può essere compilato e modificato |
| Sola lettura | Disabilitato nel modulo; se viene modificato chiamando direttamente l’API, viene restituito un errore che indica di quale campo si tratta |

## Regole di combinazione

- I campi non configurati sono decisi dagli altri ruoli di chi li possiede; se nessun ruolo li configura, il campo è visibile e modificabile.
- Quando più ruoli configurano lo stesso campo, viene applicato quello **più permissivo** (visibile > mascherato > nascosto, modificabile > sola lettura).
- Anche la ricerca e l’esportazione rispettano i permessi sui campi: i campi nascosti non possono essere usati per la ricerca e, in fase di esportazione, vengono nascosti o mascherati secondo la regola.

## Campi controllati

I campi controllati sono dichiarati nel codice del server (al momento l’indirizzo e-mail dell’utente e la data dell’ultimo accesso) e in **Gestione della piattaforma → Catalogo di risorse** viene indicato in quali interfacce ciascuno di essi compare. Il controllo dei campi delle applicazioni di business può essere implementato dall’applicazione stessa e le regole si ottengono ugualmente dall’API aperta.
