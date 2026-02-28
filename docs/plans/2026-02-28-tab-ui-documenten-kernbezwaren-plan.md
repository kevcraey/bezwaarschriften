# Tab UI: Documenten & Kernbezwaren Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Split the bezwaren-sectie into two tabs (Documenten with live progress/loading/errors in tab title, Kernbezwaren as placeholder).

**Architecture:** Purely frontend change. Modify `bezwaarschriften-project-selectie.js` to use `vl-tabs` + `vl-tabs-pane` instead of a plain div. Add a `_werkDocumentenTabTitelBij()` method that computes the tab title from bezwaren statuses. No backend changes.

**Tech Stack:** @domg-wc/components (VlTabsComponent, VlTabsPaneComponent), Shadow DOM web components, Lit BaseHTMLElement

---

### Context: Key Files

- `webapp/src/js/bezwaarschriften-project-selectie.js` — The only file to modify. Contains the parent component that manages project selection, bezwaren loading, WebSocket, and extractie-indienen.
- `webapp/src/js/bezwaarschriften-bezwaren-tabel.js` — Child table component. **Not modified** but important to understand: it tracks `this.__bezwaren` with statuses per bestand and updates them via `werkBijMetTaakUpdate(taak)`.
- Design: `docs/plans/2026-02-28-tab-ui-documenten-kernbezwaren-design.md`

### Context: vl-tabs Dynamic Title API

The `vl-tabs-pane` supports dynamic title updates **only when** the parent `<vl-tabs>` has the `observe-title` attribute. When you set `pane.setAttribute('title', newTitle)`, it triggers `titleChangedCallback()` which directly updates the tab header in the parent's shadow DOM.

```html
<vl-tabs observe-title active-tab="documenten">
  <vl-tabs-pane id="documenten" title="Documenten">...</vl-tabs-pane>
</vl-tabs>
```

```javascript
// Dynamically update title:
const pane = this.shadowRoot.querySelector('#documenten');
pane.setAttribute('title', 'Documenten (3/12) ⏳');
```

### Context: Current Template (to be replaced)

```html
<div id="selectie-wrapper">
  <vl-select id="project-select" placeholder="Kies een project..."></vl-select>
</div>
<div id="bezwaren-sectie" hidden>
  <h2>Bezwaarschriften</h2>
  <vl-button id="extraheer-knop" disabled>Extraheer geselecteerde</vl-button>
  <p id="fout-melding" hidden></p>
  <bezwaarschriften-bezwaren-tabel id="bezwaren-tabel"></bezwaarschriften-bezwaren-tabel>
</div>
```

### Context: Status Values

Bezwaren have these statuses (from `BezwaarBestandStatus` / `ExtractieTaakDto`):
- `todo` — not yet submitted for extraction
- `wachtend` — in queue
- `bezig` — extraction running
- `extractie-klaar` — extraction complete
- `fout` — extraction failed
- `niet ondersteund` — file type not supported

---

### Task 1: Add vl-tabs imports and replace template with tabs structure

**Files:**
- Modify: `webapp/src/js/bezwaarschriften-project-selectie.js:1-35`

**Step 1: Add imports**

Add the tabs component imports after the existing button import (line 3):

```javascript
import {VlTabsComponent} from '@domg-wc/components/block/tabs/vl-tabs.component.js';
import {VlTabsPaneComponent} from '@domg-wc/components/block/tabs/vl-tabs-pane.component.js';
```

Update `registerWebComponents` (line 7) to include the new components:

```javascript
registerWebComponents([VlSelectComponent, VlButtonComponent, VlTabsComponent, VlTabsPaneComponent]);
```

**Step 2: Replace template in constructor**

Replace the `super(...)` call (lines 21-35) with this template. Key changes:
- `<div id="bezwaren-sectie">` becomes `<div id="tabs-sectie">`
- `<h2>` removed (tabs provide their own headers)
- Content wrapped in `<vl-tabs observe-title>` with two `<vl-tabs-pane>` children

```javascript
    super(`
      <style>
        ${vlGlobalStyles}
        ${vlGridStyles}
      </style>
      <div id="selectie-wrapper">
        <vl-select id="project-select" placeholder="Kies een project..."></vl-select>
      </div>
      <div id="tabs-sectie" hidden>
        <vl-tabs observe-title active-tab="documenten">
          <vl-tabs-pane id="documenten" title="Documenten">
            <vl-button id="extraheer-knop" disabled>Extraheer geselecteerde</vl-button>
            <p id="fout-melding" hidden></p>
            <bezwaarschriften-bezwaren-tabel id="bezwaren-tabel"></bezwaarschriften-bezwaren-tabel>
          </vl-tabs-pane>
          <vl-tabs-pane id="kernbezwaren" title="Kernbezwaren">
            <p>Kernbezwaren worden hier getoond na verwerking.</p>
          </vl-tabs-pane>
        </vl-tabs>
      </div>
    `);
```

**Step 3: Update all `#bezwaren-sectie` references to `#tabs-sectie`**

Three places to update:

1. `_werkTabelBij()` (line 207): change `#bezwaren-sectie` → `#tabs-sectie`
2. `_verbergBezwarenSectie()` (line 218): change `#bezwaren-sectie` → `#tabs-sectie`

Also rename the method `_verbergBezwarenSectie` → `_verbergTabsSectie` and update the call site in `_koppelEventListeners()` (line 139).

**Step 4: Build frontend to verify no errors**

```bash
cd webapp && npm run build
```

Expected: Build succeeds with no errors.

**Step 5: Commit**

```bash
git add webapp/src/js/bezwaarschriften-project-selectie.js
git commit -m "feat: vervang bezwaren-sectie door vl-tabs met documenten en kernbezwaren tabs"
```

---

### Task 2: Add dynamic tab title update method

**Files:**
- Modify: `webapp/src/js/bezwaarschriften-project-selectie.js`

**Step 1: Add `_werkDocumentenTabTitelBij()` method**

Add this new method to the class. It reads from `this.__bezwaren` (already maintained by `_laadBezwaren()` and updated by `werkBijMetTaakUpdate()` in the child table, but the parent's `__bezwaren` is NOT updated by WebSocket — only the child's copy is).

**Important:** The parent `this.__bezwaren` is set once in `_laadBezwaren()` and never updated by WebSocket. To count statuses correctly, we need to also update `this.__bezwaren` when a taak-update comes in. Add this to `_verwerkTaakUpdate()`:

```javascript
  _verwerkTaakUpdate(taak) {
    if (!this.__geselecteerdProject || taak.projectNaam !== this.__geselecteerdProject) {
      return;
    }
    this.__bezwaren = this.__bezwaren.map((b) =>
      b.bestandsnaam === taak.bestandsnaam ? {
        ...b,
        status: taak.status,
        aantalWoorden: taak.aantalWoorden,
        aantalBezwaren: taak.aantalBezwaren,
      } : b,
    );
    const tabel = this.shadowRoot.querySelector('#bezwaren-tabel');
    if (tabel) {
      tabel.werkBijMetTaakUpdate(taak);
    }
    this._werkDocumentenTabTitelBij();
  }
```

Then add the title method itself:

```javascript
  _werkDocumentenTabTitelBij() {
    const pane = this.shadowRoot && this.shadowRoot.querySelector('#documenten');
    if (!pane || this.__bezwaren.length === 0) return;

    const totaal = this.__bezwaren.length;
    const aantalKlaar = this.__bezwaren.filter((b) => b.status === 'extractie-klaar').length;
    const aantalFout = this.__bezwaren.filter((b) => b.status === 'fout').length;
    const isBezig = this.__bezwaren.some(
        (b) => b.status === 'wachtend' || b.status === 'bezig',
    );

    let titel = `Documenten (${aantalKlaar}/${totaal})`;
    if (isBezig) titel += ' \u23F3';
    if (aantalFout > 0) titel += ` \u26A0\uFE0F${aantalFout}`;

    pane.setAttribute('title', titel);
  }
```

Note: `\u23F3` = ⏳, `\u26A0\uFE0F` = ⚠️. Using unicode escapes avoids encoding issues.

**Step 2: Call `_werkDocumentenTabTitelBij()` from the right places**

There are three trigger points:

1. **After bezwaren loaded** — in `_laadBezwaren()`, after `this._werkTabelBij()`:

```javascript
    .then((data) => {
      this.__bezwaren = data.bezwaren;
      this._werkTabelBij();
      this._werkDocumentenTabTitelBij();
      this._syncExtracties(projectNaam);
    })
```

2. **After taak-update from WebSocket** — already added in `_verwerkTaakUpdate()` above.

3. **After sync on reconnect** — in `_syncExtracties()`, update `this.__bezwaren` as well:

```javascript
  _syncExtracties(projectNaam) {
    fetch(`/api/v1/projects/${encodeURIComponent(projectNaam)}/extracties`)
        .then((response) => response.json())
        .then((data) => {
          const tabel = this.shadowRoot.querySelector('#bezwaren-tabel');
          if (tabel && data.taken) {
            data.taken.forEach((taak) => {
              this.__bezwaren = this.__bezwaren.map((b) =>
                b.bestandsnaam === taak.bestandsnaam ? {
                  ...b,
                  status: taak.status,
                  aantalWoorden: taak.aantalWoorden,
                  aantalBezwaren: taak.aantalBezwaren,
                } : b,
              );
              tabel.werkBijMetTaakUpdate(taak);
            });
            this._werkDocumentenTabTitelBij();
          }
        })
        .catch(() => {/* stille fout bij sync */});
  }
```

4. **After extractie indienen** — in `_dienExtractiesIn()`, after processing returned taken:

```javascript
    .then((data) => {
      const tabel = this.shadowRoot.querySelector('#bezwaren-tabel');
      if (tabel && data.taken) {
        data.taken.forEach((taak) => {
          this.__bezwaren = this.__bezwaren.map((b) =>
            b.bestandsnaam === taak.bestandsnaam ? {
              ...b,
              status: taak.status,
              aantalWoorden: taak.aantalWoorden,
              aantalBezwaren: taak.aantalBezwaren,
            } : b,
          );
          tabel.werkBijMetTaakUpdate(taak);
        });
        this._werkDocumentenTabTitelBij();
      }
    })
```

**Step 3: Build frontend to verify**

```bash
cd webapp && npm run build
```

Expected: Build succeeds with no errors.

**Step 4: Commit**

```bash
git add webapp/src/js/bezwaarschriften-project-selectie.js
git commit -m "feat: dynamische tab-titel met progress, loading en foutentelling"
```

---

### Task 3: Verify end-to-end

**Step 1: Build frontend**

```bash
cd webapp && npm run build
```

Expected: Clean build, no warnings or errors.

**Step 2: Compile backend**

```bash
cd app && mvn compile -q
```

Expected: BUILD SUCCESS (no backend changes, but verify nothing broke).

**Step 3: Review the final file**

Read `webapp/src/js/bezwaarschriften-project-selectie.js` and verify:
- Imports include VlTabsComponent, VlTabsPaneComponent
- Template uses `<vl-tabs observe-title>` with two `<vl-tabs-pane>` children
- `_werkDocumentenTabTitelBij()` is called from 4 places: `_laadBezwaren`, `_verwerkTaakUpdate`, `_syncExtracties`, `_dienExtractiesIn`
- `this.__bezwaren` is updated in all 4 trigger points (not just in the child table)
- No references to old `#bezwaren-sectie` remain
- `_verbergTabsSectie()` replaces `_verbergBezwarenSectie()`

**Step 4: Commit (if any fixes needed)**

```bash
git add webapp/src/js/bezwaarschriften-project-selectie.js
git commit -m "fix: correcties na verificatie tab UI"
```
