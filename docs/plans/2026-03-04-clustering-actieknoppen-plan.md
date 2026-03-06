# Clustering actieknoppen per status — Implementatieplan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Actieknoppen altijd zichtbaar in clustering-statusrij, GEANNULEERD status verwijderen, retry-functionaliteit toevoegen bij klaar.

**Architecture:** Backend: verwijder GEANNULEERD uit enum, annuleer = taak verwijderen (wordt weer todo). Frontend: meerdere actieknoppen per status, retry = delete + start.

**Tech Stack:** Java 21, Spring Boot 3.x, Liquibase, Lit web components, @domg-wc, Cypress

---

### Task 1: Backend — Verwijder GEANNULEERD uit enum en pas annuleer() aan

**Files:**
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/ClusteringTaakStatus.java`
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/ClusteringTaakService.java:168-200` (isGeannuleerd + annuleer)
- Test: `app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/ClusteringTaakServiceTest.java`

**Step 1: Update bestaande tests voor het nieuwe annuleer-gedrag**

De test `annuleer_zetStatusOpGeannuleerd` wordt `annuleer_verwijdertWachtendeTaak`:

```java
@Test
void annuleer_verwijdertWachtendeTaak() {
  var taak = maakTaak(1L, "windmolens", "Geluid", ClusteringTaakStatus.WACHTEND);
  when(taakRepository.findById(1L)).thenReturn(Optional.of(taak));

  var resultaat = service.annuleer(1L);

  assertThat(resultaat).isTrue();
  verify(taakRepository).delete(taak);
  verify(taakRepository, never()).save(any());
  verify(notificatie, never()).clusteringTaakGewijzigd(any());
}
```

Update `isGeannuleerd` tests — het checkt nu of de taak niet meer bestaat:

```java
@Test
void isGeannuleerd_returnsTrueWhenNietGevonden() {
  when(taakRepository.existsById(1L)).thenReturn(false);
  assertThat(service.isGeannuleerd(1L)).isTrue();
}

@Test
void isGeannuleerd_returnsFalseWhenBestaat() {
  when(taakRepository.existsById(1L)).thenReturn(true);
  assertThat(service.isGeannuleerd(1L)).isFalse();
}
```

Verwijder de oude `isGeannuleerd_returnsTrueWhenGeannuleerd`, `isGeannuleerd_returnsFalseWhenBezig`, en `isGeannuleerd_returnsFalseWhenNotFound` tests.

**Step 2: Run tests om te verifiëren dat ze falen**

Run: `mvn test -pl app -Dtest=ClusteringTaakServiceTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL — tests refereren nieuwe gedrag dat nog niet geïmplementeerd is.

**Step 3: Verwijder GEANNULEERD uit enum**

In `ClusteringTaakStatus.java`, verwijder `GEANNULEERD`:

```java
public enum ClusteringTaakStatus {
  WACHTEND,
  BEZIG,
  KLAAR,
  FOUT
}
```

**Step 4: Pas annuleer() aan om taak te verwijderen**

In `ClusteringTaakService.java`, vervang de `annuleer()` methode:

```java
@Transactional
public boolean annuleer(Long taakId) {
  var taak = taakRepository.findById(taakId).orElse(null);
  if (taak == null) {
    return false;
  }

  if (taak.getStatus() != ClusteringTaakStatus.WACHTEND
      && taak.getStatus() != ClusteringTaakStatus.BEZIG) {
    return false;
  }

  taakRepository.delete(taak);
  return true;
}
```

**Step 5: Pas isGeannuleerd() aan**

Vervang `isGeannuleerd()` in `ClusteringTaakService.java`:

```java
public boolean isGeannuleerd(Long taakId) {
  return !taakRepository.existsById(taakId);
}
```

**Step 6: Run tests**

Run: `mvn test -pl app -Dtest=ClusteringTaakServiceTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS

**Step 7: Commit**

```bash
git add app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/ClusteringTaakStatus.java \
  app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/ClusteringTaakService.java \
  app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/ClusteringTaakServiceTest.java
git commit -m "refactor: verwijder GEANNULEERD status, annuleer verwijdert taak"
```

---

### Task 2: Backend — Pas ClusteringWorker en KernbezwaarService aan

**Files:**
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/ClusteringWorker.java`
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/KernbezwaarService.java:144,265` (isGeannuleerd checks)
- Keep: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/ClusteringGeannuleerdException.java` (ongewijzigd, nog steeds nuttig)
- Test: `app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/ClusteringWorkerTest.java`

**Step 1: Update ClusteringWorkerTest**

De bestaande test `vangtClusteringGeannuleerdExceptionZonderMarkeerFout` is al correct — bij annulering wordt de exception gevangen en er wordt niets gemarkeerd. De taak is al verwijderd.

Geen test-wijzigingen nodig: de ClusteringWorker vangt al de `ClusteringGeannuleerdException` zonder verdere actie, en de `IllegalArgumentException` fallbacks bij `markeerKlaar`/`markeerFout` dekken het scenario dat de taak al verwijderd is.

**Step 2: Run alle testen om te verifiëren dat alles compileert**

Run: `mvn test -pl app -Dtest=ClusteringWorkerTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS — de worker code gebruikt `isGeannuleerd` niet direct (dat zit in KernbezwaarService).

Verifieer ook KernbezwaarService compileert (de `isGeannuleerd` calls werken nog met de nieuwe implementatie):

Run: `mvn test -pl app -Dtest=KernbezwaarServiceTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS (als test bestaat) of compilation success.

**Step 3: Run alle unit tests**

Run: `mvn test -pl app`
Expected: PASS. Alle referenties naar GEANNULEERD in de codebase zouden nu opgelost moeten zijn. Check compilatiefouten — als andere klassen `ClusteringTaakStatus.GEANNULEERD` refereren, fix ze.

**Step 4: Commit**

```bash
git add -A
git commit -m "fix: verwijder GEANNULEERD referenties uit worker en service"
```

---

### Task 3: Backend — Pas controller-test aan voor nieuw annuleer-gedrag

**Files:**
- Modify: `app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/ClusteringTaakControllerTest.java`

De controller zelf hoeft niet te wijzigen — `annuleer()` wordt al correct aangeroepen. Maar de tests moeten verifiëren dat na annulering de categorie weer `todo` wordt (geen GEANNULEERD in de response).

**Step 1: Verifieer dat bestaande controller-tests nog passen**

Run: `mvn test -pl app -Dtest=ClusteringTaakControllerTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS — de controller-tests mocken `taakService.annuleer()` en checken alleen HTTP status.

**Step 2: Commit (als er wijzigingen waren)**

Alleen committen als er daadwerkelijk bestanden gewijzigd zijn.

---

### Task 4: Backend — Liquibase migratie voor opruimen GEANNULEERD rijen

**Files:**
- Create: `app/src/main/resources/config/liquibase/changelog/20260304-verwijder-geannuleerd.xml`
- Modify: `app/src/main/resources/config/liquibase/master.xml`

**Step 1: Schrijf de migratie**

```xml
<?xml version="1.0" encoding="utf-8"?>
<databaseChangeLog
    xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-3.1.xsd">

  <changeSet id="20260304-verwijder-geannuleerd" author="kenzo">
    <comment>Verwijder clustering-taken met status GEANNULEERD (status bestaat niet meer)</comment>
    <delete tableName="clustering_taak">
      <where>status = 'GEANNULEERD'</where>
    </delete>
  </changeSet>

</databaseChangeLog>
```

**Step 2: Voeg migratie toe aan master.xml**

Voeg na de laatste `<include>` regel toe:
```xml
  <include file="config/liquibase/changelog/20260304-verwijder-geannuleerd.xml"/>
```

**Step 3: Commit**

```bash
git add app/src/main/resources/config/liquibase/changelog/20260304-verwijder-geannuleerd.xml \
  app/src/main/resources/config/liquibase/master.xml
git commit -m "chore: liquibase migratie verwijder GEANNULEERD rijen"
```

---

### Task 5: Frontend — Wijzig _maakActieKnop naar _maakActieKnoppen

**Files:**
- Modify: `webapp/src/js/bezwaarschriften-kernbezwaren.js:389-534`

**Step 1: Schrijf Cypress test voor meerdere knoppen bij klaar**

Voeg toe aan `webapp/test/bezwaarschriften-kernbezwaren.cy.js`:

```javascript
it('toont vuilbak en retry knop bij klare categorie', () => {
  cy.get('bezwaarschriften-kernbezwaren')
      .then(($el) => $el[0].laadClusteringTaken('testproject'));

  cy.wait('@clusteringTaken');

  // Vuilbak-knop
  cy.get('bezwaarschriften-kernbezwaren')
      .find('.categorie-wrapper[data-categorie="Mobiliteit"] vl-button[icon="bin"]')
      .should('exist')
      .and('have.attr', 'ghost', '')
      .and('have.attr', 'error', '');

  // Retry-knop
  cy.get('bezwaarschriften-kernbezwaren')
      .find('.categorie-wrapper[data-categorie="Mobiliteit"] vl-button[icon="synchronize"]')
      .should('exist')
      .and('have.attr', 'ghost', '');
});

it('retry bij klaar verwijdert en herstart clustering', () => {
  cy.intercept('DELETE', '/api/v1/projects/testproject/clustering-taken/Mobiliteit', {
    statusCode: 200,
  }).as('verwijderClustering');

  cy.intercept('POST', '/api/v1/projects/testproject/clustering-taken/Mobiliteit', {
    statusCode: 202,
    body: {
      id: 10, projectNaam: 'testproject', categorie: 'Mobiliteit',
      status: 'wachtend', aantalBezwaren: 42,
      aangemaaktOp: '2026-03-04T10:00:00Z',
    },
  }).as('startClustering');

  cy.get('bezwaarschriften-kernbezwaren')
      .then(($el) => $el[0].laadClusteringTaken('testproject'));

  cy.wait('@clusteringTaken');

  cy.get('bezwaarschriften-kernbezwaren')
      .find('.categorie-wrapper[data-categorie="Mobiliteit"] vl-button[icon="synchronize"]')
      .click();

  cy.wait('@verwijderClustering');
  cy.wait('@startClustering');
});

it('retry bij klaar toont modal als er antwoorden zijn', () => {
  cy.intercept('DELETE', '/api/v1/projects/testproject/clustering-taken/Mobiliteit', {
    statusCode: 409,
    body: {aantalAntwoorden: 3},
  }).as('verwijderClustering');

  cy.get('bezwaarschriften-kernbezwaren')
      .then(($el) => $el[0].laadClusteringTaken('testproject'));

  cy.wait('@clusteringTaken');

  cy.get('bezwaarschriften-kernbezwaren')
      .find('.categorie-wrapper[data-categorie="Mobiliteit"] vl-button[icon="synchronize"]')
      .click();

  cy.wait('@verwijderClustering');

  cy.get('bezwaarschriften-kernbezwaren')
      .find('#verwijder-bevestiging-inhoud')
      .should('contain.text', '3 antwoord(en)');
});
```

**Step 2: Run test om te verifiëren dat ze falen**

Run: `cd webapp && npm test`
Expected: FAIL — de oude tests zoeken naar `vl-button[label="Verwijder clustering"]` die niet meer bestaat, en de nieuwe tests falen.

**Step 3: Implementeer _maakActieKnoppen**

Vervang `_maakActieKnop(ct)` (regel 491-534) door:

```javascript
_maakActieKnoppen(ct) {
  const fragment = document.createDocumentFragment();

  const maakKnop = (icon, titel, onClick, error = false) => {
    const btn = document.createElement('vl-button');
    btn.setAttribute('icon', icon);
    btn.setAttribute('label', titel);
    btn.setAttribute('ghost', '');
    if (error) btn.setAttribute('error', '');
    btn.addEventListener('click', (e) => {
      e.stopPropagation();
      onClick();
    });
    return btn;
  };

  switch (ct.status) {
    case 'todo':
      fragment.appendChild(maakKnop('play-filled', 'Clustering starten',
          () => this._startClustering(ct.categorie)));
      break;
    case 'wachtend':
    case 'bezig':
      fragment.appendChild(maakKnop('close', 'Annuleer clustering',
          () => this._annuleerClustering(ct.categorie)));
      break;
    case 'klaar':
      fragment.appendChild(maakKnop('bin', 'Verwijder clustering',
          () => this._toonVerwijderBevestiging(ct.categorie), true));
      fragment.appendChild(maakKnop('synchronize', 'Opnieuw clusteren',
          () => this._retryClustering(ct.categorie)));
      break;
    case 'fout':
      fragment.appendChild(maakKnop('synchronize', 'Opnieuw clusteren',
          () => this._startClustering(ct.categorie)));
      break;
  }

  return fragment;
}
```

**Step 4: Update aanroep in _renderCategorieOverzicht**

Vervang in `_renderCategorieOverzicht` (rond regel 389-394):

```javascript
// Oud:
const actieKnop = this._maakActieKnop(ct);
if (actieKnop) {
  categorieHeader.appendChild(actieKnop);
}

// Nieuw:
const actieKnoppen = this._maakActieKnoppen(ct);
categorieHeader.appendChild(actieKnoppen);
```

**Step 5: Voeg _retryClustering methode toe**

Voeg toe na `_toonVerwijderBevestiging` (rond regel 684):

```javascript
_retryClustering(categorie) {
  if (!this._projectNaam) return;
  const url = `/api/v1/projects/${encodeURIComponent(this._projectNaam)}/clustering-taken/${encodeURIComponent(categorie)}`;

  fetch(url, {method: 'DELETE'})
      .then((response) => {
        if (response.status === 409) {
          return response.json().then((data) => {
            this._toonVerwijderBevestigingModal(
                categorie,
                data.aantalAntwoorden || 0,
                () => {
                  fetch(`${url}?bevestigd=true`, {method: 'DELETE'})
                      .then((resp) => {
                        if (!resp.ok) throw new Error('Verwijderen mislukt');
                        this._startClustering(categorie);
                      });
                },
            );
            return null;
          });
        }
        if (!response.ok) throw new Error('Opnieuw clusteren mislukt');
        this._startClustering(categorie);
        return null;
      })
      .catch(() => {
        this.dispatchEvent(new CustomEvent('toon-foutmelding', {
          bubbles: true, composed: true,
          detail: {bericht: 'Opnieuw clusteren mislukt'},
        }));
      });
}
```

**Step 6: Update bestaande Cypress test die naar verwijder-label zoekt**

De test `toont verwijder-bevestiging modal bij klare categorie met antwoorden` zoekt naar `vl-button[label="Verwijder clustering"]`. Update de selector naar `vl-button[icon="bin"]`:

```javascript
// Oud:
.find('.categorie-wrapper[data-categorie="Mobiliteit"] vl-button[label="Verwijder clustering"]')

// Nieuw:
.find('.categorie-wrapper[data-categorie="Mobiliteit"] vl-button[icon="bin"]')
```

**Step 7: Run tests**

Run: `cd webapp && npm test`
Expected: PASS

**Step 8: Run lint**

Run: `cd webapp && npm run format:fix`

**Step 9: Commit**

```bash
git add webapp/src/js/bezwaarschriften-kernbezwaren.js \
  webapp/test/bezwaarschriften-kernbezwaren.cy.js
git commit -m "feat: actieknoppen per clustering-status met retry bij klaar"
```

---

### Task 6: Frontend — Verwijder geannuleerd case uit rendering

**Files:**
- Modify: `webapp/src/js/bezwaarschriften-kernbezwaren.js`

**Step 1: Verifieer dat er geen geannuleerd-referenties meer zijn in de frontend**

Zoek naar `geannuleerd` of `GEANNULEERD` in het bestand. De `_maakStatusPill` default case vangt onbekende statussen al op, dus er is waarschijnlijk geen expliciete `geannuleerd` case. Verifieer en verwijder indien aanwezig.

**Step 2: Run tests**

Run: `cd webapp && npm test`
Expected: PASS

**Step 3: Commit (als er wijzigingen waren)**

Alleen committen als er daadwerkelijk wijzigingen zijn.

---

### Task 7: Integratietest — Backend verify

**Step 1: Run alle backend tests**

Run: `mvn test -pl app`
Expected: PASS

**Step 2: Run integratietests (als Docker draait)**

Run: `mvn verify -pl app`
Expected: PASS

**Step 3: Fix eventuele compilatiefouten**

Zoek naar resterende referenties naar `GEANNULEERD` in de hele codebase:
- `ExtractieWorker.java` en `ConsolidatieWorker.java` — deze bevatten referenties maar naar hun eigen annulerings-mechanismen, niet naar `ClusteringTaakStatus.GEANNULEERD`. Verifieer.

---

### Task 8: Frontend build + Maven process-resources

**Step 1: Build frontend**

Run: `cd webapp && npm run build`
Expected: PASS

**Step 2: Process resources voor Spring Boot**

Run: `mvn process-resources -pl webapp -Denforcer.skip=true`
Expected: PASS

**Step 3: Commit build-artefacten indien nodig**

---

### Task 9: Volledige verificatie

**Step 1: Run alle backend unit tests**

Run: `mvn test -pl app`
Expected: PASS

**Step 2: Run alle frontend tests**

Run: `cd webapp && npm test`
Expected: PASS

**Step 3: Run integratietests**

Run: `mvn verify -pl app`
Expected: PASS

**Step 4: Handmatige smoke test (optioneel)**

Start de applicatie:
```bash
docker compose up -d
mvn spring-boot:run -pl app -Pdev
```

Verifieer in de browser:
- Todo-categorie toont play-knop
- Klaar-categorie toont vuilbak + retry-knop
- Fout-categorie toont retry-knop
- Annuleren brengt categorie terug naar todo
