# Obscuro Pseudonimisering Integratie — Implementatieplan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Na tekst-extractie de geextraheerde tekst pseudonimiseren via Obscuro-service en de mapping-ID opslaan voor latere de-pseudonimisering.

**Architecture:** Nieuwe `PseudonimiseringPoort` (port) + `ObscuroAdapter` (adapter) in `tekstextractie` package. `TekstExtractieService.verwerkTaak()` roept de port aan na extractie, vóór opslag. Mapping-ID wordt gepersisteerd in `tekst_extractie_taak` tabel.

**Tech Stack:** Java 21, Spring Boot, `java.net.http.HttpClient` (JDK standaard, synchroon) + OkHttp3 MockWebServer voor testen, Liquibase, Testcontainers, Obscuro Docker image.

**Spec-afwijking:** Spec vermeldt `RestClient`, maar het project draait op Spring Boot 2.7.x (`acd-springboot-parent:2.7.12.0`) waar `RestClient` niet beschikbaar is (geïntroduceerd in Spring Boot 3.2). We gebruiken `java.net.http.HttpClient` — zero-dependency, synchroon, en getest met `MockWebServer` (bestaand patroon in project, zie `WebClientEmbeddingAdapterTest`).

**Spec:** `docs/superpowers/specs/2026-03-11-obscuro-pseudonimisering-design.md`

---

## Chunk 1: Domeinmodel en Port

### Task 1: PseudonimiseringResultaat record

**Files:**
- Create: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/tekstextractie/PseudonimiseringResultaat.java`

- [ ] **Step 1: Schrijf het record**

```java
package be.vlaanderen.omgeving.bezwaarschriften.tekstextractie;

/**
 * Resultaat van een pseudonimisering van tekst.
 *
 * @param gepseudonimiseerdeTekst de tekst met PII vervangen door tokens
 * @param mappingId               UUID voor de-pseudonimisering via Obscuro
 */
public record PseudonimiseringResultaat(
    String gepseudonimiseerdeTekst,
    String mappingId) {
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/tekstextractie/PseudonimiseringResultaat.java
git commit -m "feat: PseudonimiseringResultaat record toevoegen"
```

---

### Task 2: PseudonimiseringPoort interface

**Files:**
- Create: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/tekstextractie/PseudonimiseringPoort.java`

- [ ] **Step 1: Schrijf de port interface**

```java
package be.vlaanderen.omgeving.bezwaarschriften.tekstextractie;

/**
 * Poort voor pseudonimisering van teksten.
 *
 * <p>Vervangt persoonlijk identificeerbare informatie (PII) door generieke tokens.
 * De mapping-ID maakt het mogelijk om de originele tekst later te herstellen.
 */
public interface PseudonimiseringPoort {

  /**
   * Pseudonimiseert de gegeven tekst.
   *
   * @param tekst de te pseudonimiseren tekst
   * @return resultaat met gepseudonimiseerde tekst en mapping-ID
   * @throws PseudonimiseringException als de pseudonimisering mislukt
   */
  PseudonimiseringResultaat pseudonimiseer(String tekst);
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/tekstextractie/PseudonimiseringPoort.java
git commit -m "feat: PseudonimiseringPoort interface toevoegen"
```

---

### Task 3: PseudonimiseringException

**Files:**
- Create: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/tekstextractie/PseudonimiseringException.java`

- [ ] **Step 1: Schrijf de exception**

```java
package be.vlaanderen.omgeving.bezwaarschriften.tekstextractie;

/** Exceptie wanneer pseudonimisering via Obscuro mislukt. */
public class PseudonimiseringException extends RuntimeException {

  public PseudonimiseringException(String message) {
    super(message);
  }

  public PseudonimiseringException(String message, Throwable cause) {
    super(message, cause);
  }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/tekstextractie/PseudonimiseringException.java
git commit -m "feat: PseudonimiseringException toevoegen"
```

---

### Task 4: Database migratie — mapping-ID kolom

**Files:**
- Create: `app/src/main/resources/config/liquibase/changelog/20260311-pseudonimisering-mapping.xml`
- Modify: `app/src/main/resources/config/liquibase/master.xml`

- [ ] **Step 1: Schrijf het Liquibase changelog**

Maak bestand `app/src/main/resources/config/liquibase/changelog/20260311-pseudonimisering-mapping.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<databaseChangeLog
    xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-3.1.xsd">

  <changeSet id="20260311-1" author="bezwaarschriften">
    <addColumn tableName="tekst_extractie_taak">
      <column name="pseudonimisering_mapping_id" type="varchar(255)"/>
    </addColumn>
  </changeSet>

</databaseChangeLog>
```

- [ ] **Step 2: Registreer in master.xml**

Voeg toe als **laatste** `<include>` in `app/src/main/resources/config/liquibase/master.xml` (na `20260311-bezwaar-bestand.xml`):

```xml
  <include file="config/liquibase/changelog/20260311-pseudonimisering-mapping.xml"/>
```

- [ ] **Step 3: Voeg veld toe aan TekstExtractieTaak entiteit**

In `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/tekstextractie/TekstExtractieTaak.java`, voeg toe na het `afgerondOp` veld:

```java
  @Column(name = "pseudonimisering_mapping_id")
  private String pseudonimiseringMappingId;
```

En voeg getter/setter toe na de `afgerondOp` getter/setter:

```java
  public String getPseudonimiseringMappingId() {
    return pseudonimiseringMappingId;
  }

  public void setPseudonimiseringMappingId(String pseudonimiseringMappingId) {
    this.pseudonimiseringMappingId = pseudonimiseringMappingId;
  }
```

- [ ] **Step 4: Commit**

```bash
git add app/src/main/resources/config/liquibase/changelog/20260311-pseudonimisering-mapping.xml \
       app/src/main/resources/config/liquibase/master.xml \
       app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/tekstextractie/TekstExtractieTaak.java
git commit -m "feat: pseudonimisering_mapping_id kolom toevoegen aan tekst_extractie_taak"
```

---

## Chunk 2: ObscuroAdapter met java.net.http.HttpClient

### Task 5: PseudonimiseringConfig

**Files:**
- Create: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/tekstextractie/PseudonimiseringConfig.java`

- [ ] **Step 1: Schrijf de config class**

Volgt het patroon van `EmbeddingConfig` (field injection via setters, `@ConfigurationProperties`):

```java
package be.vlaanderen.omgeving.bezwaarschriften.tekstextractie;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Configuratie voor de Obscuro pseudonimiseringsservice. */
@Component
@ConfigurationProperties(prefix = "bezwaarschriften.pseudonimisering")
public class PseudonimiseringConfig {

  private String url = "http://localhost:8000";
  private int ttlSeconds = 31536000;
  private int connectTimeoutMs = 30000;
  private int readTimeoutMs = 120000;

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public int getTtlSeconds() {
    return ttlSeconds;
  }

  public void setTtlSeconds(int ttlSeconds) {
    this.ttlSeconds = ttlSeconds;
  }

  public int getConnectTimeoutMs() {
    return connectTimeoutMs;
  }

  public void setConnectTimeoutMs(int connectTimeoutMs) {
    this.connectTimeoutMs = connectTimeoutMs;
  }

  public int getReadTimeoutMs() {
    return readTimeoutMs;
  }

  public void setReadTimeoutMs(int readTimeoutMs) {
    this.readTimeoutMs = readTimeoutMs;
  }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/tekstextractie/PseudonimiseringConfig.java
git commit -m "feat: PseudonimiseringConfig toevoegen"
```

---

### Task 6: ObscuroAdapter — test eerst

**Files:**
- Create: `app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/tekstextractie/ObscuroAdapterTest.java`
- Create: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/tekstextractie/ObscuroAdapter.java`

- [ ] **Step 1: Schrijf de failing tests**

Volgt het patroon van `WebClientEmbeddingAdapterTest` met `MockWebServer`:

```java
package be.vlaanderen.omgeving.bezwaarschriften.tekstextractie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ObscuroAdapterTest {

  private MockWebServer mockServer;
  private ObscuroAdapter adapter;

  @BeforeEach
  void setUp() throws IOException {
    mockServer = new MockWebServer();
    mockServer.start();

    var config = new PseudonimiseringConfig();
    config.setUrl(mockServer.url("/").toString());
    config.setTtlSeconds(31536000);
    config.setConnectTimeoutMs(5000);
    config.setReadTimeoutMs(5000);

    adapter = new ObscuroAdapter(config);
  }

  @AfterEach
  void tearDown() throws IOException {
    mockServer.shutdown();
  }

  @Test
  void pseudonimiseertTekstSuccesvol() throws InterruptedException {
    mockServer.enqueue(new MockResponse()
        .setBody("{\"text\":\"Jan woont in {adres_1} en zijn IBAN is {rekeningnummer_1}.\","
            + "\"mapping_id\":\"550e8400-e29b-41d4-a716-446655440000\"}")
        .addHeader("Content-Type", "application/json"));

    var resultaat = adapter.pseudonimiseer(
        "Jan woont in Gent en zijn IBAN is BE12 3456 7890 1234.");

    assertThat(resultaat.gepseudonimiseerdeTekst())
        .contains("{adres_1}")
        .contains("{rekeningnummer_1}")
        .doesNotContain("Gent")
        .doesNotContain("BE12");
    assertThat(resultaat.mappingId())
        .isEqualTo("550e8400-e29b-41d4-a716-446655440000");

    RecordedRequest request = mockServer.takeRequest();
    assertThat(request.getPath()).isEqualTo("/pseudonymize");
    assertThat(request.getBody().readUtf8()).contains("\"ttl_seconds\":31536000");
  }

  @Test
  void gooitExceptieBijHttpFout() {
    mockServer.enqueue(new MockResponse()
        .setResponseCode(500)
        .setBody("{\"detail\":\"Internal server error\"}"));

    assertThatThrownBy(() -> adapter.pseudonimiseer("test tekst"))
        .isInstanceOf(PseudonimiseringException.class)
        .hasMessageContaining("500");
  }

  @Test
  void gooitExceptieBijOnbereikbareService() throws IOException {
    mockServer.shutdown();

    assertThatThrownBy(() -> adapter.pseudonimiseer("test tekst"))
        .isInstanceOf(PseudonimiseringException.class);
  }
}
```

- [ ] **Step 2: Run tests, verifieer dat ze falen**

```bash
cd /Users/kenzo/Library/CloudStorage/Dropbox/1-Kenzo/4-Coding/bezwaarschriften
mvn test -pl app -Dtest=ObscuroAdapterTest -DfailIfNoTests=false
```

Verwacht: FAIL — `ObscuroAdapter` class bestaat nog niet.

- [ ] **Step 3: Schrijf de ObscuroAdapter implementatie**

```java
package be.vlaanderen.omgeving.bezwaarschriften.tekstextractie;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Adapter die teksten pseudonimiseert via de Obscuro REST API.
 *
 * <p>Vervangt PII (namen, adressen, IBAN, etc.) door generieke tokens.
 * Gebruikt Java HttpClient voor synchrone HTTP-calls.
 */
@Component
public class ObscuroAdapter implements PseudonimiseringPoort {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final PseudonimiseringConfig config;
  private final ObjectMapper objectMapper;
  private final HttpClient httpClient;

  public ObscuroAdapter(PseudonimiseringConfig config) {
    this.config = config;
    this.objectMapper = new ObjectMapper();
    this.httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofMillis(config.getConnectTimeoutMs()))
        .build();
  }

  @Override
  public PseudonimiseringResultaat pseudonimiseer(String tekst) {
    try {
      var body = objectMapper.writeValueAsString(Map.of(
          "text", tekst,
          "ttl_seconds", config.getTtlSeconds()));

      var request = HttpRequest.newBuilder()
          .uri(URI.create(config.getUrl() + "/pseudonymize"))
          .header("Content-Type", "application/json")
          .timeout(Duration.ofMillis(config.getReadTimeoutMs()))
          .POST(HttpRequest.BodyPublishers.ofString(body))
          .build();

      var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() != 200) {
        throw new PseudonimiseringException(
            "Obscuro retourneerde HTTP " + response.statusCode() + ": " + response.body());
      }

      var json = objectMapper.readTree(response.body());
      var gepseudonimiseerdeTekst = json.get("text").asText();
      var mappingId = json.get("mapping_id").asText();

      LOGGER.info("Tekst gepseudonimiseerd (mapping={})", mappingId);
      return new PseudonimiseringResultaat(gepseudonimiseerdeTekst, mappingId);

    } catch (PseudonimiseringException e) {
      throw e;
    } catch (Exception e) {
      throw new PseudonimiseringException(
          "Pseudonimisering mislukt: " + e.getMessage(), e);
    }
  }
}
```

**Opmerking:** We gebruiken `java.net.http.HttpClient` (standaard JDK) i.p.v. `RestTemplate` of `WebClient`. Dit heeft geen extra dependencies nodig en is synchroon — ideaal voor deze use case.

- [ ] **Step 4: Run tests, verifieer dat ze slagen**

```bash
mvn test -pl app -Dtest=ObscuroAdapterTest
```

Verwacht: 3 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/tekstextractie/ObscuroAdapterTest.java \
       app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/tekstextractie/ObscuroAdapter.java
git commit -m "feat: ObscuroAdapter implementatie met unit tests"
```

---

## Chunk 3: TekstExtractieService aanpassen

### Task 7: TekstExtractieService uitbreiden met pseudonimisering

**Files:**
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/tekstextractie/TekstExtractieService.java`
- Modify: `app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/tekstextractie/TekstExtractieServiceTest.java`

- [ ] **Step 1: Voeg nieuwe test toe voor PDF + pseudonimisering**

Voeg toe aan `TekstExtractieServiceTest.java`:

1. Voeg een `@Mock PseudonimiseringPoort pseudonimiseringPoort;` veld toe na de bestaande mocks.
2. Pas `setUp()` aan om de nieuwe mock mee te geven aan de constructor.
3. Voeg deze test toe:

```java
  @Test
  void verwerkTaak_pseudonimiseertPdfTekstVoorOpslag() throws IOException {
    var taak = maakTaak(1L, "windmolens", "bezwaar.pdf", TekstExtractieTaakStatus.BEZIG);
    var pad = Path.of("/tmp/bezwaar.pdf");
    when(projectPoort.geefBestandsPad("windmolens", "bezwaar.pdf")).thenReturn(pad);
    when(pdfExtractor.extraheer(pad)).thenReturn(
        new TekstExtractieResultaat("Jan uit Gent", ExtractieMethode.DIGITAAL, "OK"));
    when(pseudonimiseringPoort.pseudonimiseer("Jan uit Gent")).thenReturn(
        new PseudonimiseringResultaat("{persoon_1} uit {adres_1}", "mapping-uuid-123"));
    when(repository.findById(1L)).thenReturn(Optional.of(taak));
    when(repository.save(any(TekstExtractieTaak.class))).thenAnswer(i -> i.getArgument(0));

    service.verwerkTaak(taak);

    // Gepseudonimiseerde tekst wordt opgeslagen, niet de originele
    verify(projectPoort).slaTekstOp("windmolens", "bezwaar.pdf", "{persoon_1} uit {adres_1}");
    assertThat(taak.getPseudonimiseringMappingId()).isEqualTo("mapping-uuid-123");
    assertThat(taak.getStatus()).isEqualTo(TekstExtractieTaakStatus.KLAAR);
  }
```

4. Voeg test toe voor TXT-pad:

```java
  @Test
  void verwerkTaak_pseudonimiseertTxtTekstVoorOpslag(@TempDir Path tempDir) throws IOException {
    var txtBestand = tempDir.resolve("bezwaar.txt");
    var tekst = "Maria uit Antwerpen " + "heeft een bezwaar ".repeat(10);
    Files.writeString(txtBestand, tekst);

    var taak = maakTaak(1L, "windmolens", "bezwaar.txt", TekstExtractieTaakStatus.BEZIG);
    when(projectPoort.geefBestandsPad("windmolens", "bezwaar.txt")).thenReturn(txtBestand);
    when(kwaliteitsControle.controleer(tekst))
        .thenReturn(TekstKwaliteitsControle.Resultaat.valide());
    when(pseudonimiseringPoort.pseudonimiseer(tekst)).thenReturn(
        new PseudonimiseringResultaat("{persoon_1} uit {adres_1}", "mapping-uuid-456"));
    when(repository.findById(1L)).thenReturn(Optional.of(taak));
    when(repository.save(any(TekstExtractieTaak.class))).thenAnswer(i -> i.getArgument(0));

    service.verwerkTaak(taak);

    verify(projectPoort).slaTekstOp("windmolens", "bezwaar.txt", "{persoon_1} uit {adres_1}");
    assertThat(taak.getPseudonimiseringMappingId()).isEqualTo("mapping-uuid-456");
  }
```

5. Voeg test toe voor pseudonimisering-fout:

```java
  @Test
  void verwerkTaak_pseudonimiseringFoutWordtMislukt() throws IOException {
    var taak = maakTaak(1L, "windmolens", "bezwaar.pdf", TekstExtractieTaakStatus.BEZIG);
    var pad = Path.of("/tmp/bezwaar.pdf");
    when(projectPoort.geefBestandsPad("windmolens", "bezwaar.pdf")).thenReturn(pad);
    when(pdfExtractor.extraheer(pad)).thenReturn(
        new TekstExtractieResultaat("tekst", ExtractieMethode.DIGITAAL, "OK"));
    when(pseudonimiseringPoort.pseudonimiseer("tekst"))
        .thenThrow(new PseudonimiseringException("Obscuro onbereikbaar"));
    when(repository.findById(1L)).thenReturn(Optional.of(taak));
    when(repository.save(any(TekstExtractieTaak.class))).thenAnswer(i -> i.getArgument(0));

    service.verwerkTaak(taak);

    assertThat(taak.getStatus()).isEqualTo(TekstExtractieTaakStatus.MISLUKT);
    assertThat(taak.getFoutmelding()).contains("Obscuro onbereikbaar");
    verify(projectPoort, never()).slaTekstOp(anyString(), anyString(), anyString());
  }
```

- [ ] **Step 2: Run tests, verifieer dat ze falen**

```bash
mvn test -pl app -Dtest=TekstExtractieServiceTest
```

Verwacht: FAIL — constructor mismatch + ontbrekende pseudonimisering-aanroep.

- [ ] **Step 3: Pas TekstExtractieService aan**

Wijzigingen in `TekstExtractieService.java`:

**a) Voeg PseudonimiseringPoort toe als dependency:**

Voeg `private final PseudonimiseringPoort pseudonimiseringPoort;` toe na het `projectPoort` veld.

Pas de constructor aan — voeg parameter toe:

```java
  public TekstExtractieService(
      TekstExtractieTaakRepository repository,
      PdfTekstExtractor pdfExtractor,
      TekstKwaliteitsControle kwaliteitsControle,
      ProjectPoort projectPoort,
      PseudonimiseringPoort pseudonimiseringPoort,
      @Value("${bezwaarschriften.tekst-extractie.max-concurrent:2}") int maxConcurrent) {
    this.repository = repository;
    this.pdfExtractor = pdfExtractor;
    this.kwaliteitsControle = kwaliteitsControle;
    this.projectPoort = projectPoort;
    this.pseudonimiseringPoort = pseudonimiseringPoort;
    this.maxConcurrent = maxConcurrent;
  }
```

**b) Pas `verwerkTaak()` aan — pseudonimiseer na extractie, sla mappingId op vóór tekst:**

Vervang het PDF-blok:

```java
      if (bestandsnaam.endsWith(".pdf")) {
        var resultaat = pdfExtractor.extraheer(pad);
        var pseudonimisering = pseudonimiseringPoort.pseudonimiseer(resultaat.tekst());
        slaOpMetMapping(taak, pseudonimisering, resultaat.methode());
```

Vervang het TXT-blok (het `else if`):

```java
      } else if (bestandsnaam.endsWith(".txt")) {
        var tekst = Files.readString(pad);
        var controle = kwaliteitsControle.controleer(tekst);
        if (!controle.isValide()) {
          markeerMislukt(taak.getId(),
              "Kwaliteitscontrole mislukt: " + controle.reden());
          return;
        }
        var pseudonimisering = pseudonimiseringPoort.pseudonimiseer(tekst);
        slaOpMetMapping(taak, pseudonimisering, ExtractieMethode.DIGITAAL);
```

**c) Voeg `PseudonimiseringException` toe aan de catch-blokken** (na `OcrNietBeschikbaarException`):

```java
    } catch (PseudonimiseringException e) {
      LOGGER.error("Pseudonimisering mislukt voor taak {}: {}", taak.getId(), e.getMessage(), e);
      markeerMislukt(taak.getId(), e.getMessage());
```

**d) Voeg helper-methode toe** (na `verwerkTaak`):

```java
  private void slaOpMetMapping(TekstExtractieTaak taak,
      PseudonimiseringResultaat pseudonimisering, ExtractieMethode methode) {
    // Sla mappingId op in het taak-object (wordt meegenomen in markeerKlaar)
    taak.setPseudonimiseringMappingId(pseudonimisering.mappingId());
    // Sla gepseudonimiseerde tekst op naar disk
    projectPoort.slaTekstOp(taak.getProjectNaam(), taak.getBestandsnaam(),
        pseudonimisering.gepseudonimiseerdeTekst());
    markeerKlaar(taak.getId(), methode);
  }
```

**e) Pas `markeerKlaar` aan zodat het mappingId behouden blijft:**

De huidige `markeerKlaar` doet `repository.findById(taakId)` wat het in-memory object vervangt. Wijzig `markeerKlaar` zodat het het bestaande taak-object accepteert i.p.v. opnieuw op te halen, of voeg de mapping-ID door na de findById:

Eenvoudigste aanpak — pas `markeerKlaar` aan met een extra parameter:

```java
  @Transactional
  public void markeerKlaar(Long taakId, ExtractieMethode methode) {
    var taak = repository.findById(taakId)
        .orElseThrow(() -> new IllegalArgumentException("Taak niet gevonden: " + taakId));
    taak.setStatus(TekstExtractieTaakStatus.KLAAR);
    taak.setExtractieMethode(methode);
    taak.setAfgerondOp(Instant.now());
    repository.save(taak);
    LOGGER.info("Tekst-extractie taak {} afgerond (methode={})", taakId, methode);
  }
```

**Let op:** `markeerKlaar` haalt de taak opnieuw op via `findById`. Omdat `slaOpMetMapping` het mappingId al op het taak-object heeft gezet en `verwerkTaak` niet `@Transactional` is, moet het mappingId eerst naar de DB geflusht zijn. Daarom voegen we een `repository.save(taak)` toe **vóór** de `markeerKlaar` call:

```java
  private void slaOpMetMapping(TekstExtractieTaak taak,
      PseudonimiseringResultaat pseudonimisering, ExtractieMethode methode) {
    taak.setPseudonimiseringMappingId(pseudonimisering.mappingId());
    repository.save(taak);  // flush mappingId naar DB
    projectPoort.slaTekstOp(taak.getProjectNaam(), taak.getBestandsnaam(),
        pseudonimisering.gepseudonimiseerdeTekst());
    markeerKlaar(taak.getId(), methode);
  }
```

- [ ] **Step 4: Pas ALLE bestaande tests aan**

**Pas de setUp() aan:**

```java
  @BeforeEach
  void setUp() {
    service = new TekstExtractieService(
        repository, pdfExtractor, kwaliteitsControle, projectPoort,
        pseudonimiseringPoort, 2);
  }
```

**Voeg pseudonimisering mock toe aan alle tests die `verwerkTaak` aanroepen met een succesvol extractiepad.** Audit elk van de bestaande verwerkTaak-tests:

In `verwerkTaak_verwerktPdfSuccesvol`:
```java
    when(pseudonimiseringPoort.pseudonimiseer("Geextraheerde tekst")).thenReturn(
        new PseudonimiseringResultaat("Geextraheerde tekst", "stub-mapping-id"));
```

In `verwerkTaak_verwerktTxtSuccesvol`:
```java
    when(pseudonimiseringPoort.pseudonimiseer(tekst)).thenReturn(
        new PseudonimiseringResultaat(tekst, "stub-mapping-id"));
```

**Tests die GEEN pseudonimisering-mock nodig hebben** (fout treedt op vóór pseudonimisering):
- `verwerkTaak_txtMetOnvoldoendeKwaliteitWordtMislukt` — stopt bij kwaliteitscontrole
- `verwerkTaak_ocrNietBeschikbaarWordtCorrectAfgehandeld` — gooit exception in pdfExtractor
- `verwerkTaak_onverwachteFoutWordtMislukt` — gooit IOException in pdfExtractor
- `verwerkTaak_nietOndersteundBestandstype` — stopt bij bestandstype-check

Deze tests raken de pseudonimisering-call niet en hoeven niet aangepast te worden.

- [ ] **Step 5: Run alle tests**

```bash
mvn test -pl app -Dtest=TekstExtractieServiceTest
```

Verwacht: alle tests PASS (bestaande + 3 nieuwe).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/tekstextractie/TekstExtractieService.java \
       app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/tekstextractie/TekstExtractieServiceTest.java
git commit -m "feat: pseudonimisering integreren in TekstExtractieService"
```

---

## Chunk 4: Configuratie en Docker

### Task 8: Application configuratie

**Files:**
- Modify: `app/src/main/resources/application.yml`
- Modify: `app/src/main/resources/application-dev.yml`

- [ ] **Step 1: Voeg pseudonimisering config toe aan application.yml**

Voeg toe onder de bestaande `bezwaarschriften:` sectie:

```yaml
  pseudonimisering:
    url: http://localhost:8000
    ttl-seconds: 31536000
    connect-timeout-ms: 30000
    read-timeout-ms: 120000
```

- [ ] **Step 2: Voeg eventueel dev-specifieke overrides toe aan application-dev.yml**

Alleen nodig als de dev-URL afwijkt. Standaard is `http://localhost:8000` al correct voor lokaal. Geen wijziging nodig.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/resources/application.yml
git commit -m "feat: pseudonimisering configuratie toevoegen aan application.yml"
```

---

### Task 9: Docker Compose

**Files:**
- Create: `docker-compose.yml` (project root)

- [ ] **Step 1: Maak docker-compose.yml aan**

Controleer eerst of er een bestaande docker-compose.yml is (verwacht: nee). Maak dan aan in de project root:

```yaml
services:
  postgres:
    image: pgvector/pgvector:pg17
    ports:
      - "5432:5432"
    environment:
      POSTGRES_DB: bezwaarschriften
      POSTGRES_USER: bezwaarschriften_user
      POSTGRES_PASSWORD: bezwaarschriften_password
    volumes:
      - postgres-data:/var/lib/postgresql/data

  obscuro:
    image: ghcr.io/kevcraey/obscuro-service:latest
    ports:
      - "8000:8000"
    environment:
      - PYTHONUNBUFFERED=1
    restart: unless-stopped
    healthcheck:
      test: python -c "import urllib.request; urllib.request.urlopen('http://localhost:8000/health')"
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 60s

volumes:
  postgres-data:
```

- [ ] **Step 2: Test dat de containers opstarten**

```bash
docker compose up -d
docker compose ps
# Verifieer dat beide containers "running" zijn
# Verifieer Obscuro health:
curl -s http://localhost:8000/health
docker compose down
```

- [ ] **Step 3: Commit**

```bash
git add docker-compose.yml
git commit -m "feat: docker-compose.yml met PostgreSQL en Obscuro"
```

---

## Chunk 5: E2E Integratietest

### Task 10: E2E test — volledige happy path

**Files:**
- Create: `app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/tekstextractie/TekstExtractiePseudonimiseringIntegrationTest.java`

- [ ] **Step 1: Schrijf de E2E integratietest**

Test maakt programmatisch een PDF aan (PDFBox dependency is er al).
Gebruikt een **static** temp-directory zodat `@DynamicPropertySource` het input-folder pad kan injecteren.

```java
package be.vlaanderen.omgeving.bezwaarschriften.tekstextractie;

import static org.assertj.core.api.Assertions.assertThat;

import be.vlaanderen.omgeving.bezwaarschriften.BaseBezwaarschriftenIntegrationTest;
import be.vlaanderen.omgeving.bezwaarschriften.project.ProjectPoort;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * E2E integratietest: upload PDF → tekst-extractie → pseudonimisering → verificatie.
 *
 * <p>Gebruikt een echte Obscuro container via Testcontainers.
 */
@Testcontainers
class TekstExtractiePseudonimiseringIntegrationTest
    extends BaseBezwaarschriftenIntegrationTest {

  private static Path testInputDir;

  @Container
  static final GenericContainer<?> obscuro =
      new GenericContainer<>("ghcr.io/kevcraey/obscuro-service:latest")
          .withExposedPorts(8000)
          .waitingFor(Wait.forHttp("/health").forStatusCode(200))
          .withStartupTimeout(Duration.ofMinutes(3));

  @BeforeAll
  static void setUpTestDir() throws IOException {
    testInputDir = Files.createTempDirectory("e2e-pseudonimisering");
    testInputDir.toFile().deleteOnExit();
  }

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    registry.add("bezwaarschriften.pseudonimisering.url",
        () -> "http://" + obscuro.getHost() + ":" + obscuro.getMappedPort(8000));
    registry.add("bezwaarschriften.input.folder", () -> testInputDir.toString());
  }

  @Autowired
  private TekstExtractieService tekstExtractieService;

  @Autowired
  private TekstExtractieTaakRepository repository;

  @Autowired
  private ProjectPoort projectPoort;

  @Test
  void volledigeHappyPath_pdfMetPiiWordtGepseudonimiseerd() throws Exception {
    // 1. Maak test-PDF aan met bekende PII
    var pdfBytes = maakTestPdfMetPii(
        "Geachte heer Thomas De Smedt, "
        + "wonende te Kerkstraat 12 in 9000 Gent. "
        + "Uw rekeningnummer BE68 5390 0754 7034 is genoteerd.");
    var bestandsnaam = "bezwaar-pii.pdf";
    var projectNaam = "e2e-test-" + System.currentTimeMillis();

    // 2. Sla het bestand op in de project-input map
    var origDir = testInputDir.resolve(projectNaam).resolve("bezwaren-orig");
    Files.createDirectories(origDir);
    var tekstDir = testInputDir.resolve(projectNaam).resolve("bezwaren-text");
    Files.createDirectories(tekstDir);
    Files.write(origDir.resolve(bestandsnaam), pdfBytes);

    // 3. Dien de extractie-taak in
    var taak = tekstExtractieService.indienen(projectNaam, bestandsnaam);
    assertThat(taak.getStatus()).isEqualTo(TekstExtractieTaakStatus.WACHTEND);

    // 4. Verwerk de taak (synchroon, niet via worker)
    var opgehaald = repository.findById(taak.getId()).orElseThrow();
    opgehaald.setStatus(TekstExtractieTaakStatus.BEZIG);
    repository.save(opgehaald);
    tekstExtractieService.verwerkTaak(opgehaald);

    // 5. Verifieer resultaat
    var bijgewerkt = repository.findById(taak.getId()).orElseThrow();

    // Status is KLAAR
    assertThat(bijgewerkt.getStatus()).isEqualTo(TekstExtractieTaakStatus.KLAAR);

    // Mapping-ID is opgeslagen
    assertThat(bijgewerkt.getPseudonimiseringMappingId()).isNotNull();
    assertThat(bijgewerkt.getPseudonimiseringMappingId()).isNotEmpty();

    // Extractiemethode is ingevuld
    assertThat(bijgewerkt.getExtractieMethode()).isEqualTo(ExtractieMethode.DIGITAAL);

    // De opgeslagen tekst bevat GEEN originele PII meer
    var tekstPad = projectPoort.geefTekstBestandsPad(projectNaam, bestandsnaam);
    var opgeslagenTekst = Files.readString(tekstPad);
    assertThat(opgeslagenTekst)
        .doesNotContain("BE68 5390 0754 7034")
        .doesNotContain("BE68539007547034");
  }

  private byte[] maakTestPdfMetPii(String tekst) throws IOException {
    try (var document = new PDDocument()) {
      var page = new PDPage();
      document.addPage(page);
      try (var contentStream = new PDPageContentStream(document, page)) {
        contentStream.beginText();
        contentStream.setFont(
            new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
        contentStream.newLineAtOffset(50, 700);
        var woorden = tekst.split(" ");
        var regel = new StringBuilder();
        for (var woord : woorden) {
          if (regel.length() + woord.length() > 70) {
            contentStream.showText(regel.toString());
            contentStream.newLineAtOffset(0, -15);
            regel = new StringBuilder();
          }
          if (regel.length() > 0) regel.append(" ");
          regel.append(woord);
        }
        if (regel.length() > 0) {
          contentStream.showText(regel.toString());
        }
        contentStream.endText();
      }
      var baos = new ByteArrayOutputStream();
      document.save(baos);
      return baos.toByteArray();
    }
  }
}
```

- [ ] **Step 3: Run de E2E test**

```bash
mvn verify -pl app -Dtest=TekstExtractiePseudonimiseringIntegrationTest -DfailIfNoTests=false
```

Verwacht: PASS. De test kan 1-3 minuten duren (Obscuro container opstarten + SpaCy model laden).

- [ ] **Step 4: Fix eventuele issues**

De E2E test zal waarschijnlijk aanpassingen nodig hebben voor:
- De juiste manier om `ProjectPoort.geefBestandsPad()` te laten wijzen naar het temp-directory
- De juiste manier om `projectPoort.geefTekst()` aan te roepen (controleer of deze methode bestaat)
- Eventuele timing/startup issues met de Obscuro container

Pas de test aan totdat hij groen is.

- [ ] **Step 5: Run de volledige test suite**

```bash
mvn test -pl app
mvn verify -pl app
```

Verwacht: alle bestaande tests + nieuwe tests PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/tekstextractie/TekstExtractiePseudonimiseringIntegrationTest.java
git commit -m "feat: E2E integratietest voor pseudonimisering happy path"
```

---

## Chunk 6: Verificatie en oplevering

### Task 11: Volledige build verificatie

- [ ] **Step 1: Run complete build met alle tests**

```bash
mvn clean install -pl app
```

Verwacht: BUILD SUCCESS

- [ ] **Step 2: Verifieer docker-compose**

```bash
docker compose up -d
# Wacht tot containers gezond zijn
docker compose ps
curl -s http://localhost:8000/health
docker compose down
```

- [ ] **Step 3: Verifieer de feature handmatig (optioneel)**

```bash
docker compose up -d
mvn spring-boot:run -pl app -Pdev
# Upload een PDF via de UI en controleer of de tekst in bezwaren-text/ gepseudonimiseerd is
```

### Task 12: C4 documentatie bijwerken

- [ ] **Step 1: Update C4 C2 container diagram**

Voeg Obscuro als externe container toe aan `docs/c4-c2-containers.md`.

- [ ] **Step 2: Commit**

```bash
git add docs/
git commit -m "docs: C4 C2 diagram bijwerken met Obscuro container"
```
