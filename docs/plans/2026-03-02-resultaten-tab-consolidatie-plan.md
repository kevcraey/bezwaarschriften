# Resultaten-tab met consolidatie-taaksysteem — Implementatieplan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Voeg een "Resultaten"-tab toe met een consolidatie-taaksysteem (parallel aan extractie) dat per document toont of alle kernbezwaar-antwoorden compleet zijn en consolidatie-jobs kan starten.

**Architecture:** Parallel taaksysteem naast extractie: eigen entiteit/service/worker/controller in `consolidatie` package. WebSocket hernoemen van `/ws/extracties` naar `/ws/taken` met type-discriminator. Frontend: nieuw `bezwaarschriften-resultaten-tabel.js` component + 3e tab in project-selectie.

**Tech Stack:** Java 21, Spring Boot 3.x, Spring Data JPA, Liquibase, WebSocket, @domg-wc web components, Mockito/JUnit 5/MockMvc.

---

## Task 1: WebSocket hernoemen — backend

Hernoem `ExtractieWebSocketHandler` → `TaakWebSocketHandler` en het WS-pad `/ws/extracties` → `/ws/taken`. Voeg ondersteuning toe voor consolidatie-notificaties.

**Files:**
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieWebSocketHandler.java` (hernoem naar `TaakWebSocketHandler`)
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/config/WebSocketConfig.java`
- Modify: `app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieWebSocketHandlerTest.java` (hernoem naar `TaakWebSocketHandlerTest`)

**Step 1: Hernoem bestand ExtractieWebSocketHandler → TaakWebSocketHandler**

Hernoem het bestand via git mv:
```bash
git mv app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieWebSocketHandler.java \
      app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/TaakWebSocketHandler.java
```

Pas de klassenaam en package-referenties aan in `TaakWebSocketHandler.java`:
- Klassenaam: `TaakWebSocketHandler`
- Logger: `LoggerFactory.getLogger(TaakWebSocketHandler.class)`
- Voeg een nieuwe methode `consolidatieTaakGewijzigd(ConsolidatieTaakDto taak)` toe die hetzelfde doet als `taakGewijzigd` maar met `type: "consolidatie-update"`

```java
package be.vlaanderen.omgeving.bezwaarschriften.project;

import be.vlaanderen.omgeving.bezwaarschriften.consolidatie.ConsolidatieNotificatie;
import be.vlaanderen.omgeving.bezwaarschriften.consolidatie.ConsolidatieTaakDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class TaakWebSocketHandler extends TextWebSocketHandler
    implements ExtractieNotificatie, ConsolidatieNotificatie {

  private static final Logger LOG = LoggerFactory.getLogger(TaakWebSocketHandler.class);

  private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();
  private final ObjectMapper objectMapper;

  public TaakWebSocketHandler(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public void afterConnectionEstablished(WebSocketSession session) {
    sessions.add(session);
    LOG.info("WebSocket sessie verbonden: {}", session.getId());
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    sessions.remove(session);
    LOG.info("WebSocket sessie afgesloten: {} (status: {})", session.getId(), status);
  }

  @Override
  public void taakGewijzigd(ExtractieTaakDto taak) {
    verstuur(Map.of("type", "taak-update", "taak", taak));
  }

  @Override
  public void consolidatieTaakGewijzigd(ConsolidatieTaakDto taak) {
    verstuur(Map.of("type", "consolidatie-update", "taak", taak));
  }

  private void verstuur(Map<String, Object> bericht) {
    String json;
    try {
      json = objectMapper.writeValueAsString(bericht);
    } catch (JsonProcessingException e) {
      LOG.error("Fout bij serialisatie van WebSocket-bericht", e);
      return;
    }

    TextMessage message = new TextMessage(json);
    for (WebSocketSession session : sessions) {
      if (session.isOpen()) {
        try {
          session.sendMessage(message);
        } catch (IOException e) {
          LOG.warn("Fout bij verzenden naar sessie {}: {}", session.getId(), e.getMessage());
        }
      }
    }
  }
}
```

**Note:** De `ConsolidatieNotificatie` interface en `ConsolidatieTaakDto` bestaan nog niet. Maak tijdelijk lege placeholder-bestanden aan zodat dit compileert, OF voer task 1 en task 3 (waar die types aangemaakt worden) tegelijk uit. Bij voorkeur: maak eerst de interface + DTO aan (task 3, stappen 1-2) en kom dan hier terug.

**Alternatief (pragmatisch):** Hernoem nu alleen de klasse en het pad, laat `implements ConsolidatieNotificatie` en de `consolidatieTaakGewijzigd`-methode weg. Voeg die toe in task 3 nadat de types bestaan. Zo compileert het nu meteen.

Gebruik deze pragmatische versie:

```java
package be.vlaanderen.omgeving.bezwaarschriften.project;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class TaakWebSocketHandler extends TextWebSocketHandler
    implements ExtractieNotificatie {

  private static final Logger LOG = LoggerFactory.getLogger(TaakWebSocketHandler.class);

  private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();
  private final ObjectMapper objectMapper;

  public TaakWebSocketHandler(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public void afterConnectionEstablished(WebSocketSession session) {
    sessions.add(session);
    LOG.info("WebSocket sessie verbonden: {}", session.getId());
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    sessions.remove(session);
    LOG.info("WebSocket sessie afgesloten: {} (status: {})", session.getId(), status);
  }

  @Override
  public void taakGewijzigd(ExtractieTaakDto taak) {
    verstuur(Map.of("type", "taak-update", "taak", taak));
  }

  void verstuur(Map<String, Object> bericht) {
    String json;
    try {
      json = objectMapper.writeValueAsString(bericht);
    } catch (JsonProcessingException e) {
      LOG.error("Fout bij serialisatie van WebSocket-bericht", e);
      return;
    }

    TextMessage message = new TextMessage(json);
    for (WebSocketSession session : sessions) {
      if (session.isOpen()) {
        try {
          session.sendMessage(message);
        } catch (IOException e) {
          LOG.warn("Fout bij verzenden naar sessie {}: {}", session.getId(), e.getMessage());
        }
      }
    }
  }
}
```

**Step 2: Pas WebSocketConfig aan**

In `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/config/WebSocketConfig.java`:
- Wijzig import en constructor-parameter van `ExtractieWebSocketHandler` → `TaakWebSocketHandler`
- Wijzig het pad van `/ws/extracties` → `/ws/taken`

```java
package be.vlaanderen.omgeving.bezwaarschriften.config;

import be.vlaanderen.omgeving.bezwaarschriften.project.TaakWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

  private final TaakWebSocketHandler handler;

  public WebSocketConfig(TaakWebSocketHandler handler) {
    this.handler = handler;
  }

  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    registry.addHandler(handler, "/ws/taken").setAllowedOrigins("*");
  }
}
```

**Step 3: Hernoem en pas test aan**

```bash
git mv app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieWebSocketHandlerTest.java \
      app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/project/TaakWebSocketHandlerTest.java
```

Pas de klassenaam aan naar `TaakWebSocketHandlerTest` en alle referenties naar `ExtractieWebSocketHandler` → `TaakWebSocketHandler`.

**Step 4: Run tests**

```bash
cd app && mvn test -pl . -Dtest="TaakWebSocketHandlerTest,ExtractieTaakServiceTest,ExtractieControllerTest,ExtractieWorkerTest" -Denforcer.skip=true
```

Expected: alle tests PASS.

**Step 5: Commit**

```bash
git add -A && git commit -m "refactor: hernoem ExtractieWebSocketHandler naar TaakWebSocketHandler, pad /ws/extracties → /ws/taken"
```

---

## Task 2: WebSocket hernoemen — frontend

Wijzig de WebSocket-URL in de frontend en voeg type-filtering toe.

**Files:**
- Modify: `webapp/src/js/bezwaarschriften-project-selectie.js`

**Step 1: Wijzig WebSocket-URL en voeg type-filtering toe**

In `bezwaarschriften-project-selectie.js`, methode `_verbindWebSocket()`:

Wijzig regel 114: `const url = \`${protocol}//${window.location.host}/ws/extracties\`;`
→ `const url = \`${protocol}//${window.location.host}/ws/taken\`;`

Wijzig de `onmessage` handler (regel 117-121) om op type te filteren:

```javascript
this._ws.onmessage = (event) => {
  const data = JSON.parse(event.data);
  if (data.type === 'taak-update') {
    this._verwerkTaakUpdate(data.taak);
  }
  // consolidatie-update wordt in task 8 afgehandeld
};
```

**Step 2: Build en test**

```bash
cd webapp && npm run build && cd .. && mvn process-resources -pl webapp -Denforcer.skip=true
```

Start de applicatie en verifieer dat extractie-functionaliteit nog werkt: upload een bestand, start extractie, controleer dat WebSocket-updates nog aankomen.

**Step 3: Commit**

```bash
git add webapp/src/js/bezwaarschriften-project-selectie.js && git commit -m "refactor: frontend WebSocket URL /ws/extracties → /ws/taken"
```

---

## Task 3: Consolidatie-domein — entiteit, enum, DTO, notificatie-interface

Maak de consolidatie-specifieke types aan in een nieuw `consolidatie` package.

**Files:**
- Create: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/consolidatie/ConsolidatieTaakStatus.java`
- Create: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/consolidatie/ConsolidatieTaak.java`
- Create: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/consolidatie/ConsolidatieTaakDto.java`
- Create: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/consolidatie/ConsolidatieNotificatie.java`
- Create: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/consolidatie/ConsolidatieTaakRepository.java`

**Step 1: Maak ConsolidatieTaakStatus enum**

```java
package be.vlaanderen.omgeving.bezwaarschriften.consolidatie;

public enum ConsolidatieTaakStatus {
  WACHTEND,
  BEZIG,
  KLAAR,
  FOUT
}
```

**Step 2: Maak ConsolidatieTaak JPA-entiteit**

Exact dezelfde structuur als `ExtractieTaak` (in `be.vlaanderen.omgeving.bezwaarschriften.project`), maar:
- Package: `be.vlaanderen.omgeving.bezwaarschriften.consolidatie`
- Tabel: `consolidatie_taak`
- Enum: `ConsolidatieTaakStatus`
- Geen `aantalWoorden` of `aantalBezwaren` velden (consolidatie slaat geen resultaat op)

```java
package be.vlaanderen.omgeving.bezwaarschriften.consolidatie;

import java.time.Instant;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Version;

@Entity
@Table(name = "consolidatie_taak")
public class ConsolidatieTaak {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "project_naam", nullable = false)
  private String projectNaam;

  @Column(name = "bestandsnaam", nullable = false)
  private String bestandsnaam;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private ConsolidatieTaakStatus status;

  @Column(name = "aantal_pogingen", nullable = false)
  private int aantalPogingen;

  @Column(name = "max_pogingen", nullable = false)
  private int maxPogingen;

  @Column(name = "foutmelding", columnDefinition = "text")
  private String foutmelding;

  @Column(name = "aangemaakt_op", nullable = false)
  private Instant aangemaaktOp;

  @Column(name = "verwerking_gestart_op")
  private Instant verwerkingGestartOp;

  @Column(name = "afgerond_op")
  private Instant afgerondOp;

  @Version
  @Column(name = "versie", nullable = false)
  private int versie;

  // Getters en setters — identiek patroon als ExtractieTaak
  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }
  public String getProjectNaam() { return projectNaam; }
  public void setProjectNaam(String projectNaam) { this.projectNaam = projectNaam; }
  public String getBestandsnaam() { return bestandsnaam; }
  public void setBestandsnaam(String bestandsnaam) { this.bestandsnaam = bestandsnaam; }
  public ConsolidatieTaakStatus getStatus() { return status; }
  public void setStatus(ConsolidatieTaakStatus status) { this.status = status; }
  public int getAantalPogingen() { return aantalPogingen; }
  public void setAantalPogingen(int aantalPogingen) { this.aantalPogingen = aantalPogingen; }
  public int getMaxPogingen() { return maxPogingen; }
  public void setMaxPogingen(int maxPogingen) { this.maxPogingen = maxPogingen; }
  public String getFoutmelding() { return foutmelding; }
  public void setFoutmelding(String foutmelding) { this.foutmelding = foutmelding; }
  public Instant getAangemaaktOp() { return aangemaaktOp; }
  public void setAangemaaktOp(Instant aangemaaktOp) { this.aangemaaktOp = aangemaaktOp; }
  public Instant getVerwerkingGestartOp() { return verwerkingGestartOp; }
  public void setVerwerkingGestartOp(Instant verwerkingGestartOp) { this.verwerkingGestartOp = verwerkingGestartOp; }
  public Instant getAfgerondOp() { return afgerondOp; }
  public void setAfgerondOp(Instant afgerondOp) { this.afgerondOp = afgerondOp; }
  public int getVersie() { return versie; }
  public void setVersie(int versie) { this.versie = versie; }
}
```

**Step 3: Maak ConsolidatieTaakDto record**

```java
package be.vlaanderen.omgeving.bezwaarschriften.consolidatie;

public record ConsolidatieTaakDto(
    Long id, String projectNaam, String bestandsnaam, String status,
    int aantalPogingen, String aangemaaktOp, String verwerkingGestartOp,
    String foutmelding) {

  static ConsolidatieTaakDto van(ConsolidatieTaak taak) {
    return new ConsolidatieTaakDto(
        taak.getId(), taak.getProjectNaam(), taak.getBestandsnaam(),
        statusNaarString(taak.getStatus()), taak.getAantalPogingen(),
        taak.getAangemaaktOp().toString(),
        taak.getVerwerkingGestartOp() != null ? taak.getVerwerkingGestartOp().toString() : null,
        taak.getFoutmelding());
  }

  private static String statusNaarString(ConsolidatieTaakStatus status) {
    return switch (status) {
      case WACHTEND -> "wachtend";
      case BEZIG -> "bezig";
      case KLAAR -> "klaar";
      case FOUT -> "fout";
    };
  }
}
```

**Step 4: Maak ConsolidatieNotificatie interface**

```java
package be.vlaanderen.omgeving.bezwaarschriften.consolidatie;

public interface ConsolidatieNotificatie {
  void consolidatieTaakGewijzigd(ConsolidatieTaakDto taak);
}
```

**Step 5: Maak ConsolidatieTaakRepository**

```java
package be.vlaanderen.omgeving.bezwaarschriften.consolidatie;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ConsolidatieTaakRepository extends JpaRepository<ConsolidatieTaak, Long> {

  List<ConsolidatieTaak> findByProjectNaam(String projectNaam);

  long countByStatus(ConsolidatieTaakStatus status);

  List<ConsolidatieTaak> findByStatusOrderByAangemaaktOpAsc(ConsolidatieTaakStatus status);

  Optional<ConsolidatieTaak> findTopByProjectNaamAndBestandsnaamOrderByAangemaaktOpDesc(
      String projectNaam, String bestandsnaam);

  List<ConsolidatieTaak> findByProjectNaamAndStatus(
      String projectNaam, ConsolidatieTaakStatus status);

  void deleteByProjectNaam(String projectNaam);
}
```

**Step 6: Voeg `implements ConsolidatieNotificatie` toe aan TaakWebSocketHandler**

Update `TaakWebSocketHandler.java`:
- Voeg import toe: `import be.vlaanderen.omgeving.bezwaarschriften.consolidatie.ConsolidatieNotificatie;` en `import be.vlaanderen.omgeving.bezwaarschriften.consolidatie.ConsolidatieTaakDto;`
- Voeg `implements ConsolidatieNotificatie` toe aan de class declaration (naast bestaande `ExtractieNotificatie`)
- Voeg de methode toe:

```java
@Override
public void consolidatieTaakGewijzigd(ConsolidatieTaakDto taak) {
  verstuur(Map.of("type", "consolidatie-update", "taak", taak));
}
```

**Step 7: Maak Liquibase changelog voor consolidatie_taak tabel**

Create: `app/src/main/resources/config/liquibase/changelog/20260302-consolidatie-taak.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<databaseChangeLog
    xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-3.1.xsd">

  <changeSet id="20260302-1" author="kenzo">
    <createTable tableName="consolidatie_taak">
      <column name="id" type="bigserial" autoIncrement="true">
        <constraints primaryKey="true" nullable="false"/>
      </column>
      <column name="project_naam" type="varchar(255)">
        <constraints nullable="false"/>
      </column>
      <column name="bestandsnaam" type="varchar(255)">
        <constraints nullable="false"/>
      </column>
      <column name="status" type="varchar(50)">
        <constraints nullable="false"/>
      </column>
      <column name="aantal_pogingen" type="int" defaultValueNumeric="0">
        <constraints nullable="false"/>
      </column>
      <column name="max_pogingen" type="int" defaultValueNumeric="3">
        <constraints nullable="false"/>
      </column>
      <column name="foutmelding" type="text"/>
      <column name="aangemaakt_op" type="timestamp with time zone">
        <constraints nullable="false"/>
      </column>
      <column name="verwerking_gestart_op" type="timestamp with time zone"/>
      <column name="afgerond_op" type="timestamp with time zone"/>
      <column name="versie" type="int" defaultValueNumeric="0">
        <constraints nullable="false"/>
      </column>
    </createTable>
  </changeSet>

</databaseChangeLog>
```

Voeg toe aan `master.xml`:

```xml
<include file="config/liquibase/changelog/20260302-consolidatie-taak.xml"/>
```

**Step 8: Compileer**

```bash
cd app && mvn compile -Denforcer.skip=true
```

Expected: BUILD SUCCESS.

**Step 9: Commit**

```bash
git add -A && git commit -m "feat: consolidatie-domein — entiteit, enum, DTO, notificatie, repository, liquibase"
```

---

## Task 4: Antwoorden-check service

Maak een service die per document berekent hoeveel van de gekoppelde kernbezwaren een antwoord hebben.

**Files:**
- Create: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/consolidatie/AntwoordStatus.java`
- Create: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/consolidatie/AntwoordStatusService.java`
- Create: `app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/consolidatie/AntwoordStatusServiceTest.java`
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/KernbezwaarReferentieRepository.java`

**Step 1: Schrijf falende test**

```java
package be.vlaanderen.omgeving.bezwaarschriften.consolidatie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar.KernbezwaarAntwoordRepository;
import be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar.KernbezwaarReferentieEntiteit;
import be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar.KernbezwaarReferentieRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AntwoordStatusServiceTest {

  @Mock
  private KernbezwaarReferentieRepository referentieRepository;

  @Mock
  private KernbezwaarAntwoordRepository antwoordRepository;

  private AntwoordStatusService service;

  @BeforeEach
  void setUp() {
    service = new AntwoordStatusService(referentieRepository, antwoordRepository);
  }

  @Test
  void berekentAntwoordStatusPerDocument() {
    // bezwaar-001.txt is gekoppeld aan kernbezwaar 10 en 20
    // bezwaar-002.txt is gekoppeld aan kernbezwaar 10 en 30
    var ref1 = maakRef("bezwaar-001.txt", 10L);
    var ref2 = maakRef("bezwaar-001.txt", 20L);
    var ref3 = maakRef("bezwaar-002.txt", 10L);
    var ref4 = maakRef("bezwaar-002.txt", 30L);

    when(referentieRepository.findByProjectNaam("windmolens"))
        .thenReturn(List.of(ref1, ref2, ref3, ref4));
    // Kernbezwaar 10 en 20 hebben een antwoord, 30 niet
    when(antwoordRepository.findKernbezwaarIdsMetAntwoord(List.of(10L, 20L, 30L)))
        .thenReturn(List.of(10L, 20L));

    var resultaat = service.berekenAntwoordStatus("windmolens");

    assertThat(resultaat).hasSize(2);
    assertThat(resultaat.get("bezwaar-001.txt").aantalMetAntwoord()).isEqualTo(2);
    assertThat(resultaat.get("bezwaar-001.txt").totaal()).isEqualTo(2);
    assertThat(resultaat.get("bezwaar-002.txt").aantalMetAntwoord()).isEqualTo(1);
    assertThat(resultaat.get("bezwaar-002.txt").totaal()).isEqualTo(2);
  }

  @Test
  void retourneertLegeMapAlsGeenReferenties() {
    when(referentieRepository.findByProjectNaam("leeg")).thenReturn(List.of());

    var resultaat = service.berekenAntwoordStatus("leeg");

    assertThat(resultaat).isEmpty();
  }

  @Test
  void documentMetAlleAntwoordenIsVolledig() {
    var ref1 = maakRef("bezwaar-001.txt", 10L);
    when(referentieRepository.findByProjectNaam("p"))
        .thenReturn(List.of(ref1));
    when(antwoordRepository.findKernbezwaarIdsMetAntwoord(List.of(10L)))
        .thenReturn(List.of(10L));

    var resultaat = service.berekenAntwoordStatus("p");

    assertThat(resultaat.get("bezwaar-001.txt").isVolledig()).isTrue();
  }

  @Test
  void documentZonderAntwoordenIsOnvolledig() {
    var ref1 = maakRef("bezwaar-001.txt", 10L);
    when(referentieRepository.findByProjectNaam("p"))
        .thenReturn(List.of(ref1));
    when(antwoordRepository.findKernbezwaarIdsMetAntwoord(List.of(10L)))
        .thenReturn(List.of());

    var resultaat = service.berekenAntwoordStatus("p");

    assertThat(resultaat.get("bezwaar-001.txt").isVolledig()).isFalse();
  }

  private KernbezwaarReferentieEntiteit maakRef(String bestandsnaam, Long kernbezwaarId) {
    var ref = new KernbezwaarReferentieEntiteit();
    ref.setBestandsnaam(bestandsnaam);
    ref.setKernbezwaarId(kernbezwaarId);
    ref.setPassage("test");
    return ref;
  }
}
```

**Step 2: Voeg repository-methodes toe**

In `KernbezwaarReferentieRepository.java`, voeg toe:

```java
List<KernbezwaarReferentieEntiteit> findByProjectNaam(String projectNaam);
```

**Probleem:** `KernbezwaarReferentieEntiteit` heeft geen `projectNaam`-veld. De relatie loopt via thema. We moeten via een join-query werken.

Voeg in plaats daarvan een `@Query` toe:

```java
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Query("SELECT r FROM KernbezwaarReferentieEntiteit r " +
    "JOIN KernbezwaarEntiteit k ON r.kernbezwaarId = k.id " +
    "JOIN ThemaEntiteit t ON k.themaId = t.id " +
    "WHERE t.projectNaam = :projectNaam")
List<KernbezwaarReferentieEntiteit> findByProjectNaam(@Param("projectNaam") String projectNaam);
```

In `KernbezwaarAntwoordRepository.java`, voeg toe:

```java
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Query("SELECT a.kernbezwaarId FROM KernbezwaarAntwoordEntiteit a WHERE a.kernbezwaarId IN :ids")
List<Long> findKernbezwaarIdsMetAntwoord(@Param("ids") List<Long> kernbezwaarIds);
```

**Step 3: Maak AntwoordStatus record**

```java
package be.vlaanderen.omgeving.bezwaarschriften.consolidatie;

public record AntwoordStatus(int aantalMetAntwoord, int totaal) {

  public boolean isVolledig() {
    return totaal > 0 && aantalMetAntwoord == totaal;
  }
}
```

**Step 4: Maak AntwoordStatusService**

```java
package be.vlaanderen.omgeving.bezwaarschriften.consolidatie;

import be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar.KernbezwaarAntwoordRepository;
import be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar.KernbezwaarReferentieRepository;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class AntwoordStatusService {

  private final KernbezwaarReferentieRepository referentieRepository;
  private final KernbezwaarAntwoordRepository antwoordRepository;

  public AntwoordStatusService(
      KernbezwaarReferentieRepository referentieRepository,
      KernbezwaarAntwoordRepository antwoordRepository) {
    this.referentieRepository = referentieRepository;
    this.antwoordRepository = antwoordRepository;
  }

  public Map<String, AntwoordStatus> berekenAntwoordStatus(String projectNaam) {
    var referenties = referentieRepository.findByProjectNaam(projectNaam);
    if (referenties.isEmpty()) {
      return Map.of();
    }

    // Groepeer: bestandsnaam → set van kernbezwaar-IDs
    Map<String, Set<Long>> kernbezwarenPerDocument = new HashMap<>();
    for (var ref : referenties) {
      kernbezwarenPerDocument
          .computeIfAbsent(ref.getBestandsnaam(), k -> new HashSet<>())
          .add(ref.getKernbezwaarId());
    }

    // Alle unieke kernbezwaar-IDs
    var alleKernbezwaarIds = kernbezwarenPerDocument.values().stream()
        .flatMap(Set::stream)
        .distinct()
        .toList();

    // Welke hebben een antwoord?
    var idsMetAntwoord = new HashSet<>(
        antwoordRepository.findKernbezwaarIdsMetAntwoord(alleKernbezwaarIds));

    // Bereken per document
    return kernbezwarenPerDocument.entrySet().stream()
        .collect(Collectors.toMap(
            Map.Entry::getKey,
            entry -> {
              var kernIds = entry.getValue();
              int totaal = kernIds.size();
              int metAntwoord = (int) kernIds.stream()
                  .filter(idsMetAntwoord::contains).count();
              return new AntwoordStatus(metAntwoord, totaal);
            }));
  }
}
```

**Step 5: Run test**

```bash
cd app && mvn test -pl . -Dtest="AntwoordStatusServiceTest" -Denforcer.skip=true
```

Expected: alle 4 tests PASS.

**Step 6: Commit**

```bash
git add -A && git commit -m "feat: AntwoordStatusService berekent antwoord-volledigheid per document"
```

---

## Task 5: ConsolidatieTaakService

Service die consolidatie-taken beheert (indienen, oppakken, markeren, verwijderen). Vrijwel identiek aan `ExtractieTaakService`, maar zonder `aantalWoorden`/`aantalBezwaren`.

**Files:**
- Create: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/consolidatie/ConsolidatieTaakService.java`
- Create: `app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/consolidatie/ConsolidatieTaakServiceTest.java`

**Step 1: Schrijf falende tests**

Kopieer het patroon van `ExtractieTaakServiceTest`. De tests zijn identiek maar met `ConsolidatieTaak`/`ConsolidatieTaakStatus`/`ConsolidatieTaakDto`/`ConsolidatieNotificatie` types. Belangrijke tests:

- `dienTakenInMetStatusWachtend`
- `geefTakenVoorProject`
- `pakOpVoorVerwerkingZetStatusOpBezig`
- `pakOpRespecteerMaxConcurrent`
- `markeerKlaarZetStatus`
- `markeerFoutMetRetryZetTerugNaarWachtend`
- `markeerFoutDefinitiefBijMaxPogingen`
- `verwijderTaakVerwijdertUitRepository`
- `verwijderTaakGooitExceptieBijOnbekendeTaak`
- `verwijderTaakGooitExceptieBijVerkeerdeProject`

```java
package be.vlaanderen.omgeving.bezwaarschriften.consolidatie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConsolidatieTaakServiceTest {

  @Mock
  private ConsolidatieTaakRepository repository;

  @Mock
  private ConsolidatieNotificatie notificatie;

  private ConsolidatieTaakService service;

  @BeforeEach
  void setUp() {
    service = new ConsolidatieTaakService(repository, notificatie, 3, 3);
  }

  @Test
  void dienTakenInMetStatusWachtend() {
    when(repository.save(any())).thenAnswer(i -> {
      var t = i.getArgument(0, ConsolidatieTaak.class);
      t.setId(1L);
      return t;
    });

    var resultaat = service.indienen("windmolens", List.of("bezwaar-001.txt"));

    assertThat(resultaat).hasSize(1);
    var captor = ArgumentCaptor.forClass(ConsolidatieTaak.class);
    verify(repository).save(captor.capture());
    assertThat(captor.getValue().getStatus()).isEqualTo(ConsolidatieTaakStatus.WACHTEND);
    assertThat(captor.getValue().getProjectNaam()).isEqualTo("windmolens");
    verify(notificatie).consolidatieTaakGewijzigd(any(ConsolidatieTaakDto.class));
  }

  @Test
  void geefTakenVoorProject() {
    var taak = maakTaak(1L, "windmolens", "bezwaar-001.txt", ConsolidatieTaakStatus.WACHTEND);
    when(repository.findByProjectNaam("windmolens")).thenReturn(List.of(taak));

    var resultaat = service.geefTaken("windmolens");

    assertThat(resultaat).hasSize(1);
    assertThat(resultaat.get(0).bestandsnaam()).isEqualTo("bezwaar-001.txt");
  }

  @Test
  void pakOpVoorVerwerkingZetStatusOpBezig() {
    when(repository.countByStatus(ConsolidatieTaakStatus.BEZIG)).thenReturn(0L);
    var taak = maakTaak(1L, "windmolens", "bezwaar-001.txt", ConsolidatieTaakStatus.WACHTEND);
    when(repository.findByStatusOrderByAangemaaktOpAsc(ConsolidatieTaakStatus.WACHTEND))
        .thenReturn(List.of(taak));
    when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

    var resultaat = service.pakOpVoorVerwerking();

    assertThat(resultaat).hasSize(1);
    assertThat(resultaat.get(0).getStatus()).isEqualTo(ConsolidatieTaakStatus.BEZIG);
    assertThat(resultaat.get(0).getVerwerkingGestartOp()).isNotNull();
  }

  @Test
  void pakOpRespecteerMaxConcurrent() {
    when(repository.countByStatus(ConsolidatieTaakStatus.BEZIG)).thenReturn(3L);

    var resultaat = service.pakOpVoorVerwerking();

    assertThat(resultaat).isEmpty();
  }

  @Test
  void markeerKlaarZetStatus() {
    var taak = maakTaak(1L, "windmolens", "bezwaar-001.txt", ConsolidatieTaakStatus.BEZIG);
    when(repository.findById(1L)).thenReturn(Optional.of(taak));
    when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

    service.markeerKlaar(1L);

    assertThat(taak.getStatus()).isEqualTo(ConsolidatieTaakStatus.KLAAR);
    assertThat(taak.getAfgerondOp()).isNotNull();
    verify(notificatie).consolidatieTaakGewijzigd(any(ConsolidatieTaakDto.class));
  }

  @Test
  void markeerFoutMetRetryZetTerugNaarWachtend() {
    var taak = maakTaak(1L, "windmolens", "bezwaar-001.txt", ConsolidatieTaakStatus.BEZIG);
    taak.setAantalPogingen(0);
    taak.setMaxPogingen(3);
    when(repository.findById(1L)).thenReturn(Optional.of(taak));
    when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

    service.markeerFout(1L, "Timeout");

    assertThat(taak.getStatus()).isEqualTo(ConsolidatieTaakStatus.WACHTEND);
    assertThat(taak.getAantalPogingen()).isEqualTo(1);
  }

  @Test
  void markeerFoutDefinitiefBijMaxPogingen() {
    var taak = maakTaak(1L, "windmolens", "bezwaar-001.txt", ConsolidatieTaakStatus.BEZIG);
    taak.setAantalPogingen(2);
    taak.setMaxPogingen(3);
    when(repository.findById(1L)).thenReturn(Optional.of(taak));
    when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

    service.markeerFout(1L, "Definitieve fout");

    assertThat(taak.getStatus()).isEqualTo(ConsolidatieTaakStatus.FOUT);
    assertThat(taak.getAantalPogingen()).isEqualTo(3);
    assertThat(taak.getFoutmelding()).isEqualTo("Definitieve fout");
  }

  @Test
  void verwijderTaakVerwijdertUitRepository() {
    var taak = maakTaak(1L, "windmolens", "bezwaar-001.txt", ConsolidatieTaakStatus.WACHTEND);
    when(repository.findById(1L)).thenReturn(Optional.of(taak));

    service.verwijderTaak("windmolens", 1L);

    verify(repository).delete(taak);
  }

  @Test
  void verwijderTaakGooitExceptieBijOnbekendeTaak() {
    when(repository.findById(999L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.verwijderTaak("windmolens", 999L))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void verwijderTaakGooitExceptieBijVerkeerdeProject() {
    var taak = maakTaak(1L, "snelweg", "bezwaar-001.txt", ConsolidatieTaakStatus.BEZIG);
    when(repository.findById(1L)).thenReturn(Optional.of(taak));

    assertThatThrownBy(() -> service.verwijderTaak("windmolens", 1L))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private ConsolidatieTaak maakTaak(Long id, String projectNaam, String bestandsnaam,
      ConsolidatieTaakStatus status) {
    var taak = new ConsolidatieTaak();
    taak.setId(id);
    taak.setProjectNaam(projectNaam);
    taak.setBestandsnaam(bestandsnaam);
    taak.setStatus(status);
    taak.setAantalPogingen(0);
    taak.setMaxPogingen(3);
    taak.setAangemaaktOp(Instant.now());
    return taak;
  }
}
```

**Step 2: Run test om te verifiëren dat het faalt**

```bash
cd app && mvn test -pl . -Dtest="ConsolidatieTaakServiceTest" -Denforcer.skip=true
```

Expected: FAIL (ConsolidatieTaakService bestaat nog niet).

**Step 3: Implementeer ConsolidatieTaakService**

Kopieer het patroon van `ExtractieTaakService` maar:
- Geen `ProjectService` dependency
- `markeerKlaar(Long taakId)` zonder `aantalWoorden`/`aantalBezwaren` parameters
- Geen `verwerkOnafgeronde` (die komt pas als we weten welke documenten "volledig" zijn — dat is de controller's verantwoordelijkheid)

```java
package be.vlaanderen.omgeving.bezwaarschriften.consolidatie;

import java.lang.invoke.MethodHandles;
import java.time.Instant;
import java.util.List;
import javax.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ConsolidatieTaakService {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final ConsolidatieTaakRepository repository;
  private final ConsolidatieNotificatie notificatie;
  private final int maxConcurrent;
  private final int maxPogingen;

  public ConsolidatieTaakService(
      ConsolidatieTaakRepository repository,
      ConsolidatieNotificatie notificatie,
      @Value("${bezwaarschriften.consolidatie.max-concurrent:3}") int maxConcurrent,
      @Value("${bezwaarschriften.consolidatie.max-pogingen:3}") int maxPogingen) {
    this.repository = repository;
    this.notificatie = notificatie;
    this.maxConcurrent = maxConcurrent;
    this.maxPogingen = maxPogingen;
  }

  @Transactional
  public List<ConsolidatieTaakDto> indienen(String projectNaam, List<String> bestandsnamen) {
    return bestandsnamen.stream()
        .map(bestandsnaam -> {
          var taak = new ConsolidatieTaak();
          taak.setProjectNaam(projectNaam);
          taak.setBestandsnaam(bestandsnaam);
          taak.setStatus(ConsolidatieTaakStatus.WACHTEND);
          taak.setAantalPogingen(0);
          taak.setMaxPogingen(maxPogingen);
          taak.setAangemaaktOp(Instant.now());
          var opgeslagen = repository.save(taak);
          var dto = ConsolidatieTaakDto.van(opgeslagen);
          notificatie.consolidatieTaakGewijzigd(dto);
          LOGGER.info("Consolidatie-taak ingediend: project='{}', bestand='{}'",
              projectNaam, bestandsnaam);
          return dto;
        })
        .toList();
  }

  public List<ConsolidatieTaakDto> geefTaken(String projectNaam) {
    return repository.findByProjectNaam(projectNaam).stream()
        .map(ConsolidatieTaakDto::van)
        .toList();
  }

  @Transactional
  public List<ConsolidatieTaak> pakOpVoorVerwerking() {
    long aantalBezig = repository.countByStatus(ConsolidatieTaakStatus.BEZIG);
    int beschikbareSlots = maxConcurrent - (int) aantalBezig;

    if (beschikbareSlots <= 0) {
      return List.of();
    }

    var wachtend = repository.findByStatusOrderByAangemaaktOpAsc(
        ConsolidatieTaakStatus.WACHTEND);
    var opTePakken = wachtend.stream().limit(beschikbareSlots).toList();

    for (var taak : opTePakken) {
      taak.setStatus(ConsolidatieTaakStatus.BEZIG);
      taak.setVerwerkingGestartOp(Instant.now());
      repository.save(taak);
      notificatie.consolidatieTaakGewijzigd(ConsolidatieTaakDto.van(taak));
      LOGGER.info("Consolidatie-taak {} opgepakt: project='{}', bestand='{}'",
          taak.getId(), taak.getProjectNaam(), taak.getBestandsnaam());
    }

    return opTePakken;
  }

  @Transactional
  public void markeerKlaar(Long taakId) {
    var taak = repository.findById(taakId)
        .orElseThrow(() -> new IllegalArgumentException("Taak niet gevonden: " + taakId));
    taak.setStatus(ConsolidatieTaakStatus.KLAAR);
    taak.setAfgerondOp(Instant.now());
    repository.save(taak);
    notificatie.consolidatieTaakGewijzigd(ConsolidatieTaakDto.van(taak));
    LOGGER.info("Consolidatie-taak {} afgerond", taakId);
  }

  @Transactional
  public void markeerFout(Long taakId, String foutmelding) {
    var taak = repository.findById(taakId)
        .orElseThrow(() -> new IllegalArgumentException("Taak niet gevonden: " + taakId));
    taak.setAantalPogingen(taak.getAantalPogingen() + 1);

    if (taak.getAantalPogingen() < taak.getMaxPogingen()) {
      taak.setStatus(ConsolidatieTaakStatus.WACHTEND);
      taak.setVerwerkingGestartOp(null);
    } else {
      taak.setStatus(ConsolidatieTaakStatus.FOUT);
      taak.setFoutmelding(foutmelding);
      taak.setAfgerondOp(Instant.now());
    }

    repository.save(taak);
    notificatie.consolidatieTaakGewijzigd(ConsolidatieTaakDto.van(taak));
  }

  @Transactional
  public void verwijderTaak(String projectNaam, Long taakId) {
    var taak = repository.findById(taakId)
        .orElseThrow(() -> new IllegalArgumentException("Taak niet gevonden: " + taakId));
    if (!taak.getProjectNaam().equals(projectNaam)) {
      throw new IllegalArgumentException(
          "Taak " + taakId + " behoort niet tot project: " + projectNaam);
    }
    repository.delete(taak);
    LOGGER.info("Consolidatie-taak {} verwijderd uit project '{}'", taakId, projectNaam);
  }
}
```

**Step 4: Run tests**

```bash
cd app && mvn test -pl . -Dtest="ConsolidatieTaakServiceTest" -Denforcer.skip=true
```

Expected: alle 10 tests PASS.

**Step 5: Commit**

```bash
git add -A && git commit -m "feat: ConsolidatieTaakService met indienen, oppakken, markeren, verwijderen"
```

---

## Task 6: ConsolidatieWorker + MockConsolidatieVerwerker

Worker die wachtende consolidatie-taken pollt en verwerkt. Mock-verwerker simuleert delay + placeholder-output.

**Files:**
- Create: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/consolidatie/ConsolidatieVerwerker.java`
- Create: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/consolidatie/MockConsolidatieVerwerker.java`
- Create: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/consolidatie/ConsolidatieWorker.java`
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/config/ExtractieConfig.java` (voeg consolidatie executor toe)
- Create: `app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/consolidatie/ConsolidatieWorkerTest.java`

**Step 1: Schrijf falende test**

```java
package be.vlaanderen.omgeving.bezwaarschriften.consolidatie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@ExtendWith(MockitoExtension.class)
class ConsolidatieWorkerTest {

  @Mock
  private ConsolidatieTaakService service;

  @Mock
  private ConsolidatieVerwerker verwerker;

  private ThreadPoolTaskExecutor executor;
  private ConsolidatieWorker worker;

  @BeforeEach
  void setUp() {
    executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(1);
    executor.setMaxPoolSize(1);
    executor.setThreadNamePrefix("test-consolidatie-");
    executor.initialize();
    worker = new ConsolidatieWorker(service, verwerker, executor);
  }

  @AfterEach
  void tearDown() {
    executor.shutdown();
  }

  @Test
  void paktTakenOpEnVoertUit() {
    var taak = maakTaak(1L, "windmolens", "bezwaar-001.txt");
    when(service.pakOpVoorVerwerking()).thenReturn(List.of(taak));

    worker.verwerkTaken();

    verify(service, timeout(2000)).markeerKlaar(1L);
  }

  @Test
  void markeertFoutBijException() {
    var taak = maakTaak(2L, "snelweg", "bezwaar-042.txt");
    when(service.pakOpVoorVerwerking()).thenReturn(List.of(taak));
    when(verwerker.verwerk("snelweg", "bezwaar-042.txt", 0))
        .thenThrow(new RuntimeException("Consolidatie mislukt"));

    worker.verwerkTaken();

    verify(service, timeout(2000)).markeerFout(2L, "Consolidatie mislukt");
  }

  @Test
  void doetNietsAlsGeenTakenBeschikbaar() {
    when(service.pakOpVoorVerwerking()).thenReturn(List.of());

    worker.verwerkTaken();

    verify(verwerker, never()).verwerk(anyString(), anyString(), anyLong());
  }

  @Test
  void annuleerTaakCanceltLopendeFuture() throws Exception {
    var taak = maakTaak(5L, "windmolens", "stuck.txt");
    when(service.pakOpVoorVerwerking()).thenReturn(List.of(taak));
    when(verwerker.verwerk("windmolens", "stuck.txt", 0))
        .thenAnswer(invocation -> {
          Thread.sleep(10_000);
          return null;
        });

    worker.verwerkTaken();
    Thread.sleep(200);

    boolean geannuleerd = worker.annuleerTaak(5L);

    assertThat(geannuleerd).isTrue();
  }

  private ConsolidatieTaak maakTaak(Long id, String projectNaam, String bestandsnaam) {
    var taak = new ConsolidatieTaak();
    taak.setId(id);
    taak.setProjectNaam(projectNaam);
    taak.setBestandsnaam(bestandsnaam);
    taak.setStatus(ConsolidatieTaakStatus.BEZIG);
    taak.setAantalPogingen(0);
    taak.setMaxPogingen(3);
    taak.setAangemaaktOp(Instant.now());
    return taak;
  }
}
```

**Step 2: Implementeer ConsolidatieVerwerker interface**

```java
package be.vlaanderen.omgeving.bezwaarschriften.consolidatie;

public interface ConsolidatieVerwerker {
  void verwerk(String projectNaam, String bestandsnaam, int poging);
}
```

**Step 3: Implementeer MockConsolidatieVerwerker**

```java
package be.vlaanderen.omgeving.bezwaarschriften.consolidatie;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class MockConsolidatieVerwerker implements ConsolidatieVerwerker {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final int minDelaySeconden;
  private final int maxDelaySeconden;

  public MockConsolidatieVerwerker(
      @Value("${bezwaarschriften.consolidatie.mock.min-delay-seconden:2}") int minDelaySeconden,
      @Value("${bezwaarschriften.consolidatie.mock.max-delay-seconden:5}") int maxDelaySeconden) {
    this.minDelaySeconden = minDelaySeconden;
    this.maxDelaySeconden = maxDelaySeconden;
  }

  @Override
  public void verwerk(String projectNaam, String bestandsnaam, int poging) {
    simuleerDelay();
    LOGGER.info("[MOCK] Consolidatie afgerond voor '{}' in project '{}' (poging {})",
        bestandsnaam, projectNaam, poging);
  }

  private void simuleerDelay() {
    if (maxDelaySeconden <= 0) return;
    try {
      long delayMillis = ThreadLocalRandom.current()
          .nextLong(minDelaySeconden * 1000L, maxDelaySeconden * 1000L + 1);
      Thread.sleep(delayMillis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
```

**Step 4: Implementeer ConsolidatieWorker**

```java
package be.vlaanderen.omgeving.bezwaarschriften.consolidatie;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

@Component
public class ConsolidatieWorker {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final ConsolidatieTaakService service;
  private final ConsolidatieVerwerker verwerker;
  private final ThreadPoolTaskExecutor executor;
  private final ConcurrentHashMap<Long, Future<?>> lopendeTaken = new ConcurrentHashMap<>();

  public ConsolidatieWorker(ConsolidatieTaakService service, ConsolidatieVerwerker verwerker,
      @Qualifier("consolidatieExecutor") ThreadPoolTaskExecutor executor) {
    this.service = service;
    this.verwerker = verwerker;
    this.executor = executor;
  }

  @Scheduled(fixedDelay = 1000)
  public void verwerkTaken() {
    var taken = service.pakOpVoorVerwerking();
    for (var taak : taken) {
      var future = executor.submit(() -> verwerkTaak(taak));
      lopendeTaken.put(taak.getId(), future);
    }
  }

  public boolean annuleerTaak(Long taakId) {
    var future = lopendeTaken.remove(taakId);
    if (future != null) {
      LOGGER.info("Consolidatie-taak {} geannuleerd", taakId);
      return future.cancel(true);
    }
    return false;
  }

  private void verwerkTaak(ConsolidatieTaak taak) {
    try {
      verwerker.verwerk(taak.getProjectNaam(), taak.getBestandsnaam(), taak.getAantalPogingen());
      try {
        service.markeerKlaar(taak.getId());
      } catch (IllegalArgumentException e) {
        LOGGER.info("Consolidatie-taak {} niet meer aanwezig na voltooiing", taak.getId());
      }
    } catch (Exception e) {
      LOGGER.error("Fout bij consolidatie-taak {}: {}", taak.getId(), e.getMessage(), e);
      try {
        service.markeerFout(taak.getId(), e.getMessage());
      } catch (IllegalArgumentException ex) {
        LOGGER.info("Consolidatie-taak {} niet meer aanwezig na fout", taak.getId());
      }
    } finally {
      lopendeTaken.remove(taak.getId());
    }
  }
}
```

**Step 5: Voeg consolidatie executor bean toe aan ExtractieConfig**

In `ExtractieConfig.java`, voeg toe:

```java
@Bean
public ThreadPoolTaskExecutor consolidatieExecutor(
    @Value("${bezwaarschriften.consolidatie.max-concurrent:3}") int maxConcurrent) {
  var executor = new ThreadPoolTaskExecutor();
  executor.setCorePoolSize(maxConcurrent);
  executor.setMaxPoolSize(maxConcurrent);
  executor.setThreadNamePrefix("consolidatie-");
  executor.initialize();
  return executor;
}
```

**Step 6: Run tests**

```bash
cd app && mvn test -pl . -Dtest="ConsolidatieWorkerTest" -Denforcer.skip=true
```

Expected: alle 4 tests PASS.

**Step 7: Commit**

```bash
git add -A && git commit -m "feat: ConsolidatieWorker + MockConsolidatieVerwerker met delay-simulatie"
```

---

## Task 7: ConsolidatieController

REST controller voor consolidatie-endpoints.

**Files:**
- Create: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/consolidatie/ConsolidatieController.java`
- Create: `app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/consolidatie/ConsolidatieControllerTest.java`

**Step 1: Schrijf falende tests**

```java
package be.vlaanderen.omgeving.bezwaarschriften.consolidatie;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ConsolidatieController.class)
@WithMockUser
class ConsolidatieControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockBean
  private ConsolidatieTaakService consolidatieTaakService;

  @MockBean
  private ConsolidatieWorker consolidatieWorker;

  @MockBean
  private AntwoordStatusService antwoordStatusService;

  @Test
  void geeftConsolidatieStatusPerDocument() throws Exception {
    when(antwoordStatusService.berekenAntwoordStatus("windmolens"))
        .thenReturn(Map.of(
            "bezwaar-001.txt", new AntwoordStatus(2, 3),
            "bezwaar-002.txt", new AntwoordStatus(2, 2)));
    when(consolidatieTaakService.geefTaken("windmolens"))
        .thenReturn(List.of(
            new ConsolidatieTaakDto(1L, "windmolens", "bezwaar-002.txt", "klaar",
                0, "2026-03-02T10:00:00Z", "2026-03-02T10:01:00Z", null)));

    mockMvc.perform(get("/api/v1/projects/windmolens/consolidaties"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.documenten[?(@.bestandsnaam=='bezwaar-001.txt')].antwoordenAantal").value(2))
        .andExpect(jsonPath("$.documenten[?(@.bestandsnaam=='bezwaar-001.txt')].antwoordenTotaal").value(3))
        .andExpect(jsonPath("$.documenten[?(@.bestandsnaam=='bezwaar-001.txt')].status").value("onvolledig"))
        .andExpect(jsonPath("$.documenten[?(@.bestandsnaam=='bezwaar-002.txt')].status").value("klaar"));
  }

  @Test
  void dientConsolidatieTakenIn() throws Exception {
    when(consolidatieTaakService.indienen("windmolens", List.of("bezwaar-001.txt")))
        .thenReturn(List.of(
            new ConsolidatieTaakDto(1L, "windmolens", "bezwaar-001.txt", "wachtend",
                0, "2026-03-02T10:00:00Z", null, null)));

    mockMvc.perform(post("/api/v1/projects/windmolens/consolidaties")
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"bestandsnamen\":[\"bezwaar-001.txt\"]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.taken[0].bestandsnaam").value("bezwaar-001.txt"))
        .andExpect(jsonPath("$.taken[0].status").value("wachtend"));
  }

  @Test
  void annuleertConsolidatieTaak() throws Exception {
    mockMvc.perform(delete("/api/v1/projects/windmolens/consolidaties/1")
            .with(csrf()))
        .andExpect(status().isNoContent());

    verify(consolidatieTaakService).verwijderTaak("windmolens", 1L);
    verify(consolidatieWorker).annuleerTaak(1L);
  }
}
```

**Step 2: Implementeer ConsolidatieController**

```java
package be.vlaanderen.omgeving.bezwaarschriften.consolidatie;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects")
public class ConsolidatieController {

  private final ConsolidatieTaakService consolidatieTaakService;
  private final ConsolidatieWorker consolidatieWorker;
  private final AntwoordStatusService antwoordStatusService;

  public ConsolidatieController(ConsolidatieTaakService consolidatieTaakService,
      ConsolidatieWorker consolidatieWorker,
      AntwoordStatusService antwoordStatusService) {
    this.consolidatieTaakService = consolidatieTaakService;
    this.consolidatieWorker = consolidatieWorker;
    this.antwoordStatusService = antwoordStatusService;
  }

  @GetMapping("/{naam}/consolidaties")
  public ResponseEntity<ConsolidatieResponse> geefStatus(@PathVariable String naam) {
    var antwoordStatus = antwoordStatusService.berekenAntwoordStatus(naam);
    var taken = consolidatieTaakService.geefTaken(naam);
    var taakPerBestand = taken.stream()
        .collect(Collectors.toMap(ConsolidatieTaakDto::bestandsnaam, t -> t, (a, b) -> b));

    var documenten = antwoordStatus.entrySet().stream()
        .map(entry -> {
          var bestandsnaam = entry.getKey();
          var status = entry.getValue();
          var taak = taakPerBestand.get(bestandsnaam);

          String consolidatieStatus;
          ConsolidatieTaakDto taakDto = null;
          if (taak != null) {
            consolidatieStatus = taak.status();
            taakDto = taak;
          } else if (status.isVolledig()) {
            consolidatieStatus = "volledig";
          } else {
            consolidatieStatus = "onvolledig";
          }

          return new DocumentConsolidatieStatus(
              bestandsnaam, status.aantalMetAntwoord(), status.totaal(),
              consolidatieStatus, taakDto);
        })
        .sorted(Comparator.comparing(DocumentConsolidatieStatus::bestandsnaam))
        .toList();

    return ResponseEntity.ok(new ConsolidatieResponse(documenten));
  }

  @PostMapping("/{naam}/consolidaties")
  public ResponseEntity<ConsolidatieTakenResponse> indienen(
      @PathVariable String naam, @RequestBody ConsolidatiesRequest request) {
    var taken = consolidatieTaakService.indienen(naam, request.bestandsnamen());
    return ResponseEntity.ok(new ConsolidatieTakenResponse(taken));
  }

  @DeleteMapping("/{naam}/consolidaties/{taakId}")
  public ResponseEntity<Void> annuleer(@PathVariable String naam, @PathVariable Long taakId) {
    try {
      consolidatieTaakService.verwijderTaak(naam, taakId);
      consolidatieWorker.annuleerTaak(taakId);
      return ResponseEntity.noContent().build();
    } catch (IllegalArgumentException e) {
      return ResponseEntity.notFound().build();
    }
  }

  record ConsolidatieResponse(List<DocumentConsolidatieStatus> documenten) {}
  record DocumentConsolidatieStatus(String bestandsnaam, int antwoordenAantal,
      int antwoordenTotaal, String status, ConsolidatieTaakDto taak) {}
  record ConsolidatieTakenResponse(List<ConsolidatieTaakDto> taken) {}
  record ConsolidatiesRequest(List<String> bestandsnamen) {}
}
```

**Step 3: Run tests**

```bash
cd app && mvn test -pl . -Dtest="ConsolidatieControllerTest" -Denforcer.skip=true
```

Expected: alle 3 tests PASS.

**Step 4: Run alle backend tests**

```bash
cd app && mvn test -Denforcer.skip=true
```

Expected: geen regressies.

**Step 5: Commit**

```bash
git add -A && git commit -m "feat: ConsolidatieController met GET/POST/DELETE endpoints"
```

---

## Task 8: Frontend — Resultaten-tabel component

Nieuw web component voor de resultaten-tabel.

**Files:**
- Create: `webapp/src/js/bezwaarschriften-resultaten-tabel.js`

**Step 1: Maak het component**

Kopieer `bezwaarschriften-bezwaren-tabel.js` en pas aan:

- Klassenaam: `BezwaarschriftenResultatenTabel`
- Component naam: `bezwaarschriften-resultaten-tabel`
- Property: `documenten` i.p.v. `bezwaren`
- Kolommen: selectie, bestandsnaam, antwoorden, status (geen aantalBezwaren, geen acties)
- `STATUS_LABELS`: `{onvolledig: 'Onvolledig', volledig: 'Volledig', wachtend: 'Wachtend', bezig: 'Bezig', klaar: 'Klaar', fout: 'Fout'}`
- `STATUS_PILL_TYPES`: `{onvolledig: '', volledig: '', wachtend: 'warning', bezig: 'warning', klaar: 'success', fout: 'error'}`
- Antwoorden-renderer: toont "X/N" gecentreerd (uit `rij.antwoordenAantal` / `rij.antwoordenTotaal`)
- Status-renderer: pill met inline knoppen:
  - `volledig`: ▶ (start-consolidatie event)
  - `wachtend`/`bezig`: × (annuleer event) + timer
  - `fout`: ↻ (herstart event)
  - `onvolledig`/`klaar`: geen knoppen
- Events: `start-consolidatie`, `annuleer-consolidatie`, `herstart-consolidatie`, `selectie-gewijzigd`

De template HTML bevat 4 `vl-rich-data-field` elementen:

```html
<vl-rich-data-field name="selectie" label=" "></vl-rich-data-field>
<vl-rich-data-field name="bestandsnaam" label="Bestandsnaam" sortable></vl-rich-data-field>
<vl-rich-data-field name="antwoorden" label="Antwoorden" sortable></vl-rich-data-field>
<vl-rich-data-field name="status" label="Status" sortable></vl-rich-data-field>
```

De sorteerlogica voor `antwoorden` sorteert op `rij.antwoordenAantal / rij.antwoordenTotaal` (fractie). De statusvolgorde is: onvolledig=0, volledig=1, wachtend=2, bezig=3, klaar=4, fout=5.

Het volledige component volgt exact het patroon van `bezwaarschriften-bezwaren-tabel.js` met bovenstaande aanpassingen. Hergebruik de timer-logica, selecteer-alles checkbox, paging, en filter-mechanisme.

**Step 2: Build en test**

```bash
cd webapp && npm run build && cd .. && mvn process-resources -pl webapp -Denforcer.skip=true
```

Expected: BUILD SUCCESS.

**Step 3: Commit**

```bash
git add webapp/src/js/bezwaarschriften-resultaten-tabel.js && git commit -m "feat: bezwaarschriften-resultaten-tabel component"
```

---

## Task 9: Frontend — Project-selectie uitbreiding met Resultaten-tab

Voeg de 3e tab toe en koppel alle events.

**Files:**
- Modify: `webapp/src/js/bezwaarschriften-project-selectie.js`

**Step 1: Importeer het nieuwe component en voeg de tab toe**

Bovenaan, voeg import toe:
```javascript
import './bezwaarschriften-resultaten-tabel.js';
```

In de constructor template, voeg een 3e `vl-tabs-pane` toe na de kernbezwaren-tab:

```html
<vl-tabs-pane id="resultaten" title="Resultaten">
  <vl-button id="consolideren-knop" hidden>Consolideren</vl-button>
  <bezwaarschriften-resultaten-tabel id="resultaten-tabel"></bezwaarschriften-resultaten-tabel>
</vl-tabs-pane>
```

**Step 2: Laad consolidatie-data bij project selectie**

In `_laadBezwaren`, na de bestaande `.then()` chain, voeg een call toe:
```javascript
this._laadConsolidaties(projectNaam);
```

Voeg methode toe:
```javascript
_laadConsolidaties(projectNaam) {
  fetch(`/api/v1/projects/${encodeURIComponent(projectNaam)}/consolidaties`)
    .then((response) => {
      if (!response.ok) return null;
      return response.json();
    })
    .then((data) => {
      if (data && data.documenten) {
        this.__consolidatieDocumenten = data.documenten;
        this._werkResultatenTabelBij();
        this._werkResultatenTabTitelBij();
        this._werkConsoliderenKnopBij();
      }
    })
    .catch(() => {/* stille fout bij laden consolidaties */});
}
```

**Step 3: Koppel consolidatie-events**

In `_koppelEventListeners()`, voeg toe:

```javascript
// Consolidatie-events
const consoliderenKnop = this.shadowRoot && this.shadowRoot.querySelector('#consolideren-knop');

this.shadowRoot.addEventListener('start-consolidatie', (e) => {
  const {bestandsnaam} = e.detail;
  if (this.__geselecteerdProject) {
    this._dienConsolidatiesIn(this.__geselecteerdProject, [bestandsnaam]);
  }
});

this.shadowRoot.addEventListener('herstart-consolidatie', (e) => {
  const {bestandsnaam} = e.detail;
  if (this.__geselecteerdProject) {
    this._dienConsolidatiesIn(this.__geselecteerdProject, [bestandsnaam]);
  }
});

this.shadowRoot.addEventListener('annuleer-consolidatie', (e) => {
  const {bestandsnaam, taakId} = e.detail;
  this._annuleerConsolidatieTaak(taakId);
});

if (consoliderenKnop) {
  consoliderenKnop.addEventListener('vl-click', () => {
    if (!this.__geselecteerdProject) return;
    const tabel = this.shadowRoot.querySelector('#resultaten-tabel');
    const geselecteerd = tabel ? tabel.geefGeselecteerdeBestandsnamen() : [];
    if (geselecteerd.length > 0) {
      this._dienConsolidatiesIn(this.__geselecteerdProject, geselecteerd);
    }
  });
}

// Bij antwoord-voortgang: refresh consolidaties
this.shadowRoot.addEventListener('antwoord-voortgang', (e) => {
  this._werkKernbezwarenTabTitelBij(e.detail.aantalMetAntwoord, e.detail.totaal);
  if (this.__geselecteerdProject) {
    this._laadConsolidaties(this.__geselecteerdProject);
  }
});
```

**Step 4: Voeg consolidatie API-methodes toe**

```javascript
_dienConsolidatiesIn(projectNaam, bestandsnamen) {
  fetch(`/api/v1/projects/${encodeURIComponent(projectNaam)}/consolidaties`, {
    method: 'POST',
    headers: {'Content-Type': 'application/json'},
    body: JSON.stringify({bestandsnamen}),
  })
    .then((response) => {
      if (!response.ok) throw new Error('Indienen consolidaties mislukt');
      return response.json();
    })
    .then((data) => {
      if (data && data.taken) {
        const tabel = this.shadowRoot.querySelector('#resultaten-tabel');
        if (tabel) {
          data.taken.forEach((taak) => tabel.werkBijMetTaakUpdate(taak));
        }
        this._werkResultatenTabTitelBij();
        this._werkConsoliderenKnopBij();
      }
    })
    .catch(() => {
      this._toonToast('error', 'Consolidatie kon niet worden ingediend.');
    });
}

_annuleerConsolidatieTaak(taakId) {
  if (!this.__geselecteerdProject) return;
  fetch(`/api/v1/projects/${encodeURIComponent(this.__geselecteerdProject)}/consolidaties/${taakId}`, {
    method: 'DELETE',
  })
    .then((response) => {
      if (!response.ok) throw new Error('Annuleren mislukt');
      this._toonToast('success', 'Consolidatie geannuleerd.');
      this._laadConsolidaties(this.__geselecteerdProject);
    })
    .catch(() => {
      this._toonToast('error', 'Annuleren van consolidatie mislukt.');
    });
}
```

**Step 5: Voeg WebSocket consolidatie-update afhandeling toe**

In `_verbindWebSocket`, de `onmessage` handler, voeg toe:

```javascript
if (data.type === 'consolidatie-update') {
  this._verwerkConsolidatieUpdate(data.taak);
}
```

Voeg methode toe:

```javascript
_verwerkConsolidatieUpdate(taak) {
  if (!this.__geselecteerdProject || taak.projectNaam !== this.__geselecteerdProject) {
    return;
  }
  const tabel = this.shadowRoot.querySelector('#resultaten-tabel');
  if (tabel) {
    tabel.werkBijMetTaakUpdate(taak);
  }
  this._werkResultatenTabTitelBij();
  this._werkConsoliderenKnopBij();
}
```

**Step 6: Voeg tab-titel en knop-helper methodes toe**

```javascript
_werkResultatenTabelBij() {
  const tabel = this.shadowRoot && this.shadowRoot.querySelector('#resultaten-tabel');
  if (tabel && this.__consolidatieDocumenten) {
    tabel.projectNaam = this.__geselecteerdProject;
    tabel.documenten = this.__consolidatieDocumenten;
  }
}

_werkResultatenTabTitelBij() {
  if (!this.__consolidatieDocumenten || this.__consolidatieDocumenten.length === 0) return;
  const totaal = this.__consolidatieDocumenten.length;
  const aantalKlaar = this.__consolidatieDocumenten.filter(
      (d) => d.status === 'klaar').length;
  const isBezig = this.__consolidatieDocumenten.some(
      (d) => d.status === 'wachtend' || d.status === 'bezig');
  const allesKlaar = aantalKlaar === totaal;

  let titel = `Resultaten (${aantalKlaar}/${totaal})`;
  if (allesKlaar) titel = `\u2714\uFE0F Resultaten (${totaal}/${totaal})`;
  if (isBezig) titel += ' \u23F3';

  const tabs = this.shadowRoot.querySelector('vl-tabs');
  const slot = tabs && tabs.shadowRoot &&
      tabs.shadowRoot.querySelector(`slot[name="resultaten-title-slot"]`);
  if (slot) {
    slot.innerHTML = titel;
    slot.style.color = allesKlaar ? '#0e7c3a' : '';
  }
}

_werkConsoliderenKnopBij() {
  const consoliderenKnop = this.shadowRoot && this.shadowRoot.querySelector('#consolideren-knop');
  if (!consoliderenKnop || !this.__consolidatieDocumenten) return;
  const tabel = this.shadowRoot.querySelector('#resultaten-tabel');
  const geselecteerd = tabel ? tabel.geefGeselecteerdeBestandsnamen() : [];
  if (geselecteerd.length > 0) {
    consoliderenKnop.hidden = false;
    consoliderenKnop.textContent = `Consolideren (${geselecteerd.length})`;
  } else {
    const aantalVolledig = this.__consolidatieDocumenten.filter(
        (d) => d.status === 'volledig' || d.status === 'fout').length;
    consoliderenKnop.hidden = aantalVolledig === 0;
    if (aantalVolledig > 0) {
      consoliderenKnop.textContent = `Consolideren (${aantalVolledig})`;
    }
  }
}
```

**Step 7: Initialiseer `__consolidatieDocumenten` in constructor**

In de constructor, voeg toe:
```javascript
this.__consolidatieDocumenten = [];
```

**Step 8: Build en test**

```bash
cd webapp && npm run build && cd .. && mvn process-resources -pl webapp -Denforcer.skip=true
```

Start de applicatie en verifieer:
1. Drie tabs zichtbaar: Documenten, Kernbezwaren, Resultaten
2. Resultaten-tab toont documenten met antwoorden X/N en status
3. "Consolideren" knop verschijnt bij documenten met status "volledig"
4. WebSocket-updates werken voor zowel extractie als consolidatie

**Step 9: Commit**

```bash
git add webapp/src/js/bezwaarschriften-project-selectie.js && git commit -m "feat: resultaten-tab met consolidatie-functionaliteit in project-selectie"
```

---

## Task 10: Integratie-test en cleanup

Verifieer de volledige flow end-to-end en fix eventuele issues.

**Step 1: Run alle backend tests**

```bash
cd app && mvn test -Denforcer.skip=true
```

Expected: alle tests PASS, geen regressies.

**Step 2: Build frontend**

```bash
cd webapp && npm run build && cd .. && mvn process-resources -pl webapp -Denforcer.skip=true
```

**Step 3: Start applicatie en test handmatig**

1. Upload bezwaarbestanden
2. Start extractie → wacht tot extractie-klaar
3. Groepeer bezwaren tot kernbezwaren
4. Ga naar Kernbezwaren-tab → voer antwoorden in voor alle kernbezwaren
5. Ga naar Resultaten-tab → verifieer X/N waarden en statussen
6. Start consolidatie voor een "volledig" document
7. Verifieer status-updates via WebSocket (wachtend → bezig → klaar)

**Step 4: Commit finale**

```bash
git add -A && git commit -m "feat: resultaten-tab met consolidatie-taaksysteem - integratie compleet"
```
