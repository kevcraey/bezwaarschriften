# Verwijder individueel bezwaar — Implementatieplan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Gebruikers kunnen elk individueel bezwaar (manueel én AI-gegenereerd) verwijderen via een bevestigingsmodal. Bij verwijdering van een AI-bezwaar verschijnt de ✍️-signalisatie; bij verwijdering van het laatste manuele bezwaar verdwijnt deze.

**Architecture:** Backend-service `verwijderManueelBezwaar` uitbreiden naar `verwijderBezwaar` zonder manueel-guard. Frontend voegt `vl-modal` toe als bevestigingsstap voor de verwijder-actie, en toont de verwijder-knop op alle bezwaren.

**Tech Stack:** Java 21 + Spring Boot, JUnit 5 + Mockito, JavaScript Web Components, `@domg-wc` (vl-modal, vl-button), Cypress component tests.

---

## Overzicht

- **Task 1:** Backend — service-methode uitbreiden voor AI-bezwaren
- **Task 2:** Backend — controller aanpassen
- **Task 3:** Frontend — `vl-modal` bevestigingsflow
- **Task 4:** Frontend — verwijder-knop op alle bezwaren
- **Task 5:** Cypress tests bijwerken en uitbreiden
- **Task 6:** Build & verificatie

---

### Task 1: Backend service — `verwijderBezwaar`

**Files:**
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieTaakService.java`
- Test: `app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieTaakServiceTest.java`

#### Stap 1.1: Schrijf de falende test voor AI-bezwaar verwijdering

Voeg toe in `ExtractieTaakServiceTest` (na de bestaande verwijder-tests):

```java
@Test
void verwijderAiBezwaarZetHeeftManueelOp() {
    var taak = maakTaak(1L, "windmolens", "bezwaar-001.txt", ExtractieTaakStatus.KLAAR);
    taak.setAantalBezwaren(3);
    taak.setHeeftManueel(false);

    var bezwaar = new GeextraheerdBezwaarEntiteit();
    bezwaar.setId(10L);
    bezwaar.setTaakId(1L);
    bezwaar.setManueel(false); // AI-bezwaar
    when(bezwaarRepository.findById(10L)).thenReturn(Optional.of(bezwaar));
    when(repository.findById(1L)).thenReturn(Optional.of(taak));
    when(bezwaarRepository.findByTaakId(1L)).thenReturn(List.of());
    when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

    service.verwijderBezwaar("windmolens", "bezwaar-001.txt", 10L);

    verify(bezwaarRepository).delete(bezwaar);
    assertThat(taak.getAantalBezwaren()).isEqualTo(2);
    assertThat(taak.isHeeftManueel()).isTrue(); // manueel aangepast door verwijdering AI-bezwaar
}
```

#### Stap 1.2: Verifieer dat de test faalt

```bash
cd app && mvn test -pl . -Dtest=ExtractieTaakServiceTest#verwijderAiBezwaarZetHeeftManueelOp -Dsurefire.failIfNoSpecifiedTests=false
```
Verwacht: `FAIL` (methode `verwijderBezwaar` bestaat niet)

#### Stap 1.3: Pas de bestaande verwijder-tests aan (hernoeming)

In `ExtractieTaakServiceTest`, verander **alle** aanroepen van `service.verwijderManueelBezwaar(...)` naar `service.verwijderBezwaar(...)`. Er zijn 3 testmethoden:
- `verwijderManueelBezwaarSuccesvol` → aanroep hernoemd
- `verwijderLaatstManueelBezwaarZetHeeftManueelUit` → aanroep hernoemd
- `verwijderNietManueelBezwaarGooitForbidden` → **verwijder deze test volledig** (het gedrag wijzigt)

#### Stap 1.4: Implementeer `verwijderBezwaar` in `ExtractieTaakService`

Hernoem de methode en verwijder de manueel-guard. Voeg logica toe voor AI-bezwaren:

```java
/**
 * Verwijdert een bezwaar (manueel of AI-gegenereerd).
 *
 * <p>Bij verwijdering van een AI-bezwaar wordt heeftManueel op true gezet,
 * omdat de gebruiker het document handmatig heeft aangepast.
 * Bij verwijdering van een manueel bezwaar wordt heeftManueel herberekend.
 *
 * @param projectNaam naam van het project
 * @param bestandsnaam naam van het bestand
 * @param bezwaarId id van het te verwijderen bezwaar
 * @throws IllegalArgumentException als het bezwaar niet gevonden wordt
 */
@Transactional
public void verwijderBezwaar(String projectNaam, String bestandsnaam, Long bezwaarId) {
    var bezwaar = bezwaarRepository.findById(bezwaarId)
        .orElseThrow(() -> new IllegalArgumentException("Bezwaar niet gevonden: " + bezwaarId));

    boolean wasAiBezwaar = !bezwaar.isManueel();

    var taak = repository.findById(bezwaar.getTaakId())
        .orElseThrow(() -> new IllegalArgumentException("Taak niet gevonden"));

    bezwaarRepository.delete(bezwaar);

    int huidigAantal = taak.getAantalBezwaren() != null ? taak.getAantalBezwaren() : 0;
    taak.setAantalBezwaren(Math.max(0, huidigAantal - 1));

    if (wasAiBezwaar) {
        taak.setHeeftManueel(true);
    } else {
        var overigeBezwaren = bezwaarRepository.findByTaakId(taak.getId());
        boolean nogManueel = overigeBezwaren.stream().anyMatch(GeextraheerdBezwaarEntiteit::isManueel);
        taak.setHeeftManueel(nogManueel);
    }

    repository.save(taak);
    notificatie.taakGewijzigd(ExtractieTaakDto.van(taak));
}
```

Verwijder ook de oude methode `verwijderManueelBezwaar` volledig.

#### Stap 1.5: Verifieer dat alle service-tests slagen

```bash
cd app && mvn test -pl . -Dtest=ExtractieTaakServiceTest -Dsurefire.failIfNoSpecifiedTests=false
```
Verwacht: alle tests groen (inclusief de nieuwe `verwijderAiBezwaarZetHeeftManueelOp`)

#### Stap 1.6: Commit

```bash
git add app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieTaakService.java \
        app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieTaakServiceTest.java
git commit -m "feat: verwijderBezwaar ondersteunt ook AI-bezwaren"
```

---

### Task 2: Backend controller aanpassen

**Files:**
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieController.java`
- Test: `app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieControllerTest.java`

#### Stap 2.1: Pas de controller-test aan

In `ExtractieControllerTest`:

1. In de test `verwijderManueelBezwaar`: verander `verify(extractieTaakService).verwijderManueelBezwaar(...)` naar `verify(extractieTaakService).verwijderBezwaar(...)`

2. Verwijder de test `verwijderNietManueelBezwaarGeeft403` volledig.

3. Voeg geen nieuwe test toe — de bestaande test dekt 204 al.

#### Stap 2.2: Verifieer dat de controller-test faalt

```bash
cd app && mvn test -pl . -Dtest=ExtractieControllerTest -Dsurefire.failIfNoSpecifiedTests=false
```
Verwacht: `FAIL` — `verwijderManueelBezwaar` bestaat niet meer in de service

#### Stap 2.3: Pas de controller aan

In `ExtractieController`, method `verwijderBezwaar`:
- Verander de aanroep: `extractieTaakService.verwijderManueelBezwaar(...)` → `extractieTaakService.verwijderBezwaar(...)`
- Verwijder het `catch (IllegalStateException e)` blok + de 403-response

Resultaat:

```java
@DeleteMapping("/{naam}/extracties/{bestandsnaam}/bezwaren/{bezwaarId}")
public ResponseEntity<Void> verwijderBezwaar(
    @PathVariable String naam, @PathVariable String bestandsnaam,
    @PathVariable Long bezwaarId) {
  try {
    extractieTaakService.verwijderBezwaar(naam, bestandsnaam, bezwaarId);
    return ResponseEntity.noContent().build();
  } catch (IllegalArgumentException e) {
    return ResponseEntity.notFound().build();
  }
}
```

#### Stap 2.4: Verifieer dat alle controller-tests slagen

```bash
cd app && mvn test -pl . -Dtest=ExtractieControllerTest -Dsurefire.failIfNoSpecifiedTests=false
```
Verwacht: alle tests groen

#### Stap 2.5: Verifieer alle back-end tests

```bash
cd app && mvn test -Dsurefire.failIfNoSpecifiedTests=false
```
Verwacht: BUILD SUCCESS, geen falende tests

#### Stap 2.6: Commit

```bash
git add app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieController.java \
        app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieControllerTest.java
git commit -m "feat: controller gebruikt verwijderBezwaar, geen 403 meer"
```

---

### Task 3: Frontend — `vl-modal` bevestigingsflow

**Files:**
- Modify: `webapp/src/js/bezwaarschriften-bezwaren-tabel.js`

#### Stap 3.1: Voeg `VlModalComponent` toe aan imports

Bovenaan het bestand, voeg toe:

```javascript
import {VlModalComponent} from '@domg-wc/components/block/modal/vl-modal.component.js';
```

Voeg `VlModalComponent` toe aan de `registerWebComponents([...])` aanroep.

#### Stap 3.2: Voeg de modal toe aan het HTML-template in de constructor

Na het `</vl-side-sheet>` element in de constructor-template, voeg toe:

```html
<vl-modal id="verwijder-bezwaar-modal" title="Bezwaar verwijderen" closable>
  <div slot="content">
    <p>Weet je zeker dat je dit bezwaar wil verwijderen? Deze actie kan niet ongedaan gemaakt worden.</p>
  </div>
  <div slot="button">
    <vl-button id="verwijder-bezwaar-bevestig" error="">Verwijderen</vl-button>
  </div>
</vl-modal>
```

#### Stap 3.3: Initialiseer modal-state en koppel bevestigingsknop in `connectedCallback`

Voeg toe in de constructor (na de bestaande `this.__herberekenGepland = false;` regel):

```javascript
this.__teVerwijderenBezwaar = null; // { projectNaam, bestandsnaam, bezwaarId, actieveTab }
```

Voeg toe in `connectedCallback`, na de sluitknop-listener:

```javascript
const bevestigKnop = this.shadowRoot.querySelector('#verwijder-bezwaar-bevestig');
if (bevestigKnop) {
  bevestigKnop.addEventListener('click', () => this._voerVerwijderingUit());
}
```

#### Stap 3.4: Voeg `_vraagBevestigingVerwijder` toe

```javascript
_vraagBevestigingVerwijder(projectNaam, bestandsnaam, bezwaarId, actieveTab) {
  this.__teVerwijderenBezwaar = {projectNaam, bestandsnaam, bezwaarId, actieveTab};
  const modal = this.shadowRoot.querySelector('#verwijder-bezwaar-modal');
  if (modal) modal.open();
}
```

#### Stap 3.5: Hernoem en pas `_verwijderManueelBezwaar` aan naar `_voerVerwijderingUit`

Vervang de bestaande methode `_verwijderManueelBezwaar` volledig door:

```javascript
_voerVerwijderingUit() {
  const data = this.__teVerwijderenBezwaar;
  if (!data) return;

  const modal = this.shadowRoot.querySelector('#verwijder-bezwaar-modal');
  if (modal) modal.close();

  fetch(`/api/v1/projects/${encodeURIComponent(data.projectNaam)}/extracties/${encodeURIComponent(data.bestandsnaam)}/bezwaren/${data.bezwaarId}`, {
    method: 'DELETE',
  }).then((response) => {
    if (!response.ok) throw new Error('Verwijderen mislukt');
    this.__teVerwijderenBezwaar = null;
    this.toonExtractieDetails(data.projectNaam, data.bestandsnaam, data.actieveTab);
    this.dispatchEvent(new CustomEvent('bezwaar-gewijzigd', {
      bubbles: true, composed: true,
    }));
  }).catch(() => {
    this.__teVerwijderenBezwaar = null;
    const inhoud = this.shadowRoot.querySelector('#extractie-side-sheet-inhoud');
    if (inhoud) {
      const fout = document.createElement('div');
      fout.className = 'bezwaar-waarschuwing';
      fout.textContent = 'Verwijderen mislukt, probeer opnieuw.';
      inhoud.prepend(fout);
    }
  });
}
```

#### Stap 3.6: Commit

```bash
git add webapp/src/js/bezwaarschriften-bezwaren-tabel.js
git commit -m "feat: vl-modal bevestiging voor bezwaar verwijderen"
```

---

### Task 4: Frontend — verwijder-knop op alle bezwaren

**Files:**
- Modify: `webapp/src/js/bezwaarschriften-bezwaren-tabel.js`

#### Stap 4.1: Pas `_maakBezwaarItem` aan

De bestaande code toont de verwijder-knop enkel als `bezwaar.manueel === true`. Vervang dat blok door een knop die voor alle bezwaren verschijnt. Verander ook de aanroep van `_verwijderManueelBezwaar` naar `_vraagBevestigingVerwijder`.

Zoek het huidige blok:

```javascript
if (bezwaar.manueel) {
  const verwijderKnop = document.createElement('vl-button');
  verwijderKnop.setAttribute('icon', 'bin');
  verwijderKnop.setAttribute('error', '');
  verwijderKnop.setAttribute('ghost', '');
  verwijderKnop.setAttribute('label', 'Manueel bezwaar verwijderen');
  verwijderKnop.addEventListener('vl-click', () => {
    this._verwijderManueelBezwaar(projectNaam, bestandsnaam, bezwaar.id);
  });
  header.appendChild(verwijderKnop);
}
```

Vervang dit door (zónder de `if (bezwaar.manueel)` guard):

```javascript
const verwijderKnop = document.createElement('vl-button');
verwijderKnop.setAttribute('icon', 'bin');
verwijderKnop.setAttribute('error', '');
verwijderKnop.setAttribute('ghost', '');
verwijderKnop.setAttribute('label', 'Bezwaar verwijderen');
verwijderKnop.addEventListener('vl-click', () => {
  const actieveTab = isManueelTab ? 'manueel' : 'automatisch';
  this._vraagBevestigingVerwijder(projectNaam, bestandsnaam, bezwaar.id, actieveTab);
});
header.appendChild(verwijderKnop);
```

**Let op:** `_maakBezwaarItem(bezwaar, projectNaam, bestandsnaam, isManueelTab)` ontvangt al `isManueelTab` als parameter — gebruik die om de actieve tab door te geven.

#### Stap 4.2: Commit

```bash
git add webapp/src/js/bezwaarschriften-bezwaren-tabel.js
git commit -m "feat: verwijder-knop op alle bezwaren (manueel en AI)"
```

---

### Task 5: Cypress tests bijwerken en uitbreiden

**Files:**
- Modify: `webapp/test/bezwaarschriften-bezwaren-tabel-extractie-details.cy.js`

#### Stap 5.1: Update de bestaande verwijder-test om modal te bevestigen

De bestaande test `verwijdert manueel bezwaar na klik op verwijder-knop` klikt rechtstreeks op de verwijder-knop en verwacht dan een DELETE-call. Nu moet eerst de modal bevestigd worden.

Pas de test aan:

```javascript
it('verwijdert manueel bezwaar na bevestiging in modal', () => {
  cy.intercept('GET', '/api/v1/projects/*/extracties/*/details', {
    statusCode: 200,
    body: {
      bestandsnaam: 'bezwaar-001.txt',
      aantalBezwaren: 1,
      bezwaren: [
        {id: 10, samenvatting: 'Manueel bezwaar', passage: 'Passage.', passageGevonden: true, manueel: true},
      ],
    },
  }).as('details');

  cy.intercept('DELETE', '/api/v1/projects/windmolens/extracties/bezwaar-001.txt/bezwaren/10', {
    statusCode: 204,
  }).as('verwijder');

  cy.get('bezwaarschriften-bezwaren-tabel')
      .its(0)
      .then((el) => {
        el.projectNaam = 'windmolens';
      });

  cy.get('bezwaarschriften-bezwaren-tabel')
      .its(0)
      .invoke('toonExtractieDetails', 'windmolens', 'bezwaar-001.txt');

  cy.wait('@details');

  switchNaarManueelTab();

  cy.get('bezwaarschriften-bezwaren-tabel')
      .find('vl-tabs-pane#manueel .bezwaar-header vl-button[icon="bin"]')
      .click();

  // Modal moet verschijnen
  cy.get('bezwaarschriften-bezwaren-tabel')
      .find('#verwijder-bezwaar-modal')
      .should('exist');

  // Klik op bevestigingsknop
  cy.get('bezwaarschriften-bezwaren-tabel')
      .find('#verwijder-bezwaar-bevestig')
      .click({force: true});

  cy.wait('@verwijder');
});
```

#### Stap 5.2: Voeg test toe: verwijder-knop ook zichtbaar op AI-bezwaren

```javascript
it('toont verwijder-knop ook bij AI-bezwaar in automatisch-tab', () => {
  cy.intercept('GET', '/api/v1/projects/*/extracties/*/details', {
    statusCode: 200,
    body: MOCK_DETAILS_GEMENGD,
  }).as('details');

  cy.get('bezwaarschriften-bezwaren-tabel')
      .its(0)
      .invoke('toonExtractieDetails', 'windmolens', 'bezwaar-003.txt');

  cy.wait('@details');

  cy.get('bezwaarschriften-bezwaren-tabel')
      .find('vl-tabs-pane#automatisch .bezwaar-header vl-button[icon="bin"]')
      .should('have.length', 2);
});
```

#### Stap 5.3: Voeg test toe: verwijder AI-bezwaar blijft op automatisch-tab

```javascript
it('verwijdert AI-bezwaar en herlaadt op automatisch-tab', () => {
  cy.intercept('GET', '/api/v1/projects/*/extracties/*/details', {
    statusCode: 200,
    body: {
      bestandsnaam: 'bezwaar-003.txt',
      aantalBezwaren: 1,
      bezwaren: [
        {id: 1, samenvatting: 'AI bezwaar', passage: 'Passage.', passageGevonden: true, manueel: false},
      ],
    },
  }).as('details');

  cy.intercept('DELETE', '/api/v1/projects/windmolens/extracties/bezwaar-003.txt/bezwaren/1', {
    statusCode: 204,
  }).as('verwijder');

  cy.get('bezwaarschriften-bezwaren-tabel')
      .its(0)
      .then((el) => {
        el.projectNaam = 'windmolens';
      });

  cy.get('bezwaarschriften-bezwaren-tabel')
      .its(0)
      .invoke('toonExtractieDetails', 'windmolens', 'bezwaar-003.txt');

  cy.wait('@details');

  cy.get('bezwaarschriften-bezwaren-tabel')
      .find('vl-tabs-pane#automatisch .bezwaar-header vl-button[icon="bin"]')
      .first()
      .click();

  cy.get('bezwaarschriften-bezwaren-tabel')
      .find('#verwijder-bezwaar-bevestig')
      .click({force: true});

  cy.wait('@verwijder');

  // Side-panel herlaadt op automatisch-tab
  cy.get('bezwaarschriften-bezwaren-tabel')
      .find('vl-tabs')
      .should('have.attr', 'active-tab', 'automatisch');
});
```

#### Stap 5.4: Run de Cypress tests

```bash
cd webapp && npx cypress run --component --spec "test/bezwaarschriften-bezwaren-tabel-extractie-details.cy.js"
```
Verwacht: alle tests groen

#### Stap 5.5: Commit

```bash
git add webapp/test/bezwaarschriften-bezwaren-tabel-extractie-details.cy.js
git commit -m "test: cypress tests voor verwijder bezwaar met modal"
```

---

### Task 6: Build & verificatie

**Files:** geen nieuwe bestanden

#### Stap 6.1: Frontend build

```bash
cd webapp && npm run build
```
Verwacht: BUILD SUCCESS, geen errors

#### Stap 6.2: Maven resources bijwerken

```bash
mvn process-resources -pl webapp -Denforcer.skip=true
```
Verwacht: BUILD SUCCESS

#### Stap 6.3: Volledige back-end testsuite

```bash
cd app && mvn test
```
Verwacht: BUILD SUCCESS, 0 failures

#### Stap 6.4: Alle Cypress tests

```bash
cd webapp && npx cypress run --component
```
Verwacht: alle specs groen

#### Stap 6.5: Commit build output

```bash
git add webapp/build/ webapp/src/js/bezwaarschriften-bezwaren-tabel.js
git commit -m "chore: frontend build voor verwijder individueel bezwaar"
```

---

## Test- en verificatieplan

| Wat | Hoe | Verwacht |
|-----|-----|---------|
| AI-bezwaar verwijderen zet heeftManueel | `ExtractieTaakServiceTest#verwijderAiBezwaarZetHeeftManueelOp` | ✅ groen |
| Manueel bezwaar verwijderen herberekent heeftManueel | `ExtractieTaakServiceTest#verwijderLaatstManueelBezwaarZetHeeftManueelUit` | ✅ groen |
| Controller geeft 204 voor elk bezwaar | `ExtractieControllerTest#verwijderManueelBezwaar` | ✅ groen |
| Controller geeft geen 403 meer | `verwijderNietManueelBezwaarGeeft403` verwijderd | — |
| Verwijder-knop op AI-bezwaar zichtbaar | Cypress: `toont verwijder-knop ook bij AI-bezwaar` | ✅ groen |
| Modal verschijnt bij klik | Cypress: bevestigingstest | ✅ groen |
| DELETE pas na bevestiging | Cypress: `cy.wait('@verwijder')` na modal-klik | ✅ groen |
| Herlaad op juiste tab | Cypress: `active-tab` attribuut check | ✅ groen |
