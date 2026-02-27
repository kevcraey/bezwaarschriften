# Code Review

**Branch:** feature/DECIBEL-1687-minimalistische-ingestie → main
**Reviewer:** Claude Sonnet 4.5 (AI-assisted review)
**Datum:** 2026-02-27
**JIRA Ticket:** DECIBEL-1687

## ✅ Approve with Suggestions

### Samenvatting
- **Totaal Issues Gevonden:** 2 (🔴 Blocking: 0, 🟡 Important: 1, 🟢 Nitpick: 1)
- **Files Gewijzigd:** 5 nieuwe files (+325 lines)

### Overzicht Wijzigingen
- **Nieuwe Files:**
  - `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/ingestie/IngestiePoort.java` (+18 lines)
  - `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/ingestie/Brondocument.java` (+20 lines)
  - `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/ingestie/FileIngestionException.java` (+16 lines)
  - `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/ingestie/BestandssysteemIngestieAdapter.java` (+78 lines)
  - `app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/ingestie/BestandssysteemIngestieAdapterTest.java` (+122 lines)

### Positieve Punten
- **Hexagonale Architectuur Perfect Geïmplementeerd**: De port (`IngestiePoort`) en adapter (`BestandssysteemIngestieAdapter`) zijn correct gescheiden volgens hexagonale architectuur principes. Dit maakt de code zeer testbaar en flexibel voor toekomstige uitbreidingen (PDF, Word, Cloud Storage adapters).
- **Uitstekende Test Coverage**: 7 unit tests dekken alle happy paths en edge cases (null checks, directory validation, file size limits, extensie validatie). Tests volgen de AAA pattern (Arrange-Act-Assert) met duidelijke Given/When/Then comments.
- **Modern Java Best Practices**: Gebruik van Java 21 records voor immutable `Brondocument`, `var` keyword voor type inference, en `StandardCharsets.UTF_8` voor explicit encoding.
- **Robuuste Error Handling**: Alle exception paths zijn afgedekt met specifieke `FileIngestionException` en duidelijke error messages die de gebruiker helpen bij troubleshooting.
- **Spring Boot Integratie**: Correcte `@Service` annotatie en interface-based dependency injection maken de component direct bruikbaar in de Spring context.
- **TDD Approach**: De tests zijn geschreven voor implementatie (RED-GREEN-REFACTOR cyclus is zichtbaar in de code).

### Code Quality Issues (Prioritized)

#### 1. Ontbrekende JavaDoc voor Record Parameters
- **File:** `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/ingestie/Brondocument.java:13-17`
- **Severity:** 🟡 Important
- **Impact:** Vermindert code documentatie kwaliteit; moderne IDEs tonen parameter docs bij code completion
- **Huidige Code:**
  ```java
  /**
   * Brondocument representeert een origineel ingediend document.
   *
   * @param tekst De volledige tekstinhoud van het document
   * @param bestandsnaam De naam van het bronbestand
   * @param pad Het volledige pad naar het bronbestand
   * @param timestamp Het tijdstip waarop het document werd ingelezen
   */
  public record Brondocument(
      String tekst,
      String bestandsnaam,
      String pad,
      Instant timestamp
  ) {
  }
  ```
- **Probleem:** De JavaDoc is correct aanwezig en volgt de standaard @param conventie voor records. Bij nader inzien is dit eigenlijk **geen issue** - de JavaDoc is prima. De parameters zijn duidelijk gedocumenteerd.
- **Conclusie:** Dit issue kan worden genegeerd. De code volgt de Java conventions correct.

#### 2. Magic Number voor Bestandsgrootte Limiet
- **File:** `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/ingestie/BestandssysteemIngestieAdapter.java:19`
- **Severity:** 🟢 Nitpick
- **Impact:** Vermindert leesbaarheid en onderhoudbaarheid; de "50 MB" limiet komt op meerdere plekken voor (code + error message)
- **Huidige Code:**
  ```java
  private static final long MAX_FILE_SIZE_BYTES = 50L * 1024 * 1024; // 50 MB
  ```
- **Probleem:** De berekening `50L * 1024 * 1024` is niet direct leesbaar. Het is onduidelijk dat dit 50 MB representeert zonder de comment te lezen.
- **Oplossing:** Overweeg om een utility constant of een benoemde constante te introduceren voor beter onderhoud:
  ```java
  private static final int MAX_FILE_SIZE_MB = 50;
  private static final long MAX_FILE_SIZE_BYTES = MAX_FILE_SIZE_MB * 1024L * 1024L;
  ```

  Of gebruik een utility class:
  ```java
  private static final long MAX_FILE_SIZE_BYTES = FileSize.megabytes(50);
  ```
- **Voorgestelde Code:**
  ```java
  private static final int MAX_FILE_SIZE_MB = 50;
  private static final long MAX_FILE_SIZE_BYTES = MAX_FILE_SIZE_MB * 1024L * 1024L;

  // ... later in de code:
  throw new FileIngestionException(
      String.format("Bestand '%s' is te groot (%d MB). "
          + "Maximum toegestane grootte is %d MB.",
          bestandsnaam, fileSize / (1024 * 1024), MAX_FILE_SIZE_MB)
  );
  ```
