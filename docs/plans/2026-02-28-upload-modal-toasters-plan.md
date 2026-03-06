# Upload Modal & Toasters Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Verplaats de upload-zone naar een modal en toon succes/error toasters na upload.

**Architecture:** Puur frontend wijzigingen in `bezwaarschriften-project-selectie.js`. Twee `vl-toaster` instanties (één met `fade-out` voor succes, één zonder voor errors). Upload-zone verhuist van inline div naar `vl-modal`. Backend blijft ongewijzigd.

**Tech Stack:** `@domg-wc/components` (VlToasterComponent, VlModalComponent, VlUploadComponent)

---

### Task 1: Voeg VlToasterComponent import en registratie toe

**Files:**
- Modify: `webapp/src/js/bezwaarschriften-project-selectie.js:1-11`

**Step 1: Voeg import toe**

Voeg deze regel toe na regel 7 (VlModalComponent import):

```js
import {VlToasterComponent} from '@domg-wc/components/block/toaster/vl-toaster.component.js';
```

**Step 2: Voeg toe aan registerWebComponents**

Wijzig regel 11 van:
```js
registerWebComponents([VlSelectComponent, VlButtonComponent, VlTabsComponent, VlTabsPaneComponent, VlUploadComponent, VlModalComponent]);
```
naar:
```js
registerWebComponents([VlSelectComponent, VlButtonComponent, VlTabsComponent, VlTabsPaneComponent, VlUploadComponent, VlModalComponent, VlToasterComponent]);
```

**Step 3: Commit**

```bash
git add webapp/src/js/bezwaarschriften-project-selectie.js
git commit -m "chore: registreer VlToasterComponent"
```

---

### Task 2: Verplaats upload-zone naar een vl-modal

**Files:**
- Modify: `webapp/src/js/bezwaarschriften-project-selectie.js` (constructor HTML template + event listeners)

**Step 1: Vervang inline upload-zone door upload-modal in de constructor HTML**

In de constructor template, vervang de `#upload-zone` div (regels 39-48):
```html
            <div id="upload-zone" hidden>
              <vl-upload id="bestand-upload"
                accepted-files=".txt"
                max-files="100"
                max-size="50"
                main-title="Bezwaarbestanden toevoegen"
                sub-title="Sleep .txt bestanden hierheen of klik om te bladeren">
              </vl-upload>
              <vl-button id="upload-verzend-knop">Uploaden</vl-button>
            </div>
```

door een nieuwe `vl-modal` (toe te voegen NA de bestaande `#verwijder-modal`, dus na regel 64):
```html
      <vl-modal id="upload-modal" title="Bestanden toevoegen" closable>
        <div slot="content">
          <vl-upload id="bestand-upload"
            accepted-files=".txt"
            max-files="100"
            max-size="50"
            main-title="Bezwaarbestanden toevoegen"
            sub-title="Sleep .txt bestanden hierheen of klik om te bladeren">
          </vl-upload>
        </div>
        <div slot="button">
          <vl-button id="upload-verzend-knop">Uploaden</vl-button>
        </div>
      </vl-modal>
```

**Step 2: Wijzig event listener voor "Bestanden toevoegen" knop**

In `_koppelEventListeners()`, vervang het toevoegenKnop blok (regels 246-251):
```js
    if (toevoegenKnop) {
      toevoegenKnop.addEventListener('vl-click', () => {
        const uploadZone = this.shadowRoot.querySelector('#upload-zone');
        if (uploadZone) uploadZone.hidden = !uploadZone.hidden;
      });
    }
```
door:
```js
    if (toevoegenKnop) {
      toevoegenKnop.addEventListener('vl-click', () => {
        const modal = this.shadowRoot.querySelector('#upload-modal');
        if (modal) modal.open();
      });
    }
```

**Step 3: Wijzig `_verzendUpload()` om modal te sluiten in plaats van upload-zone te verbergen**

In `_verzendUpload()`, vervang de regels na `uploadEl.removeAllFiles()` (regels 378-380):
```js
          uploadEl.removeAllFiles();
          const uploadZone = this.shadowRoot.querySelector('#upload-zone');
          if (uploadZone) uploadZone.hidden = true;
```
door:
```js
          uploadEl.removeAllFiles();
          const modal = this.shadowRoot.querySelector('#upload-modal');
          if (modal) modal.close();
```

**Step 4: Verifieer in de browser**

- Klik "Bestanden toevoegen" — modal opent
- Voeg bestanden toe via drag-and-drop of file picker
- Klik "Uploaden" — modal sluit na succes
- Klik X op de modal — modal sluit zonder upload

**Step 5: Commit**

```bash
git add webapp/src/js/bezwaarschriften-project-selectie.js
git commit -m "feat: verplaats upload-zone naar vl-modal"
```

---

### Task 3: Voeg toasters toe voor upload feedback

**Files:**
- Modify: `webapp/src/js/bezwaarschriften-project-selectie.js` (constructor HTML + `_verzendUpload()`)

**Step 1: Voeg twee vl-toaster elementen toe aan de constructor HTML**

Voeg toe aan het einde van de constructor template, vlak voor de afsluitende `` ` `` (na de `#upload-modal`):
```html
      <vl-toaster id="succes-toaster" fade-out></vl-toaster>
      <vl-toaster id="error-toaster"></vl-toaster>
```

De `succes-toaster` heeft `fade-out` (auto-verdwijnt na ~5s). De `error-toaster` heeft geen `fade-out` (blijft staan).

**Step 2: Voeg helper method `_toonToast` toe**

Voeg een nieuwe method toe aan de class, na `_verbergFout()`:

```js
  _toonToast(type, bericht) {
    const toasterId = type === 'success' ? '#succes-toaster' : '#error-toaster';
    const toaster = this.shadowRoot.querySelector(toasterId);
    if (toaster) {
      toaster.showAlert({
        type: type,
        icon: type === 'success' ? 'check' : 'warning',
        message: bericht,
        closable: 'true',
      });
    }
  }
```

**Step 3: Wijzig `_verzendUpload()` om toasters te gebruiken**

Vervang het hele `.then((data) => { ... })` blok in `_verzendUpload()` (regels 377-388):
```js
        .then((data) => {
          uploadEl.removeAllFiles();
          const uploadZone = this.shadowRoot.querySelector('#upload-zone');
          if (uploadZone) uploadZone.hidden = true;

          if (data.fouten && data.fouten.length > 0) {
            const foutTekst = data.fouten.map((f) => `${f.bestandsnaam}: ${f.reden}`).join(', ');
            this._toonFout(`Sommige bestanden konden niet worden geupload: ${foutTekst}`);
          }

          this._laadBezwaren(this.__geselecteerdProject);
        })
```

door (LET OP: na Task 2 staat hier al de modal-versie, dit vervangt die):
```js
        .then((data) => {
          uploadEl.removeAllFiles();
          const modal = this.shadowRoot.querySelector('#upload-modal');
          if (modal) modal.close();

          if (data.geupload && data.geupload.length > 0) {
            this._toonToast('success',
              `${data.geupload.length} bestand(en) succesvol opgeladen.`);
          }

          if (data.fouten && data.fouten.length > 0) {
            this._toonToast('error',
              `${data.fouten.length} bestand(en) niet opgeladen: bestand met dezelfde naam bestaat al.`);
          }

          this._laadBezwaren(this.__geselecteerdProject);
        })
```

**Step 4: Wijzig catch blok om error toaster te gebruiken**

Vervang het `.catch()` blok (regel 389-391):
```js
        .catch(() => {
          this._toonFout('Upload mislukt.');
        })
```
door:
```js
        .catch(() => {
          this._toonToast('error', 'Upload mislukt.');
        })
```

**Step 5: Verifieer in de browser**

Test deze scenario's:
1. Upload nieuwe bestanden → groene toaster "X bestand(en) succesvol opgeladen"
2. Upload bestanden die al bestaan → rode toaster "X bestand(en) niet opgeladen: bestand met dezelfde naam bestaat al"
3. Mix van nieuwe + bestaande → BEIDE toasters verschijnen
4. Groene toaster verdwijnt na ~5 seconden
5. Rode toaster blijft staan, kan gesloten worden met X

**Step 6: Commit**

```bash
git add webapp/src/js/bezwaarschriften-project-selectie.js
git commit -m "feat: succes- en error-toasters na upload"
```
