# Rich Data Table Migratie — Implementatieplan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Migreer de documententabel van handmatige `<vl-table>` naar `<vl-rich-data-table>` met client-side filtering, sorting en paginatie.

**Architecture:** Herschrijf `bezwaarschriften-bezwaren-tabel.js` volledig. De component behoudt dezelfde publieke API (bezwaren setter, werkBijMetTaakUpdate, geefGeselecteerdeBestandsnamen, selectie-gewijzigd event). Intern wordt een filter→sort→paginate pipeline toegevoegd die bij elke wijziging (filter, sort, paginatie, WebSocket-update) opnieuw draait.

**Tech Stack:** Lit 3 / @domg-wc 2.7.0 Web Components (BaseHTMLElement), vl-rich-data-table, vl-rich-data-field, vl-search-filter, vl-pager

**Design doc:** `docs/plans/2026-03-01-rich-data-table-design.md`

---

### Task 1: Basis rich-data-table met dataweergave

Vervang de huidige `<vl-table>` door `<vl-rich-data-table>` met de 4 kolommen. Nog geen filtering, sorting of paginatie — alleen correcte dataweergave.

**Files:**
- Modify: `webapp/src/js/bezwaarschriften-bezwaren-tabel.js` (volledig herschrijven)

**Step 1: Herschrijf imports en registratie**

Vervang de huidige imports door:

```javascript
import {BaseHTMLElement, defineWebComponent, registerWebComponents} from '@domg-wc/common';
import {VlRichDataTable} from '@domg-wc/components/block/rich-data-table/vl-rich-data-table.component.js';
import {VlRichDataField} from '@domg-wc/components/block/rich-data-table/vl-rich-data-field.component.js';
import {VlPillComponent} from '@domg-wc/components/block/pill/vl-pill.component.js';
import {vlGlobalStyles} from '@domg-wc/styles';

registerWebComponents([VlRichDataTable, VlRichDataField, VlPillComponent]);
```

**Step 2: Herschrijf constructor met rich-data-table HTML**

```javascript
constructor() {
  super(`
    <style>
      ${vlGlobalStyles}
      .status-cel { min-width: 220px; }
    </style>
    <vl-rich-data-table id="tabel">
      <vl-rich-data-field name="selectie" label=" "></vl-rich-data-field>
      <vl-rich-data-field name="bestandsnaam" label="Bestandsnaam"></vl-rich-data-field>
      <vl-rich-data-field name="aantalBezwaren" label="Aantal bezwaren"></vl-rich-data-field>
      <vl-rich-data-field name="status" label="Status"></vl-rich-data-field>
    </vl-rich-data-table>
  `);
  this.__bronBezwaren = [];
  this.__takenData = {};
  this.__timerInterval = null;
  this._projectNaam = null;
  this.__filters = {};
  this.__sorting = [];
  this.__paginaGrootte = 50;
  this.__huidigePagina = 1;
}
```

**Step 3: Stel custom renderers in tijdens connectedCallback**

```javascript
connectedCallback() {
  super.connectedCallback();
  this._configureerRenderers();
  this._herbereken();
}

_configureerRenderers() {
  const velden = this.shadowRoot.querySelectorAll('vl-rich-data-field');
  velden.forEach((veld) => {
    switch (veld.getAttribute('name')) {
      case 'selectie':
        veld.renderer = (td, rij) => {
          const cb = document.createElement('input');
          cb.type = 'checkbox';
          cb.className = 'rij-checkbox';
          cb.dataset.bestandsnaam = rij.bestandsnaam;
          if (this._isDisabled(rij.status)) cb.disabled = true;
          cb.addEventListener('change', () => this._dispatchSelectieGewijzigd());
          td.appendChild(cb);
        };
        break;
      case 'bestandsnaam':
        veld.renderer = (td, rij) => {
          if (this._projectNaam) {
            const a = document.createElement('a');
            a.href = `/api/v1/projects/${encodeURIComponent(this._projectNaam)}/bezwaren/${encodeURIComponent(rij.bestandsnaam)}/download`;
            a.download = rij.bestandsnaam;
            a.textContent = rij.bestandsnaam;
            td.appendChild(a);
          } else {
            td.textContent = rij.bestandsnaam;
          }
        };
        break;
      case 'aantalBezwaren':
        veld.renderer = (td, rij) => {
          td.textContent = rij.aantalBezwaren != null ? rij.aantalBezwaren : '';
        };
        break;
      case 'status':
        veld.renderer = (td, rij) => {
          td.className = 'status-cel';
          td.dataset.bestandsnaam = rij.bestandsnaam;
          const pill = document.createElement('vl-pill');
          const type = STATUS_PILL_TYPES[rij.status] || '';
          if (type) pill.setAttribute('type', type);
          if (rij.status === 'niet ondersteund') pill.setAttribute('disabled', '');
          pill.textContent = this._formatStatusLabel(rij);
          td.appendChild(pill);
        };
        break;
    }
  });
}
```

**Step 4: Implementeer de herbereken-pipeline (initieel zonder filter/sort)**

```javascript
set bezwaren(waarde) {
  this.__bronBezwaren = waarde || [];
  this.__huidigePagina = 1;
  this._herbereken();
}

get bezwaren() {
  return this.__bronBezwaren;
}

_herbereken() {
  const tabel = this.shadowRoot && this.shadowRoot.querySelector('#tabel');
  if (!tabel) return;

  let resultaat = [...this.__bronBezwaren];
  // Filter en sort worden in latere taken toegevoegd
  tabel.data = {data: resultaat};
  this._dispatchSelectieGewijzigd();
  this._beheerTimer();
}
```

**Step 5: Behoud bestaande hulpmethodes**

Kopieer ongewijzigd: `projectNaam` getter/setter, `werkBijMetTaakUpdate()`, `geefGeselecteerdeBestandsnamen()`, `_isDisabled()`, `_beheerTimer()`, `_stopTimer()`, `_updateTimers()`, `_formatStatusLabel()`, `_formatTijd()`, `_dispatchSelectieGewijzigd()`, `disconnectedCallback()`.

Pas `werkBijMetTaakUpdate()` aan om `_herbereken()` te callen i.p.v. `_renderRijen()`.

Pas `_updateTimers()` aan: zoek pills via `td.dataset.bestandsnaam` op het `<vl-rich-data-table>` element (de tabel rendert in zijn eigen shadow DOM, maar de `renderer` functie vult `<td>` elementen die in die shadow DOM leven).

**Step 6: Verwijder niet meer gebruikte methodes**

Verwijder: `_renderRijen()`, `_renderBestandsnaam()`, `_formatStatus()`, `_escapeHtml()`.

**Step 7: Build en handmatig testen**

Run: `cd webapp && npm run build`
Expected: Build slaagt zonder fouten.

Run: `mvn process-resources -pl webapp -Denforcer.skip=true` (vanuit project root)
Expected: Succesvol.

Handmatige test in browser:
- Open de app, selecteer een project
- Tabel toont alle bezwaren met bestandsnaam (klikbare download-link), aantal bezwaren, en status (pill)
- Checkboxes werken (selecteren, deselecteren)
- Status-pills tonen correcte kleuren per status

**Step 8: Commit**

```bash
git add webapp/src/js/bezwaarschriften-bezwaren-tabel.js
git commit -m "refactor: migreer documententabel naar vl-rich-data-table

Vervangt handmatige vl-table door vl-rich-data-table met custom
renderers voor checkbox, bestandsnaam, en status-kolommen."
```

---

### Task 2: Client-side filtering

Voeg het filterformulier toe met bestandsnaam-zoekbalk en status-dropdown.

**Files:**
- Modify: `webapp/src/js/bezwaarschriften-bezwaren-tabel.js`

**Step 1: Voeg search-filter import toe**

```javascript
import {VlSearchFilterComponent} from '@domg-wc/components/block/search-filter/vl-search-filter.component.js';

registerWebComponents([VlRichDataTable, VlRichDataField, VlPillComponent, VlSearchFilterComponent]);
```

**Step 2: Voeg filter HTML toe aan constructor**

Voeg toe als eerste child van `<vl-rich-data-table>`, voor de `<vl-rich-data-field>` elementen:

```html
<vl-search-filter slot="filter" filter-title="Filters">
  <form id="filter-form">
    <label>
      Bestandsnaam
      <input type="text" name="bestandsnaam" placeholder="Zoek op bestandsnaam...">
    </label>
    <label>
      Status
      <select name="status">
        <option value="">Alle statussen</option>
        <option value="todo">Te verwerken</option>
        <option value="wachtend">Wachtend</option>
        <option value="bezig">Bezig</option>
        <option value="extractie-klaar">Extractie klaar</option>
        <option value="fout">Fout</option>
        <option value="niet ondersteund">Niet ondersteund</option>
      </select>
    </label>
  </form>
</vl-search-filter>
```

**Step 3: Luister naar change-events op de tabel**

In `connectedCallback()`, na `_configureerRenderers()`:

```javascript
const tabel = this.shadowRoot.querySelector('#tabel');
if (tabel) {
  tabel.addEventListener('change', (e) => this._onTabelChange(e));
}
```

**Step 4: Implementeer de change-handler**

```javascript
_onTabelChange(event) {
  const detail = event.detail || {};

  // Filter state uit formData
  if (detail.formData) {
    this.__filters = {};
    for (const [key, value] of detail.formData.entries()) {
      if (value) this.__filters[key] = value;
    }
  } else {
    this.__filters = {};
  }

  // Sorteer state (wordt in task 3 uitgebreid)
  if (detail.sorting) {
    this.__sorting = detail.sorting;
  }

  // Bij filter-wijziging: reset naar pagina 1
  this.__huidigePagina = 1;
  this._herbereken();
}
```

**Step 5: Voeg filterlogica toe aan _herbereken()**

```javascript
_herbereken() {
  const tabel = this.shadowRoot && this.shadowRoot.querySelector('#tabel');
  if (!tabel) return;

  let resultaat = this._filterBezwaren(this.__bronBezwaren, this.__filters);
  // Sorting wordt in task 3 toegevoegd
  tabel.data = {data: resultaat};
  this._dispatchSelectieGewijzigd();
  this._beheerTimer();
}

_filterBezwaren(bezwaren, filters) {
  return bezwaren.filter((b) => {
    if (filters.bestandsnaam &&
        !b.bestandsnaam.toLowerCase().includes(filters.bestandsnaam.toLowerCase())) {
      return false;
    }
    if (filters.status && b.status !== filters.status) {
      return false;
    }
    return true;
  });
}
```

**Step 6: Build en handmatig testen**

Run: `cd webapp && npm run build`

Handmatige test:
- Filter op bestandsnaam: typ een deel van een bestandsnaam, tabel filtert live
- Filter op status: kies "Fout" in dropdown, alleen rijen met status "fout" zichtbaar
- Combineer beide filters
- Reset filter: wis invoer, alle rijen verschijnen weer

**Step 7: Commit**

```bash
git add webapp/src/js/bezwaarschriften-bezwaren-tabel.js
git commit -m "feat: voeg filtering toe aan documententabel

Client-side filtering op bestandsnaam (vrije tekst, case-insensitive)
en status (dropdown met alle 6 statussen)."
```

---

### Task 3: Client-side sorting

Maak de drie datakolommen sorteerbaar.

**Files:**
- Modify: `webapp/src/js/bezwaarschriften-bezwaren-tabel.js`

**Step 1: Voeg `sortable` attribuut toe aan velden**

In de constructor HTML, wijzig de drie datakolommen:

```html
<vl-rich-data-field name="bestandsnaam" label="Bestandsnaam" sortable></vl-rich-data-field>
<vl-rich-data-field name="aantalBezwaren" label="Aantal bezwaren" sortable></vl-rich-data-field>
<vl-rich-data-field name="status" label="Status" sortable></vl-rich-data-field>
```

**Step 2: Voeg sorteerlogica toe aan _herbereken()**

```javascript
_herbereken() {
  const tabel = this.shadowRoot && this.shadowRoot.querySelector('#tabel');
  if (!tabel) return;

  let resultaat = this._filterBezwaren(this.__bronBezwaren, this.__filters);
  resultaat = this._sorteerBezwaren(resultaat, this.__sorting);
  // Paginatie wordt in task 4 toegevoegd
  tabel.data = {data: resultaat};
  this._dispatchSelectieGewijzigd();
  this._beheerTimer();
}

_sorteerBezwaren(bezwaren, sorting) {
  if (!sorting || sorting.length === 0) return bezwaren;

  const statusVolgorde = {
    'todo': 0, 'wachtend': 1, 'bezig': 2,
    'extractie-klaar': 3, 'fout': 4, 'niet ondersteund': 5,
  };

  return [...bezwaren].sort((a, b) => {
    for (const sort of sorting) {
      let cmp = 0;
      if (sort.name === 'status') {
        cmp = (statusVolgorde[a.status] ?? 99) - (statusVolgorde[b.status] ?? 99);
      } else if (sort.name === 'aantalBezwaren') {
        cmp = (a.aantalBezwaren ?? 0) - (b.aantalBezwaren ?? 0);
      } else {
        const valA = a[sort.name] ?? '';
        const valB = b[sort.name] ?? '';
        cmp = String(valA).localeCompare(String(valB), 'nl');
      }
      if (cmp !== 0) return sort.direction === 'asc' ? cmp : -cmp;
    }
    return 0;
  });
}
```

**Step 3: Bewaar sorting state in _onTabelChange**

De sorting state wordt al gelezen in `_onTabelChange()` (stap 4 van task 2). Controleer dat `detail.sorting` correct wordt opgeslagen in `this.__sorting`.

**Step 4: Build en handmatig testen**

Run: `cd webapp && npm run build`

Handmatige test:
- Klik op kolomheader "Bestandsnaam": rijen gesorteerd A→Z
- Klik nogmaals: Z→A
- Klik nogmaals: geen sortering (standaard volgorde)
- Klik op "Aantal bezwaren": numeriek gesorteerd
- Klik op "Status": gesorteerd op vaste volgorde (todo → niet ondersteund)
- Sorteer + filter combinatie: sorteer op naam, filter op status "fout", resultaat is gefilterd en gesorteerd

**Step 5: Commit**

```bash
git add webapp/src/js/bezwaarschriften-bezwaren-tabel.js
git commit -m "feat: voeg sorting toe aan documententabel

Sorteerbaar op bestandsnaam (alfabetisch NL), aantal bezwaren
(numeriek), en status (vaste volgorde). Klik cycled: asc → desc → uit."
```

---

### Task 4: Paginatie met paginagrootte-keuze

Voeg `<vl-pager>` toe en een dropdown voor paginagrootte (50/100/alle).

**Files:**
- Modify: `webapp/src/js/bezwaarschriften-bezwaren-tabel.js`

**Step 1: Voeg pager import toe**

```javascript
import {VlPagerComponent} from '@domg-wc/components/block/pager/vl-pager.component.js';

// Voeg VlPagerComponent toe aan registerWebComponents array
```

**Step 2: Voeg pager en paginagrootte-selector toe aan constructor HTML**

Voeg toe als laatste child van `<vl-rich-data-table>`:

```html
<div slot="pager" id="pager-wrapper">
  <div style="display: flex; align-items: center; gap: 16px; flex-wrap: wrap;">
    <vl-pager id="pager" items-per-page="50" current-page="1" total-items="0"></vl-pager>
    <label>
      Per pagina:
      <select id="pagina-grootte">
        <option value="50" selected>50</option>
        <option value="100">100</option>
        <option value="alle">Alle</option>
      </select>
    </label>
  </div>
</div>
```

**Step 3: Luister naar paginagrootte-wijzigingen**

In `connectedCallback()`:

```javascript
const paginaGrootteSelect = this.shadowRoot.querySelector('#pagina-grootte');
if (paginaGrootteSelect) {
  paginaGrootteSelect.addEventListener('change', (e) => {
    const waarde = e.target.value;
    this.__paginaGrootte = waarde === 'alle' ? Infinity : parseInt(waarde, 10);
    this.__huidigePagina = 1;
    this._herbereken();
  });
}
```

**Step 4: Voeg paginatie toe aan _herbereken()**

```javascript
_herbereken() {
  const tabel = this.shadowRoot && this.shadowRoot.querySelector('#tabel');
  if (!tabel) return;

  let resultaat = this._filterBezwaren(this.__bronBezwaren, this.__filters);
  resultaat = this._sorteerBezwaren(resultaat, this.__sorting);

  const totaal = resultaat.length;
  const paginaGrootte = this.__paginaGrootte === Infinity ? totaal : this.__paginaGrootte;
  const totalePaginas = paginaGrootte > 0 ? Math.ceil(totaal / paginaGrootte) : 1;
  this.__huidigePagina = Math.max(1, Math.min(this.__huidigePagina, totalePaginas));

  const start = (this.__huidigePagina - 1) * paginaGrootte;
  const pagina = paginaGrootte === totaal ? resultaat : resultaat.slice(start, start + paginaGrootte);

  // Werk pager bij
  const pager = this.shadowRoot.querySelector('#pager');
  if (pager) {
    pager.setAttribute('total-items', String(totaal));
    pager.setAttribute('items-per-page', String(paginaGrootte > totaal ? totaal : paginaGrootte));
    pager.setAttribute('current-page', String(this.__huidigePagina));
    if (this.__paginaGrootte === Infinity) {
      pager.setAttribute('pagination-disabled', '');
    } else {
      pager.removeAttribute('pagination-disabled');
    }
  }

  tabel.data = {data: pagina};
  this._dispatchSelectieGewijzigd();
  this._beheerTimer();
}
```

**Step 5: Verwerk paginatie-events in _onTabelChange()**

Voeg paging-verwerking toe:

```javascript
_onTabelChange(event) {
  const detail = event.detail || {};

  if (detail.formData) {
    this.__filters = {};
    for (const [key, value] of detail.formData.entries()) {
      if (value) this.__filters[key] = value;
    }
    this.__huidigePagina = 1; // Reset bij filter-wijziging
  }

  if (detail.sorting) {
    this.__sorting = detail.sorting;
  }

  if (detail.paging && detail.paging.currentPage) {
    this.__huidigePagina = detail.paging.currentPage;
  }

  this._herbereken();
}
```

**Step 6: Build en handmatig testen**

Run: `cd webapp && npm run build`

Handmatige test:
- Bij >50 bezwaren: pager verschijnt onderaan
- Klik op volgende pagina: tabel toont volgende 50
- Wijzig paginagrootte naar 100: tabel toont 100 per pagina
- Wijzig naar "Alle": hele lijst zichtbaar, pager verdwijnt
- Filter + paginatie: filter resultaten, paginatie past zich aan
- Filter wijzigen reset pagina naar 1

**Step 7: Commit**

```bash
git add webapp/src/js/bezwaarschriften-bezwaren-tabel.js
git commit -m "feat: voeg paginatie toe aan documententabel

Pager met 50/100/alle per pagina. Reset naar pagina 1 bij
filter-wijziging. Verbergt paginatie bij 'alle'."
```

---

### Task 5: WebSocket-integratie en timer-logica

Zorg dat `werkBijMetTaakUpdate()` correct werkt met de filter/sort/paginate pipeline, en dat de timer-logica (wachtend/bezig) blijft functioneren.

**Files:**
- Modify: `webapp/src/js/bezwaarschriften-bezwaren-tabel.js`

**Step 1: Pas werkBijMetTaakUpdate aan**

```javascript
werkBijMetTaakUpdate(taak) {
  this.__takenData[taak.bestandsnaam] = {
    aangemaaktOp: taak.aangemaaktOp,
    verwerkingGestartOp: taak.verwerkingGestartOp,
  };
  this.__bronBezwaren = this.__bronBezwaren.map((b) =>
    b.bestandsnaam === taak.bestandsnaam ? {
      bestandsnaam: taak.bestandsnaam,
      status: taak.status,
      aantalWoorden: taak.aantalWoorden,
      aantalBezwaren: taak.aantalBezwaren,
    } : b,
  );
  this._herbereken();
}
```

**Step 2: Fix _updateTimers() voor rich-data-table DOM-structuur**

De `vl-rich-data-table` rendert `<td>` elementen in zijn eigen shadow DOM. De renderers krijgen een `td` element mee en vullen dat. We moeten de juiste plek zoeken om de pill-tekst te updaten.

De `status` renderer zet `td.dataset.bestandsnaam`. De `_updateTimers()` moet zoeken in de rich-data-table's interne table:

```javascript
_updateTimers() {
  const nu = Date.now();
  const tabel = this.shadowRoot && this.shadowRoot.querySelector('#tabel');
  if (!tabel) return;

  // De td elementen zitten in de vl-rich-data-table's shadow DOM
  const innerTable = tabel.shadowRoot && tabel.shadowRoot.querySelector('vl-table');
  const tableEl = innerTable && innerTable.shadowRoot && innerTable.shadowRoot.querySelector('table');
  if (!tableEl) return;

  this.__bronBezwaren.forEach((b) => {
    if (b.status !== 'wachtend' && b.status !== 'bezig') return;
    const cel = tableEl.querySelector(
        `td[data-bestandsnaam="${CSS.escape(b.bestandsnaam)}"]`,
    );
    if (!cel) return;
    const pill = cel.querySelector('vl-pill');
    if (pill) {
      pill.textContent = this._formatStatusLabel(b, nu);
    }
  });
}
```

Merk op: de exacte DOM-structuur van vl-rich-data-table moet getest worden. Als de `<td>` elementen niet in een geneste shadow DOM zitten maar direct in de light DOM van de component, dan is de query eenvoudiger. **Test dit handmatig in de browser devtools en pas de selector aan.**

**Step 3: Fix _beheerTimer() om bronBezwaren te gebruiken**

```javascript
_beheerTimer() {
  const heeftActief = this.__bronBezwaren.some(
    (b) => b.status === 'wachtend' || b.status === 'bezig',
  );
  if (heeftActief && !this.__timerInterval) {
    this.__timerInterval = setInterval(() => this._updateTimers(), 1000);
  } else if (!heeftActief && this.__timerInterval) {
    this._stopTimer();
  }
}
```

**Step 4: Fix geefGeselecteerdeBestandsnamen() voor rich-data-table DOM**

De checkboxes worden gerenderd via de renderer in de rich-data-table's interne DOM. Pas de query aan:

```javascript
geefGeselecteerdeBestandsnamen() {
  const tabel = this.shadowRoot && this.shadowRoot.querySelector('#tabel');
  if (!tabel) return [];
  // Zoek in de inner table van rich-data-table
  const innerTable = tabel.shadowRoot && tabel.shadowRoot.querySelector('vl-table');
  const tableEl = innerTable && innerTable.shadowRoot && innerTable.shadowRoot.querySelector('table');
  if (!tableEl) return [];
  const checkboxes = tableEl.querySelectorAll('.rij-checkbox:checked');
  return Array.from(checkboxes).map((cb) => cb.dataset.bestandsnaam);
}
```

**Step 5: Build en handmatig testen**

Run: `cd webapp && npm run build`

Handmatige test:
- Dien extracties in voor enkele bestanden
- Status wijzigt live van "Te verwerken" → "Wachtend (0:05)" → "Bezig (0:10 + 0:03)" → "Extractie klaar"
- Timer telt elke seconde op
- Checkboxes: selecteer bestanden, klik verwerken, geselecteerde bestandsnamen worden correct doorgegeven
- Filter actief tijdens WebSocket-update: gefilterde rij die van status verandert, verdwijnt/verschijnt correct

**Step 6: Commit**

```bash
git add webapp/src/js/bezwaarschriften-bezwaren-tabel.js
git commit -m "feat: WebSocket-updates en timer-logica voor rich-data-table

werkBijMetTaakUpdate draait de filter/sort/paginate pipeline opnieuw.
Timer-logica vindt pills via rich-data-table shadow DOM."
```

---

### Task 6: Selecteer-alles checkbox

Voeg een "selecteer alles" checkbox toe in de header van de selectie-kolom.

**Files:**
- Modify: `webapp/src/js/bezwaarschriften-bezwaren-tabel.js`

**Step 1: Voeg selecteer-alles toe via de label-template**

In de constructor HTML, pas het selectie-veld aan:

```html
<vl-rich-data-field name="selectie">
  <template slot="label"><input type="checkbox" id="selecteer-alles" title="Selecteer alles"></template>
</vl-rich-data-field>
```

Opmerking: als het `<template slot="label">` niet werkt in de shadow DOM context, gebruik dan de `renderer` approach voor de header. In dat geval moet de checkbox handmatig in de `<th>` worden geplaatst na rendering. **Test dit. Als templates niet werken, voeg de checkbox toe via `_configureerRenderers()` door de `<th>` te querien na eerste render.**

**Step 2: Koppel selecteer-alles event listener**

In `connectedCallback()`:

```javascript
// Wacht tot de rich-data-table gerenderd is (kan async zijn)
requestAnimationFrame(() => {
  this._koppelSelecteerAlles();
});
```

```javascript
_koppelSelecteerAlles() {
  // Zoek de selecteer-alles checkbox (kan in shadow DOM van rich-data-table zitten)
  const tabel = this.shadowRoot.querySelector('#tabel');
  if (!tabel) return;

  // De template slot="label" zou de checkbox in de header moeten plaatsen
  // Exacte locatie moet getest worden in browser devtools
  const selecteerAlles = tabel.querySelector('#selecteer-alles') ||
    (tabel.shadowRoot && tabel.shadowRoot.querySelector('#selecteer-alles'));

  if (selecteerAlles) {
    selecteerAlles.addEventListener('change', (e) => {
      const checked = e.target.checked;
      const innerTable = tabel.shadowRoot && tabel.shadowRoot.querySelector('vl-table');
      const tableEl = innerTable && innerTable.shadowRoot && innerTable.shadowRoot.querySelector('table');
      if (tableEl) {
        tableEl.querySelectorAll('.rij-checkbox:not([disabled])').forEach((cb) => {
          cb.checked = checked;
        });
      }
      this._dispatchSelectieGewijzigd();
    });
  }
}
```

**Step 3: Reset selecteer-alles bij herbereken**

In `_herbereken()`, na `tabel.data = ...`:

```javascript
// Reset selecteer-alles checkbox
requestAnimationFrame(() => {
  const selecteerAlles = /* query zoals in step 2 */;
  if (selecteerAlles) selecteerAlles.checked = false;
});
```

**Step 4: Build en handmatig testen**

Run: `cd webapp && npm run build`

Handmatige test:
- "Selecteer alles" checkbox in header selecteert alle niet-disabled checkboxes op huidige pagina
- Deselecteer: alle checkboxes uit
- Disabled rijen (wachtend/bezig/niet ondersteund) worden niet geselecteerd
- Bij paginawissel: selecteer-alles reset

**Step 5: Commit**

```bash
git add webapp/src/js/bezwaarschriften-bezwaren-tabel.js
git commit -m "feat: selecteer-alles checkbox in documententabel header

Selecteert alle niet-disabled rijen op de huidige pagina.
Reset bij paginawissel of her-render."
```

---

### Task 7: Eindcontrole en opruimen

Laatste check: alle features werken samen, parent component hoeft niet te wijzigen.

**Files:**
- Review: `webapp/src/js/bezwaarschriften-bezwaren-tabel.js`
- Review: `webapp/src/js/bezwaarschriften-project-selectie.js`

**Step 1: Controleer publieke API compatibiliteit**

Verifieer dat deze members nog bestaan en correct werken:
- `bezwaren` setter → zet `__bronBezwaren`, roept `_herbereken()` aan
- `projectNaam` getter/setter → gebruikt in bestandsnaam-renderer
- `werkBijMetTaakUpdate(taak)` → update `__bronBezwaren`, roept `_herbereken()` aan
- `geefGeselecteerdeBestandsnamen()` → returnt array van geselecteerde bestandsnamen
- `selectie-gewijzigd` event → dispatcht bij elke selectie-wijziging

**Step 2: Controleer of parent component ongewijzigd blijft**

`bezwaarschriften-project-selectie.js` moet zonder wijzigingen werken. De volgende interacties moeten functioneren:
- `tabel.projectNaam = this.__geselecteerdProject`
- `tabel.bezwaren = this.__bezwaren`
- `tabel.werkBijMetTaakUpdate(taak)`
- `tabel.geefGeselecteerdeBestandsnamen()`
- Luistert naar `selectie-gewijzigd` event

**Step 3: Volledige handmatige test**

1. Open app, selecteer project → tabel laadt
2. Filter op bestandsnaam → live filtering
3. Filter op status → dropdown filtering
4. Klik op kolomheader → sorting
5. Wissel pagina → paginatie
6. Wijzig paginagrootte → 50/100/alle
7. Selecteer bestanden → verwerken-knop verschijnt
8. Selecteer alles → alle zichtbare niet-disabled geselecteerd
9. Klik verwerken → extracties worden ingediend
10. WebSocket status-updates → timers tellen, status-pills updaten
11. Upload bestanden → tabel refresht met nieuwe bezwaren
12. Verwijder bestanden → tabel refresht

**Step 4: Build productie**

Run: `cd webapp && npm run build && cd .. && mvn process-resources -pl webapp -Denforcer.skip=true`

**Step 5: Commit (indien wijzigingen nodig waren)**

```bash
git add webapp/src/js/bezwaarschriften-bezwaren-tabel.js
git commit -m "fix: eindcontrole rich-data-table migratie"
```