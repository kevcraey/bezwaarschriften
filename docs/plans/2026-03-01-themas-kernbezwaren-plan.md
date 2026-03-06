# Thema's & Kernbezwaren — Implementatieplan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Thema-indeling en kernbezwaar-groepering met gemockte backend + volledige UI in het Vlaams Design System.

**Architecture:** Hexagonale architectuur met `KernbezwaarPoort` (port interface), `MockKernbezwaarAdapter` (hardcoded mock data), `KernbezwaarService` (orchestratie + in-memory cache), `KernbezwaarController` (REST). Frontend: nieuw `bezwaarschriften-kernbezwaren` web component met `vl-accordion` en `vl-side-sheet`.

**Tech Stack:** Java 21, Spring Boot, @domg-wc 2.7.0, Lit-based web components, JUnit 5 + Mockito + AssertJ.

**Package:** `be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar`

---

## Task 1: Domeinmodel — records

**Files:**
- Create: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/IndividueelBezwaarReferentie.java`
- Create: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/Kernbezwaar.java`
- Create: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/Thema.java`
- Test: `app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/ThemaTest.java`

**Step 1: Write the failing test**

```java
package be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ThemaTest {

  @Test
  void themaBevatKernbezwarenMetReferenties() {
    var ref1 = new IndividueelBezwaarReferentie(1L, "bezwaar1.txt",
        "De geluidsoverlast is ondraaglijk.");
    var ref2 = new IndividueelBezwaarReferentie(2L, "bezwaar3.txt",
        "Wij ondervinden ernstige hinder van nachtelijk transport.");
    var kern = new Kernbezwaar(1L,
        "Geluidshinder tijdens nachtelijke uren door vrachtverkeer",
        List.of(ref1, ref2));
    var thema = new Thema("Geluid", List.of(kern));

    assertThat(thema.naam()).isEqualTo("Geluid");
    assertThat(thema.kernbezwaren()).hasSize(1);
    assertThat(thema.kernbezwaren().get(0).samenvatting())
        .isEqualTo("Geluidshinder tijdens nachtelijke uren door vrachtverkeer");
    assertThat(thema.kernbezwaren().get(0).individueleBezwaren()).hasSize(2);
    assertThat(thema.kernbezwaren().get(0).individueleBezwaren().get(0).passage())
        .isEqualTo("De geluidsoverlast is ondraaglijk.");
  }
}
```

**Step 2: Run test to verify it fails**

Run: `mvn test -pl app -Dtest=ThemaTest -Denforcer.skip=true -q`
Expected: FAIL — classes do not exist yet.

**Step 3: Write minimal implementation**

`IndividueelBezwaarReferentie.java`:
```java
package be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar;

public record IndividueelBezwaarReferentie(Long bezwaarId, String bestandsnaam, String passage) {}
```

`Kernbezwaar.java`:
```java
package be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar;

import java.util.List;

public record Kernbezwaar(Long id, String samenvatting,
    List<IndividueelBezwaarReferentie> individueleBezwaren) {}
```

`Thema.java`:
```java
package be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar;

import java.util.List;

public record Thema(String naam, List<Kernbezwaar> kernbezwaren) {}
```

**Step 4: Run test to verify it passes**

Run: `mvn test -pl app -Dtest=ThemaTest -Denforcer.skip=true -q`
Expected: PASS

**Step 5: Commit**

```bash
git add app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/ \
       app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/
git commit -m "feat: domeinmodel voor thema's en kernbezwaren"
```

---

## Task 2: Port interface — KernbezwaarPoort

**Files:**
- Create: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/KernbezwaarPoort.java`

**Step 1: Write the port interface**

```java
package be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar;

import java.util.List;

/**
 * Port voor het groeperen van individuele bezwaren tot thema's en kernbezwaren.
 */
public interface KernbezwaarPoort {

  /**
   * Groepeert individuele bezwaarteksten tot thema's met kernbezwaren.
   *
   * @param bezwaarTeksten Lijst van individuele bezwaarteksten met hun metadata
   * @return Lijst van thema's met kernbezwaren
   */
  List<Thema> groepeer(List<BezwaarInvoer> bezwaarTeksten);

  /**
   * Invoer voor de groepering: een individueel bezwaar met bron-metadata.
   */
  record BezwaarInvoer(Long id, String bestandsnaam, String tekst) {}
}
```

> Opmerking: we gebruiken `BezwaarInvoer` i.p.v. direct `IndividueelBezwaar` (JPA entity) om de port onafhankelijk te houden van de persistence-laag.

**Step 2: Commit**

```bash
git add app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/KernbezwaarPoort.java
git commit -m "feat: KernbezwaarPoort interface"
```

---

## Task 3: Mock adapter — MockKernbezwaarAdapter

**Files:**
- Create: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/MockKernbezwaarAdapter.java`
- Test: `app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/MockKernbezwaarAdapterTest.java`

**Step 1: Write the failing test**

```java
package be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class MockKernbezwaarAdapterTest {

  private final MockKernbezwaarAdapter adapter = new MockKernbezwaarAdapter();

  @Test
  void groepeertBezwarenInThemas() {
    var invoer = List.of(
        new KernbezwaarPoort.BezwaarInvoer(1L, "bezwaar1.txt", "tekst 1"),
        new KernbezwaarPoort.BezwaarInvoer(2L, "bezwaar2.txt", "tekst 2"),
        new KernbezwaarPoort.BezwaarInvoer(3L, "bezwaar3.txt", "tekst 3"));

    var themas = adapter.groepeer(invoer);

    assertThat(themas).isNotEmpty();
    assertThat(themas).allSatisfy(thema -> {
      assertThat(thema.naam()).isNotBlank();
      assertThat(thema.kernbezwaren()).isNotEmpty();
      assertThat(thema.kernbezwaren()).allSatisfy(kern -> {
        assertThat(kern.samenvatting()).isNotBlank();
        assertThat(kern.individueleBezwaren()).isNotEmpty();
        assertThat(kern.individueleBezwaren()).allSatisfy(ref -> {
          assertThat(ref.bestandsnaam()).isNotBlank();
          assertThat(ref.passage()).isNotBlank();
        });
      });
    });
  }

  @Test
  void retourneertDrieThemas() {
    var invoer = List.of(
        new KernbezwaarPoort.BezwaarInvoer(1L, "b1.txt", "t1"));

    var themas = adapter.groepeer(invoer);

    var themaNamen = themas.stream().map(Thema::naam).toList();
    assertThat(themaNamen).containsExactly("Geluid", "Mobiliteit", "Geurhinder");
  }

  @Test
  void retourneertLeegBijGeenInvoer() {
    var themas = adapter.groepeer(List.of());

    assertThat(themas).isEmpty();
  }
}
```

**Step 2: Run test to verify it fails**

Run: `mvn test -pl app -Dtest=MockKernbezwaarAdapterTest -Denforcer.skip=true -q`
Expected: FAIL — class does not exist.

**Step 3: Write minimal implementation**

```java
package be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar;

import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Mock-implementatie van {@link KernbezwaarPoort} die hardcoded thema's retourneert.
 */
@Component
public class MockKernbezwaarAdapter implements KernbezwaarPoort {

  @Override
  public List<Thema> groepeer(List<BezwaarInvoer> bezwaarTeksten) {
    if (bezwaarTeksten.isEmpty()) {
      return List.of();
    }

    return List.of(
        new Thema("Geluid", List.of(
            new Kernbezwaar(1L,
                "Geluidshinder tijdens nachtelijke uren door vrachtverkeer",
                List.of(
                    new IndividueelBezwaarReferentie(1L, "bezwaar1.txt",
                        "De geluidsoverlast is ondraaglijk, vooral 's nachts wanneer het vrachtverkeer op volle toeren draait."),
                    new IndividueelBezwaarReferentie(2L, "bezwaar3.txt",
                        "Wij ondervinden ernstige hinder van nachtelijk transport langs onze woning."))),
            new Kernbezwaar(2L,
                "Trillingen en laagfrequent geluid door zware machines",
                List.of(
                    new IndividueelBezwaarReferentie(3L, "bezwaar2.txt",
                        "De trillingen van de machines zijn voelbaar tot in onze woonkamer."),
                    new IndividueelBezwaarReferentie(4L, "bezwaar4.txt",
                        "Het laagfrequent bromgeluid is dag en nacht aanwezig."))))),
        new Thema("Mobiliteit", List.of(
            new Kernbezwaar(3L,
                "Verkeerscongestie op de N-weg door projectgerelateerd verkeer",
                List.of(
                    new IndividueelBezwaarReferentie(5L, "bezwaar1.txt",
                        "De N-weg staat elke ochtend vast door de vrachtwagens van het project."),
                    new IndividueelBezwaarReferentie(6L, "bezwaar5.txt",
                        "Het verkeer is sinds de start van het project onhoudbaar geworden."))),
            new Kernbezwaar(4L,
                "Onveilige verkeerssituaties voor fietsers en voetgangers",
                List.of(
                    new IndividueelBezwaarReferentie(7L, "bezwaar2.txt",
                        "Mijn kinderen kunnen niet meer veilig naar school fietsen."))))),
        new Thema("Geurhinder", List.of(
            new Kernbezwaar(5L,
                "Aanhoudende geuroverlast vanuit de fabriek bij bepaalde windrichtingen",
                List.of(
                    new IndividueelBezwaarReferentie(8L, "bezwaar3.txt",
                        "Bij zuidwestenwind is de stank ondraaglijk en moeten we ramen en deuren sluiten."),
                    new IndividueelBezwaarReferentie(9L, "bezwaar4.txt",
                        "De geurhinder maakt het onmogelijk om buiten te zitten in de tuin."),
                    new IndividueelBezwaarReferentie(10L, "bezwaar5.txt",
                        "Wij klagen al jaren over de geuroverlast maar er wordt niets aan gedaan."))))));
  }
}
```

**Step 4: Run test to verify it passes**

Run: `mvn test -pl app -Dtest=MockKernbezwaarAdapterTest -Denforcer.skip=true -q`
Expected: PASS

**Step 5: Commit**

```bash
git add app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/MockKernbezwaarAdapter.java \
       app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/MockKernbezwaarAdapterTest.java
git commit -m "feat: MockKernbezwaarAdapter met hardcoded thema's"
```

---

## Task 4: Service — KernbezwaarService

**Files:**
- Create: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/KernbezwaarService.java`
- Test: `app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/KernbezwaarServiceTest.java`

De service houdt een in-memory cache bij (`Map<String, List<Thema>>`) zodat het GET-endpoint eerder berekende kernbezwaren kan teruggeven.

**Step 1: Write the failing test**

```java
package be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KernbezwaarServiceTest {

  @Mock
  private KernbezwaarPoort kernbezwaarPoort;

  @Mock
  private ProjectService projectService;

  private KernbezwaarService service;

  @BeforeEach
  void setUp() {
    service = new KernbezwaarService(kernbezwaarPoort, projectService);
  }

  @Test
  void groepeertBezwarenVanProject() {
    when(projectService.geefBezwaartekstenVoorGroepering("windmolens"))
        .thenReturn(List.of(
            new KernbezwaarPoort.BezwaarInvoer(1L, "bezwaar1.txt", "tekst")));
    var thema = new Thema("Geluid", List.of(
        new Kernbezwaar(1L, "samenvatting", List.of(
            new IndividueelBezwaarReferentie(1L, "bezwaar1.txt", "passage")))));
    when(kernbezwaarPoort.groepeer(anyList())).thenReturn(List.of(thema));

    var resultaat = service.groepeer("windmolens");

    assertThat(resultaat).hasSize(1);
    assertThat(resultaat.get(0).naam()).isEqualTo("Geluid");
    verify(projectService).geefBezwaartekstenVoorGroepering("windmolens");
    verify(kernbezwaarPoort).groepeer(anyList());
  }

  @Test
  void cachetResultaatNaGroepering() {
    when(projectService.geefBezwaartekstenVoorGroepering("windmolens"))
        .thenReturn(List.of(
            new KernbezwaarPoort.BezwaarInvoer(1L, "b1.txt", "t")));
    var thema = new Thema("Mobiliteit", List.of());
    when(kernbezwaarPoort.groepeer(anyList())).thenReturn(List.of(thema));

    service.groepeer("windmolens");

    var gecached = service.geefKernbezwaren("windmolens");
    assertThat(gecached).isPresent();
    assertThat(gecached.get()).hasSize(1);
    assertThat(gecached.get().get(0).naam()).isEqualTo("Mobiliteit");
  }

  @Test
  void geeftLeegOptionalAlsNogNietGegroepeerd() {
    var resultaat = service.geefKernbezwaren("onbekend");

    assertThat(resultaat).isEmpty();
  }
}
```

> Let op: `projectService.geefBezwaartekstenVoorGroepering()` bestaat nog niet. Die voegen we toe in Task 5.

**Step 2: Run test to verify it fails**

Run: `mvn test -pl app -Dtest=KernbezwaarServiceTest -Denforcer.skip=true -q`
Expected: FAIL — `KernbezwaarService` en `geefBezwaartekstenVoorGroepering` bestaan niet.

**Step 3: Write minimal implementation**

```java
package be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar;

import be.vlaanderen.omgeving.bezwaarschriften.project.ProjectService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * Orchestreert de groepering van individuele bezwaren tot thema's en kernbezwaren.
 */
@Service
public class KernbezwaarService {

  private final KernbezwaarPoort kernbezwaarPoort;
  private final ProjectService projectService;
  private final Map<String, List<Thema>> cache = new ConcurrentHashMap<>();

  public KernbezwaarService(KernbezwaarPoort kernbezwaarPoort, ProjectService projectService) {
    this.kernbezwaarPoort = kernbezwaarPoort;
    this.projectService = projectService;
  }

  /**
   * Groepeert de individuele bezwaren van een project tot thema's en kernbezwaren.
   *
   * @param projectNaam Naam van het project
   * @return Lijst van thema's met kernbezwaren
   */
  public List<Thema> groepeer(String projectNaam) {
    var invoer = projectService.geefBezwaartekstenVoorGroepering(projectNaam);
    var themas = kernbezwaarPoort.groepeer(invoer);
    cache.put(projectNaam, themas);
    return themas;
  }

  /**
   * Geeft eerder berekende kernbezwaren voor een project.
   *
   * @param projectNaam Naam van het project
   * @return Thema's met kernbezwaren, of empty als nog niet gegroepeerd
   */
  public Optional<List<Thema>> geefKernbezwaren(String projectNaam) {
    return Optional.ofNullable(cache.get(projectNaam));
  }
}
```

**Step 4: Run test — nog steeds FAIL** (want `geefBezwaartekstenVoorGroepering` bestaat niet op `ProjectService`)

→ Ga door naar Task 5 om dit op te lossen.

---

## Task 5: ProjectService uitbreiden — geefBezwaartekstenVoorGroepering

**Files:**
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ProjectService.java`
- Modify: `app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/project/ProjectServiceTest.java`

Deze methode levert de bezwaarteksten aan als `BezwaarInvoer`-objecten voor de groepering. In de mock-fase gebruiken we de `tekst` uit de bestandsinhoud (via `IngestiePoort`), met bestandsnaam als referentie.

> De mock-adapter negeert de invoer, maar de service-laag moet wel correct wired zijn zodat de echte adapter later transparant kan overnemen.

**Step 1: Write the failing test**

Voeg toe aan `ProjectServiceTest.java`:

```java
@Mock
private IngestiePoort ingestiePoort;

// Wijzig setUp:
// service = new ProjectService(projectPoort, extractieTaakRepository, ingestiePoort);

@Test
void geeftBezwaartekstenVoorGroepering() {
  when(projectPoort.geefBestandsnamen("windmolens"))
      .thenReturn(List.of("bezwaar1.txt", "bijlage.pdf"));
  when(projectPoort.geefBestandsPad("windmolens", "bezwaar1.txt"))
      .thenReturn(Path.of("/tmp/windmolens/bezwaren/bezwaar1.txt"));
  when(ingestiePoort.leesBestand(Path.of("/tmp/windmolens/bezwaren/bezwaar1.txt")))
      .thenReturn(new Brondocument("bezwaar1.txt", "De geluidsoverlast is ondraaglijk."));

  var resultaat = service.geefBezwaartekstenVoorGroepering("windmolens");

  assertThat(resultaat).hasSize(1);
  assertThat(resultaat.get(0).bestandsnaam()).isEqualTo("bezwaar1.txt");
  assertThat(resultaat.get(0).tekst()).isEqualTo("De geluidsoverlast is ondraaglijk.");
}
```

> Import `IngestiePoort` en `Brondocument` van het juiste package. Controleer in de codebase welk record `IngestiePoort.leesBestand()` retourneert (waarschijnlijk `Brondocument`) en pas de test aan indien nodig.

**Step 2: Run test to verify it fails**

Run: `mvn test -pl app -Dtest=ProjectServiceTest#geeftBezwaartekstenVoorGroepering -Denforcer.skip=true -q`
Expected: FAIL — method bestaat niet.

**Step 3: Write minimal implementation**

Voeg toe aan `ProjectService.java`:

```java
// Voeg IngestiePoort toe als constructor-parameter
private final IngestiePoort ingestiePoort;

// Pas constructor aan:
public ProjectService(ProjectPoort projectPoort,
    ExtractieTaakRepository extractieTaakRepository,
    IngestiePoort ingestiePoort) {
  this.projectPoort = projectPoort;
  this.extractieTaakRepository = extractieTaakRepository;
  this.ingestiePoort = ingestiePoort;
}

/**
 * Geeft de bezwaarteksten van een project als invoer voor kernbezwaar-groepering.
 * Filtert op .txt-bestanden met status EXTRACTIE_KLAAR.
 */
public List<KernbezwaarPoort.BezwaarInvoer> geefBezwaartekstenVoorGroepering(String projectNaam) {
  var bezwaren = geefBezwaren(projectNaam);
  return bezwaren.stream()
      .filter(b -> b.status() == BezwaarBestandStatus.EXTRACTIE_KLAAR
          || b.status() == BezwaarBestandStatus.TODO)
      .filter(b -> isTxtBestand(b.bestandsnaam()))
      .map(b -> {
        var pad = projectPoort.geefBestandsPad(projectNaam, b.bestandsnaam());
        var doc = ingestiePoort.leesBestand(pad);
        return new KernbezwaarPoort.BezwaarInvoer(null, b.bestandsnaam(), doc.tekst());
      })
      .toList();
}
```

**Step 4: Run alle ProjectServiceTest tests om regressie te checken**

Run: `mvn test -pl app -Dtest=ProjectServiceTest -Denforcer.skip=true -q`
Expected: PASS (bestaande tests moeten setUp aanpassen met extra mock)

**Step 5: Run ook KernbezwaarServiceTest**

Run: `mvn test -pl app -Dtest=KernbezwaarServiceTest -Denforcer.skip=true -q`
Expected: PASS

**Step 6: Commit**

```bash
git add app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ProjectService.java \
       app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/project/ProjectServiceTest.java \
       app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/KernbezwaarService.java \
       app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/KernbezwaarServiceTest.java
git commit -m "feat: KernbezwaarService + ProjectService.geefBezwaartekstenVoorGroepering"
```

---

## Task 6: REST controller — KernbezwaarController

**Files:**
- Create: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/KernbezwaarController.java`
- Test: `app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/KernbezwaarControllerTest.java`

**Step 1: Write the failing test**

```java
package be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class KernbezwaarControllerTest {

  @Mock
  private KernbezwaarService kernbezwaarService;

  private KernbezwaarController controller;

  @BeforeEach
  void setUp() {
    controller = new KernbezwaarController(kernbezwaarService);
  }

  @Test
  void groepeerRetourneertThemas() {
    var thema = new Thema("Geluid", List.of(
        new Kernbezwaar(1L, "samenvatting", List.of(
            new IndividueelBezwaarReferentie(1L, "b1.txt", "passage")))));
    when(kernbezwaarService.groepeer("windmolens")).thenReturn(List.of(thema));

    var response = controller.groepeer("windmolens");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().themas()).hasSize(1);
    assertThat(response.getBody().themas().get(0).naam()).isEqualTo("Geluid");
    verify(kernbezwaarService).groepeer("windmolens");
  }

  @Test
  void geefKernbezwarenRetourneertCachedResultaat() {
    var thema = new Thema("Mobiliteit", List.of());
    when(kernbezwaarService.geefKernbezwaren("windmolens"))
        .thenReturn(Optional.of(List.of(thema)));

    var response = controller.geefKernbezwaren("windmolens");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().themas()).hasSize(1);
  }

  @Test
  void geefKernbezwarenRetourneert404AlsNogNietGegroepeerd() {
    when(kernbezwaarService.geefKernbezwaren("windmolens"))
        .thenReturn(Optional.empty());

    var response = controller.geefKernbezwaren("windmolens");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }
}
```

**Step 2: Run test to verify it fails**

Run: `mvn test -pl app -Dtest=KernbezwaarControllerTest -Denforcer.skip=true -q`
Expected: FAIL

**Step 3: Write minimal implementation**

```java
package be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller voor kernbezwaar-groepering.
 */
@RestController
@RequestMapping("/api/v1/projects")
public class KernbezwaarController {

  private final KernbezwaarService kernbezwaarService;

  public KernbezwaarController(KernbezwaarService kernbezwaarService) {
    this.kernbezwaarService = kernbezwaarService;
  }

  /**
   * Triggert de groepering van individuele bezwaren tot thema's en kernbezwaren.
   */
  @PostMapping("/{naam}/kernbezwaren/groepeer")
  public ResponseEntity<ThemasResponse> groepeer(@PathVariable String naam) {
    var themas = kernbezwaarService.groepeer(naam);
    return ResponseEntity.ok(new ThemasResponse(themas));
  }

  /**
   * Geeft eerder berekende kernbezwaren voor een project.
   */
  @GetMapping("/{naam}/kernbezwaren")
  public ResponseEntity<ThemasResponse> geefKernbezwaren(@PathVariable String naam) {
    return kernbezwaarService.geefKernbezwaren(naam)
        .map(themas -> ResponseEntity.ok(new ThemasResponse(themas)))
        .orElse(ResponseEntity.notFound().build());
  }

  /** Response DTO met thema's en kernbezwaren. */
  record ThemasResponse(List<Thema> themas) {}
}
```

**Step 4: Run test to verify it passes**

Run: `mvn test -pl app -Dtest=KernbezwaarControllerTest -Denforcer.skip=true -q`
Expected: PASS

**Step 5: Commit**

```bash
git add app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/KernbezwaarController.java \
       app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/KernbezwaarControllerTest.java
git commit -m "feat: KernbezwaarController met groepeer en geef endpoints"
```

---

## Task 7: Frontend — bezwaarschriften-kernbezwaren component

**Files:**
- Create: `webapp/src/js/bezwaarschriften-kernbezwaren.js`

Dit component beheert de kernbezwaren-tab met drie staten en bevat de accordeon-layout + side-sheet.

**Step 1: Maak het component aan**

```javascript
import {BaseHTMLElement, defineWebComponent, registerWebComponents} from '@domg-wc/common';
import {VlButtonComponent} from '@domg-wc/components/atom/button/vl-button.component.js';
import {VlAccordionComponent} from '@domg-wc/components/block/accordion/vl-accordion.component.js';
import {VlSideSheetComponent} from '@domg-wc/components/block/side-sheet/vl-side-sheet.component.js';
import {VlIconComponent} from '@domg-wc/components/atom/icon/vl-icon.component.js';
import {vlGlobalStyles, vlGridStyles} from '@domg-wc/styles';

registerWebComponents([VlButtonComponent, VlAccordionComponent, VlSideSheetComponent, VlIconComponent]);

export class BezwaarschriftenKernbezwaren extends BaseHTMLElement {
  static get properties() {
    return {
      projectNaam: {type: String},
      aantalBezwaren: {type: Number},
      extractieKlaar: {type: Boolean},
    };
  }

  constructor() {
    super(`
      <style>
        ${vlGlobalStyles}
        ${vlGridStyles}
        :host { display: block; }
        .kernbezwaar-item {
          display: flex;
          justify-content: space-between;
          align-items: flex-start;
          padding: 1rem 0;
          border-bottom: 1px solid #e8ebee;
        }
        .kernbezwaar-item:last-child { border-bottom: none; }
        .kernbezwaar-samenvatting { flex: 1; margin-right: 1rem; }
        .kernbezwaar-actie { white-space: nowrap; }
        .side-sheet-kern-titel {
          font-weight: bold;
          margin-bottom: 1.5rem;
          padding-bottom: 1rem;
          border-bottom: 2px solid #e8ebee;
        }
        .passage-item {
          margin-bottom: 1.5rem;
          padding-bottom: 1rem;
          border-bottom: 1px solid #e8ebee;
        }
        .passage-item:last-child { border-bottom: none; }
        .passage-bestandsnaam {
          font-size: 0.85rem;
          color: #687483;
          margin-bottom: 0.25rem;
        }
        .passage-tekst {
          font-style: italic;
          line-height: 1.5;
        }
        .lege-staat {
          padding: 2rem;
          text-align: center;
          color: #687483;
        }
      </style>
      <div id="inhoud"></div>
      <vl-side-sheet id="side-sheet">
        <div id="side-sheet-inhoud"></div>
      </vl-side-sheet>
    `);
    this.projectNaam = null;
    this.aantalBezwaren = 0;
    this.extractieKlaar = false;
    this._themas = null;
    this._bezig = false;
  }

  static get observedAttributes() {
    return ['project-naam', 'aantal-bezwaren', 'extractie-klaar'];
  }

  attributeChangedCallback(naam, oud, nieuw) {
    if (naam === 'project-naam') this.projectNaam = nieuw;
    if (naam === 'aantal-bezwaren') this.aantalBezwaren = parseInt(nieuw) || 0;
    if (naam === 'extractie-klaar') this.extractieKlaar = nieuw !== null;
    this._renderInhoud();
  }

  _renderInhoud() {
    const inhoud = this.shadowRoot.querySelector('#inhoud');
    if (!inhoud) return;

    if (this._themas) {
      this._renderThemas(inhoud);
      return;
    }

    if (!this.extractieKlaar || this.aantalBezwaren === 0) {
      inhoud.innerHTML = `
        <div class="lege-staat">
          <p>Nog geen bezwaren geëxtraheerd. Verwerk eerst documenten op de Documenten-tab.</p>
        </div>`;
      return;
    }

    inhoud.innerHTML = `
      <div class="lege-staat">
        <p>${this.aantalBezwaren} individuele bezwaren gevonden.
           Voer groepering tot thema's en kernbezwaren uit om verder te gaan.</p>
        <vl-button id="groepeer-knop">Groepeer bezwaren</vl-button>
      </div>`;

    const knop = inhoud.querySelector('#groepeer-knop');
    if (knop) {
      knop.addEventListener('vl-click', () => this._groepeer());
    }
  }

  _groepeer() {
    if (this._bezig || !this.projectNaam) return;
    this._bezig = true;

    const knop = this.shadowRoot.querySelector('#groepeer-knop');
    if (knop) {
      knop.setAttribute('disabled', '');
      knop.textContent = 'Bezig met groeperen...';
    }

    fetch(`/api/v1/projects/${encodeURIComponent(this.projectNaam)}/kernbezwaren/groepeer`, {
      method: 'POST',
    })
        .then((response) => {
          if (!response.ok) throw new Error('Groepering mislukt');
          return response.json();
        })
        .then((data) => {
          this._themas = data.themas;
          this._renderInhoud();
        })
        .catch(() => {
          if (knop) {
            knop.removeAttribute('disabled');
            knop.textContent = 'Groepeer bezwaren';
          }
        })
        .finally(() => {
          this._bezig = false;
        });
  }

  _renderThemas(inhoud) {
    inhoud.innerHTML = '';
    this._themas.forEach((thema) => {
      const accordion = document.createElement('vl-accordion');
      const aantalKern = thema.kernbezwaren.length;
      const label = aantalKern === 1 ? '1 kernbezwaar' : `${aantalKern} kernbezwaren`;
      accordion.setAttribute('toggle-text', `${thema.naam} (${label})`);

      const wrapper = document.createElement('div');
      thema.kernbezwaren.forEach((kern) => {
        const item = document.createElement('div');
        item.className = 'kernbezwaar-item';

        const samenvatting = document.createElement('div');
        samenvatting.className = 'kernbezwaar-samenvatting';
        samenvatting.textContent = kern.samenvatting;

        const actie = document.createElement('div');
        actie.className = 'kernbezwaar-actie';
        const knop = document.createElement('vl-button');
        knop.setAttribute('tertiary', '');
        knop.textContent = `(${kern.individueleBezwaren.length})`;
        knop.addEventListener('vl-click', () => this._toonPassages(kern));

        actie.appendChild(knop);
        item.appendChild(samenvatting);
        item.appendChild(actie);
        wrapper.appendChild(item);
      });

      accordion.appendChild(wrapper);
      inhoud.appendChild(accordion);
    });
  }

  _toonPassages(kernbezwaar) {
    const sideSheet = this.shadowRoot.querySelector('#side-sheet');
    const inhoud = this.shadowRoot.querySelector('#side-sheet-inhoud');
    if (!sideSheet || !inhoud) return;

    inhoud.innerHTML = '';

    const titel = document.createElement('div');
    titel.className = 'side-sheet-kern-titel';
    titel.textContent = kernbezwaar.samenvatting;
    inhoud.appendChild(titel);

    const aantalLabel = document.createElement('p');
    const n = kernbezwaar.individueleBezwaren.length;
    aantalLabel.textContent = `${n} individuele bezwar${n === 1 ? '' : 'en'}:`;
    inhoud.appendChild(aantalLabel);

    kernbezwaar.individueleBezwaren.forEach((ref) => {
      const item = document.createElement('div');
      item.className = 'passage-item';

      const bestand = document.createElement('div');
      bestand.className = 'passage-bestandsnaam';
      bestand.textContent = ref.bestandsnaam;

      const passage = document.createElement('div');
      passage.className = 'passage-tekst';
      passage.textContent = `"${ref.passage}"`;

      item.appendChild(bestand);
      item.appendChild(passage);
      inhoud.appendChild(item);
    });

    sideSheet.open = true;
  }

  laadKernbezwaren(projectNaam) {
    this.projectNaam = projectNaam;
    fetch(`/api/v1/projects/${encodeURIComponent(projectNaam)}/kernbezwaren`)
        .then((response) => {
          if (response.status === 404) {
            this._themas = null;
            this._renderInhoud();
            return;
          }
          if (!response.ok) throw new Error('Ophalen kernbezwaren mislukt');
          return response.json();
        })
        .then((data) => {
          if (data) {
            this._themas = data.themas;
            this._renderInhoud();
          }
        })
        .catch(() => {
          this._themas = null;
          this._renderInhoud();
        });
  }

  reset() {
    this._themas = null;
    this._renderInhoud();
  }
}

defineWebComponent(BezwaarschriftenKernbezwaren, 'bezwaarschriften-kernbezwaren');
```

> Let op: de exacte component API van `vl-accordion` en `vl-side-sheet` moet gecontroleerd worden in de @domg-wc documentatie. Attributen als `toggle-text` en `open` kunnen afwijken. Controleer dit in `node_modules/@domg-wc/components/block/accordion/` en `node_modules/@domg-wc/components/block/side-sheet/`.

**Step 2: Commit**

```bash
git add webapp/src/js/bezwaarschriften-kernbezwaren.js
git commit -m "feat: bezwaarschriften-kernbezwaren web component"
```

---

## Task 8: Integratie in hoofdcomponent

**Files:**
- Modify: `webapp/src/js/bezwaarschriften-project-selectie.js`

**Step 1: Voeg import en registratie toe**

Bovenaan het bestand:
```javascript
import './bezwaarschriften-kernbezwaren.js';
```

**Step 2: Vervang de kernbezwaren-tab placeholder**

Vervang:
```html
<vl-tabs-pane id="kernbezwaren" title="Kernbezwaren">
  <p>Kernbezwaren worden hier getoond na verwerking.</p>
</vl-tabs-pane>
```

Door:
```html
<vl-tabs-pane id="kernbezwaren" title="Kernbezwaren">
  <bezwaarschriften-kernbezwaren id="kernbezwaren-component"></bezwaarschriften-kernbezwaren>
</vl-tabs-pane>
```

**Step 3: Update `_laadBezwaren` om kernbezwaren-component te configureren**

Na het laden van bezwaren, stel het kernbezwaren-component in:

```javascript
// In _laadBezwaren, na this._werkTabelBij():
const kernComp = this.shadowRoot.querySelector('#kernbezwaren-component');
if (kernComp) {
  const aantalKlaar = data.bezwaren.filter(b => b.status === 'extractie-klaar').length;
  const totaalBezwaren = data.bezwaren
      .filter(b => b.status === 'extractie-klaar')
      .reduce((sum, b) => sum + (b.aantalBezwaren || 0), 0);
  kernComp.setAttribute('project-naam', projectNaam);
  kernComp.setAttribute('aantal-bezwaren', String(totaalBezwaren));
  if (aantalKlaar > 0) {
    kernComp.setAttribute('extractie-klaar', '');
  } else {
    kernComp.removeAttribute('extractie-klaar');
  }
  kernComp.laadKernbezwaren(projectNaam);
}
```

**Step 4: Update `_verwerkTaakUpdate` om kernbezwaren-component bij te werken**

Na de bestaande status-updates, update ook het kernbezwaren-component wanneer een extractie klaar is:

```javascript
// In _verwerkTaakUpdate, na this._werkVerwerkenKnopBij():
if (taak.status === 'extractie-klaar') {
  const kernComp = this.shadowRoot.querySelector('#kernbezwaren-component');
  if (kernComp) {
    const totaalBezwaren = this.__bezwaren
        .filter(b => b.status === 'extractie-klaar')
        .reduce((sum, b) => sum + (b.aantalBezwaren || 0), 0);
    kernComp.setAttribute('aantal-bezwaren', String(totaalBezwaren));
    kernComp.setAttribute('extractie-klaar', '');
  }
}
```

**Step 5: Build en test**

```bash
cd webapp && npm run build
mvn process-resources -pl webapp -Denforcer.skip=true
```

Start de applicatie en test handmatig:
1. Selecteer een project
2. Verwerk documenten
3. Ga naar Kernbezwaren-tab → moet "N individuele bezwaren gevonden..." tonen
4. Klik "Groepeer bezwaren" → accordeons verschijnen
5. Klik op een vergrootglas-knop → side-sheet opent met passages

**Step 6: Commit**

```bash
git add webapp/src/js/bezwaarschriften-project-selectie.js
git commit -m "feat: kernbezwaren-component geïntegreerd in hoofdcomponent"
```

---

## Task 9: Handmatige verificatie en polish

**Step 1: Start de applicatie**

```bash
mvn spring-boot:run -pl app -Dspring-boot.run.profiles=dev -Denforcer.skip=true
```

**Step 2: Test de volledige flow**

1. Open `http://localhost:8080`
2. Selecteer een project
3. Upload/verwerk bestanden
4. Ga naar Kernbezwaren-tab
5. Check de drie staten (geen extractie → extractie klaar → gegroepeerd)
6. Klik op vergrootglas → side-sheet opent
7. Check dat side-sheet correct passages toont

**Step 3: Fix eventuele component-API issues**

De volgende zaken moeten mogelijk aangepast worden na handmatige test:
- `vl-accordion` attribute namen (bijv. `toggle-text` vs `toggleText`)
- `vl-side-sheet` open/close mechanisme (bijv. `open` property vs methode)
- Knop-icon: als `vl-button` geen icon-ondersteuning heeft via tertiary, gebruik `vl-icon` apart

**Step 4: Commit fixes**

```bash
git add -u
git commit -m "fix: UI-polish na handmatige verificatie"
```
