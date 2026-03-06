# Pill-stabiliteit & Annuleer-functionaliteit Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Fix flickering timer-pills en voeg annuleer-functionaliteit toe voor wachtende/bezig extractie-taken.

**Architecture:** CSS fix voor pill-breedte + backend DELETE endpoint met thread interrupt + frontend kruisje-button met bevestigingsmodal. Controller coordineert tussen service (DB delete) en worker (Future cancel).

**Tech Stack:** Java 21, Spring Boot 3.4, JPA, WebSocket, Lit web components, @domg-wc design system.

---

### Task 1: CSS pill-flicker fix

**Files:**
- Modify: `webapp/src/js/bezwaarschriften-bezwaren-tabel.js:34-37`

**Step 1: Add CSS rules to constructor style block**

In de `<style>` sectie in de constructor (regel 34-37), wijzig:

```javascript
      <style>
        ${vlGlobalStyles}
        .status-cel { min-width: 220px; }
      </style>
```

naar:

```javascript
      <style>
        ${vlGlobalStyles}
        .status-cel { min-width: 220px; }
        .status-cel vl-pill {
          font-variant-numeric: tabular-nums;
          min-width: 180px;
          display: inline-block;
        }
      </style>
```

`font-variant-numeric: tabular-nums` maakt alle cijfers even breed (inherited door shadow DOM). `min-width: 180px` vangt breedte-sprongen bij "9:59" naar "10:00".

**Step 2: Handmatig testen in browser**

Start de app en controleer dat timer-pills niet meer van breedte verspringen bij elke seconde.

**Step 3: Commit**

```bash
git add webapp/src/js/bezwaarschriften-bezwaren-tabel.js
git commit -m "fix: pill-flicker door tabular-nums en min-width"
```

---

### Task 2: Backend — ExtractieWorker Future-tracking en annuleerTaak

**Files:**
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieWorker.java`
- Modify: `app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieWorkerTest.java`

**Step 1: Write failing test — annuleerTaak cancelt Future**

Voeg toe aan `ExtractieWorkerTest.java`:

```java
@Test
void annuleerTaakCanceltLopendeFuture() throws Exception {
  var taak = maakTaak(5L, "windmolens", "stuck.txt", 0);
  when(service.pakOpVoorVerwerking()).thenReturn(List.of(taak));
  when(verwerker.verwerk("windmolens", "stuck.txt", 0))
      .thenAnswer(invocation -> {
        Thread.sleep(10_000); // simuleer stuck taak
        return new ExtractieResultaat(100, 1);
      });

  worker.verwerkTaken();
  Thread.sleep(200); // wacht tot taak gestart is

  boolean geannuleerd = worker.annuleerTaak(5L);

  assertThat(geannuleerd).isTrue();
}

@Test
void annuleerTaakRetourneertFalseVoorOnbekendeTaak() {
  boolean geannuleerd = worker.annuleerTaak(999L);

  assertThat(geannuleerd).isFalse();
}
```

Voeg bovenaan de imports toe:

```java
import static org.assertj.core.api.Assertions.assertThat;
```

**Step 2: Run test — verify it fails**

```bash
cd app && mvn test -pl . -Dtest=ExtractieWorkerTest -Denforcer.skip=true
```

Expected: compilatiefout — `annuleerTaak` bestaat nog niet.

**Step 3: Implement Future-tracking en annuleerTaak**

Wijzig `ExtractieWorker.java`:

```java
package be.vlaanderen.omgeving.bezwaarschriften.project;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

/**
 * Worker die periodiek wachtende extractie-taken oppakt en asynchroon verwerkt.
 *
 * <p>Pollt elke seconde via {@link ExtractieTaakService#pakOpVoorVerwerking()}
 * en dient opgepakte taken in bij de thread pool voor parallelle verwerking.
 */
@Component
public class ExtractieWorker {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final ExtractieTaakService service;
  private final ExtractieVerwerker verwerker;
  private final ThreadPoolTaskExecutor executor;
  private final ConcurrentHashMap<Long, Future<?>> lopendeTaken = new ConcurrentHashMap<>();

  /**
   * Maakt een nieuwe ExtractieWorker aan.
   *
   * @param service service voor het beheren van extractie-taken
   * @param verwerker verwerker die de daadwerkelijke extractie uitvoert
   * @param executor thread pool voor asynchrone uitvoering
   */
  public ExtractieWorker(ExtractieTaakService service, ExtractieVerwerker verwerker,
      ThreadPoolTaskExecutor executor) {
    this.service = service;
    this.verwerker = verwerker;
    this.executor = executor;
  }

  /**
   * Pollt periodiek voor wachtende taken en dient ze in bij de thread pool.
   */
  @Scheduled(fixedDelay = 1000)
  public void verwerkTaken() {
    var taken = service.pakOpVoorVerwerking();
    for (var taak : taken) {
      var future = executor.submit(() -> verwerkTaak(taak));
      lopendeTaken.put(taak.getId(), future);
    }
  }

  /**
   * Annuleert een lopende taak door de bijbehorende Future te cancellen.
   *
   * @param taakId id van de te annuleren taak
   * @return true als de taak gevonden en geannuleerd is, false als er geen lopende taak was
   */
  public boolean annuleerTaak(Long taakId) {
    var future = lopendeTaken.remove(taakId);
    if (future != null) {
      LOGGER.info("Taak {} geannuleerd", taakId);
      return future.cancel(true);
    }
    return false;
  }

  private void verwerkTaak(ExtractieTaak taak) {
    try {
      var resultaat = verwerker.verwerk(
          taak.getProjectNaam(),
          taak.getBestandsnaam(),
          taak.getAantalPogingen());
      try {
        service.markeerKlaar(taak.getId(), resultaat.aantalWoorden(), resultaat.aantalBezwaren());
      } catch (IllegalArgumentException e) {
        LOGGER.info("Taak {} niet meer aanwezig na voltooiing (geannuleerd?)", taak.getId());
      }
    } catch (Exception e) {
      LOGGER.error("Fout bij verwerking van taak {}: {}", taak.getId(), e.getMessage(), e);
      try {
        service.markeerFout(taak.getId(), e.getMessage());
      } catch (IllegalArgumentException ex) {
        LOGGER.info("Taak {} niet meer aanwezig na fout (geannuleerd?)", taak.getId());
      }
    } finally {
      lopendeTaken.remove(taak.getId());
    }
  }
}
```

**Step 4: Run tests — verify they pass**

```bash
cd app && mvn test -pl . -Dtest=ExtractieWorkerTest -Denforcer.skip=true
```

Expected: alle tests PASS.

**Step 5: Commit**

```bash
git add app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieWorker.java \
       app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieWorkerTest.java
git commit -m "feat: Future-tracking en annuleerTaak in ExtractieWorker"
```

---

### Task 3: Backend — ExtractieTaakService.verwijderTaak

**Files:**
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieTaakService.java`
- Modify: `app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieTaakServiceTest.java`

**Step 1: Write failing tests**

Voeg toe aan `ExtractieTaakServiceTest.java`:

```java
@Test
void verwijderTaakVerwijdertUitRepository() {
  var taak = maakTaak(1L, "windmolens", "bezwaar-001.txt", ExtractieTaakStatus.WACHTEND);
  when(repository.findById(1L)).thenReturn(Optional.of(taak));

  service.verwijderTaak("windmolens", 1L);

  verify(repository).delete(taak);
}

@Test
void verwijderTaakGooitExceptieBijOnbekendeTaak() {
  when(repository.findById(999L)).thenReturn(Optional.empty());

  org.assertj.core.api.Assertions.assertThatThrownBy(() ->
      service.verwijderTaak("windmolens", 999L))
      .isInstanceOf(IllegalArgumentException.class);
}

@Test
void verwijderTaakGooitExceptieBijVerkeerdeProject() {
  var taak = maakTaak(1L, "snelweg", "bezwaar-001.txt", ExtractieTaakStatus.BEZIG);
  when(repository.findById(1L)).thenReturn(Optional.of(taak));

  org.assertj.core.api.Assertions.assertThatThrownBy(() ->
      service.verwijderTaak("windmolens", 1L))
      .isInstanceOf(IllegalArgumentException.class);
}
```

**Step 2: Run tests — verify they fail**

```bash
cd app && mvn test -pl . -Dtest=ExtractieTaakServiceTest -Denforcer.skip=true
```

Expected: compilatiefout — `verwijderTaak` bestaat nog niet.

**Step 3: Implement verwijderTaak**

Voeg toe aan `ExtractieTaakService.java` (na de `verwerkOnafgeronde` methode):

```java
  /**
   * Verwijdert een extractie-taak uit de database.
   *
   * @param projectNaam naam van het project (voor validatie)
   * @param taakId id van de te verwijderen taak
   * @throws IllegalArgumentException als de taak niet bestaat of niet bij het project hoort
   */
  @Transactional
  public void verwijderTaak(String projectNaam, Long taakId) {
    var taak = repository.findById(taakId)
        .orElseThrow(() -> new IllegalArgumentException("Taak niet gevonden: " + taakId));
    if (!taak.getProjectNaam().equals(projectNaam)) {
      throw new IllegalArgumentException(
          "Taak " + taakId + " behoort niet tot project: " + projectNaam);
    }
    repository.delete(taak);
    LOGGER.info("Taak {} verwijderd uit project '{}'", taakId, projectNaam);
  }
```

**Step 4: Run tests — verify they pass**

```bash
cd app && mvn test -pl . -Dtest=ExtractieTaakServiceTest -Denforcer.skip=true
```

Expected: alle tests PASS.

**Step 5: Commit**

```bash
git add app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieTaakService.java \
       app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieTaakServiceTest.java
git commit -m "feat: verwijderTaak in ExtractieTaakService"
```

---

### Task 4: Backend — DELETE endpoint in ExtractieController

**Files:**
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieController.java`
- Modify: `app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieControllerTest.java`

**Step 1: Write failing tests**

Voeg import toe bovenaan `ExtractieControllerTest.java`:

```java
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
```

Voeg `ExtractieWorker` mock toe:

```java
@MockBean
private ExtractieWorker extractieWorker;
```

Voeg tests toe:

```java
@Test
void annuleertExtractieTaak() throws Exception {
  mockMvc.perform(delete("/api/v1/projects/windmolens/extracties/1")
          .with(csrf()))
      .andExpect(status().isNoContent());

  verify(extractieTaakService).verwijderTaak("windmolens", 1L);
  verify(extractieWorker).annuleerTaak(1L);
}

@Test
void annulerenGeeft404BijOnbekendeTaak() throws Exception {
  doThrow(new IllegalArgumentException("Taak niet gevonden"))
      .when(extractieTaakService).verwijderTaak("windmolens", 999L);

  mockMvc.perform(delete("/api/v1/projects/windmolens/extracties/999")
          .with(csrf()))
      .andExpect(status().isNotFound());
}
```

**Step 2: Run tests — verify they fail**

```bash
cd app && mvn test -pl . -Dtest=ExtractieControllerTest -Denforcer.skip=true
```

Expected: compilatiefout of 405 Method Not Allowed.

**Step 3: Implement DELETE endpoint**

Wijzig `ExtractieController.java`. Voeg imports toe:

```java
import org.springframework.web.bind.annotation.DeleteMapping;
```

Voeg `ExtractieWorker` toe als dependency (wijzig constructor):

```java
  private final ExtractieTaakService extractieTaakService;
  private final ExtractieWorker extractieWorker;

  public ExtractieController(ExtractieTaakService extractieTaakService,
      ExtractieWorker extractieWorker) {
    this.extractieTaakService = extractieTaakService;
    this.extractieWorker = extractieWorker;
  }
```

Voeg endpoint toe (na het `verwerken` endpoint):

```java
  /**
   * Annuleert een extractie-taak. Verwijdert de taak uit de database
   * en annuleert eventuele lopende verwerking.
   *
   * @param naam projectnaam
   * @param taakId id van de te annuleren taak
   * @return 204 No Content bij succes, 404 als taak niet gevonden
   */
  @DeleteMapping("/{naam}/extracties/{taakId}")
  public ResponseEntity<Void> annuleer(@PathVariable String naam, @PathVariable Long taakId) {
    try {
      extractieTaakService.verwijderTaak(naam, taakId);
      extractieWorker.annuleerTaak(taakId);
      return ResponseEntity.noContent().build();
    } catch (IllegalArgumentException e) {
      return ResponseEntity.notFound().build();
    }
  }
```

**Step 4: Run tests — verify they pass**

```bash
cd app && mvn test -pl . -Dtest=ExtractieControllerTest -Denforcer.skip=true
```

Expected: alle tests PASS.

**Step 5: Run alle backend tests**

```bash
cd app && mvn test -pl . -Denforcer.skip=true
```

Expected: alle tests PASS. Let op: `ExtractieWorkerTest` moet nog steeds slagen — de constructor-wijziging in de controller raakt de worker-test niet.

**Step 6: Commit**

```bash
git add app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieController.java \
       app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieControllerTest.java
git commit -m "feat: DELETE endpoint voor annuleren extractie-taak"
```

---

### Task 5: Frontend — kruisje-button op actieve pills

**Files:**
- Modify: `webapp/src/js/bezwaarschriften-bezwaren-tabel.js`

**Step 1: Voeg CSS toe voor annuleer-button**

In de `<style>` sectie in de constructor, voeg toe na de pill-regels:

```css
        .actieve-pill {
          display: inline-flex;
          align-items: center;
          gap: 4px;
        }
        .annuleer-btn {
          background: none;
          border: 1px solid #ccc;
          border-radius: 50%;
          cursor: pointer;
          font-size: 14px;
          color: #666;
          width: 22px;
          height: 22px;
          padding: 0;
          line-height: 1;
          display: inline-flex;
          align-items: center;
          justify-content: center;
        }
        .annuleer-btn:hover {
          color: #fff;
          background: #d32f2f;
          border-color: #d32f2f;
        }
```

**Step 2: Sla taakId op in __takenData**

Wijzig `werkBijMetTaakUpdate` (regel 76-90). Voeg `id` toe:

```javascript
  werkBijMetTaakUpdate(taak) {
    this.__takenData[taak.bestandsnaam] = {
      id: taak.id,
      aangemaaktOp: taak.aangemaaktOp,
      verwerkingGestartOp: taak.verwerkingGestartOp,
    };
```

**Step 3: Wijzig _formatStatus — wrap actieve pills met span + button**

Vervang `_formatStatus` (regel 189-197):

```javascript
  _formatStatus(b, nu) {
    nu = nu || Date.now();
    const label = this._formatStatusLabel(b, nu);
    const type = STATUS_PILL_TYPES[b.status] || '';
    const typeAttr = type ? ` type="${type}"` : '';
    const disabledAttr = b.status === 'niet ondersteund' ? ' disabled' : '';
    const isActief = b.status === 'wachtend' || b.status === 'bezig';

    if (isActief) {
      return `<span class="actieve-pill"><vl-pill${typeAttr}><span class="timer-tekst">${label}</span></vl-pill><button class="annuleer-btn" data-bestandsnaam="${this._escapeHtml(b.bestandsnaam)}" title="Annuleer verwerking">&times;</button></span>`;
    }
    return `<vl-pill${typeAttr}${disabledAttr}>${label}</vl-pill>`;
  }
```

**Step 4: Wijzig _updateTimers — target de span i.p.v. de pill**

Vervang `_updateTimers` (regel 174-187):

```javascript
  _updateTimers() {
    const nu = Date.now();
    this.__bezwaren.forEach((b) => {
      if (b.status !== 'wachtend' && b.status !== 'bezig') return;
      const cel = this.shadowRoot.querySelector(
          `.status-cel[data-bestandsnaam="${CSS.escape(b.bestandsnaam)}"]`,
      );
      if (!cel) return;
      const timerTekst = cel.querySelector('.timer-tekst');
      if (timerTekst) {
        timerTekst.textContent = this._formatStatusLabel(b, nu);
      }
    });
  }
```

**Step 5: Koppel event listeners voor annuleer-buttons**

In `_renderRijen` (na de bestaande checkbox-listener koppeling op regel 144-146), voeg toe:

```javascript
    tbody.querySelectorAll('.annuleer-btn').forEach((btn) => {
      btn.addEventListener('click', (e) => {
        e.stopPropagation();
        const bestandsnaam = btn.dataset.bestandsnaam;
        const taakData = this.__takenData[bestandsnaam];
        if (taakData && taakData.id) {
          this.dispatchEvent(new CustomEvent('annuleer-taak', {
            detail: {bestandsnaam, taakId: taakData.id},
            bubbles: true,
            composed: true,
          }));
        }
      });
    });
```

**Step 6: Commit**

```bash
git add webapp/src/js/bezwaarschriften-bezwaren-tabel.js
git commit -m "feat: kruisje-button op actieve pills voor annuleren"
```

---

### Task 6: Frontend — bevestigingsmodal en API-call

**Files:**
- Modify: `webapp/src/js/bezwaarschriften-project-selectie.js`

**Step 1: Voeg annuleer-modal toe aan constructor HTML**

Na de bestaande `verwijder-modal` (regel 49-56), voeg toe:

```html
      <vl-modal id="annuleer-modal" title="Verwerking annuleren" closable>
        <div slot="content">
          <p id="annuleer-bevestiging-tekst"></p>
        </div>
        <div slot="button">
          <vl-button id="annuleer-bevestig-knop" error="">Annuleren</vl-button>
        </div>
      </vl-modal>
```

**Step 2: Voeg state-variabele toe in constructor**

Na `this._teVerwijderenBestanden = [];` (regel 80):

```javascript
    this._teAnnulerenTaak = null;
```

**Step 3: Luister naar annuleer-taak event**

In `_koppelEventListeners`, na het `selectie-gewijzigd` listener blok (rond regel 228), voeg toe:

```javascript
    this.shadowRoot.addEventListener('annuleer-taak', (e) => {
      const {bestandsnaam, taakId} = e.detail;
      this._teAnnulerenTaak = {bestandsnaam, taakId};
      const tekst = this.shadowRoot.querySelector('#annuleer-bevestiging-tekst');
      if (tekst) {
        tekst.textContent = `Weet je zeker dat je de verwerking van "${bestandsnaam}" wilt annuleren?`;
      }
      const modal = this.shadowRoot.querySelector('#annuleer-modal');
      if (modal) modal.open();
    });

    const annuleerBevestigKnop = this.shadowRoot && this.shadowRoot.querySelector('#annuleer-bevestig-knop');
    if (annuleerBevestigKnop) {
      annuleerBevestigKnop.addEventListener('vl-click', () => {
        if (this._teAnnulerenTaak) {
          this._annuleerTaak(this._teAnnulerenTaak.taakId);
        }
      });
    }
```

**Step 4: Implementeer _annuleerTaak API-call**

Voeg nieuwe methode toe (na `_verwijderBestanden`):

```javascript
  _annuleerTaak(taakId) {
    if (!this.__geselecteerdProject) return;

    this._zetBezig(true);
    this._verbergFout();

    fetch(`/api/v1/projects/${encodeURIComponent(this.__geselecteerdProject)}/extracties/${taakId}`, {
      method: 'DELETE',
    })
        .then((response) => {
          if (!response.ok) throw new Error('Annuleren mislukt');
          this._toonToast('success', 'Verwerking geannuleerd.');
          this._laadBezwaren(this.__geselecteerdProject);
        })
        .catch(() => {
          this._toonFout('Annuleren van verwerking mislukt.');
        })
        .finally(() => {
          this._zetBezig(false);
          this._teAnnulerenTaak = null;
        });
  }
```

**Step 5: Build en test**

```bash
cd webapp && npm run build && cd .. && mvn process-resources -pl webapp -Denforcer.skip=true
```

Start de app en test handmatig:
1. Start een extractie op een bestand
2. Controleer dat wachtend/bezig pills een × knop tonen
3. Klik op × — bevestigingsmodal verschijnt
4. Bevestig — taak wordt geannuleerd, bestand keert terug naar "Te verwerken"
5. Controleer dat voltooide/fout/todo pills GEEN × knop hebben
6. Controleer dat pills niet meer flikkeren

**Step 6: Commit**

```bash
git add webapp/src/js/bezwaarschriften-project-selectie.js
git commit -m "feat: bevestigingsmodal en API-call voor annuleren extractie-taak"
```

---

### Task 7: Slotcontrole — alle tests draaien

**Step 1: Run alle backend tests**

```bash
cd app && mvn test -pl . -Denforcer.skip=true
```

Expected: alle tests PASS.

**Step 2: Build frontend**

```bash
cd webapp && npm run build
```

Expected: build succesvol zonder warnings.

**Step 3: Final commit (indien nodig)**

Als er nog aanpassingen zijn, commit die. Anders is de feature branch klaar voor review.
