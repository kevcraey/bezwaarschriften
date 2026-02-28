# Tab UI: Documenten & Kernbezwaren

## Doel

De bezwaren-sectie opsplitsen in twee tabs: "Documenten" (bestaande tabel met bestanden en extractie-status) en "Kernbezwaren" (placeholder voor toekomstige functionaliteit). De documenten-tab toont live progress, loading-indicatie en foutentelling in de tab-titel.

## Beslissingen

- **Tabs in `project-selectie` component** - geen nieuwe wrapper-component nodig. De project-selectie beheert al de WebSocket-verbinding en alle state.
- **Kernbezwaren tab is placeholder** - toont statische tekst, inhoud volgt in latere story.
- **Progress telt alleen `extractie-klaar`** - bestanden met status `fout` tellen niet mee als verwerkt.
- **Loading indicator in tab-titel** - compact symbool (bv. `⏳`), geen vl-loader boven de tabel.
- **Tabs verschijnen pas na projectselectie** - zelfde gedrag als huidige bezwaren-sectie.
- **Geen backend-wijzigingen** - alle data komt al via bestaande endpoints en WebSocket.

## Component-structuur

```
<vl-select> (project dropdown)
<div#tabs-sectie hidden>
  <vl-tabs active-tab="documenten">
    <vl-tabs-pane id="documenten" title="Documenten (3/100) ⏳">
      <vl-button>Extraheer geselecteerde</vl-button>
      <p#fout-melding>
      <bezwaarschriften-bezwaren-tabel>
    </vl-tabs-pane>
    <vl-tabs-pane id="kernbezwaren" title="Kernbezwaren">
      <p>Kernbezwaren worden hier getoond na verwerking.</p>
    </vl-tabs-pane>
  </vl-tabs>
</div>
```

## Dynamische tab-titel "Documenten"

Samengesteld uit drie delen:

| Onderdeel | Wanneer | Voorbeeld |
|-----------|---------|-----------|
| Progress | Altijd na laden bestanden | `(3/100)` |
| Loading | Als er taken met status `wachtend` of `bezig` zijn | `⏳` |
| Fouten | Als er bestanden met status `fout` zijn | `⚠️N` |

Voorbeelden:
- `Documenten` - voor projectselectie
- `Documenten (0/12)` - net geladen, niets verwerkt
- `Documenten (0/12) ⏳` - extractie gestart
- `Documenten (3/12) ⏳` - 3 klaar, nog bezig
- `Documenten (10/12) ⚠️2` - 10 klaar, 2 fouten, verwerking klaar
- `Documenten (12/12)` - alles verwerkt, geen fouten

## Titel-update mechanisme

Na elke wijziging (bezwaren geladen, WebSocket taak-update, sync) wordt `_werkDocumentenTabTitelBij()` aangeroepen. Deze methode:

1. Telt `aantalKlaar` = bestanden met status `extractie-klaar`
2. Telt `aantalFout` = bestanden met status `fout`
3. Bepaalt `isBezig` = minstens 1 bestand met status `wachtend` of `bezig`
4. Berekent `totaal` = totaal aantal bestanden
5. Zet de `title` property van de documenten `vl-tabs-pane`

De bezwaren-tabel component exposed al de status per bestand intern. De project-selectie component houdt `this.__bezwaren` bij als bron voor de tellingen, aangevuld met taak-updates die de status wijzigen.

## Benodigde @domg-wc imports

```javascript
import {VlTabsComponent} from '@domg-wc/components/block/tabs/vl-tabs.component.js';
import {VlTabsPaneComponent} from '@domg-wc/components/block/tabs/vl-tabs-pane.component.js';
```

Registratie via `registerWebComponents([..., VlTabsComponent, VlTabsPaneComponent])`.
