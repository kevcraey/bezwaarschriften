# Passage-deduplicatie in Side Panel — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Groepeer gelijkaardige passages in de kernbezwaar side panel zodat dezelfde passage niet herhaaldelijk getoond wordt, en toon een compactere teller op de search-knop.

**Architecture:** Puur frontend oplossing. Een `passageGroepering` utility-functie groepeert individuele bezwaren op basis van Sørensen-Dice tekst-gelijkenis (>=90%). De `_toonPassages` methode rendert gegroepeerde passages met inklapbare documentenlijsten. De search-knop teller wordt `(totaal|groepen)`.

**Tech Stack:** JavaScript, Lit web components, Cypress component tests

---

### Task 1: Schrijf Sørensen-Dice utility met tests

**Files:**
- Create: `webapp/src/js/passage-groepering.js`
- Create: `webapp/test/passage-groepering.cy.js`

**Step 1: Schrijf de Cypress tests voor de groeperings-utility**

```js
import {groepeerPassages} from '../src/js/passage-groepering.js';

describe('passage-groepering', () => {
  it('groepeert exact gelijke passages samen', () => {
    const bezwaren = [
      {bestandsnaam: '001.txt', passage: 'De geluidshinder is onaanvaardbaar'},
      {bestandsnaam: '007.txt', passage: 'De geluidshinder is onaanvaardbaar'},
      {bestandsnaam: '012.txt', passage: 'Verkeer neemt toe'},
    ];
    const groepen = groepeerPassages(bezwaren);
    expect(groepen).to.have.length(2);
    expect(groepen[0].bezwaren).to.have.length(2);
    expect(groepen[0].passage).to.equal('De geluidshinder is onaanvaardbaar');
    expect(groepen[1].bezwaren).to.have.length(1);
  });

  it('groepeert fuzzy gelijkaardige passages (>=90% Dice)', () => {
    const bezwaren = [
      {bestandsnaam: '001.txt', passage: 'De geluidshinder is onaanvaardbaar groot'},
      {bestandsnaam: '007.txt', passage: 'De geluidshinder is onaanvaardbaar'},
      {bestandsnaam: '012.txt', passage: 'Verkeer neemt toe'},
    ];
    const groepen = groepeerPassages(bezwaren);
    expect(groepen).to.have.length(2);
    // Langste passage is representatief
    expect(groepen[0].passage).to.equal('De geluidshinder is onaanvaardbaar groot');
    expect(groepen[0].bezwaren).to.have.length(2);
  });

  it('houdt duidelijk verschillende passages apart', () => {
    const bezwaren = [
      {bestandsnaam: '001.txt', passage: 'De geluidshinder is onaanvaardbaar'},
      {bestandsnaam: '002.txt', passage: 'Het verkeer neemt dramatisch toe'},
      {bestandsnaam: '003.txt', passage: 'Natuurwaarden worden aangetast'},
    ];
    const groepen = groepeerPassages(bezwaren);
    expect(groepen).to.have.length(3);
  });

  it('sorteert groepen op aantal documenten (meest eerst)', () => {
    const bezwaren = [
      {bestandsnaam: '001.txt', passage: 'Uniek bezwaar'},
      {bestandsnaam: '002.txt', passage: 'Veel voorkomend bezwaar'},
      {bestandsnaam: '003.txt', passage: 'Veel voorkomend bezwaar'},
      {bestandsnaam: '004.txt', passage: 'Veel voorkomend bezwaar'},
    ];
    const groepen = groepeerPassages(bezwaren);
    expect(groepen[0].bezwaren).to.have.length(3);
    expect(groepen[1].bezwaren).to.have.length(1);
  });

  it('retourneert lege array voor lege input', () => {
    expect(groepeerPassages([])).to.deep.equal([]);
  });

  it('behoudt alle originele bezwaar-data in groep', () => {
    const bezwaren = [
      {bestandsnaam: '001.txt', passage: 'Geluid is te hard', extraVeld: 'waarde'},
    ];
    const groepen = groepeerPassages(bezwaren);
    expect(groepen[0].bezwaren[0].extraVeld).to.equal('waarde');
  });
});
```

**Step 2: Run test om te verifiëren dat het faalt**

Run: `cd webapp && npm test -- --spec test/passage-groepering.cy.js`
Expected: FAIL — module `passage-groepering.js` bestaat niet

**Step 3: Implementeer de groeperings-utility**

```js
/**
 * Bereken Sørensen-Dice coëfficiënt tussen twee strings.
 * Gebaseerd op bigrammen van de genormaliseerde tekst.
 */
function diceCoefficient(a, b) {
  const normA = a.toLowerCase().trim();
  const normB = b.toLowerCase().trim();
  if (normA === normB) return 1.0;
  if (normA.length < 2 || normB.length < 2) return 0.0;

  const bigrammen = (s) => {
    const set = new Map();
    for (let i = 0; i < s.length - 1; i++) {
      const bigram = s.substring(i, i + 2);
      set.set(bigram, (set.get(bigram) || 0) + 1);
    }
    return set;
  };

  const bigrammenA = bigrammen(normA);
  const bigrammenB = bigrammen(normB);
  let overlap = 0;
  for (const [bigram, count] of bigrammenA) {
    if (bigrammenB.has(bigram)) {
      overlap += Math.min(count, bigrammenB.get(bigram));
    }
  }

  const totaal = normA.length - 1 + normB.length - 1;
  return (2 * overlap) / totaal;
}

const GELIJKENIS_DREMPEL = 0.9;

/**
 * Groepeer individuele bezwaren op basis van passage-gelijkenis.
 * Retourneert array van { passage, bezwaren } gesorteerd op groepsgrootte (aflopend).
 */
export function groepeerPassages(bezwaren) {
  if (!bezwaren || bezwaren.length === 0) return [];

  const groepen = [];

  for (const bezwaar of bezwaren) {
    let gevonden = false;
    for (const groep of groepen) {
      if (diceCoefficient(bezwaar.passage, groep.passage) >= GELIJKENIS_DREMPEL) {
        groep.bezwaren.push(bezwaar);
        // Langste passage als representatief
        if (bezwaar.passage.length > groep.passage.length) {
          groep.passage = bezwaar.passage;
        }
        gevonden = true;
        break;
      }
    }
    if (!gevonden) {
      groepen.push({passage: bezwaar.passage, bezwaren: [bezwaar]});
    }
  }

  groepen.sort((a, b) => b.bezwaren.length - a.bezwaren.length);
  return groepen;
}
```

**Step 4: Run test om te verifiëren dat het slaagt**

Run: `cd webapp && npm test -- --spec test/passage-groepering.cy.js`
Expected: PASS — alle 6 tests groen

**Step 5: Commit**

```bash
git add webapp/src/js/passage-groepering.js webapp/test/passage-groepering.cy.js
git commit -m "feat: voeg passage-groepering utility toe met Sørensen-Dice fuzzy matching"
```

---

### Task 2: Pas de search-knop teller aan naar (totaal|groepen) formaat

**Files:**
- Modify: `webapp/src/js/bezwaarschriften-kernbezwaren.js:427-458` (`_maakKernbezwaarItem`)
- Modify: `webapp/test/bezwaarschriften-kernbezwaren.cy.js`

**Step 1: Schrijf Cypress test voor de nieuwe teller**

Voeg toe aan `webapp/test/bezwaarschriften-kernbezwaren.cy.js`:

```js
it('toont teller in (totaal|groepen) formaat op search-knop', () => {
  cy.intercept('GET', '/api/v1/projects/*/kernbezwaren', {
    statusCode: 200,
    body: {
      themas: [{
        naam: 'Mobiliteit',
        kernbezwaren: [{
          id: 1,
          samenvatting: 'Geluidshinder',
          antwoord: null,
          individueleBezwaren: [
            {bestandsnaam: '001.txt', passage: 'Te veel geluid'},
            {bestandsnaam: '002.txt', passage: 'Te veel geluid'},
            {bestandsnaam: '003.txt', passage: 'Te veel geluid'},
            {bestandsnaam: '004.txt', passage: 'Verkeer is gevaarlijk'},
            {bestandsnaam: '005.txt', passage: 'Fietspaden ontbreken'},
          ],
        }],
      }],
    },
  }).as('kernbezwaren');

  cy.get('bezwaarschriften-kernbezwaren')
      .then(($el) => {
        $el[0].laadClusteringTaken('testproject');
        $el[0].laadKernbezwaren('testproject');
      });

  cy.wait('@clusteringTaken');
  cy.wait('@kernbezwaren');

  // Search-knop toont (5|3): 5 individuele bezwaren, 3 unieke groepen
  cy.get('bezwaarschriften-kernbezwaren')
      .find('.kernbezwaar-actie vl-button[icon="search"]')
      .should('contain.text', '(5|3)');
});
```

**Step 2: Run test om te verifiëren dat het faalt**

Run: `cd webapp && npm test -- --spec test/bezwaarschriften-kernbezwaren.cy.js`
Expected: FAIL — knop toont nog `(5)` i.p.v. `(5|3)`

**Step 3: Pas `_maakKernbezwaarItem` aan**

In `webapp/src/js/bezwaarschriften-kernbezwaren.js`, importeer de utility bovenaan:

```js
import {groepeerPassages} from './passage-groepering.js';
```

Wijzig in `_maakKernbezwaarItem` (rond regel 447-450):

Oud:
```js
knop.textContent = `(${kern.individueleBezwaren.length})`;
```

Nieuw:
```js
const totaal = kern.individueleBezwaren.length;
const groepen = groepeerPassages(kern.individueleBezwaren);
const aantalGroepen = groepen.length;
knop.textContent = totaal === aantalGroepen ? `(${totaal})` : `(${totaal}|${aantalGroepen})`;
```

**Step 4: Run test om te verifiëren dat het slaagt**

Run: `cd webapp && npm test -- --spec test/bezwaarschriften-kernbezwaren.cy.js`
Expected: PASS — alle tests groen (inclusief bestaande)

**Step 5: Commit**

```bash
git add webapp/src/js/bezwaarschriften-kernbezwaren.js webapp/test/bezwaarschriften-kernbezwaren.cy.js
git commit -m "feat: toon (totaal|groepen) teller op search-knop kernbezwaren"
```

---

### Task 3: Render gegroepeerde passages in side panel

**Files:**
- Modify: `webapp/src/js/bezwaarschriften-kernbezwaren.js:1102-1137` (`_toonPassages`)
- Modify: `webapp/test/bezwaarschriften-kernbezwaren.cy.js`

**Step 1: Schrijf Cypress tests voor gegroepeerde weergave**

Voeg toe aan `webapp/test/bezwaarschriften-kernbezwaren.cy.js`:

```js
describe('side panel passage-deduplicatie', () => {
  const MOCK_MET_DUPLICATEN = {
    themas: [{
      naam: 'Mobiliteit',
      kernbezwaren: [{
        id: 1,
        samenvatting: 'Geluidshinder',
        antwoord: null,
        individueleBezwaren: [
          {bestandsnaam: '001.txt', passage: 'Te veel geluid in de buurt'},
          {bestandsnaam: '002.txt', passage: 'Te veel geluid in de buurt'},
          {bestandsnaam: '003.txt', passage: 'Te veel geluid in de buurt'},
          {bestandsnaam: '004.txt', passage: 'Te veel geluid in de buurt'},
          {bestandsnaam: '005.txt', passage: 'Te veel geluid in de buurt'},
          {bestandsnaam: '006.txt', passage: 'Te veel geluid in de buurt'},
          {bestandsnaam: '007.txt', passage: 'Te veel geluid in de buurt'},
          {bestandsnaam: '008.txt', passage: 'Verkeer is gevaarlijk'},
        ],
      }],
    }],
  };

  beforeEach(() => {
    cy.intercept('GET', '/api/v1/projects/*/clustering-taken', {
      statusCode: 200,
      body: {
        categorieen: [{
          categorie: 'Mobiliteit', status: 'klaar', taakId: 1,
          aantalBezwaren: 8, aantalKernbezwaren: 1,
          aangemaaktOp: '2026-03-03T10:00:00Z',
          verwerkingGestartOp: '2026-03-03T10:00:01Z',
          verwerkingVoltooidOp: '2026-03-03T10:00:15Z',
        }],
      },
    }).as('clusteringTaken');

    cy.intercept('GET', '/api/v1/projects/*/kernbezwaren', {
      statusCode: 200,
      body: MOCK_MET_DUPLICATEN,
    }).as('kernbezwaren');

    cy.mount(html`<bezwaarschriften-kernbezwaren></bezwaarschriften-kernbezwaren>`);

    cy.get('bezwaarschriften-kernbezwaren')
        .then(($el) => {
          $el[0].laadClusteringTaken('testproject');
          $el[0].laadKernbezwaren('testproject');
        });

    cy.wait('@clusteringTaken');
    cy.wait('@kernbezwaren');
  });

  it('toont gegroepeerde passage slechts 1x met documentenlijst', () => {
    // Open side panel
    cy.get('bezwaarschriften-kernbezwaren')
        .find('.kernbezwaar-actie vl-button[icon="search"]')
        .click();

    // Passage tekst verschijnt maar 1x (niet 7x)
    cy.get('bezwaarschriften-kernbezwaren')
        .find('.passage-groep .passage-tekst')
        .should('have.length', 2); // 2 groepen: geluid (7x) + verkeer (1x)

    // Eerste groep toont max 5 documenten + "... (7 documenten)" link
    cy.get('bezwaarschriften-kernbezwaren')
        .find('.passage-groep')
        .first()
        .find('.passage-document-link')
        .should('have.length', 5);

    cy.get('bezwaarschriften-kernbezwaren')
        .find('.passage-groep')
        .first()
        .find('.passage-toon-alle')
        .should('contain.text', '7 documenten');
  });

  it('toont alle documenten na klik op "Toon alle"', () => {
    cy.get('bezwaarschriften-kernbezwaren')
        .find('.kernbezwaar-actie vl-button[icon="search"]')
        .click();

    cy.get('bezwaarschriften-kernbezwaren')
        .find('.passage-groep')
        .first()
        .find('.passage-toon-alle')
        .click();

    // Nu alle 7 documenten zichtbaar
    cy.get('bezwaarschriften-kernbezwaren')
        .find('.passage-groep')
        .first()
        .find('.passage-document-link')
        .should('have.length', 7);

    // "Toon alle" link verdwenen
    cy.get('bezwaarschriften-kernbezwaren')
        .find('.passage-groep')
        .first()
        .find('.passage-toon-alle')
        .should('not.exist');
  });

  it('toont header met totaal en groepen-aantal', () => {
    cy.get('bezwaarschriften-kernbezwaren')
        .find('.kernbezwaar-actie vl-button[icon="search"]')
        .click();

    cy.get('bezwaarschriften-kernbezwaren')
        .find('#side-sheet-inhoud')
        .should('contain.text', '8 individuele bezwaren')
        .and('contain.text', '2 unieke passages');
  });

  it('toont enkel document als passage uniek is (geen groepering nodig)', () => {
    cy.get('bezwaarschriften-kernbezwaren')
        .find('.kernbezwaar-actie vl-button[icon="search"]')
        .click();

    // Tweede groep (verkeer) heeft maar 1 document, geen "Toon alle"
    cy.get('bezwaarschriften-kernbezwaren')
        .find('.passage-groep')
        .last()
        .find('.passage-document-link')
        .should('have.length', 1);

    cy.get('bezwaarschriften-kernbezwaren')
        .find('.passage-groep')
        .last()
        .find('.passage-toon-alle')
        .should('not.exist');
  });
});
```

**Step 2: Run test om te verifiëren dat het faalt**

Run: `cd webapp && npm test -- --spec test/bezwaarschriften-kernbezwaren.cy.js`
Expected: FAIL — `.passage-groep` class bestaat nog niet

**Step 3: Herschrijf `_toonPassages` methode**

Vervang de `_toonPassages` methode in `webapp/src/js/bezwaarschriften-kernbezwaren.js` (regels 1102-1137):

```js
_toonPassages(kernbezwaar) {
  const sideSheet = this.shadowRoot.querySelector('#side-sheet');
  const inhoud = this.shadowRoot.querySelector('#side-sheet-inhoud');
  const titelEl = this.shadowRoot.querySelector('#side-sheet-titel');
  if (!sideSheet || !inhoud) return;

  inhoud.innerHTML = '';
  if (titelEl) titelEl.textContent = kernbezwaar.samenvatting;

  const groepen = groepeerPassages(kernbezwaar.individueleBezwaren);
  const totaal = kernbezwaar.individueleBezwaren.length;

  const aantalLabel = document.createElement('p');
  if (groepen.length < totaal) {
    aantalLabel.textContent = `${totaal} individuele bezwaren, ${groepen.length} unieke passages:`;
  } else {
    aantalLabel.textContent = `${totaal} individuele bezwar${totaal === 1 ? '' : 'en'}:`;
  }
  inhoud.appendChild(aantalLabel);

  const MAX_ZICHTBAAR = 5;

  groepen.forEach((groep) => {
    const groepEl = document.createElement('div');
    groepEl.className = 'passage-groep';

    const passage = document.createElement('div');
    passage.className = 'passage-tekst';
    passage.textContent = `"${groep.passage}"`;
    groepEl.appendChild(passage);

    const docContainer = document.createElement('div');
    docContainer.className = 'passage-documenten';

    const zichtbaar = groep.bezwaren.slice(0, MAX_ZICHTBAAR);
    const verborgen = groep.bezwaren.slice(MAX_ZICHTBAAR);

    zichtbaar.forEach((ref) => {
      docContainer.appendChild(this._maakDocumentLink(ref));
    });

    if (verborgen.length > 0) {
      const verborgenContainer = document.createElement('span');
      verborgenContainer.className = 'passage-verborgen-docs';
      verborgenContainer.style.display = 'none';
      verborgen.forEach((ref) => {
        verborgenContainer.appendChild(this._maakDocumentLink(ref));
      });
      docContainer.appendChild(verborgenContainer);

      const toonAlleLink = document.createElement('a');
      toonAlleLink.className = 'passage-toon-alle';
      toonAlleLink.href = '#';
      toonAlleLink.textContent = `... (${groep.bezwaren.length} documenten)`;
      toonAlleLink.addEventListener('click', (e) => {
        e.preventDefault();
        verborgenContainer.style.display = 'inline';
        toonAlleLink.remove();
      });
      docContainer.appendChild(toonAlleLink);
    }

    groepEl.appendChild(docContainer);
    inhoud.appendChild(groepEl);
  });

  sideSheet.open();
  this.classList.add('side-sheet-open');
}

_maakDocumentLink(ref) {
  const link = document.createElement('a');
  link.className = 'passage-document-link';
  link.textContent = ref.bestandsnaam;
  link.href = `/api/v1/projects/${encodeURIComponent(this._projectNaam)}/bezwaren/${encodeURIComponent(ref.bestandsnaam)}/download`;
  link.download = ref.bestandsnaam;
  return link;
}
```

**Step 4: Voeg CSS toe voor de nieuwe structuur**

Voeg toe aan de `<style>` sectie in `bezwaarschriften-kernbezwaren.js` (na de bestaande `.passage-tekst` stijl):

```css
.passage-groep {
  margin-bottom: 1.5rem;
  padding-bottom: 1rem;
  border-bottom: 1px solid #e8ebee;
}
.passage-groep:last-child {
  border-bottom: none;
}
.passage-documenten {
  margin-top: 0.5rem;
  font-size: 0.85rem;
  line-height: 1.8;
}
.passage-document-link {
  color: #687483;
  text-decoration: none;
  cursor: pointer;
  margin-right: 0.5rem;
}
.passage-document-link:hover {
  text-decoration: underline;
  color: #0055cc;
}
.passage-toon-alle {
  color: #0055cc;
  cursor: pointer;
  font-size: 0.85rem;
  text-decoration: none;
}
.passage-toon-alle:hover {
  text-decoration: underline;
}
```

Verwijder de oude `.passage-item`, `.passage-bestandsnaam` CSS regels (die niet meer gebruikt worden).

**Step 5: Run tests om te verifiëren dat alles slaagt**

Run: `cd webapp && npm test -- --spec test/bezwaarschriften-kernbezwaren.cy.js`
Expected: PASS — alle tests groen

**Step 6: Commit**

```bash
git add webapp/src/js/bezwaarschriften-kernbezwaren.js webapp/test/bezwaarschriften-kernbezwaren.cy.js
git commit -m "feat: gegroepeerde passage-weergave in side panel met max 5 documenten"
```

---

### Task 4: Build en volledige test-suite draaien

**Files:**
- Geen nieuwe bestanden

**Step 1: Run de volledige frontend build**

Run: `cd webapp && npm run build`
Expected: PASS — geen lint- of buildfouten

**Step 2: Run alle Cypress tests**

Run: `cd webapp && npm test`
Expected: PASS — alle tests groen

**Step 3: Run Maven process-resources**

Run: `mvn process-resources -pl webapp -Denforcer.skip=true`
Expected: PASS — target/classes bijgewerkt

**Step 4: Commit indien nodig (bv. lint-fixes)**

```bash
git add -u
git commit -m "chore: lint-fixes passage-deduplicatie"
```

---

## Test- en verificatieplan

1. **Unit-achtige tests** (Task 1): Sørensen-Dice coëfficiënt + groeperings-algoritme
2. **Component tests** (Task 2-3): Teller formaat + side panel rendering
3. **Handmatige verificatie**: Open een project met bekende duplicaat-passages, controleer:
   - Search-knop toont `(N|M)` formaat
   - Side panel groepeert gelijkaardige passages
   - "Toon alle" link ontvouwt verborgen documenten
   - Elk document is individueel downloadbaar
   - Passages zonder duplicaten tonen gewoon als 1 document
