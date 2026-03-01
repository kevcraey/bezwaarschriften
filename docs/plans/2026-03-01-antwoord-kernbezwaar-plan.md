# Antwoord op kernbezwaar — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Ambtenaren kunnen per kernbezwaar een formeel weerwoord invoeren via een inline rich text editor, opgeslagen in PostgreSQL.

**Architecture:** Uitbreiding van het bestaande `kernbezwaar` package met een JPA entity, repository, en PUT endpoint. Frontend breidt de accordeon-items uit met een pennetje-knop die een `vl-textarea-rich` inline opent. Hexagonale architectuur: de antwoord-opslag is een directe JPA-laag, geen port nodig (het is puur CRUD, geen externe integratie).

**Tech Stack:** Java 21, Spring Boot 3.4, Spring Data JPA (`javax.persistence`), Liquibase, `@domg-wc/components` (`vl-textarea-rich`), vanilla web components.

---

### Task 1: Liquibase migratie — `kernbezwaar_antwoord` tabel

**Files:**
- Create: `app/src/main/resources/config/liquibase/changelog/20260301-kernbezwaar-antwoord.xml`
- Modify: `app/src/main/resources/config/liquibase/master.xml`

**Step 1: Maak het migratie-bestand**

```xml
<?xml version="1.0" encoding="utf-8"?>
<databaseChangeLog
    xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-3.1.xsd">

  <changeSet id="20260301-1" author="kenzo">
    <createTable tableName="kernbezwaar_antwoord">
      <column name="kernbezwaar_id" type="bigint">
        <constraints primaryKey="true" nullable="false"/>
      </column>
      <column name="inhoud" type="text">
        <constraints nullable="false"/>
      </column>
      <column name="bijgewerkt_op" type="timestamp with time zone">
        <constraints nullable="false"/>
      </column>
    </createTable>
  </changeSet>

</databaseChangeLog>
```

**Step 2: Voeg de changelog toe aan `master.xml`**

Voeg na de bestaande `<include>` regel toe:
```xml
  <include file="config/liquibase/changelog/20260301-kernbezwaar-antwoord.xml"/>
```

**Step 3: Commit**

```bash
git add app/src/main/resources/config/liquibase/changelog/20260301-kernbezwaar-antwoord.xml \
       app/src/main/resources/config/liquibase/master.xml
git commit -m "feat: liquibase migratie voor kernbezwaar_antwoord tabel"
```

---

### Task 2: JPA Entity — `KernbezwaarAntwoordEntiteit`

**Files:**
- Create: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/KernbezwaarAntwoordEntiteit.java`

**Step 1: Maak de JPA entity**

Volg het patroon van `ExtractieTaak.java`: `javax.persistence`, getter/setter, `@Column` met expliciete `name`.

```java
package be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar;

import java.time.Instant;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "kernbezwaar_antwoord")
public class KernbezwaarAntwoordEntiteit {

  @Id
  @Column(name = "kernbezwaar_id")
  private Long kernbezwaarId;

  @Column(name = "inhoud", columnDefinition = "text", nullable = false)
  private String inhoud;

  @Column(name = "bijgewerkt_op", nullable = false)
  private Instant bijgewerktOp;

  public Long getKernbezwaarId() {
    return kernbezwaarId;
  }

  public void setKernbezwaarId(Long kernbezwaarId) {
    this.kernbezwaarId = kernbezwaarId;
  }

  public String getInhoud() {
    return inhoud;
  }

  public void setInhoud(String inhoud) {
    this.inhoud = inhoud;
  }

  public Instant getBijgewerktOp() {
    return bijgewerktOp;
  }

  public void setBijgewerktOp(Instant bijgewerktOp) {
    this.bijgewerktOp = bijgewerktOp;
  }
}
```

**Step 2: Commit**

```bash
git add app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/KernbezwaarAntwoordEntiteit.java
git commit -m "feat: JPA entity KernbezwaarAntwoordEntiteit"
```

---

### Task 3: Repository — `KernbezwaarAntwoordRepository`

**Files:**
- Create: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/KernbezwaarAntwoordRepository.java`

**Step 1: Maak de Spring Data JPA repository**

```java
package be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KernbezwaarAntwoordRepository
    extends JpaRepository<KernbezwaarAntwoordEntiteit, Long> {

  List<KernbezwaarAntwoordEntiteit> findByKernbezwaarIdIn(List<Long> kernbezwaarIds);
}
```

De `findByKernbezwaarIdIn` methode haalt alle antwoorden op voor een lijst van kernbezwaar-IDs in een enkele query (voor het verrijken van de GET response).

**Step 2: Commit**

```bash
git add app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/KernbezwaarAntwoordRepository.java
git commit -m "feat: KernbezwaarAntwoordRepository"
```

---

### Task 4: Domeinmodel — `Kernbezwaar` record uitbreiden met `antwoord` veld

**Files:**
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/Kernbezwaar.java`
- Modify: `app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/ThemaTest.java`
- Modify: `app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/KernbezwaarControllerTest.java`
- Modify: `app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/KernbezwaarServiceTest.java`
- Modify: `app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/MockKernbezwaarAdapterTest.java`

**Step 1: Pas het `Kernbezwaar` record aan**

```java
package be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar;

import java.util.List;

public record Kernbezwaar(Long id, String samenvatting,
    List<IndividueelBezwaarReferentie> individueleBezwaren,
    String antwoord) {}
```

`antwoord` is nullable — `null` betekent "nog geen antwoord ingevoerd".

**Step 2: Fix alle compilatiefouten in tests en productie-code**

Overal waar `new Kernbezwaar(id, samenvatting, refs)` staat, moet een vierde argument `null` (of een waarde) worden toegevoegd. Bestanden die aangepast moeten worden:

- `MockKernbezwaarAdapter.java`: `new Kernbezwaar(id, samenvatting, refs, null)`
- `ThemaTest.java`: `new Kernbezwaar(1L, "...", List.of(ref1, ref2), null)`
- `KernbezwaarControllerTest.java`: `new Kernbezwaar(1L, "samenvatting", List.of(...), null)`
- `KernbezwaarServiceTest.java`: `new Kernbezwaar(1L, "samenvatting", List.of(...), null)`
- `MockKernbezwaarAdapterTest.java`: geen wijziging nodig (test maakt geen Kernbezwaar objecten zelf aan)

**Step 3: Draai alle tests**

Run: `mvn test -pl app -Dtest="be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar.*Test" -DfailIfNoTests=false`
Expected: Alle tests slagen

**Step 4: Commit**

```bash
git add app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/Kernbezwaar.java \
       app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/MockKernbezwaarAdapter.java \
       app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/
git commit -m "feat: voeg antwoord-veld toe aan Kernbezwaar record"
```

---

### Task 5: Service — `slaAntwoordOp` methode + verrijking van GET

**Files:**
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/KernbezwaarService.java`
- Modify: `app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/KernbezwaarServiceTest.java`

**Step 1: Schrijf de falende test voor `slaAntwoordOp`**

Voeg aan `KernbezwaarServiceTest.java` toe:

```java
@Mock
private KernbezwaarAntwoordRepository antwoordRepository;

// Pas setUp aan:
// service = new KernbezwaarService(kernbezwaarPoort, projectService, antwoordRepository);

@Test
void slaatAntwoordOp() {
  var entiteit = new KernbezwaarAntwoordEntiteit();
  when(antwoordRepository.save(any())).thenReturn(entiteit);

  service.slaAntwoordOp(42L, "<p>Het weerwoord</p>");

  var captor = ArgumentCaptor.forClass(KernbezwaarAntwoordEntiteit.class);
  verify(antwoordRepository).save(captor.capture());
  assertThat(captor.getValue().getKernbezwaarId()).isEqualTo(42L);
  assertThat(captor.getValue().getInhoud()).isEqualTo("<p>Het weerwoord</p>");
  assertThat(captor.getValue().getBijgewerktOp()).isNotNull();
}
```

Import toevoegen: `import static org.mockito.ArgumentMatchers.any;` en `import org.mockito.ArgumentCaptor;`

**Step 2: Draai test, controleer dat hij faalt**

Run: `mvn test -pl app -Dtest="KernbezwaarServiceTest#slaatAntwoordOp"`
Expected: FAIL — constructor accepteert geen derde argument

**Step 3: Implementeer `slaAntwoordOp` in `KernbezwaarService`**

Pas de constructor aan om `KernbezwaarAntwoordRepository` te injecteren. Voeg de methode toe:

```java
private final KernbezwaarAntwoordRepository antwoordRepository;

// Constructor:
public KernbezwaarService(KernbezwaarPoort kernbezwaarPoort,
    ProjectService projectService,
    KernbezwaarAntwoordRepository antwoordRepository) {
  this.kernbezwaarPoort = kernbezwaarPoort;
  this.projectService = projectService;
  this.antwoordRepository = antwoordRepository;
}

public void slaAntwoordOp(Long kernbezwaarId, String inhoud) {
  var entiteit = new KernbezwaarAntwoordEntiteit();
  entiteit.setKernbezwaarId(kernbezwaarId);
  entiteit.setInhoud(inhoud);
  entiteit.setBijgewerktOp(Instant.now());
  antwoordRepository.save(entiteit);
}
```

**Step 4: Schrijf de falende test voor verrijking van `geefKernbezwaren`**

```java
@Test
void verrijktKernbezwarenMetAntwoorden() {
  var kern = new Kernbezwaar(5L, "samenvatting", List.of(), null);
  var thema = new Thema("Geluid", List.of(kern));
  cache the themas first:
  when(projectService.geefBezwaartekstenVoorGroepering("windmolens"))
      .thenReturn(List.of(new KernbezwaarPoort.BezwaarInvoer(1L, "b.txt", "t")));
  when(kernbezwaarPoort.groepeer(anyList())).thenReturn(List.of(thema));
  service.groepeer("windmolens");

  var antwoord = new KernbezwaarAntwoordEntiteit();
  antwoord.setKernbezwaarId(5L);
  antwoord.setInhoud("<p>Weerwoord</p>");
  when(antwoordRepository.findByKernbezwaarIdIn(List.of(5L)))
      .thenReturn(List.of(antwoord));

  var resultaat = service.geefKernbezwaren("windmolens");

  assertThat(resultaat).isPresent();
  assertThat(resultaat.get().get(0).kernbezwaren().get(0).antwoord())
      .isEqualTo("<p>Weerwoord</p>");
}
```

**Step 5: Draai test, controleer dat hij faalt**

Run: `mvn test -pl app -Dtest="KernbezwaarServiceTest#verrijktKernbezwarenMetAntwoorden"`
Expected: FAIL — antwoord is null (niet verrijkt)

**Step 6: Implementeer verrijking in `geefKernbezwaren`**

```java
public Optional<List<Thema>> geefKernbezwaren(String projectNaam) {
  var themas = cache.get(projectNaam);
  if (themas == null) {
    return Optional.empty();
  }
  return Optional.of(verrijkMetAntwoorden(themas));
}

private List<Thema> verrijkMetAntwoorden(List<Thema> themas) {
  var alleIds = themas.stream()
      .flatMap(t -> t.kernbezwaren().stream())
      .map(Kernbezwaar::id)
      .toList();
  var antwoorden = antwoordRepository.findByKernbezwaarIdIn(alleIds);
  var antwoordMap = antwoorden.stream()
      .collect(java.util.stream.Collectors.toMap(
          KernbezwaarAntwoordEntiteit::getKernbezwaarId,
          KernbezwaarAntwoordEntiteit::getInhoud));
  return themas.stream()
      .map(thema -> new Thema(thema.naam(),
          thema.kernbezwaren().stream()
              .map(kern -> new Kernbezwaar(kern.id(), kern.samenvatting(),
                  kern.individueleBezwaren(),
                  antwoordMap.get(kern.id())))
              .toList()))
      .toList();
}
```

**Step 7: Draai alle kernbezwaar tests**

Run: `mvn test -pl app -Dtest="be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar.*Test" -DfailIfNoTests=false`
Expected: Alle tests slagen

**Step 8: Commit**

```bash
git add app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/KernbezwaarService.java \
       app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/KernbezwaarServiceTest.java
git commit -m "feat: slaAntwoordOp en verrijking van kernbezwaren met antwoorden"
```

---

### Task 6: Controller — PUT endpoint voor antwoord

**Files:**
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/KernbezwaarController.java`
- Modify: `app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/KernbezwaarControllerTest.java`

**Step 1: Schrijf de falende test voor PUT endpoint**

Voeg aan `KernbezwaarControllerTest.java` toe:

```java
@Test
void slaatAntwoordOp() {
  var request = new KernbezwaarController.AntwoordRequest("<p>Weerwoord</p>");

  var response = controller.slaAntwoordOp("windmolens", 42L, request);

  assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  verify(kernbezwaarService).slaAntwoordOp(42L, "<p>Weerwoord</p>");
}
```

**Step 2: Draai test, controleer dat hij faalt**

Run: `mvn test -pl app -Dtest="KernbezwaarControllerTest#slaatAntwoordOp"`
Expected: FAIL — `AntwoordRequest` en `slaAntwoordOp` bestaan nog niet

**Step 3: Implementeer het PUT endpoint**

Voeg aan `KernbezwaarController.java` toe:

```java
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Slaat het antwoord (weerwoord) op voor een specifiek kernbezwaar.
 */
@PutMapping("/{naam}/kernbezwaren/{id}/antwoord")
public ResponseEntity<Void> slaAntwoordOp(
    @PathVariable String naam,
    @PathVariable Long id,
    @RequestBody AntwoordRequest request) {
  kernbezwaarService.slaAntwoordOp(id, request.inhoud());
  return ResponseEntity.ok().build();
}

record AntwoordRequest(String inhoud) {}
```

**Step 4: Draai alle controller tests**

Run: `mvn test -pl app -Dtest="KernbezwaarControllerTest"`
Expected: Alle tests slagen

**Step 5: Commit**

```bash
git add app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/KernbezwaarController.java \
       app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/KernbezwaarControllerTest.java
git commit -m "feat: PUT endpoint voor antwoord op kernbezwaar"
```

---

### Task 7: Frontend — pennetje-knop en inline editor

**Files:**
- Modify: `webapp/src/js/bezwaarschriften-kernbezwaren.js`

**Step 1: Voeg de `vl-textarea-rich` import toe**

Bovenaan het bestand, bij de imports:

```javascript
import '@domg-wc/components/form/textarea-rich';
```

**Step 2: Voeg CSS toe voor de editor en preview**

Voeg de volgende CSS-regels toe in het `<style>` blok:

```css
.antwoord-preview {
  margin-top: 0.5rem;
  padding: 0.5rem 0.75rem;
  background: #f7f9fc;
  border-left: 3px solid #0055cc;
  font-size: 0.9rem;
  color: #333;
  max-height: 3.5em;
  overflow: hidden;
  cursor: pointer;
}
.antwoord-editor-wrapper {
  margin-top: 0.75rem;
  padding-top: 0.75rem;
  border-top: 1px solid #e8ebee;
}
.antwoord-opslaan-rij {
  margin-top: 0.5rem;
  display: flex;
  gap: 0.5rem;
  align-items: center;
}
.antwoord-opslaan-melding {
  font-size: 0.85rem;
  color: #0e7c26;
}
```

**Step 3: Voeg de pennetje-knop toe naast de vergrootglas-knop**

In de `_renderThemas` methode, na de vergrootglas-knop (`knop`), voeg een tweede knop toe:

```javascript
const penKnop = document.createElement('vl-button');
penKnop.setAttribute('tertiary', '');
penKnop.setAttribute('icon', kern.antwoord ? 'close' : 'pencil');
if (kern.antwoord) {
  penKnop.classList.add('heeft-antwoord');
}
penKnop.addEventListener('vl-click', () => this._toggleEditor(item, kern, penKnop));
actie.appendChild(penKnop);
```

Let op de volgorde: eerst vergrootglas-knop, dan pennetje-knop.

**Step 4: Voeg de preview toe als er een antwoord is**

Na het toevoegen van `samenvatting` aan `item`, maar voor `actie`:

```javascript
if (kern.antwoord) {
  const preview = document.createElement('div');
  preview.className = 'antwoord-preview';
  preview.innerHTML = kern.antwoord;
  preview.addEventListener('click', () => this._toggleEditor(item, kern, penKnop));
  samenvatting.appendChild(preview);
}
```

**Step 5: Implementeer `_toggleEditor` methode**

Voeg een nieuwe methode toe aan de class:

```javascript
_toggleEditor(item, kern, penKnop) {
  const bestaandeEditor = item.querySelector('.antwoord-editor-wrapper');
  if (bestaandeEditor) {
    // Sluit editor
    const textarea = bestaandeEditor.querySelector('vl-textarea-rich');
    const huidigeWaarde = textarea ? textarea.value : '';
    const origineleWaarde = kern.antwoord || '';
    if (huidigeWaarde !== origineleWaarde
        && !confirm('Je hebt onopgeslagen wijzigingen. Wil je afsluiten?')) {
      return;
    }
    bestaandeEditor.remove();
    penKnop.setAttribute('icon', kern.antwoord ? 'pencil' : 'pencil');
    if (kern.antwoord) penKnop.classList.add('heeft-antwoord');
    return;
  }

  // Open editor
  penKnop.setAttribute('icon', 'close');

  const wrapper = document.createElement('div');
  wrapper.className = 'antwoord-editor-wrapper';

  const textarea = document.createElement('vl-textarea-rich');
  textarea.setAttribute('block', '');
  textarea.setAttribute('rows', '8');
  textarea.setAttribute('label', 'Antwoord op kernbezwaar');
  if (kern.antwoord) {
    textarea.setAttribute('value', kern.antwoord);
  }

  const opslaanRij = document.createElement('div');
  opslaanRij.className = 'antwoord-opslaan-rij';

  const opslaanKnop = document.createElement('vl-button');
  opslaanKnop.textContent = 'Opslaan';
  opslaanKnop.addEventListener('vl-click', () =>
      this._slaAntwoordOp(kern, textarea, opslaanRij, penKnop, item));

  opslaanRij.appendChild(opslaanKnop);
  wrapper.appendChild(textarea);
  wrapper.appendChild(opslaanRij);
  item.appendChild(wrapper);
}
```

**Step 6: Implementeer `_slaAntwoordOp` methode**

```javascript
_slaAntwoordOp(kern, textarea, opslaanRij, penKnop, item) {
  const inhoud = textarea.value;
  if (!inhoud || !inhoud.trim()) return;

  const opslaanKnop = opslaanRij.querySelector('vl-button');
  if (opslaanKnop) opslaanKnop.setAttribute('disabled', '');

  fetch(`/api/v1/projects/${encodeURIComponent(this._projectNaam)}/kernbezwaren/${kern.id}/antwoord`, {
    method: 'PUT',
    headers: {'Content-Type': 'application/json'},
    body: JSON.stringify({inhoud: inhoud}),
  })
      .then((response) => {
        if (!response.ok) throw new Error('Opslaan mislukt');
        kern.antwoord = inhoud;

        // Toon bevestiging
        let melding = opslaanRij.querySelector('.antwoord-opslaan-melding');
        if (!melding) {
          melding = document.createElement('span');
          melding.className = 'antwoord-opslaan-melding';
          opslaanRij.appendChild(melding);
        }
        melding.textContent = 'Opgeslagen';
        setTimeout(() => melding.textContent = '', 3000);

        // Update preview
        this._updatePreview(item, kern);
      })
      .catch(() => {
        alert('Het opslaan van het antwoord is mislukt. Probeer opnieuw.');
      })
      .finally(() => {
        if (opslaanKnop) opslaanKnop.removeAttribute('disabled');
      });
}
```

**Step 7: Implementeer `_updatePreview` helper methode**

```javascript
_updatePreview(item, kern) {
  const samenvatting = item.querySelector('.kernbezwaar-samenvatting');
  if (!samenvatting) return;
  let preview = samenvatting.querySelector('.antwoord-preview');
  if (kern.antwoord) {
    if (!preview) {
      preview = document.createElement('div');
      preview.className = 'antwoord-preview';
      samenvatting.appendChild(preview);
    }
    preview.innerHTML = kern.antwoord;
  } else if (preview) {
    preview.remove();
  }
}
```

**Step 8: Bouw de frontend en kopieer naar Spring Boot**

Run: `cd webapp && npm run build && cd .. && mvn process-resources -pl webapp -Denforcer.skip=true`
Expected: Build slaagt

**Step 9: Commit**

```bash
git add webapp/src/js/bezwaarschriften-kernbezwaren.js
git commit -m "feat: pennetje-knop, inline rich text editor, en preview voor kernbezwaar-antwoord"
```

---

### Task 8: Integratietest — PUT endpoint met database

**Files:**
- Create: `app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/KernbezwaarAntwoordIntegratieTest.java`

**Step 1: Schrijf een integratietest**

Kijk naar bestaande integratietests in het project voor het juiste patroon (Testcontainers, `@SpringBootTest`, etc.). Maak een test die:

1. Een POST doet naar de PUT endpoint met een antwoord
2. Controleert dat het antwoord in de database terecht is gekomen
3. De GET endpoint aanroept en controleert dat het antwoord mee terug komt

Zoek eerst naar een bestaand `@SpringBootTest` voorbeeld in het project om het patroon te volgen. Als er geen is, maak een simpele `@DataJpaTest` voor de repository:

```java
package be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
class KernbezwaarAntwoordRepositoryTest {

  @Autowired
  private KernbezwaarAntwoordRepository repository;

  @Test
  void slaatAntwoordOpEnHaaltHetOp() {
    var entiteit = new KernbezwaarAntwoordEntiteit();
    entiteit.setKernbezwaarId(1L);
    entiteit.setInhoud("<p>Het weerwoord</p>");
    entiteit.setBijgewerktOp(Instant.now());

    repository.save(entiteit);

    var opgehaald = repository.findById(1L);
    assertThat(opgehaald).isPresent();
    assertThat(opgehaald.get().getInhoud()).isEqualTo("<p>Het weerwoord</p>");
  }

  @Test
  void vindAntwoordenVoorMeerdereKernbezwaarIds() {
    var e1 = new KernbezwaarAntwoordEntiteit();
    e1.setKernbezwaarId(10L);
    e1.setInhoud("<p>Antwoord 1</p>");
    e1.setBijgewerktOp(Instant.now());

    var e2 = new KernbezwaarAntwoordEntiteit();
    e2.setKernbezwaarId(20L);
    e2.setInhoud("<p>Antwoord 2</p>");
    e2.setBijgewerktOp(Instant.now());

    repository.saveAll(List.of(e1, e2));

    var resultaat = repository.findByKernbezwaarIdIn(List.of(10L, 20L, 30L));
    assertThat(resultaat).hasSize(2);
  }

  @Test
  void upsertBijBestaandAntwoord() {
    var entiteit = new KernbezwaarAntwoordEntiteit();
    entiteit.setKernbezwaarId(1L);
    entiteit.setInhoud("<p>Oud</p>");
    entiteit.setBijgewerktOp(Instant.now());
    repository.save(entiteit);

    // Overschrijven met nieuw antwoord
    var bijgewerkt = new KernbezwaarAntwoordEntiteit();
    bijgewerkt.setKernbezwaarId(1L);
    bijgewerkt.setInhoud("<p>Nieuw</p>");
    bijgewerkt.setBijgewerktOp(Instant.now());
    repository.save(bijgewerkt);

    var opgehaald = repository.findById(1L);
    assertThat(opgehaald.get().getInhoud()).isEqualTo("<p>Nieuw</p>");
  }
}
```

Let op: dit vereist een `application-test.yml` met H2 of Testcontainers config. Controleer of die al bestaat.

**Step 2: Draai de test**

Run: `mvn test -pl app -Dtest="KernbezwaarAntwoordRepositoryTest" -DfailIfNoTests=false`
Expected: Tests slagen

**Step 3: Commit**

```bash
git add app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/KernbezwaarAntwoordRepositoryTest.java
git commit -m "test: integratietest voor KernbezwaarAntwoordRepository"
```

---

### Task 9: Handmatige end-to-end test

**Files:** geen nieuwe bestanden

**Step 1: Start de applicatie**

Run: `mvn spring-boot:run -pl app -Dspring-boot.run.profiles=dev`

**Step 2: Test de volledige flow**

1. Open de browser, navigeer naar het project
2. Ga naar de Kernbezwaren-tab
3. Groepeer bezwaren
4. Controleer dat elk kernbezwaar nu een pennetje-knop heeft naast het vergrootglas
5. Klik op een pennetje — de rich text editor moet inline verschijnen
6. Typ een antwoord, klik "Opslaan"
7. De melding "Opgeslagen" moet verschijnen
8. Klik op het X-icoon om de editor te sluiten
9. Controleer dat er een preview van het antwoord onder de samenvatting staat
10. Herlaad de pagina — het antwoord moet bewaard zijn gebleven (database)

**Step 3: Test onopgeslagen wijzigingen**

1. Open een editor, wijzig tekst
2. Klik op het X-icoon
3. Controleer dat de confirm-dialoog verschijnt

---

### Task 10: Alle tests draaien + final commit

**Step 1: Draai alle tests**

Run: `mvn test -pl app`
Expected: Alle tests slagen

**Step 2: Bouw frontend**

Run: `cd webapp && npm run build && cd .. && mvn process-resources -pl webapp -Denforcer.skip=true`

**Step 3: Final commit als er nog ongecommitte wijzigingen zijn**

```bash
git status
# Als er nog wijzigingen zijn:
git add -A && git commit -m "chore: build artifacts en fixes"
```
