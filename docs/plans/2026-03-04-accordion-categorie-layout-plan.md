# Accordion Categorie Layout - Implementatieplan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Vervang de flat card-layout per categorie door `vl-accordion` componenten met subtitle- en menu-slots.

**Architecture:** De `_renderCategorieOverzicht` methode maakt per categorie een `vl-accordion` aan. De categorienaam gaat in `toggle-text`, de info-tekst in het `subtitle` slot, de status-pill in het `menu` slot, en de kernbezwaren in de default slot. Alle accordions staan standaard dichtgeklapt.

**Tech Stack:** Lit web components, @domg-wc/components (vl-accordion, vl-pill), Cypress component tests.

---

### Task 1: Update Cypress tests — selectors aanpassen

**Files:**
- Modify: `webapp/test/bezwaarschriften-kernbezwaren.cy.js`

De bestaande tests gebruiken `.categorie-wrapper[data-categorie="X"]` selectors. Deze moeten wijzigen naar `vl-accordion[data-categorie="X"]`.

**Step 1: Vervang alle `.categorie-wrapper` selectors**

In `bezwaarschriften-kernbezwaren.cy.js`, vervang globaal:
- `.categorie-wrapper[data-categorie=` → `vl-accordion[data-categorie=`

Dit betreft alle tests die een specifieke categorie targeten (pill-status checks, knop-clicks, etc.).

**Step 2: Run de tests — ze moeten falen**

Run: `cd webapp && npm test`

Expected: FAIL — de component rendert nog `.categorie-wrapper` divs, niet `vl-accordion` elementen.

**Step 3: Commit**

```bash
git add webapp/test/bezwaarschriften-kernbezwaren.cy.js
git commit -m "test: selectors aanpassen voor accordion-layout"
```

---

### Task 2: Nieuwe tests toevoegen voor accordion-specifiek gedrag

**Files:**
- Modify: `webapp/test/bezwaarschriften-kernbezwaren.cy.js`

**Step 1: Voeg tests toe voor subtitle en accordion-gedrag**

Voeg de volgende tests toe aan het bestaande `describe` blok:

```javascript
it('toont subtitle met aantal bezwaren voor todo categorie', () => {
  cy.get('bezwaarschriften-kernbezwaren')
      .then(($el) => $el[0].laadClusteringTaken('testproject'));

  cy.wait('@clusteringTaken');

  cy.get('bezwaarschriften-kernbezwaren')
      .find('vl-accordion[data-categorie="Milieu"] [slot="subtitle"]')
      .should('contain.text', '18 bezwaren');
});

it('toont subtitle met bezwaren en kernbezwaren voor klare categorie', () => {
  cy.intercept('GET', '/api/v1/projects/*/kernbezwaren', {
    statusCode: 200,
    body: {
      themas: [{
        naam: 'Mobiliteit',
        kernbezwaren: [
          {id: 1, samenvatting: 'Kern 1', individueleBezwaren: [], antwoord: null},
          {id: 2, samenvatting: 'Kern 2', individueleBezwaren: [], antwoord: null},
          {id: 3, samenvatting: 'Kern 3', individueleBezwaren: [], antwoord: null},
        ],
      }],
    },
  }).as('kernbezwarenKlaar');

  cy.get('bezwaarschriften-kernbezwaren')
      .then(($el) => {
        $el[0].laadClusteringTaken('testproject');
        $el[0].laadKernbezwaren('testproject');
      });

  cy.wait('@clusteringTaken');
  cy.wait('@kernbezwarenKlaar');

  cy.get('bezwaarschriften-kernbezwaren')
      .find('vl-accordion[data-categorie="Mobiliteit"] [slot="subtitle"]')
      .should('contain.text', '42 bezwaren')
      .and('contain.text', '3 kernbezwaren');
});

it('toont pill in menu-slot van accordion', () => {
  cy.get('bezwaarschriften-kernbezwaren')
      .then(($el) => $el[0].laadClusteringTaken('testproject'));

  cy.wait('@clusteringTaken');

  cy.get('bezwaarschriften-kernbezwaren')
      .find('vl-accordion[data-categorie="Mobiliteit"] vl-pill[slot="menu"]')
      .should('exist')
      .and('have.attr', 'type', 'success');
});

it('alle accordions zijn standaard dichtgeklapt', () => {
  cy.get('bezwaarschriften-kernbezwaren')
      .then(($el) => $el[0].laadClusteringTaken('testproject'));

  cy.wait('@clusteringTaken');

  // Geen enkele accordion heeft default-open
  cy.get('bezwaarschriften-kernbezwaren')
      .find('vl-accordion[default-open]')
      .should('not.exist');
});

it('toont categorienaam als toggle-text zonder aantal', () => {
  cy.get('bezwaarschriften-kernbezwaren')
      .then(($el) => $el[0].laadClusteringTaken('testproject'));

  cy.wait('@clusteringTaken');

  cy.get('bezwaarschriften-kernbezwaren')
      .find('vl-accordion[data-categorie="Mobiliteit"]')
      .should('have.attr', 'toggle-text', 'Mobiliteit');
});
```

**Step 2: Run tests — ze moeten falen**

Run: `cd webapp && npm test`

Expected: FAIL — accordion elementen bestaan nog niet.

**Step 3: Commit**

```bash
git add webapp/test/bezwaarschriften-kernbezwaren.cy.js
git commit -m "test: accordion subtitle, menu-slot en toggle-text tests"
```

---

### Task 3: Implementeer accordion-layout in `_renderCategorieOverzicht`

**Files:**
- Modify: `webapp/src/js/bezwaarschriften-kernbezwaren.js`

**Step 1: Vervang de categorie-rendering**

In `_renderCategorieOverzicht`, vervang de `forEach` loop (regels 371-413) door een accordion-gebaseerde aanpak:

```javascript
// Per categorie
this._clusteringTaken.forEach((ct) => {
  const accordion = document.createElement('vl-accordion');
  accordion.setAttribute('toggle-text', ct.categorie);
  accordion.dataset.categorie = ct.categorie;

  // Subtitle slot: info-tekst
  const subtitle = document.createElement('span');
  subtitle.slot = 'subtitle';
  subtitle.textContent = this._maakSubtitleTekst(ct);
  accordion.appendChild(subtitle);

  // Menu slot: pill met status + actieknoppen
  const pill = this._maakStatusPill(ct);
  pill.slot = 'menu';
  accordion.appendChild(pill);

  // Content: kernbezwaren (alleen bij klaar)
  if (ct.status === 'klaar' && this._themas) {
    const thema = this._themas.find((t) => t.naam === ct.categorie);
    if (thema && thema.kernbezwaren.length > 0) {
      const wrapper = document.createElement('div');
      thema.kernbezwaren.forEach((kern) => {
        wrapper.appendChild(this._maakKernbezwaarItem(kern));
      });
      accordion.appendChild(wrapper);
    }
  }

  inhoud.appendChild(accordion);
});
```

**Step 2: Voeg de `_maakSubtitleTekst` methode toe**

Voeg toe na `_maakStatusPill`:

```javascript
_maakSubtitleTekst(ct) {
  if (ct.status === 'klaar' && this._themas) {
    const thema = this._themas.find((t) => t.naam === ct.categorie);
    if (thema) {
      return `${ct.aantalBezwaren} bezwaren → ${thema.kernbezwaren.length} kernbezwaren`;
    }
  }
  return `${ct.aantalBezwaren} bezwaren`;
}
```

**Step 3: Verwijder de per-categorie `vl-alert`**

De `vl-alert` met reductie-info per categorie (regels 395-404) is niet meer nodig — die info zit nu in de subtitle.

**Step 4: Verwijder de `(X bezwaren)` uit het label**

De categorienaam in `toggle-text` bevat geen aantal meer. Dat was eerder in `.categorie-label`:
```javascript
// OUD: label.textContent = `${ct.categorie} (${ct.aantalBezwaren} bezwaren)`;
// NIEUW: accordion.setAttribute('toggle-text', ct.categorie);
```

**Step 5: Run tests**

Run: `cd webapp && npm test`

Expected: PASS — alle tests slagen met de nieuwe accordion-layout.

**Step 6: Commit**

```bash
git add webapp/src/js/bezwaarschriften-kernbezwaren.js
git commit -m "feat: accordion-layout per categorie met subtitle en menu-slot"
```

---

### Task 4: CSS opruimen

**Files:**
- Modify: `webapp/src/js/bezwaarschriften-kernbezwaren.js`

**Step 1: Verwijder ongebruikte CSS classes**

Verwijder uit het `<style>` blok:
- `.categorie-wrapper` (regel 25-27)
- `.categorie-header` (regel 28-34)
- `.categorie-label` (regel 35-38)

**Step 2: Voeg eventueel accordion-specifieke styling toe**

Check of de pill in het menu-slot correct uitlijnt. Eventueel toevoegen:

```css
vl-accordion {
  margin-bottom: 0.5rem;
}
```

**Step 3: Run tests**

Run: `cd webapp && npm test`

Expected: PASS

**Step 4: Commit**

```bash
git add webapp/src/js/bezwaarschriften-kernbezwaren.js
git commit -m "refactor: verwijder ongebruikte categorie-wrapper CSS"
```

---

### Task 5: Timer-updates aanpassen voor accordion-layout

**Files:**
- Modify: `webapp/src/js/bezwaarschriften-kernbezwaren.js`

**Step 1: Update `_updateTimers` selector**

De `_updateTimers` methode zoekt pills via `.categorie-wrapper` (dat is al `vl-pill[data-categorie="..."]` selector). Controleer dat de selector werkt met de nieuwe DOM-structuur.

Huidige selector op regel 550:
```javascript
const pill = inhoud.querySelector(`vl-pill[data-categorie="${CSS.escape(ct.categorie)}"]`);
```

Dit zoekt op `data-categorie` attribuut van de pill zelf, niet van de wrapper. De pill heeft nog steeds `dataset.categorie` in `_maakStatusPill`. Dit zou moeten blijven werken, maar verifieer dat het pill-element nu vindbaar is binnen de accordion (het zit in het `menu` slot, dus in de light DOM van `vl-accordion`).

**Step 2: Run tests**

Run: `cd webapp && npm test`

Expected: PASS

**Step 3: Commit (alleen als er wijzigingen waren)**

```bash
git add webapp/src/js/bezwaarschriften-kernbezwaren.js
git commit -m "fix: timer-update selectors voor accordion-layout"
```

---

### Task 6: Build en visuele verificatie

**Step 1: Build frontend**

Run: `cd webapp && npm run build`

Expected: PASS — geen lint/webpack fouten.

**Step 2: Process resources voor Spring Boot**

Run: `mvn process-resources -pl webapp -Denforcer.skip=true`

Expected: PASS

**Step 3: Visuele check**

Start de dev server (`npm run start` of Spring Boot) en verifieer:
- Alle categorieën tonen als accordions
- Subtitle toont correcte info per status
- Pill zit rechts in de header
- Accordions zijn standaard dicht
- Open/dicht klikken werkt
- Globale samenvatting alert is nog zichtbaar bovenaan

**Step 4: Commit**

```bash
git commit -m "build: accordion-layout frontend build"
```

---

## Test- en verificatieplan

### Geautomatiseerd (Cypress)
- Alle bestaande tests passend gemaakt voor nieuwe selectors
- Nieuwe tests voor: subtitle-tekst per status, pill in menu-slot, toggle-text, standaard dichtgeklapt

### Handmatig
- Visuele check: accordions openen/sluiten
- Pill-knoppen klikbaar in menu-slot (geen click-propagatie naar accordion toggle)
- Timer-updates werken bij wachtend/bezig status
- Globale alert correct boven de accordions
