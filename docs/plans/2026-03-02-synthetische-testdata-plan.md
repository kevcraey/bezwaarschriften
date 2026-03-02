# Synthetische Testdata & Fixture-based Extractie Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Genereer realistische testdata uit synthetische PDF-bezwaarschriften en bouw een fixture-based extractie-implementatie zodat tests deterministische, door een echt LLM gegenereerde antwoorden krijgen.

**Architecture:** Hexagonale aanpak met een `ChatModelPoort` interface (eigen port, geen Spring AI — project draait op Spring Boot 2.7 en Spring AI vereist 3.2+). `FixtureChatModel` implementeert de port voor tests, `AiExtractieVerwerker` vervangt `MockExtractieVerwerker` door een implementatie die documenttekst naar het ChatModel stuurt en het JSON-antwoord parst.

**Tech Stack:** Java 21, Spring Boot 2.7, Jackson (al beschikbaar), JUnit 5 + Mockito 5.14.2, TestContainers PostgreSQL 17.

**Design doc:** `docs/plans/2026-03-02-synthetische-testdata-design.md`

---

## Phase 1: Fixture Generation

### Task 1: Lees Projectvoorstel en maak testdata-structuur

**Files:**
- Create: `testdata/gaverbeek-stadion/projectomschrijving.txt`
- Create: `testdata/gaverbeek-stadion/bezwaren/` (directory)

**Step 1: Maak de folder structuur**

```bash
mkdir -p testdata/gaverbeek-stadion/bezwaren
```

**Step 2: Lees Projectvoorstel PDF**

Lees `synthetic-dataset/Projectvoorstel_Gaverbeek_Stadion.pdf` (multimodaal) en schrijf een korte projectomschrijving naar `testdata/gaverbeek-stadion/projectomschrijving.txt`.

Formaat:
```
Herontwikkeling Gaverbeek Stadion — Waregem

[2-5 zinnen samenvatting van het projectvoorstel, gebaseerd op de PDF inhoud]
```

**Step 3: Commit**

```bash
git add testdata/
git commit -m "chore: testdata folder structuur + projectomschrijving"
```

---

### Task 2: Genereer .txt en .json fixtures voor alle bezwaarschriften

**Files:** Voor elk bezwaarschrift in `synthetic-dataset/`:
- Create: `testdata/gaverbeek-stadion/bezwaren/{naam}.txt`
- Create: `testdata/gaverbeek-stadion/bezwaren/{naam}.json`

**PDF bestanden (19 bezwaarschriften + 1 handgeschreven PNG):**

| Bestand | Type |
|---------|------|
| `Bezwaar_01.pdf` | Digitaal |
| `Bezwaar_02.pdf` | Digitaal |
| `Bezwaar_03.pdf` | Digitaal |
| `Bezwaar_04.pdf` | Digitaal |
| `Bezwaar_05.pdf` | Digitaal |
| `Bezwaar_06.pdf` | Digitaal |
| `Bezwaar_07.pdf` | Digitaal |
| `Bezwaar_08.pdf` | Digitaal |
| `Bezwaar_09.pdf` | Digitaal |
| `Bezwaar_010.pdf` | Digitaal |
| `Bezwaar_011.pdf` | Digitaal |
| `Bezwaar_012.pdf` | Digitaal |
| `Bezwaar_013.pdf` | Digitaal |
| `Bezwaar_10.pdf` | Digitaal |
| `Bezwaar_11.pdf` | Digitaal |
| `Bezwaar_12.pdf` | Digitaal |
| `Bezwaar_13.pdf` | Digitaal |
| `Bezwaar_14_geheel_gescand.pdf` | Gescand (OCR nodig) |
| `Bezwaar_15_mixed.pdf` | Mix digitaal + gescand |
| `Bewzaar_16_handgeschreven.png` | Handgeschreven (PNG) |

**Step 1: Dispatch parallel sub-agents**

Dispatch sub-agents in batches van 5 (parallel). Elke sub-agent:

1. Leest het PDF/PNG bestand multimodaal
2. Extraheert de volledige tekst → schrijft naar `testdata/gaverbeek-stadion/bezwaren/{naam}.txt`
3. Analyseert de tekst met de productie-prompt (zie hieronder) → schrijft naar `testdata/gaverbeek-stadion/bezwaren/{naam}.json`

**Productie-prompt voor analyse (system prompt):**

```
Je bent een ervaren ambtenaar bij het Departement Omgeving van de Vlaamse overheid.
Je analyseert bezwaarschriften die zijn ingediend tijdens een openbaar onderzoek.

Je taak is om uit het bezwaarschrift alle individuele bezwaren te identificeren.

## Wat is een individueel bezwaar?

Een individueel bezwaar is één concreet punt van bezwaar dat zelfstandig beantwoord
kan worden door de vergunningverlenende overheid. Voorbeelden:
- "De geluidsoverlast door evenementen zal onze nachtrust verstoren" → één bezwaar (geluidshinder)
- "Het verkeer zal toenemen EN er zijn onvoldoende parkeerplaatsen" → TWEE bezwaren
  (verkeerslast + parkeertekort), ook al staan ze in dezelfde zin

Splits passages die meerdere bezwaren bevatten altijd op in afzonderlijke items.
Eén passage kan dus leiden tot meerdere bezwaren.

## Per bezwaar lever je:

1. **passage**: De letterlijke tekst uit het bezwaarschrift waaruit dit bezwaar blijkt.
   Kopieer de exacte woorden — niet parafraseren. Als het bezwaar over meerdere zinnen
   loopt, neem de volledige relevante passage op.
2. **samenvatting**: Eén zin die het bezwaar kernachtig beschrijft in je eigen woorden.
3. **categorie**: Eén van: milieu, mobiliteit, ruimtelijke_ordening, procedure,
   gezondheid, economisch, sociaal, overig.

Antwoord UITSLUITEND in het volgende JSON-formaat (geen extra tekst):
{
  "passages": [
    { "id": 1, "tekst": "..." }
  ],
  "bezwaren": [
    { "passageId": 1, "samenvatting": "...", "categorie": "..." }
  ],
  "metadata": {
    "aantalWoorden": 0,
    "documentSamenvatting": "..."
  }
}
```

**User prompt (per document):**

```
Context: Openbaar onderzoek voor het project "Herontwikkeling Gaverbeek Stadion"
in Waregem — bouw van een multifunctioneel stadion met parking, commerciële ruimtes
en publieke groenzones langs de Gaverbeek.

Analyseer het volgende bezwaarschrift en extraheer alle individuele bezwaren:

---
{inhoud van de .txt}
---
```

**Step 2: Verifieer fixtures**

Na generatie, controleer per bestand:
- `.txt` bevat leesbare tekst (niet leeg)
- `.json` is valide JSON die parseert
- `metadata.aantalWoorden` klopt bij benadering met `wc -w` op de .txt
- Elke `passageId` in bezwaren verwijst naar een bestaande passage

**Step 3: Commit**

```bash
git add testdata/gaverbeek-stadion/bezwaren/
git commit -m "chore: genereer txt + json fixtures voor 20 bezwaarschriften"
```

---

### Task 3: Genereer manifest.json

**Files:**
- Create: `testdata/gaverbeek-stadion/manifest.json`

**Step 1: Genereer manifest uit de fixtures**

Lees alle gegenereerde `.json` fixtures en bouw `manifest.json`:

```json
{
  "project": "Herontwikkeling Gaverbeek Stadion",
  "bestanden": [
    {
      "bestandsnaam": "Bezwaar_01",
      "txtBestand": "bezwaren/Bezwaar_01.txt",
      "fixtureBestand": "bezwaren/Bezwaar_01.json",
      "aantalBezwaren": 4,
      "aantalWoorden": 542
    }
  ]
}
```

De `aantalBezwaren` en `aantalWoorden` komen uit de `metadata` van elk fixture-bestand.

**Step 2: Commit**

```bash
git add testdata/gaverbeek-stadion/manifest.json
git commit -m "chore: manifest.json voor testdata fixtures"
```

---

## Phase 2: Domain Model Extension

### Task 4: Maak Passage en GeextraheerdBezwaar records

**Files:**
- Create: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/Passage.java`
- Create: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/GeextraheerdBezwaar.java`
- Test: `app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieResultaatTest.java`

**Step 1: Schrijf failing test**

```java
package be.vlaanderen.omgeving.bezwaarschriften.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import org.junit.jupiter.api.Test;

class ExtractieResultaatTest {

  @Test
  void volledigResultaatBevatPassagesEnBezwaren() {
    var passage = new Passage(1, "De geluidsoverlast is onaanvaardbaar.");
    var bezwaar = new GeextraheerdBezwaar(1, "Geluidsoverlast door evenementen", "milieu");

    var resultaat = new ExtractieResultaat(
        542, 1, List.of(passage), List.of(bezwaar), "Bezwaar over geluid");

    assertEquals(542, resultaat.aantalWoorden());
    assertEquals(1, resultaat.aantalBezwaren());
    assertEquals(1, resultaat.passages().size());
    assertEquals("De geluidsoverlast is onaanvaardbaar.", resultaat.passages().get(0).tekst());
    assertEquals(1, resultaat.bezwaren().size());
    assertEquals("milieu", resultaat.bezwaren().get(0).categorie());
    assertEquals("Bezwaar over geluid", resultaat.documentSamenvatting());
  }

  @Test
  void terugwaartseCompatibiliteitMetEnkelCounts() {
    var resultaat = new ExtractieResultaat(100, 3);

    assertEquals(100, resultaat.aantalWoorden());
    assertEquals(3, resultaat.aantalBezwaren());
    assertNotNull(resultaat.passages());
    assertEquals(0, resultaat.passages().size());
    assertNotNull(resultaat.bezwaren());
    assertEquals(0, resultaat.bezwaren().size());
  }
}
```

**Step 2: Run test om te bevestigen dat het faalt**

```bash
cd app && mvn test -pl . -Dtest=ExtractieResultaatTest -Denforcer.skip=true -Dcheckstyle.skip=true 2>&1 | tail -5
```

Verwacht: COMPILATION ERROR (Passage, GeextraheerdBezwaar bestaan nog niet, ExtractieResultaat mist constructor)

**Step 3: Implementeer de records**

`app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/Passage.java`:
```java
package be.vlaanderen.omgeving.bezwaarschriften.project;

/**
 * Een passage uit een bezwaarschrift die als bron dient voor een of meer bezwaren.
 *
 * @param id Volgnummer van de passage binnen het document
 * @param tekst De letterlijke tekst uit het bezwaarschrift
 */
public record Passage(int id, String tekst) {}
```

`app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/GeextraheerdBezwaar.java`:
```java
package be.vlaanderen.omgeving.bezwaarschriften.project;

/**
 * Een individueel bezwaar geextraheerd uit een passage van een bezwaarschrift.
 *
 * @param passageId Verwijzing naar de bron-passage
 * @param samenvatting Kernachtige omschrijving van het bezwaar
 * @param categorie Classificatie: milieu, mobiliteit, ruimtelijke_ordening,
 *                  procedure, gezondheid, economisch, sociaal, overig
 */
public record GeextraheerdBezwaar(int passageId, String samenvatting, String categorie) {}
```

Wijzig `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieResultaat.java`:
```java
package be.vlaanderen.omgeving.bezwaarschriften.project;

import java.util.List;

/**
 * Resultaat van een bezwaarbestand-extractie.
 *
 * @param aantalWoorden Aantal woorden in het verwerkte bestand
 * @param aantalBezwaren Aantal geextraheerde bezwaren
 * @param passages Geidentificeerde passages uit het brondocument
 * @param bezwaren Individuele bezwaren met verwijzing naar bron-passage
 * @param documentSamenvatting Korte samenvatting van het volledige document
 */
public record ExtractieResultaat(
    int aantalWoorden,
    int aantalBezwaren,
    List<Passage> passages,
    List<GeextraheerdBezwaar> bezwaren,
    String documentSamenvatting) {

  /**
   * Terugwaarts-compatibele constructor met enkel counts (voor MockExtractieVerwerker).
   */
  public ExtractieResultaat(int aantalWoorden, int aantalBezwaren) {
    this(aantalWoorden, aantalBezwaren, List.of(), List.of(), null);
  }
}
```

**Step 4: Run test om te bevestigen dat het slaagt**

```bash
cd app && mvn test -pl . -Dtest=ExtractieResultaatTest -Denforcer.skip=true -Dcheckstyle.skip=true 2>&1 | tail -5
```

Verwacht: BUILD SUCCESS

**Step 5: Run alle tests om regressie te checken**

```bash
cd app && mvn test -Denforcer.skip=true -Dcheckstyle.skip=true 2>&1 | tail -20
```

Verwacht: Alle bestaande tests slagen (de oude `ExtractieResultaat(int, int)` constructor werkt nog).

**Step 6: Commit**

```bash
git add app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/Passage.java \
       app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/GeextraheerdBezwaar.java \
       app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieResultaat.java \
       app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieResultaatTest.java
git commit -m "feat: uitgebreid ExtractieResultaat met passages en bezwaren"
```

---

## Phase 3: ChatModelPoort & AiExtractieVerwerker

### Task 5: Maak ChatModelPoort interface

> **Waarom eigen port?** Het project draait op Spring Boot 2.7 (`acd-springboot-parent 2.7.12.0`).
> Spring AI vereist Spring Boot 3.2+. We maken een eigen hexagonale port die later kan
> bruggen naar Spring AI wanneer het project upgradet.

**Files:**
- Create: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ChatModelPoort.java`

**Step 1: Maak de interface**

```java
package be.vlaanderen.omgeving.bezwaarschriften.project;

/**
 * Port voor communicatie met een taalmodel (LLM).
 *
 * <p>Hexagonale abstractie die onafhankelijk is van de concrete LLM-provider.
 * Kan geimplementeerd worden door Spring AI, een HTTP-client, of een fixture-mock.
 */
public interface ChatModelPoort {

  /**
   * Stuurt een prompt naar het taalmodel en retourneert het antwoord.
   *
   * @param systemPrompt De systeem-instructie voor het model
   * @param userPrompt De gebruikers-prompt met documentinhoud
   * @return Het ruwe antwoord van het model (verwacht: JSON)
   */
  String chat(String systemPrompt, String userPrompt);
}
```

**Step 2: Commit**

```bash
git add app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ChatModelPoort.java
git commit -m "feat: ChatModelPoort hexagonale interface voor LLM-communicatie"
```

---

### Task 6: Maak AiExtractieVerwerker (TDD)

**Files:**
- Create: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/AiExtractieVerwerker.java`
- Create: `app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/project/AiExtractieVerwerkerTest.java`

**Step 1: Schrijf failing tests**

```java
package be.vlaanderen.omgeving.bezwaarschriften.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import be.vlaanderen.omgeving.bezwaarschriften.ingestie.Brondocument;
import be.vlaanderen.omgeving.bezwaarschriften.ingestie.IngestiePoort;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AiExtractieVerwerkerTest {

  private static final String FIXTURE_JSON = """
      {
        "passages": [
          { "id": 1, "tekst": "Het verkeer zal onhoudbaar toenemen." }
        ],
        "bezwaren": [
          { "passageId": 1, "samenvatting": "Verkeerstoename", "categorie": "mobiliteit" }
        ],
        "metadata": {
          "aantalWoorden": 7,
          "documentSamenvatting": "Bezwaar over verkeer"
        }
      }
      """;

  @Mock
  private ChatModelPoort chatModel;

  @Mock
  private IngestiePoort ingestiePoort;

  private AiExtractieVerwerker verwerker;

  @BeforeEach
  void setUp() {
    verwerker = new AiExtractieVerwerker(chatModel, ingestiePoort, "input");
  }

  @Test
  void verwerktDocumentEnParstLlmResponse() {
    var document = new Brondocument(
        "Het verkeer zal onhoudbaar toenemen.", "bezwaar.txt", "pad", Instant.now());
    when(ingestiePoort.leesBestand(anyString())).thenReturn(document);
    when(chatModel.chat(anyString(), anyString())).thenReturn(FIXTURE_JSON);

    var resultaat = verwerker.verwerk("project", "bezwaar.txt", 0);

    assertEquals(7, resultaat.aantalWoorden());
    assertEquals(1, resultaat.aantalBezwaren());
    assertEquals(1, resultaat.passages().size());
    assertEquals("Het verkeer zal onhoudbaar toenemen.", resultaat.passages().get(0).tekst());
    assertEquals(1, resultaat.bezwaren().size());
    assertEquals("mobiliteit", resultaat.bezwaren().get(0).categorie());
    assertEquals("Bezwaar over verkeer", resultaat.documentSamenvatting());
  }

  @Test
  void stuurtDocumentTekstInUserPrompt() {
    var document = new Brondocument("Mijn bezwaar tekst.", "b.txt", "p", Instant.now());
    when(ingestiePoort.leesBestand(anyString())).thenReturn(document);
    when(chatModel.chat(anyString(), anyString())).thenReturn(FIXTURE_JSON);

    verwerker.verwerk("project", "b.txt", 0);

    verify(chatModel).chat(anyString(), contains("Mijn bezwaar tekst."));
  }

  @Test
  void gooitExceptieBijOngeldigeJson() {
    var document = new Brondocument("tekst", "b.txt", "p", Instant.now());
    when(ingestiePoort.leesBestand(anyString())).thenReturn(document);
    when(chatModel.chat(anyString(), anyString())).thenReturn("geen json");

    assertThrows(RuntimeException.class, () -> verwerker.verwerk("project", "b.txt", 0));
  }
}
```

**Step 2: Run tests om te bevestigen dat ze falen**

```bash
cd app && mvn test -pl . -Dtest=AiExtractieVerwerkerTest -Denforcer.skip=true -Dcheckstyle.skip=true 2>&1 | tail -5
```

Verwacht: COMPILATION ERROR (AiExtractieVerwerker bestaat nog niet)

**Step 3: Implementeer AiExtractieVerwerker**

```java
package be.vlaanderen.omgeving.bezwaarschriften.project;

import be.vlaanderen.omgeving.bezwaarschriften.ingestie.IngestiePoort;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.invoke.MethodHandles;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Extractie-verwerker die een LLM (via {@link ChatModelPoort}) gebruikt om individuele
 * bezwaren te identificeren in bezwaarschriften.
 */
public class AiExtractieVerwerker implements ExtractieVerwerker {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private static final String SYSTEM_PROMPT = """
      Je bent een ervaren ambtenaar bij het Departement Omgeving van de Vlaamse overheid.
      Je analyseert bezwaarschriften die zijn ingediend tijdens een openbaar onderzoek.

      Je taak is om uit het bezwaarschrift alle individuele bezwaren te identificeren.

      ## Wat is een individueel bezwaar?

      Een individueel bezwaar is \u00e9\u00e9n concreet punt van bezwaar dat zelfstandig beantwoord
      kan worden door de vergunningverlenende overheid. Voorbeelden:
      - "De geluidsoverlast door evenementen zal onze nachtrust verstoren" \
      \u2192 \u00e9\u00e9n bezwaar (geluidshinder)
      - "Het verkeer zal toenemen EN er zijn onvoldoende parkeerplaatsen" \
      \u2192 TWEE bezwaren (verkeerslast + parkeertekort), ook al staan ze in dezelfde zin

      Splits passages die meerdere bezwaren bevatten altijd op in afzonderlijke items.
      E\u00e9n passage kan dus leiden tot meerdere bezwaren.

      ## Per bezwaar lever je:

      1. **passage**: De letterlijke tekst uit het bezwaarschrift waaruit dit bezwaar blijkt.
      2. **samenvatting**: E\u00e9n zin die het bezwaar kernachtig beschrijft in je eigen woorden.
      3. **categorie**: E\u00e9n van: milieu, mobiliteit, ruimtelijke_ordening, procedure,
         gezondheid, economisch, sociaal, overig.

      Antwoord UITSLUITEND in het volgende JSON-formaat (geen extra tekst):
      {
        "passages": [{ "id": 1, "tekst": "..." }],
        "bezwaren": [{ "passageId": 1, "samenvatting": "...", "categorie": "..." }],
        "metadata": { "aantalWoorden": 0, "documentSamenvatting": "..." }
      }
      """;

  private static final String USER_PROMPT_TEMPLATE = """
      Context: Openbaar onderzoek voor het project "Herontwikkeling Gaverbeek Stadion"
      in Waregem \u2014 bouw van een multifunctioneel stadion met parking, commerci\u00eble ruimtes
      en publieke groenzones langs de Gaverbeek.

      Analyseer het volgende bezwaarschrift en extraheer alle individuele bezwaren:

      ---
      %s
      ---
      """;

  private final ChatModelPoort chatModel;
  private final IngestiePoort ingestiePoort;
  private final Path inputFolder;
  private final ObjectMapper objectMapper;

  /**
   * Maakt een nieuwe AiExtractieVerwerker aan.
   *
   * @param chatModel port naar het taalmodel
   * @param ingestiePoort port voor bestandsingestie
   * @param inputFolderString root input folder
   */
  public AiExtractieVerwerker(ChatModelPoort chatModel, IngestiePoort ingestiePoort,
      String inputFolderString) {
    this.chatModel = chatModel;
    this.ingestiePoort = ingestiePoort;
    this.inputFolder = Path.of(inputFolderString);
    this.objectMapper = new ObjectMapper();
  }

  @Override
  public ExtractieResultaat verwerk(String projectNaam, String bestandsnaam, int poging) {
    var pad = inputFolder.resolve(projectNaam).resolve("bezwaren").resolve(bestandsnaam);
    var brondocument = ingestiePoort.leesBestand(pad);
    var userPrompt = String.format(USER_PROMPT_TEMPLATE, brondocument.tekst());

    LOGGER.info("Start LLM-extractie voor '{}' (project '{}', poging {})",
        bestandsnaam, projectNaam, poging);

    var response = chatModel.chat(SYSTEM_PROMPT, userPrompt);
    return parseResponse(response);
  }

  private ExtractieResultaat parseResponse(String json) {
    try {
      var root = objectMapper.readTree(json);
      var passages = parsePassages(root.get("passages"));
      var bezwaren = parseBezwaren(root.get("bezwaren"));
      var metadata = root.get("metadata");
      int aantalWoorden = metadata.get("aantalWoorden").asInt();
      String samenvatting = metadata.get("documentSamenvatting").asText();
      return new ExtractieResultaat(aantalWoorden, bezwaren.size(), passages, bezwaren,
          samenvatting);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("Ongeldig JSON-antwoord van LLM: " + e.getMessage(), e);
    }
  }

  private List<Passage> parsePassages(JsonNode node) {
    var lijst = new ArrayList<Passage>();
    if (node != null && node.isArray()) {
      for (var item : node) {
        lijst.add(new Passage(item.get("id").asInt(), item.get("tekst").asText()));
      }
    }
    return List.copyOf(lijst);
  }

  private List<GeextraheerdBezwaar> parseBezwaren(JsonNode node) {
    var lijst = new ArrayList<GeextraheerdBezwaar>();
    if (node != null && node.isArray()) {
      for (var item : node) {
        lijst.add(new GeextraheerdBezwaar(
            item.get("passageId").asInt(),
            item.get("samenvatting").asText(),
            item.get("categorie").asText()));
      }
    }
    return List.copyOf(lijst);
  }
}
```

> **Let op:** De `IngestiePoort.leesBestand()` accepteert momenteel een `Path` parameter.
> Als de huidige signatuur `leesBestand(Path pad)` is, gebruik die. Controleer de
> interface signatuur en pas de mock in de test aan indien nodig (`anyString()` → `any(Path.class)`).

**Step 4: Run tests om te bevestigen dat ze slagen**

```bash
cd app && mvn test -pl . -Dtest=AiExtractieVerwerkerTest -Denforcer.skip=true -Dcheckstyle.skip=true 2>&1 | tail -5
```

Verwacht: BUILD SUCCESS

**Step 5: Commit**

```bash
git add app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/AiExtractieVerwerker.java \
       app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/project/AiExtractieVerwerkerTest.java
git commit -m "feat: AiExtractieVerwerker met LLM-gebaseerde bezwaarextractie"
```

---

### Task 7: Maak FixtureChatModel (TDD)

**Files:**
- Create: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/FixtureChatModel.java`
- Create: `app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/project/FixtureChatModelTest.java`

**Step 1: Schrijf failing tests**

```java
package be.vlaanderen.omgeving.bezwaarschriften.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FixtureChatModelTest {

  @TempDir
  Path tempDir;

  private FixtureChatModel chatModel;

  @BeforeEach
  void setUp() throws IOException {
    // Maak testbestanden aan
    var bezwarenDir = tempDir.resolve("bezwaren");
    Files.createDirectories(bezwarenDir);

    Files.writeString(bezwarenDir.resolve("Bezwaar_01.txt"),
        "Het verkeer zal onhoudbaar toenemen in de Gaverstraat.");
    Files.writeString(bezwarenDir.resolve("Bezwaar_01.json"),
        """
        {"passages":[{"id":1,"tekst":"verkeer"}],"bezwaren":[],\
        "metadata":{"aantalWoorden":8,"documentSamenvatting":"test"}}
        """);

    chatModel = new FixtureChatModel(tempDir.toString());
  }

  @Test
  void retourneertFixtureVoorBekendDocument() {
    var prompt = """
        ---
        Het verkeer zal onhoudbaar toenemen in de Gaverstraat.
        ---
        """;
    var response = chatModel.chat("system prompt", prompt);

    assertTrue(response.contains("\"passages\""));
    assertTrue(response.contains("\"aantalWoorden\":8"));
  }

  @Test
  void gooitExceptieVoorOnbekendDocument() {
    var prompt = """
        ---
        Dit document bestaat niet in de fixtures.
        ---
        """;
    assertThrows(IllegalArgumentException.class,
        () -> chatModel.chat("system prompt", prompt));
  }
}
```

**Step 2: Run tests om te bevestigen dat ze falen**

```bash
cd app && mvn test -pl . -Dtest=FixtureChatModelTest -Denforcer.skip=true -Dcheckstyle.skip=true 2>&1 | tail -5
```

Verwacht: COMPILATION ERROR (FixtureChatModel bestaat nog niet)

**Step 3: Implementeer FixtureChatModel**

```java
package be.vlaanderen.omgeving.bezwaarschriften.project;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fixture-gebaseerde implementatie van {@link ChatModelPoort} die vooraf gegenereerde
 * LLM-antwoorden teruggeeft op basis van documenttekst-matching.
 *
 * <p>Laadt bij initialisatie alle .txt/.json paren uit de testdata-directory.
 * Matcht inkomende prompts door de documenttekst (tussen --- markers) te vergelijken
 * met de geladen .txt bestanden.
 */
public class FixtureChatModel implements ChatModelPoort {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final Map<String, String> fixtures; // trimmed txt content → json content

  /**
   * Laadt fixtures uit de opgegeven testdata-directory.
   *
   * @param testdataPad pad naar de testdata project-directory (bv. testdata/gaverbeek-stadion)
   */
  public FixtureChatModel(String testdataPad) {
    this.fixtures = laadFixtures(Path.of(testdataPad));
    LOGGER.info("FixtureChatModel geladen met {} fixtures", fixtures.size());
  }

  @Override
  public String chat(String systemPrompt, String userPrompt) {
    var documentTekst = extractDocumentTekst(userPrompt).strip();
    var fixtureJson = fixtures.get(documentTekst);
    if (fixtureJson == null) {
      throw new IllegalArgumentException(
          "Geen fixture gevonden voor document (eerste 80 chars): '"
              + documentTekst.substring(0, Math.min(80, documentTekst.length())) + "...'");
    }
    return fixtureJson;
  }

  private Map<String, String> laadFixtures(Path basePad) {
    var result = new HashMap<String, String>();
    var bezwarenDir = basePad.resolve("bezwaren");
    if (!Files.isDirectory(bezwarenDir)) {
      LOGGER.warn("Bezwaren directory niet gevonden: {}", bezwarenDir);
      return result;
    }
    try (var stream = Files.list(bezwarenDir)) {
      stream.filter(p -> p.toString().endsWith(".txt")).forEach(txtPath -> {
        var jsonPath = Path.of(txtPath.toString().replace(".txt", ".json"));
        if (Files.exists(jsonPath)) {
          try {
            var txtContent = Files.readString(txtPath, StandardCharsets.UTF_8).strip();
            var jsonContent = Files.readString(jsonPath, StandardCharsets.UTF_8).strip();
            result.put(txtContent, jsonContent);
          } catch (IOException e) {
            LOGGER.error("Fout bij laden fixture: {}", txtPath, e);
          }
        }
      });
    } catch (IOException e) {
      throw new RuntimeException("Kan bezwaren directory niet lezen: " + bezwarenDir, e);
    }
    return result;
  }

  private String extractDocumentTekst(String userPrompt) {
    int start = userPrompt.indexOf("---");
    int end = userPrompt.lastIndexOf("---");
    if (start == -1 || end == -1 || start == end) {
      throw new IllegalArgumentException("User prompt bevat geen --- markers");
    }
    return userPrompt.substring(start + 3, end);
  }
}
```

**Step 4: Run tests om te bevestigen dat ze slagen**

```bash
cd app && mvn test -pl . -Dtest=FixtureChatModelTest -Denforcer.skip=true -Dcheckstyle.skip=true 2>&1 | tail -5
```

Verwacht: BUILD SUCCESS

**Step 5: Commit**

```bash
git add app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/FixtureChatModel.java \
       app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/project/FixtureChatModelTest.java
git commit -m "feat: FixtureChatModel retourneert pre-computed LLM-antwoorden"
```

---

## Phase 4: Spring-wiring & Integration Test

### Task 8: Wire FixtureChatModel + AiExtractieVerwerker in Spring configuratie

**Files:**
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/MockExtractieVerwerker.java`
- Create: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/config/ExtractieVerwerkerConfig.java`

**Step 1: Voeg @Profile("dev") toe aan MockExtractieVerwerker**

Verander de `@Component` annotatie van `MockExtractieVerwerker` zodat deze alleen actief is in dev-profiel:

```java
@Component
@Profile("dev")
public class MockExtractieVerwerker implements ExtractieVerwerker {
```

**Step 2: Maak configuratie-klasse voor fixture-profiel**

```java
package be.vlaanderen.omgeving.bezwaarschriften.config;

import be.vlaanderen.omgeving.bezwaarschriften.ingestie.IngestiePoort;
import be.vlaanderen.omgeving.bezwaarschriften.project.AiExtractieVerwerker;
import be.vlaanderen.omgeving.bezwaarschriften.project.ChatModelPoort;
import be.vlaanderen.omgeving.bezwaarschriften.project.ExtractieVerwerker;
import be.vlaanderen.omgeving.bezwaarschriften.project.FixtureChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Configuratie voor fixture-gebaseerde extractie in test-modus.
 */
@Configuration
@Profile("fixture")
public class ExtractieVerwerkerConfig {

  @Bean
  ChatModelPoort fixtureChatModel(
      @Value("${bezwaarschriften.testdata.pad}") String testdataPad) {
    return new FixtureChatModel(testdataPad);
  }

  @Bean
  ExtractieVerwerker aiExtractieVerwerker(
      ChatModelPoort chatModel,
      IngestiePoort ingestiePoort,
      @Value("${bezwaarschriften.input.folder}") String inputFolder) {
    return new AiExtractieVerwerker(chatModel, ingestiePoort, inputFolder);
  }
}
```

**Step 3: Voeg testdata configuratie toe aan test application.yml**

Voeg toe aan `app/src/test/resources/application.yml`:

```yaml
bezwaarschriften:
  testdata:
    pad: testdata/gaverbeek-stadion
```

**Step 4: Run alle tests**

```bash
cd app && mvn test -Denforcer.skip=true -Dcheckstyle.skip=true 2>&1 | tail -20
```

Verwacht: Alle tests slagen. Het "fixture" profiel is niet actief in bestaande tests (die draaien op "dev"), dus geen conflict.

**Step 5: Commit**

```bash
git add app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/MockExtractieVerwerker.java \
       app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/config/ExtractieVerwerkerConfig.java \
       app/src/test/resources/application.yml
git commit -m "feat: fixture profiel voor test-configuratie met FixtureChatModel"
```

---

### Task 9: Schrijf integratietest met fixtures

**Files:**
- Create: `app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/project/FixtureExtractieIntegrationTest.java`

**Step 1: Schrijf integratietest**

```java
package be.vlaanderen.omgeving.bezwaarschriften.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Integratietest die verifieert dat AiExtractieVerwerker + FixtureChatModel
 * samen correcte resultaten opleveren voor de synthetische testdata.
 *
 * <p>Draait ZONDER Spring context — test enkel de verwerker + chatmodel samenwerking.
 */
class FixtureExtractieIntegrationTest {

  private static final Path TESTDATA_PAD = Path.of("testdata/gaverbeek-stadion");
  private static final Path BEZWAREN_DIR = TESTDATA_PAD.resolve("bezwaren");

  private AiExtractieVerwerker verwerker;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    // Gebruik testdata/ als zowel input-folder als fixture-bron
    var chatModel = new FixtureChatModel(TESTDATA_PAD.toString());
    var ingestiePoort = new TestdataIngestiePoort();
    verwerker = new AiExtractieVerwerker(chatModel, ingestiePoort, TESTDATA_PAD.toString());
    objectMapper = new ObjectMapper();
  }

  @Test
  void verwerktAlleFixtureBestanden() throws IOException {
    try (var stream = Files.list(BEZWAREN_DIR)) {
      var txtBestanden = stream
          .filter(p -> p.toString().endsWith(".txt"))
          .toList();

      assertFalse(txtBestanden.isEmpty(), "Geen .txt bestanden gevonden in " + BEZWAREN_DIR);

      for (var txtPath : txtBestanden) {
        var bestandsnaam = txtPath.getFileName().toString();
        var resultaat = verwerker.verwerk("", bestandsnaam, 0);

        assertNotNull(resultaat, "Null resultaat voor " + bestandsnaam);
        assertTrue(resultaat.aantalWoorden() > 0,
            "Geen woorden voor " + bestandsnaam);
        assertTrue(resultaat.aantalBezwaren() > 0,
            "Geen bezwaren voor " + bestandsnaam);
        assertFalse(resultaat.passages().isEmpty(),
            "Geen passages voor " + bestandsnaam);
        assertFalse(resultaat.bezwaren().isEmpty(),
            "Geen bezwaren-lijst voor " + bestandsnaam);
        assertNotNull(resultaat.documentSamenvatting(),
            "Geen samenvatting voor " + bestandsnaam);

        // Verifieer dat elke bezwaar verwijst naar een bestaande passage
        var passageIds = resultaat.passages().stream().map(Passage::id).toList();
        for (var bezwaar : resultaat.bezwaren()) {
          assertTrue(passageIds.contains(bezwaar.passageId()),
              "Bezwaar verwijst naar onbestaande passage " + bezwaar.passageId()
                  + " in " + bestandsnaam);
        }
      }
    }
  }

  @Test
  void manifestKloptMetFixtures() throws IOException {
    var manifestPath = TESTDATA_PAD.resolve("manifest.json");
    assertTrue(Files.exists(manifestPath), "manifest.json ontbreekt");

    var manifest = objectMapper.readTree(Files.readString(manifestPath));
    var bestanden = manifest.get("bestanden");
    assertNotNull(bestanden);
    assertTrue(bestanden.isArray());

    for (var entry : bestanden) {
      var txtPad = BEZWAREN_DIR.resolve(
          Path.of(entry.get("txtBestand").asText()).getFileName());
      var jsonPad = BEZWAREN_DIR.resolve(
          Path.of(entry.get("fixtureBestand").asText()).getFileName());
      assertTrue(Files.exists(txtPad), "Ontbrekend txt: " + txtPad);
      assertTrue(Files.exists(jsonPad), "Ontbrekend json: " + jsonPad);
    }
  }

  /**
   * Simpele IngestiePoort die .txt bestanden leest uit testdata/bezwaren/.
   */
  private static class TestdataIngestiePoort
      implements be.vlaanderen.omgeving.bezwaarschriften.ingestie.IngestiePoort {

    @Override
    public be.vlaanderen.omgeving.bezwaarschriften.ingestie.Brondocument leesBestand(
        java.nio.file.Path pad) {
      try {
        var tekst = Files.readString(pad, java.nio.charset.StandardCharsets.UTF_8);
        return new be.vlaanderen.omgeving.bezwaarschriften.ingestie.Brondocument(
            tekst, pad.getFileName().toString(), pad.toString(), java.time.Instant.now());
      } catch (IOException e) {
        throw new RuntimeException("Kan testbestand niet lezen: " + pad, e);
      }
    }
  }
}
```

> **Let op:** De `IngestiePoort.leesBestand()` accepteert een `Path`. De `AiExtractieVerwerker`
> bouwt het pad als `inputFolder/projectNaam/bezwaren/bestandsnaam`. In deze test is de
> projectNaam leeg ("") zodat het pad uitkomt op `testdata/gaverbeek-stadion/bezwaren/bestandsnaam`.
> Pas het pad-opbouw in de verwerker of de test aan als dat niet klopt.

**Step 2: Run de test**

```bash
cd app && mvn test -pl . -Dtest=FixtureExtractieIntegrationTest -Denforcer.skip=true -Dcheckstyle.skip=true 2>&1 | tail -20
```

Verwacht: BUILD SUCCESS (fixtures bestaan uit Phase 1)

**Step 3: Commit**

```bash
git add app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/project/FixtureExtractieIntegrationTest.java
git commit -m "test: integratietest verifieert fixture-pipeline voor alle bezwaarschriften"
```

---

## Phase 5: Afronden

### Task 10: Run volledige test suite en final commit

**Step 1: Run alle tests**

```bash
cd app && mvn test -Denforcer.skip=true -Dcheckstyle.skip=true 2>&1 | tail -20
```

Verwacht: Alle tests slagen, geen regressies.

**Step 2: Run checkstyle**

```bash
cd app && mvn validate -Denforcer.skip=true 2>&1 | tail -20
```

Verwacht: Geen checkstyle-violations. Fix eventuele violations.

**Step 3: Final commit indien nodig**

```bash
git status
# Als er nog ongecommitte wijzigingen zijn:
git add -A && git commit -m "chore: fix checkstyle en test cleanup"
```
