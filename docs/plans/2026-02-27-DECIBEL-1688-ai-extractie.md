# DECIBEL-1688: AI-extractie van individuele bezwaren — Implementatieplan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Ruwe bezwaarschrifttekst opsplitsen in gestructureerde individuele bezwaren via een lokale LLM (Ollama).

**Architecture:** Hexagonale architectuur met `ExtractiePoort` interface en `OllamaExtractieAdapter`. De adapter gebruikt Spring AI `ChatClient` om via Ollama (`mistral-small:24b`) de tekst te analyseren. Extractie-resultaten worden in-memory opgeslagen in het bestaande `statusRegister` van `ProjectService`.

**Tech Stack:** Java 24, Spring Boot 3.x, Spring AI (Ollama), JUnit 5, Mockito, AssertJ

**Spec:** `specs/DECIBEL-1688.md`

---

## Task 1: Spring AI Ollama dependency toevoegen

**Files:**
- Modify: `pom.xml` (root) — Spring AI BOM toevoegen in `<dependencyManagement>`
- Modify: `app/pom.xml` — `spring-ai-ollama-spring-boot-starter` dependency toevoegen
- Modify: `app/src/main/resources/application.yml` — Ollama configuratie toevoegen

**Step 1: Voeg Spring AI BOM toe aan root pom.xml**

In `pom.xml` (root), voeg toe in `<properties>`:
```xml
<spring-ai.version>1.0.0</spring-ai.version>
```

Voeg toe in `<dependencyManagement><dependencies>`:
```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-bom</artifactId>
    <version>${spring-ai.version}</version>
    <type>pom</type>
    <scope>import</scope>
</dependency>
```

**Step 2: Voeg Ollama starter toe aan app/pom.xml**

In `app/pom.xml`, voeg toe na de Spring-dependencies (regel ~58):
```xml
<!-- Spring AI -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-ollama-spring-boot-starter</artifactId>
</dependency>
```

**Step 3: Voeg Ollama configuratie toe aan application.yml**

In `app/src/main/resources/application.yml`, voeg toe na `bezwaarschriften.input.folder`:
```yaml
spring:
  ai:
    ollama:
      base-url: http://localhost:11434
      chat:
        model: mistral-small:24b
        options:
          temperature: 0.1
          num-predict: 4096
```

**Step 4: Verifieer dat de build slaagt**

Run: `mvn compile -pl app -am -q` vanuit de project root.
Expected: BUILD SUCCESS (geen dependency-conflicten)

**Step 5: Commit**

```bash
git add pom.xml app/pom.xml app/src/main/resources/application.yml
git commit -m "DECIBEL-1688: voeg Spring AI Ollama dependency toe"
```

---

## Task 2: Hernoem TODO naar TEKST_INGELADEN + fix bestaande code en tests

**Files:**
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/BezwaarBestandStatus.java:7`
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ProjectService.java:74,94`
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ProjectController.java:79`
- Modify: `app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/project/ProjectServiceTest.java:57`
- Modify: `app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/project/ProjectControllerTest.java:50,57`
- Modify: `webapp/src/js/bezwaarschriften-bezwaren-tabel.js:8`

**Step 1: Hernoem enum-waarde**

In `BezwaarBestandStatus.java` verander `TODO` naar `TEKST_INGELADEN`:
```java
public enum BezwaarBestandStatus {
  TEKST_INGELADEN,
  EXTRACTIE_KLAAR,
  FOUT,
  NIET_ONDERSTEUND
}
```

**Step 2: Fix ProductService.java referenties**

In `ProjectService.java`:
- Regel 74: `BezwaarBestandStatus.TODO` → `BezwaarBestandStatus.TEKST_INGELADEN`
- Regel 94: `BezwaarBestandStatus.TODO` → `BezwaarBestandStatus.TEKST_INGELADEN`

**Step 3: Fix ProjectController.java status-mapping**

In `ProjectController.java` regel 79:
```java
case TEKST_INGELADEN -> "tekst-ingeladen";
```

**Step 4: Fix ProjectServiceTest.java**

In `ProjectServiceTest.java` regel 57:
```java
assertThat(b.status()).isEqualTo(BezwaarBestandStatus.TEKST_INGELADEN);
```

**Step 5: Fix ProjectControllerTest.java**

In `ProjectControllerTest.java`:
- Regel 50: `BezwaarBestandStatus.TODO` → `BezwaarBestandStatus.TEKST_INGELADEN`
- Regel 57: `"todo"` → `"tekst-ingeladen"`

**Step 6: Fix frontend status-label**

In `bezwaarschriften-bezwaren-tabel.js` regel 8:
```javascript
'tekst-ingeladen': 'Tekst ingeladen',
```
(verwijder de `'todo'` regel)

**Step 7: Verifieer alle tests**

Run: `mvn test -pl app -q`
Expected: Alle bestaande tests slagen.

**Step 8: Commit**

```bash
git add -A
git commit -m "DECIBEL-1688: hernoem TODO naar TEKST_INGELADEN in statusflow"
```

---

## Task 3: Maak GeextraheerdBezwaar record

**Files:**
- Create: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/extractie/GeextraheerdBezwaar.java`
- Create: `app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/extractie/GeextraheerdBezwaarTest.java`

**Step 1: Schrijf falende test**

```java
package be.vlaanderen.omgeving.bezwaarschriften.extractie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class GeextraheerdBezwaarTest {

  @Test
  void bevatThemaCitaatEnSamenvatting() {
    var bezwaar = new GeextraheerdBezwaar(
        "Geluidsoverlast",
        "De windturbines veroorzaken geluidsoverlast.",
        "Indiener vreest geluidsoverlast door windturbines.");

    assertThat(bezwaar.thema()).isEqualTo("Geluidsoverlast");
    assertThat(bezwaar.citaat()).isEqualTo("De windturbines veroorzaken geluidsoverlast.");
    assertThat(bezwaar.samenvatting())
        .isEqualTo("Indiener vreest geluidsoverlast door windturbines.");
  }

  @Test
  void citaatMagNietLeegZijn() {
    assertThatThrownBy(() -> new GeextraheerdBezwaar("thema", "", "samenvatting"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new GeextraheerdBezwaar("thema", null, "samenvatting"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void themaMagNietLeegZijn() {
    assertThatThrownBy(() -> new GeextraheerdBezwaar("", "citaat", "samenvatting"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
```

**Step 2: Run test — verifieer dat het faalt**

Run: `mvn test -pl app -Dtest=GeextraheerdBezwaarTest -q`
Expected: FAIL (class not found)

**Step 3: Implementeer het record**

```java
package be.vlaanderen.omgeving.bezwaarschriften.extractie;

/**
 * Gestructureerd bezwaar geextraheerd uit een brondocument door een LLM.
 *
 * @param thema Door AI gegenereerd thema (vrije tekst)
 * @param citaat Letterlijke passage uit het brondocument
 * @param samenvatting Beknopte samenvatting, maximaal een zin
 */
public record GeextraheerdBezwaar(String thema, String citaat, String samenvatting) {

  public GeextraheerdBezwaar {
    if (thema == null || thema.isBlank()) {
      throw new IllegalArgumentException("Thema mag niet leeg zijn");
    }
    if (citaat == null || citaat.isBlank()) {
      throw new IllegalArgumentException("Citaat mag niet leeg zijn");
    }
    if (samenvatting == null || samenvatting.isBlank()) {
      throw new IllegalArgumentException("Samenvatting mag niet leeg zijn");
    }
  }
}
```

**Step 4: Run test — verifieer dat het slaagt**

Run: `mvn test -pl app -Dtest=GeextraheerdBezwaarTest -q`
Expected: PASS

**Step 5: Commit**

```bash
git add app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/extractie/GeextraheerdBezwaar.java \
       app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/extractie/GeextraheerdBezwaarTest.java
git commit -m "DECIBEL-1688: voeg GeextraheerdBezwaar domeinmodel toe"
```

---

## Task 4: Maak ExtractiePoort interface

**Files:**
- Create: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/extractie/ExtractiePoort.java`

**Step 1: Maak de port interface**

```java
package be.vlaanderen.omgeving.bezwaarschriften.extractie;

import java.util.List;

/**
 * Port interface voor het extraheren van individuele bezwaren uit documenttekst.
 */
public interface ExtractiePoort {

  /**
   * Extraheert individuele bezwaren uit de tekst van een brondocument.
   *
   * @param documentTekst Volledige tekst van het bezwaarschrift
   * @return Lijst van geextraheerde bezwaren (leeg als geen bezwaren gevonden)
   * @throws IllegalArgumentException als documentTekst null is
   */
  List<GeextraheerdBezwaar> extraheerBezwaren(String documentTekst);
}
```

**Step 2: Verifieer compilatie**

Run: `mvn compile -pl app -am -q`
Expected: BUILD SUCCESS

**Step 3: Commit**

```bash
git add app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/extractie/ExtractiePoort.java
git commit -m "DECIBEL-1688: voeg ExtractiePoort interface toe"
```

---

## Task 5: Maak OllamaExtractieAdapter + tests

**Files:**
- Create: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/extractie/OllamaExtractieAdapter.java`
- Create: `app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/extractie/OllamaExtractieAdapterTest.java`

**Step 1: Schrijf falende tests**

```java
package be.vlaanderen.omgeving.bezwaarschriften.extractie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.messages.AssistantMessage;

@ExtendWith(MockitoExtension.class)
class OllamaExtractieAdapterTest {

  private OllamaExtractieAdapter adapter;

  @Mock
  private ChatClient chatClient;

  @BeforeEach
  void setUp() {
    adapter = new OllamaExtractieAdapter(chatClient);
  }

  @Test
  void extraheertBezwarenUitGeldigeLlmResponse() {
    // Setup mock die een JSON-array retourneert
    // De exacte mock-setup hangt af van de ChatClient API
    // Dit wordt concreet gemaakt tijdens implementatie
  }

  @Test
  void retourneertLegeLijstBijGeenBezwaren() {
    // LLM retourneert lege array → lege lijst
  }

  @Test
  void gooitExceptionBijNullInput() {
    assertThatThrownBy(() -> adapter.extraheerBezwaren(null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void retourneertLegeLijstBijLegeTekst() {
    var resultaat = adapter.extraheerBezwaren("");
    assertThat(resultaat).isEmpty();
  }
}
```

**Note:** De exacte mock-setup voor Spring AI `ChatClient` hangt af van de API (builder pattern). De tests worden concreet gemaakt met de werkende imports nadat de dependency beschikbaar is. Focus op het gedrag:
- Geldige JSON → lijst GeextraheerdBezwaar
- Lege response → lege lijst
- Null input → IllegalArgumentException
- Lege tekst → lege lijst

**Step 2: Implementeer OllamaExtractieAdapter**

```java
package be.vlaanderen.omgeving.bezwaarschriften.extractie;

import java.lang.invoke.MethodHandles;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;

/**
 * Adapter die via Ollama (Spring AI) individuele bezwaren extraheert uit documenttekst.
 */
@Service
public class OllamaExtractieAdapter implements ExtractiePoort {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private static final String SYSTEEM_PROMPT = """
      Je bent een expert in het analyseren van bezwaarschriften van burgers.
      Je taak is om de tekst op te splitsen in individuele bezwaren.

      Per bezwaar geef je:
      - thema: een kort thema dat het bezwaar samenvat (vrije tekst, max 5 woorden)
      - citaat: de letterlijke passage uit het document die het bezwaar bevat
      - samenvatting: een beknopte samenvatting van maximaal een zin

      Als er geen bezwaren in de tekst staan, retourneer dan een lege lijst.
      Antwoord uitsluitend in het Nederlands.
      """;

  private final ChatClient chatClient;

  public OllamaExtractieAdapter(ChatClient.Builder chatClientBuilder) {
    this.chatClient = chatClientBuilder.build();
  }

  @Override
  public List<GeextraheerdBezwaar> extraheerBezwaren(String documentTekst) {
    if (documentTekst == null) {
      throw new IllegalArgumentException("Document tekst mag niet null zijn");
    }
    if (documentTekst.isBlank()) {
      return List.of();
    }

    LOGGER.info("Start extractie van bezwaren uit document ({} karakters)", documentTekst.length());

    List<GeextraheerdBezwaar> bezwaren = chatClient
        .prompt()
        .system(SYSTEEM_PROMPT)
        .user(documentTekst)
        .call()
        .entity(new ParameterizedTypeReference<>() {});

    LOGGER.info("Extractie voltooid: {} bezwaren gevonden", bezwaren.size());
    return bezwaren != null ? bezwaren : List.of();
  }
}
```

**Step 3: Run tests en verifieer**

Run: `mvn test -pl app -Dtest=OllamaExtractieAdapterTest -q`
Expected: PASS (tests die de mock gebruiken slagen; de LLM wordt niet echt aangeroepen)

**Step 4: Commit**

```bash
git add app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/extractie/OllamaExtractieAdapter.java \
       app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/extractie/OllamaExtractieAdapterTest.java
git commit -m "DECIBEL-1688: voeg OllamaExtractieAdapter toe met Spring AI"
```

---

## Task 6: Breid BezwaarBestand en VerwerkingsResultaat uit

**Files:**
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/BezwaarBestand.java`
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ProjectService.java:128` (VerwerkingsResultaat)

**Step 1: Breid BezwaarBestand uit met individueleBezwaren**

```java
package be.vlaanderen.omgeving.bezwaarschriften.project;

import be.vlaanderen.omgeving.bezwaarschriften.extractie.GeextraheerdBezwaar;
import java.util.List;

/**
 * Representeert een bezwaarbestand met zijn verwerkingsstatus en geextraheerde bezwaren.
 *
 * @param bestandsnaam Naam van het bezwaarbestand
 * @param status Huidige verwerkingsstatus
 * @param aantalWoorden Aantal woorden in het document
 * @param individueleBezwaren Lijst van geextraheerde individuele bezwaren
 */
public record BezwaarBestand(String bestandsnaam, BezwaarBestandStatus status,
    Integer aantalWoorden, List<GeextraheerdBezwaar> individueleBezwaren) {

  public BezwaarBestand(String bestandsnaam, BezwaarBestandStatus status) {
    this(bestandsnaam, status, null, List.of());
  }

  public BezwaarBestand(String bestandsnaam, BezwaarBestandStatus status, Integer aantalWoorden) {
    this(bestandsnaam, status, aantalWoorden, List.of());
  }
}
```

**Step 2: Breid VerwerkingsResultaat uit in ProjectService**

In `ProjectService.java` regel 128, vervang:
```java
private record VerwerkingsResultaat(BezwaarBestandStatus status, Integer aantalWoorden) { }
```
door:
```java
private record VerwerkingsResultaat(BezwaarBestandStatus status, Integer aantalWoorden,
    List<GeextraheerdBezwaar> individueleBezwaren) {
  VerwerkingsResultaat(BezwaarBestandStatus status, Integer aantalWoorden) {
    this(status, aantalWoorden, List.of());
  }
}
```

En import toevoegen:
```java
import be.vlaanderen.omgeving.bezwaarschriften.extractie.GeextraheerdBezwaar;
import java.util.List;
```

Pas ook `geefBezwaren()` aan (rond regel 72) om bezwaren door te geven:
```java
return new BezwaarBestand(naam, resultaat.status(), resultaat.aantalWoorden(),
    resultaat.individueleBezwaren());
```

**Step 3: Verifieer compilatie en bestaande tests**

Run: `mvn test -pl app -q`
Expected: Alle bestaande tests slagen (BezwaarBestand constructors zijn backward compatible).

**Step 4: Commit**

```bash
git add app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/BezwaarBestand.java \
       app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ProjectService.java
git commit -m "DECIBEL-1688: breid BezwaarBestand uit met individueleBezwaren"
```

---

## Task 7: Integreer ExtractiePoort in ProjectService + tests

**Files:**
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ProjectService.java`
- Modify: `app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/project/ProjectServiceTest.java`

**Step 1: Schrijf falende tests in ProjectServiceTest**

Voeg toe na bestaande tests:

```java
@Mock
private ExtractiePoort extractiePoort;

// In setUp():
service = new ProjectService(projectPoort, ingestiePoort, extractiePoort, "input");

@Test
void verwerkingRoeptExtractieAanEnSlaatResultaatOp() throws Exception {
  when(projectPoort.geefBestandsnamen("windmolens"))
      .thenReturn(List.of("bezwaar-001.txt"));
  when(ingestiePoort.leesBestand(Path.of("input", "windmolens", "bezwaren", "bezwaar-001.txt")))
      .thenReturn(new Brondocument("tekst met bezwaar", "bezwaar-001.txt",
          "input/windmolens/bezwaren/bezwaar-001.txt", Instant.now()));
  when(extractiePoort.extraheerBezwaren("tekst met bezwaar"))
      .thenReturn(List.of(
          new GeextraheerdBezwaar("Geluid", "tekst met bezwaar", "Bezwaar over geluid.")));

  var resultaat = service.verwerk("windmolens");

  assertThat(resultaat).hasSize(1);
  assertThat(resultaat.get(0).status()).isEqualTo(BezwaarBestandStatus.EXTRACTIE_KLAAR);
  assertThat(resultaat.get(0).individueleBezwaren()).hasSize(1);
  assertThat(resultaat.get(0).individueleBezwaren().get(0).thema()).isEqualTo("Geluid");
}

@Test
void leegExtractieResultaatGeeftStatusExtractieKlaar() throws Exception {
  when(projectPoort.geefBestandsnamen("windmolens"))
      .thenReturn(List.of("brief.txt"));
  when(ingestiePoort.leesBestand(Path.of("input", "windmolens", "bezwaren", "brief.txt")))
      .thenReturn(new Brondocument("begeleidende brief", "brief.txt",
          "input/windmolens/bezwaren/brief.txt", Instant.now()));
  when(extractiePoort.extraheerBezwaren("begeleidende brief"))
      .thenReturn(List.of());

  var resultaat = service.verwerk("windmolens");

  assertThat(resultaat.get(0).status()).isEqualTo(BezwaarBestandStatus.EXTRACTIE_KLAAR);
  assertThat(resultaat.get(0).individueleBezwaren()).isEmpty();
}

@Test
void foutBijExtractieGeeftStatusFout() throws Exception {
  when(projectPoort.geefBestandsnamen("windmolens"))
      .thenReturn(List.of("bezwaar.txt"));
  when(ingestiePoort.leesBestand(Path.of("input", "windmolens", "bezwaren", "bezwaar.txt")))
      .thenReturn(new Brondocument("tekst", "bezwaar.txt",
          "input/windmolens/bezwaren/bezwaar.txt", Instant.now()));
  when(extractiePoort.extraheerBezwaren("tekst"))
      .thenThrow(new RuntimeException("LLM onbereikbaar"));

  var resultaat = service.verwerk("windmolens");

  assertThat(resultaat.get(0).status()).isEqualTo(BezwaarBestandStatus.FOUT);
  assertThat(resultaat.get(0).individueleBezwaren()).isEmpty();
}
```

**Step 2: Run tests — verifieer dat de nieuwe tests falen**

Run: `mvn test -pl app -Dtest=ProjectServiceTest -q`
Expected: Nieuwe tests falen (constructor mismatch)

**Step 3: Pas ProjectService aan**

Voeg `ExtractiePoort` toe als constructor-parameter:
```java
private final ExtractiePoort extractiePoort;

public ProjectService(
    ProjectPoort projectPoort,
    IngestiePoort ingestiePoort,
    ExtractiePoort extractiePoort,
    @Value("${bezwaarschriften.input.folder}") String inputFolderString) {
  this.projectPoort = projectPoort;
  this.ingestiePoort = ingestiePoort;
  this.extractiePoort = extractiePoort;
  this.inputFolder = Path.of(inputFolderString);
}
```

Pas de `verwerk()` methode aan — na het inlezen van het brondocument, roep extractie aan:
```java
var brondocument = ingestiePoort.leesBestand(bestandsPad);
var aantalWoorden = telWoorden(brondocument.tekst());
var bezwarenLijst = extractiePoort.extraheerBezwaren(brondocument.tekst());
projectStatussen.put(bestand.bestandsnaam(),
    new VerwerkingsResultaat(BezwaarBestandStatus.EXTRACTIE_KLAAR, aantalWoorden,
        bezwarenLijst));
```

En in de catch-block:
```java
projectStatussen.put(bestand.bestandsnaam(),
    new VerwerkingsResultaat(BezwaarBestandStatus.FOUT, null, List.of()));
```

**Step 4: Fix bestaande tests (voeg ExtractiePoort mock toe)**

Alle bestaande tests in `ProjectServiceTest` moeten de `ExtractiePoort` mock ontvangen.
- Voeg `@Mock private ExtractiePoort extractiePoort;` toe
- Pas `setUp()` aan: `service = new ProjectService(projectPoort, ingestiePoort, extractiePoort, "input");`
- Voeg in `verwerkingZetStatusOpExtractieKlaarBijSucces` een `when(extractiePoort.extraheerBezwaren(...)).thenReturn(List.of())` toe
- Idem voor `verwerkingGaatDoorBijFoutOpEnkelBestand` en `slaatNietOndersteundeBestandenOverBijVerwerking`

**Step 5: Run alle tests**

Run: `mvn test -pl app -q`
Expected: Alle tests slagen.

**Step 6: Commit**

```bash
git add app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ProjectService.java \
       app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/project/ProjectServiceTest.java
git commit -m "DECIBEL-1688: integreer ExtractiePoort in ProjectService"
```

---

## Task 8: Breid ProjectController DTOs uit + tests

**Files:**
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ProjectController.java`
- Modify: `app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/project/ProjectControllerTest.java`

**Step 1: Schrijf falende controller test**

```java
@Test
void verwerkResponseBevatIndividueleBezwaren() throws Exception {
  when(projectService.verwerk("windmolens")).thenReturn(List.of(
      new BezwaarBestand("bezwaar.txt", BezwaarBestandStatus.EXTRACTIE_KLAAR, 42,
          List.of(new GeextraheerdBezwaar("Geluid",
              "De windturbines veroorzaken geluidsoverlast.",
              "Bezwaar over geluidsoverlast door windturbines.")))
  ));

  mockMvc.perform(post("/api/v1/projects/windmolens/verwerk").with(csrf()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.bezwaren[0].individueleBezwaren[0].thema").value("Geluid"))
      .andExpect(jsonPath("$.bezwaren[0].individueleBezwaren[0].citaat")
          .value("De windturbines veroorzaken geluidsoverlast."))
      .andExpect(jsonPath("$.bezwaren[0].individueleBezwaren[0].samenvatting")
          .value("Bezwaar over geluidsoverlast door windturbines."));
}
```

**Step 2: Pas BezwaarBestandDto aan**

```java
record BezwaarBestandDto(String bestandsnaam, String status, Integer aantalWoorden,
    List<IndividueelBezwaarDto> individueleBezwaren) {}

record IndividueelBezwaarDto(String thema, String citaat, String samenvatting) {}
```

Pas de mapping in `BezwarenResponse.van()` aan:
```java
static BezwarenResponse van(List<BezwaarBestand> bezwaren) {
  return new BezwarenResponse(bezwaren.stream()
      .map(b -> new BezwaarBestandDto(
          b.bestandsnaam(),
          statusNaarString(b.status()),
          b.aantalWoorden(),
          b.individueleBezwaren().stream()
              .map(ib -> new IndividueelBezwaarDto(ib.thema(), ib.citaat(), ib.samenvatting()))
              .toList()))
      .toList());
}
```

**Step 3: Run tests**

Run: `mvn test -pl app -Dtest=ProjectControllerTest -q`
Expected: Alle tests slagen.

**Step 4: Commit**

```bash
git add app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ProjectController.java \
       app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/project/ProjectControllerTest.java
git commit -m "DECIBEL-1688: breid controller DTOs uit met individueleBezwaren"
```

---

## Task 9: Update frontend bezwarentabel

**Files:**
- Modify: `webapp/src/js/bezwaarschriften-bezwaren-tabel.js`

**Step 1: Voeg kolom 'Individuele bezwaren' toe**

In de constructor template, voeg een derde `<th>` toe:
```html
<thead>
  <tr>
    <th>Bestandsnaam</th>
    <th>Status</th>
    <th>Individuele bezwaren</th>
  </tr>
</thead>
```

**Step 2: Voeg bezwaren-cel toe in _renderRijen**

Pas de map in `_renderRijen` aan (rond regel 62):
```javascript
tbody.innerHTML = this.__bezwaren
    .map((b) => `<tr>
      <td>${this._escapeHtml(b.bestandsnaam)}</td>
      <td>${this._formatStatus(b)}</td>
      <td>${this._formatBezwaren(b)}</td>
    </tr>`)
    .join('');
```

Pas de lege toestand aan (`colspan="3"`):
```javascript
tbody.innerHTML = '<tr><td colspan="3">Geen bestanden gevonden</td></tr>';
```

**Step 3: Voeg _formatBezwaren methode toe**

```javascript
_formatBezwaren(b) {
  if (!b.individueleBezwaren || b.individueleBezwaren.length === 0) {
    return '-';
  }
  return '<ul style="margin:0;padding-left:1.2em;">' +
      b.individueleBezwaren
          .map((ib) => `<li>${this._escapeHtml(ib.samenvatting)}</li>`)
          .join('') +
      '</ul>';
}
```

**Step 4: Verifieer visueel**

Start de applicatie lokaal en verifieer dat de tabel een derde kolom toont.

**Step 5: Commit**

```bash
git add webapp/src/js/bezwaarschriften-bezwaren-tabel.js
git commit -m "DECIBEL-1688: toon geextraheerde bezwaren in bezwarentabel"
```

---

## Task 10: Retry-configuratie toevoegen

**Files:**
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/extractie/OllamaExtractieAdapter.java`
- Modify: `app/src/main/resources/application.yml`

**Step 1: Voeg retry-logica toe in de adapter**

Voeg een simpele retry-loop toe in `extraheerBezwaren()`:
```java
private static final int MAX_POGINGEN = 3;

@Override
public List<GeextraheerdBezwaar> extraheerBezwaren(String documentTekst) {
  if (documentTekst == null) {
    throw new IllegalArgumentException("Document tekst mag niet null zijn");
  }
  if (documentTekst.isBlank()) {
    return List.of();
  }

  LOGGER.info("Start extractie ({} karakters)", documentTekst.length());
  Exception laatsteFout = null;

  for (int poging = 1; poging <= MAX_POGINGEN; poging++) {
    try {
      List<GeextraheerdBezwaar> bezwaren = chatClient
          .prompt()
          .system(SYSTEEM_PROMPT)
          .user(documentTekst)
          .call()
          .entity(new ParameterizedTypeReference<>() {});
      LOGGER.info("Extractie voltooid: {} bezwaren (poging {})", bezwaren.size(), poging);
      return bezwaren != null ? bezwaren : List.of();
    } catch (Exception e) {
      laatsteFout = e;
      LOGGER.warn("Extractie mislukt (poging {}/{}): {}", poging, MAX_POGINGEN, e.getMessage());
    }
  }

  throw new ExtractieException("Extractie mislukt na " + MAX_POGINGEN + " pogingen", laatsteFout);
}
```

**Step 2: Maak ExtractieException**

```java
package be.vlaanderen.omgeving.bezwaarschriften.extractie;

public class ExtractieException extends RuntimeException {
  public ExtractieException(String message, Throwable cause) {
    super(message, cause);
  }
}
```

**Step 3: Schrijf retry-tests**

Maak `ExtractieRetryTest` of voeg toe aan `OllamaExtractieAdapterTest`:
- Test dat na 2 falen + 1 succes het resultaat wordt geretourneerd
- Test dat na 3 falen een ExtractieException wordt gegooid
- Test dat bij eerste succes de client slechts 1x wordt aangeroepen

**Step 4: Run alle tests**

Run: `mvn test -pl app -q`
Expected: Alle tests slagen.

**Step 5: Commit**

```bash
git add app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/extractie/ \
       app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/extractie/
git commit -m "DECIBEL-1688: voeg retry-logica toe (max 3 pogingen)"
```

---

## Task 11: Integratietest met Ollama

**Files:**
- Create: `app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/extractie/ExtractieOllamaIntegrationTest.java`
- Create: `app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/extractie/TestBezwaarteksten.java`

**Step 1: Maak testdata-klasse**

```java
package be.vlaanderen.omgeving.bezwaarschriften.extractie;

final class TestBezwaarteksten {
  // Synthetische bezwaarteksten — zie specs/DECIBEL-1688.md QA testplan sectie 3.1
  static final String TWEE_BEZWAREN = """
      Geachte heer/mevrouw,

      Hierbij maak ik bezwaar tegen het bestemmingsplan "Windpark Zeebrugge".

      Ten eerste is er onvoldoende onderzoek gedaan naar de geluidsoverlast ...
      (volledige tekst uit testplan)
      """;
  // etc.
}
```

**Step 2: Schrijf integratietest**

```java
@Tag("slow")
@SpringBootTest
class ExtractieOllamaIntegrationTest {

  @Autowired
  private ExtractiePoort extractiePoort;

  @Test
  void extraheertMinstensTweeBezwarenUitSynthetischeTekst() {
    var resultaat = extractiePoort.extraheerBezwaren(TestBezwaarteksten.TWEE_BEZWAREN);

    assertThat(resultaat)
        .hasSizeGreaterThanOrEqualTo(2)
        .allSatisfy(b -> {
          assertThat(b.thema()).isNotBlank();
          assertThat(b.citaat()).isNotBlank();
          assertThat(b.samenvatting()).isNotBlank();
        });
  }
}
```

**Step 3: Run integratietest (vereist draaiende Ollama)**

Run: `mvn test -pl app -Dtest=ExtractieOllamaIntegrationTest -Dgroups=slow -q`
Expected: PASS (als Ollama draait met mistral-small:24b)

**Step 4: Commit**

```bash
git add app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/extractie/
git commit -m "DECIBEL-1688: integratietest met Ollama voor extractie"
```

---

## Task 12: Eindverificatie — alle tests draaien

**Step 1: Run volledige test suite**

Run: `mvn test -pl app -q`
Expected: Alle unit tests + controller tests slagen.

**Step 2: Run integratietests (optioneel)**

Run: `mvn test -pl app -Dgroups=slow -q`
Expected: Integratietests slagen (met draaiende Ollama).

**Step 3: Verifieer dat de applicatie start**

Run: `mvn spring-boot:run -pl app` (met docker-compose en Ollama actief)
Test handmatig:
1. Open de UI
2. Selecteer een project
3. Klik "Verwerk alles"
4. Verifieer dat individuele bezwaren verschijnen in de tabel

---

## Samenvatting

| Task | Beschrijving | Nieuwe files | Gewijzigde files |
|------|-------------|-------------|-----------------|
| 1 | Spring AI dependency | - | pom.xml (2x), application.yml |
| 2 | TODO → TEKST_INGELADEN | - | 6 bestanden |
| 3 | GeextraheerdBezwaar record | 2 | - |
| 4 | ExtractiePoort interface | 1 | - |
| 5 | OllamaExtractieAdapter | 2 | - |
| 6 | BezwaarBestand uitbreiden | - | 2 |
| 7 | ProjectService integratie | - | 2 |
| 8 | Controller DTOs | - | 2 |
| 9 | Frontend bezwarentabel | - | 1 |
| 10 | Retry-logica | 1 | 1 |
| 11 | Integratietest Ollama | 2 | - |
| 12 | Eindverificatie | - | - |
