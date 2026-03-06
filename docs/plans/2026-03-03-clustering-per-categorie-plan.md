# Clustering per categorie — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Maak clustering per categorie onafhankelijk startbaar, annuleerbaar en verwijderbaar, met een async backend en pill-UI per categorie in accordion-headers.

**Architecture:** Nieuw `ClusteringTaak`-entiteit volgt exact het bestaande Worker-patroon (ExtractieWorker/ConsolidatieWorker). Elke categorie wordt een eigen taak met status-tracking in de database. WebSocket-notificaties pushen status-updates naar de frontend, die per categorie een pill met timer en actieknoppen toont in de accordion-header.

**Tech Stack:** Java 21 / Spring Boot 3.4 / JPA / Liquibase / WebSocket / @domg-wc componenten / Cypress component tests

---

### Task 1: Liquibase migratie voor `clustering_taak` tabel

**Files:**
- Create: `app/src/main/resources/db/changelog/20260303-clustering-taak.xml`
- Modify: `app/src/main/resources/db/changelog/db.changelog-master.xml` (include toevoegen)

**Step 1: Schrijf het migratiebestand**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
                   xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                   xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
                   http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-latest.xsd">

  <changeSet id="20260303-1" author="kenzo">
    <createTable tableName="clustering_taak">
      <column name="id" type="bigserial" autoIncrement="true">
        <constraints primaryKey="true" nullable="false"/>
      </column>
      <column name="project_naam" type="varchar(255)">
        <constraints nullable="false"/>
      </column>
      <column name="categorie" type="varchar(50)">
        <constraints nullable="false"/>
      </column>
      <column name="status" type="varchar(20)">
        <constraints nullable="false"/>
      </column>
      <column name="foutmelding" type="text"/>
      <column name="aangemaakt_op" type="timestamp with time zone">
        <constraints nullable="false"/>
      </column>
      <column name="verwerking_gestart_op" type="timestamp with time zone"/>
      <column name="verwerking_voltooid_op" type="timestamp with time zone"/>
      <column name="versie" type="int" defaultValueNumeric="0">
        <constraints nullable="false"/>
      </column>
    </createTable>
  </changeSet>

</databaseChangeLog>
```

**Step 2: Voeg include toe aan master changelog**

Voeg na de laatste `<include>` in `db.changelog-master.xml` toe:
```xml
<include file="db/changelog/20260303-clustering-taak.xml"/>
```

**Step 3: Voeg `deleteByProjectNaamAndNaam` toe aan ThemaRepository**

Nodig voor per-categorie verwijdering. Bestand: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/ThemaRepository.java`

```java
void deleteByProjectNaamAndNaam(String projectNaam, String naam);
```

**Step 4: Verifieer dat de applicatie start**

Run: `cd /Users/kenzo/Library/CloudStorage/Dropbox/1-Kenzo/4-Coding/bezwaarschriften && ./mvnw spring-boot:run -pl app -Dspring-boot.run.profiles=local`
Verwacht: Applicatie start zonder fouten, tabel wordt aangemaakt.

**Step 5: Commit**

```bash
git add app/src/main/resources/db/changelog/20260303-clustering-taak.xml app/src/main/resources/db/changelog/db.changelog-master.xml app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/ThemaRepository.java
git commit -m "feat: liquibase migratie voor clustering_taak tabel"
```

---

### Task 2: ClusteringTaak entiteit en repository

**Files:**
- Create: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/ClusteringTaakStatus.java`
- Create: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/ClusteringTaak.java`
- Create: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/ClusteringTaakRepository.java`

**Step 1: Schrijf de status enum**

```java
package be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar;

public enum ClusteringTaakStatus {
  WACHTEND,
  BEZIG,
  KLAAR,
  FOUT,
  GEANNULEERD
}
```

**Step 2: Schrijf de entiteit**

Volg het patroon van `ExtractieTaak.java` (zelfde package-structuur, @Entity, @Table, @Version):

```java
package be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "clustering_taak")
public class ClusteringTaak {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String projectNaam;

  @Column(nullable = false, length = 50)
  private String categorie;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private ClusteringTaakStatus status;

  @Column(columnDefinition = "text")
  private String foutmelding;

  @Column(nullable = false)
  private Instant aangemaaktOp;

  private Instant verwerkingGestartOp;

  private Instant verwerkingVoltooidOp;

  @Version
  @Column(nullable = false)
  private int versie;

  // Getters en setters voor alle velden
}
```

**Step 3: Schrijf de repository**

```java
package be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClusteringTaakRepository extends JpaRepository<ClusteringTaak, Long> {

  List<ClusteringTaak> findByProjectNaam(String projectNaam);

  Optional<ClusteringTaak> findByProjectNaamAndCategorie(String projectNaam, String categorie);

  List<ClusteringTaak> findByStatusOrderByAangemaaktOpAsc(ClusteringTaakStatus status);

  int countByStatus(ClusteringTaakStatus status);

  void deleteByProjectNaam(String projectNaam);
}
```

**Step 4: Verifieer compilatie**

Run: `cd /Users/kenzo/Library/CloudStorage/Dropbox/1-Kenzo/4-Coding/bezwaarschriften && ./mvnw compile -pl app`
Verwacht: BUILD SUCCESS

**Step 5: Commit**

```bash
git add app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/ClusteringTaakStatus.java app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/ClusteringTaak.java app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/ClusteringTaakRepository.java
git commit -m "feat: ClusteringTaak entiteit, status enum en repository"
```

---

### Task 3: ClusteringTaakDto en WebSocket-notificatie

**Files:**
- Create: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/ClusteringTaakDto.java`
- Create: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/ClusteringNotificatie.java`
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/TaakWebSocketHandler.java`

**Step 1: Schrijf de DTO**

```java
package be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar;

import java.time.Instant;

public record ClusteringTaakDto(
    Long id,
    String projectNaam,
    String categorie,
    String status,
    int aantalBezwaren,
    Integer aantalKernbezwaren,
    Instant aangemaaktOp,
    Instant verwerkingGestartOp,
    Instant verwerkingVoltooidOp,
    String foutmelding
) {
  public static ClusteringTaakDto van(ClusteringTaak taak, int aantalBezwaren, Integer aantalKernbezwaren) {
    return new ClusteringTaakDto(
        taak.getId(),
        taak.getProjectNaam(),
        taak.getCategorie(),
        taak.getStatus().name().toLowerCase(),
        aantalBezwaren,
        aantalKernbezwaren,
        taak.getAangemaaktOp(),
        taak.getVerwerkingGestartOp(),
        taak.getVerwerkingVoltooidOp(),
        taak.getFoutmelding()
    );
  }
}
```

**Step 2: Schrijf de notificatie-interface**

```java
package be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar;

public interface ClusteringNotificatie {
  void clusteringTaakGewijzigd(ClusteringTaakDto taak);
}
```

**Step 3: Implementeer in TaakWebSocketHandler**

Voeg `ClusteringNotificatie` toe aan de implements-clausule van `TaakWebSocketHandler` en voeg de methode toe:

```java
@Override
public void clusteringTaakGewijzigd(ClusteringTaakDto taak) {
    verstuur(Map.of("type", "clustering-update", "taak", taak));
}
```

**Step 4: Verifieer compilatie**

Run: `./mvnw compile -pl app`
Verwacht: BUILD SUCCESS

**Step 5: Commit**

```bash
git add app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/ClusteringTaakDto.java app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/ClusteringNotificatie.java app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/TaakWebSocketHandler.java
git commit -m "feat: ClusteringTaakDto en WebSocket-notificatie voor clustering-taken"
```

---

### Task 4: ClusteringTaakService — taak-lifecycle

**Files:**
- Create: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/ClusteringTaakService.java`
- Create: `app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/ClusteringTaakServiceTest.java`

**Step 1: Schrijf de falende test — taak indienen**

```java
package be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ClusteringTaakServiceTest {

  private ClusteringTaakRepository taakRepository;
  private GeextraheerdBezwaarRepository bezwaarRepository;
  private ThemaRepository themaRepository;
  private KernbezwaarRepository kernbezwaarRepository;
  private KernbezwaarAntwoordRepository antwoordRepository;
  private ClusteringNotificatie notificatie;
  private ClusteringTaakService service;

  @BeforeEach
  void setUp() {
    taakRepository = mock(ClusteringTaakRepository.class);
    bezwaarRepository = mock(GeextraheerdBezwaarRepository.class);
    themaRepository = mock(ThemaRepository.class);
    kernbezwaarRepository = mock(KernbezwaarRepository.class);
    antwoordRepository = mock(KernbezwaarAntwoordRepository.class);
    notificatie = mock(ClusteringNotificatie.class);
    service = new ClusteringTaakService(
        taakRepository, bezwaarRepository, themaRepository,
        kernbezwaarRepository, antwoordRepository, notificatie);
  }

  @Test
  void indienen_maaktTaakAanMetStatusWachtend() {
    when(taakRepository.findByProjectNaamAndCategorie("project", "Geluid"))
        .thenReturn(Optional.empty());
    when(taakRepository.save(any())).thenAnswer(inv -> {
      ClusteringTaak t = inv.getArgument(0);
      t.setId(1L);
      return t;
    });
    when(bezwaarRepository.countByProjectNaamAndCategorie("project", "Geluid"))
        .thenReturn(42);

    var dto = service.indienen("project", "Geluid");

    assertThat(dto.status()).isEqualTo("wachtend");
    assertThat(dto.categorie()).isEqualTo("Geluid");
    verify(notificatie).clusteringTaakGewijzigd(any());
  }
}
```

**Step 2: Run test om te verifiëren dat het faalt**

Run: `./mvnw test -pl app -Dtest=ClusteringTaakServiceTest#indienen_maaktTaakAanMetStatusWachtend -Dsurefire.failIfNoSpecifiedTests=false`
Verwacht: FAIL (class niet gevonden)

**Step 3: Schrijf de service**

```java
package be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar;

import be.vlaanderen.omgeving.bezwaarschriften.project.GeextraheerdBezwaarRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClusteringTaakService {

  private final ClusteringTaakRepository taakRepository;
  private final GeextraheerdBezwaarRepository bezwaarRepository;
  private final ThemaRepository themaRepository;
  private final KernbezwaarRepository kernbezwaarRepository;
  private final KernbezwaarAntwoordRepository antwoordRepository;
  private final ClusteringNotificatie notificatie;

  public ClusteringTaakService(
      ClusteringTaakRepository taakRepository,
      GeextraheerdBezwaarRepository bezwaarRepository,
      ThemaRepository themaRepository,
      KernbezwaarRepository kernbezwaarRepository,
      KernbezwaarAntwoordRepository antwoordRepository,
      ClusteringNotificatie notificatie) {
    this.taakRepository = taakRepository;
    this.bezwaarRepository = bezwaarRepository;
    this.themaRepository = themaRepository;
    this.kernbezwaarRepository = kernbezwaarRepository;
    this.antwoordRepository = antwoordRepository;
    this.notificatie = notificatie;
  }

  @Transactional
  public ClusteringTaakDto indienen(String projectNaam, String categorie) {
    // Verwijder bestaande taak als die er is
    taakRepository.findByProjectNaamAndCategorie(projectNaam, categorie)
        .ifPresent(taakRepository::delete);

    // Verwijder bestaand thema voor deze categorie (cascade ruimt kernbezwaren op)
    themaRepository.deleteByProjectNaamAndNaam(projectNaam, categorie);

    var taak = new ClusteringTaak();
    taak.setProjectNaam(projectNaam);
    taak.setCategorie(categorie);
    taak.setStatus(ClusteringTaakStatus.WACHTEND);
    taak.setAangemaaktOp(Instant.now());
    taak = taakRepository.save(taak);

    int aantalBezwaren = bezwaarRepository.countByProjectNaamAndCategorie(projectNaam, categorie);
    var dto = ClusteringTaakDto.van(taak, aantalBezwaren, null);
    notificatie.clusteringTaakGewijzigd(dto);
    return dto;
  }

  @Transactional
  public List<ClusteringTaakStatus> pakOpVoorVerwerking(int maxConcurrent) {
    int bezigAantal = taakRepository.countByStatus(ClusteringTaakStatus.BEZIG);
    int beschikbaar = maxConcurrent - bezigAantal;
    if (beschikbaar <= 0) return List.of();

    var wachtend = taakRepository.findByStatusOrderByAangemaaktOpAsc(ClusteringTaakStatus.WACHTEND);
    var opgepakt = new ArrayList<ClusteringTaak>();

    for (int i = 0; i < Math.min(beschikbaar, wachtend.size()); i++) {
      var taak = wachtend.get(i);
      taak.setStatus(ClusteringTaakStatus.BEZIG);
      taak.setVerwerkingGestartOp(Instant.now());
      taak = taakRepository.save(taak);
      opgepakt.add(taak);

      int aantalBezwaren = bezwaarRepository.countByProjectNaamAndCategorie(
          taak.getProjectNaam(), taak.getCategorie());
      notificatie.clusteringTaakGewijzigd(ClusteringTaakDto.van(taak, aantalBezwaren, null));
    }
    return opgepakt.stream().map(ClusteringTaak::getStatus).toList();
  }

  @Transactional
  public void markeerKlaar(Long taakId) {
    taakRepository.findById(taakId).ifPresent(taak -> {
      taak.setStatus(ClusteringTaakStatus.KLAAR);
      taak.setVerwerkingVoltooidOp(Instant.now());
      taak = taakRepository.save(taak);

      int aantalBezwaren = bezwaarRepository.countByProjectNaamAndCategorie(
          taak.getProjectNaam(), taak.getCategorie());
      var themaOpt = themaRepository.findByProjectNaamAndNaam(
          taak.getProjectNaam(), taak.getCategorie());
      Integer aantalKernbezwaren = themaOpt
          .map(t -> kernbezwaarRepository.countByThemaId(t.getId()))
          .orElse(null);
      notificatie.clusteringTaakGewijzigd(
          ClusteringTaakDto.van(taak, aantalBezwaren, aantalKernbezwaren));
    });
  }

  @Transactional
  public void markeerFout(Long taakId, String foutmelding) {
    taakRepository.findById(taakId).ifPresent(taak -> {
      taak.setStatus(ClusteringTaakStatus.FOUT);
      taak.setFoutmelding(foutmelding);
      taak.setVerwerkingVoltooidOp(Instant.now());
      taak = taakRepository.save(taak);

      int aantalBezwaren = bezwaarRepository.countByProjectNaamAndCategorie(
          taak.getProjectNaam(), taak.getCategorie());
      notificatie.clusteringTaakGewijzigd(ClusteringTaakDto.van(taak, aantalBezwaren, null));
    });
  }

  public boolean isGeannuleerd(Long taakId) {
    return taakRepository.findById(taakId)
        .map(t -> t.getStatus() == ClusteringTaakStatus.GEANNULEERD)
        .orElse(true);
  }

  @Transactional
  public boolean annuleer(Long taakId) {
    return taakRepository.findById(taakId)
        .filter(t -> t.getStatus() == ClusteringTaakStatus.WACHTEND
            || t.getStatus() == ClusteringTaakStatus.BEZIG)
        .map(taak -> {
          taak.setStatus(ClusteringTaakStatus.GEANNULEERD);
          taak.setVerwerkingVoltooidOp(Instant.now());
          taak = taakRepository.save(taak);

          int aantalBezwaren = bezwaarRepository.countByProjectNaamAndCategorie(
              taak.getProjectNaam(), taak.getCategorie());
          notificatie.clusteringTaakGewijzigd(ClusteringTaakDto.van(taak, aantalBezwaren, null));
          return true;
        })
        .orElse(false);
  }

  public List<ClusteringTaakDto> geefTaken(String projectNaam) {
    var taken = taakRepository.findByProjectNaam(projectNaam);
    return taken.stream().map(taak -> {
      int aantalBezwaren = bezwaarRepository.countByProjectNaamAndCategorie(
          projectNaam, taak.getCategorie());
      var themaOpt = themaRepository.findByProjectNaamAndNaam(projectNaam, taak.getCategorie());
      Integer aantalKernbezwaren = themaOpt
          .map(t -> kernbezwaarRepository.countByThemaId(t.getId()))
          .orElse(null);
      return ClusteringTaakDto.van(taak, aantalBezwaren, aantalKernbezwaren);
    }).toList();
  }

  @Transactional
  public VerwijderResultaat verwijderClustering(String projectNaam, String categorie, boolean bevestigd) {
    // Check of er antwoorden zijn
    var themaOpt = themaRepository.findByProjectNaamAndNaam(projectNaam, categorie);
    if (themaOpt.isPresent()) {
      var kernIds = kernbezwaarRepository.findByThemaId(themaOpt.get().getId())
          .stream().map(KernbezwaarEntiteit::getId).toList();
      long aantalAntwoorden = antwoordRepository.countByKernbezwaarIdIn(kernIds);
      if (aantalAntwoorden > 0 && !bevestigd) {
        return VerwijderResultaat.bevestigingNodig(aantalAntwoorden);
      }
    }

    // Verwijder thema (cascade ruimt kernbezwaren, referenties, antwoorden op)
    themaRepository.deleteByProjectNaamAndNaam(projectNaam, categorie);

    // Verwijder clustering-taak
    taakRepository.findByProjectNaamAndCategorie(projectNaam, categorie)
        .ifPresent(taakRepository::delete);

    return VerwijderResultaat.verwijderd();
  }
}
```

**Step 4: Voeg ontbrekende repository-methoden toe**

In `GeextraheerdBezwaarRepository.java`:
```java
int countByProjectNaamAndCategorie(String projectNaam, String categorie);
```

Waar `projectNaam` wordt afgeleid via de taak — we moeten hier een custom query gebruiken. Kijk eerst naar het bestaande `findByProjectNaam` patroon om te zien hoe projectNaam geresolved wordt (via join op extractie_taak).

In `ThemaRepository.java`:
```java
Optional<ThemaEntiteit> findByProjectNaamAndNaam(String projectNaam, String naam);
```

In `KernbezwaarRepository.java`:
```java
int countByThemaId(Long themaId);
List<KernbezwaarEntiteit> findByThemaId(Long themaId);
```

In `KernbezwaarAntwoordRepository.java`:
```java
long countByKernbezwaarIdIn(List<Long> kernbezwaarIds);
```

Maak ook het `VerwijderResultaat` record:
```java
package be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar;

public record VerwijderResultaat(boolean verwijderd, boolean bevestigingNodig, long aantalAntwoorden) {
  static VerwijderResultaat verwijderd() { return new VerwijderResultaat(true, false, 0); }
  static VerwijderResultaat bevestigingNodig(long aantalAntwoorden) {
    return new VerwijderResultaat(false, true, aantalAntwoorden);
  }
}
```

**Step 5: Voeg extra tests toe aan ClusteringTaakServiceTest**

```java
@Test
void indienen_verwijdertBestaandeTaakEnThema() {
    var bestaandeTaak = new ClusteringTaak();
    bestaandeTaak.setId(5L);
    when(taakRepository.findByProjectNaamAndCategorie("project", "Geluid"))
        .thenReturn(Optional.of(bestaandeTaak));
    when(taakRepository.save(any())).thenAnswer(inv -> {
      ClusteringTaak t = inv.getArgument(0);
      t.setId(6L);
      return t;
    });
    when(bezwaarRepository.countByProjectNaamAndCategorie("project", "Geluid")).thenReturn(10);

    service.indienen("project", "Geluid");

    verify(taakRepository).delete(bestaandeTaak);
    verify(themaRepository).deleteByProjectNaamAndNaam("project", "Geluid");
}

@Test
void annuleer_zetStatusOpGeannuleerd() {
    var taak = new ClusteringTaak();
    taak.setId(1L);
    taak.setProjectNaam("project");
    taak.setCategorie("Geluid");
    taak.setStatus(ClusteringTaakStatus.BEZIG);
    when(taakRepository.findById(1L)).thenReturn(Optional.of(taak));
    when(taakRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(bezwaarRepository.countByProjectNaamAndCategorie("project", "Geluid")).thenReturn(10);

    boolean result = service.annuleer(1L);

    assertThat(result).isTrue();
    assertThat(taak.getStatus()).isEqualTo(ClusteringTaakStatus.GEANNULEERD);
}

@Test
void verwijderClustering_vraagBevestigingBijAntwoorden() {
    var thema = new ThemaEntiteit();
    thema.setId(10L);
    when(themaRepository.findByProjectNaamAndNaam("project", "Geluid"))
        .thenReturn(Optional.of(thema));

    var kern = new KernbezwaarEntiteit();
    kern.setId(100L);
    when(kernbezwaarRepository.findByThemaId(10L)).thenReturn(List.of(kern));
    when(antwoordRepository.countByKernbezwaarIdIn(List.of(100L))).thenReturn(2L);

    var resultaat = service.verwijderClustering("project", "Geluid", false);

    assertThat(resultaat.bevestigingNodig()).isTrue();
    assertThat(resultaat.aantalAntwoorden()).isEqualTo(2);
    verify(themaRepository, never()).deleteByProjectNaamAndNaam(any(), any());
}

@Test
void verwijderClustering_verwijdertBijBevestiging() {
    var thema = new ThemaEntiteit();
    thema.setId(10L);
    when(themaRepository.findByProjectNaamAndNaam("project", "Geluid"))
        .thenReturn(Optional.of(thema));

    var kern = new KernbezwaarEntiteit();
    kern.setId(100L);
    when(kernbezwaarRepository.findByThemaId(10L)).thenReturn(List.of(kern));
    when(antwoordRepository.countByKernbezwaarIdIn(List.of(100L))).thenReturn(2L);

    var taak = new ClusteringTaak();
    taak.setId(1L);
    when(taakRepository.findByProjectNaamAndCategorie("project", "Geluid"))
        .thenReturn(Optional.of(taak));

    var resultaat = service.verwijderClustering("project", "Geluid", true);

    assertThat(resultaat.verwijderd()).isTrue();
    verify(themaRepository).deleteByProjectNaamAndNaam("project", "Geluid");
    verify(taakRepository).delete(taak);
}
```

**Step 6: Run tests**

Run: `./mvnw test -pl app -Dtest=ClusteringTaakServiceTest`
Verwacht: Alle tests PASS

**Step 7: Commit**

```bash
git add app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/ClusteringTaakService.java app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/VerwijderResultaat.java app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/ClusteringTaakServiceTest.java
git add app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/GeextraheerdBezwaarRepository.java app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/ThemaRepository.java app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/KernbezwaarRepository.java app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/KernbezwaarAntwoordRepository.java
git commit -m "feat: ClusteringTaakService met lifecycle-management en tests"
```

---

### Task 5: ClusteringWorker — async verwerking

**Files:**
- Create: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/ClusteringWorker.java`
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/config/ExtractieConfig.java` (toevoegen clusteringExecutor bean)
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/KernbezwaarService.java` (methode per categorie aanpassen)

**Step 1: Voeg clusteringExecutor bean toe aan ExtractieConfig**

```java
@Bean
public ThreadPoolTaskExecutor clusteringExecutor(
    @Value("${bezwaarschriften.clustering.max-concurrent:2}") int maxConcurrent) {
  var executor = new ThreadPoolTaskExecutor();
  executor.setCorePoolSize(maxConcurrent);
  executor.setMaxPoolSize(maxConcurrent);
  executor.setThreadNamePrefix("clustering-");
  executor.initialize();
  return executor;
}
```

**Step 2: Refactor KernbezwaarService.clusterCategorie naar publieke methode**

Pas `KernbezwaarService.clusterCategorie()` aan zodat deze:
- Publiek is en alleen projectNaam + categorie als parameters accepteert
- Zelf de bezwaren ophaalt en lookups bouwt
- De annuleringsstatus checkt via `ClusteringTaakService.isGeannuleerd(taakId)` op strategische punten (na embedding, na clustering)
- Een `ClusteringGeannuleerdException` gooit als de taak geannuleerd is
- Bestaande functionaliteit exact behouden voor het clusteren zelf

De methode signatuur wordt:
```java
@Transactional
public void clusterEenCategorie(String projectNaam, String categorie, Long taakId) {
    // Check annulering
    if (clusteringTaakService.isGeannuleerd(taakId)) {
      throw new ClusteringGeannuleerdException();
    }

    var alleBezwaren = bezwaarRepository.findByProjectNaamAndCategorie(projectNaam, categorie);
    // ... bestaande logica van clusterCategorie ...
    // Check annulering na embedding-generatie
    if (clusteringTaakService.isGeannuleerd(taakId)) {
      throw new ClusteringGeannuleerdException();
    }
    // ... clustering en opslag ...
}
```

Maak ook `ClusteringGeannuleerdException`:
```java
package be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar;

public class ClusteringGeannuleerdException extends RuntimeException {
  public ClusteringGeannuleerdException() {
    super("Clustering is geannuleerd");
  }
}
```

Pas de bestaande `groepeer()` methode aan om `clusterEenCategorie` te hergebruiken, of markeer als deprecated.

**Step 3: Schrijf ClusteringWorker**

Volg exact het patroon van `ExtractieWorker`:

```java
package be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

@Component
public class ClusteringWorker {

  private static final Logger log = LoggerFactory.getLogger(ClusteringWorker.class);

  private final ClusteringTaakService taakService;
  private final ClusteringTaakRepository taakRepository;
  private final KernbezwaarService kernbezwaarService;
  private final ThreadPoolTaskExecutor clusteringExecutor;
  private final ConcurrentHashMap<Long, Future<?>> actieveTaken = new ConcurrentHashMap<>();

  public ClusteringWorker(
      ClusteringTaakService taakService,
      ClusteringTaakRepository taakRepository,
      KernbezwaarService kernbezwaarService,
      ThreadPoolTaskExecutor clusteringExecutor) {
    this.taakService = taakService;
    this.taakRepository = taakRepository;
    this.kernbezwaarService = kernbezwaarService;
    this.clusteringExecutor = clusteringExecutor;
  }

  @Scheduled(fixedDelay = 1000)
  public void verwerkTaken() {
    var wachtend = taakRepository.findByStatusOrderByAangemaaktOpAsc(
        ClusteringTaakStatus.WACHTEND);
    int bezigAantal = taakRepository.countByStatus(ClusteringTaakStatus.BEZIG);
    int beschikbaar = clusteringExecutor.getCorePoolSize() - bezigAantal;

    for (int i = 0; i < Math.min(beschikbaar, wachtend.size()); i++) {
      var taak = wachtend.get(i);
      if (actieveTaken.containsKey(taak.getId())) continue;

      taak.setStatus(ClusteringTaakStatus.BEZIG);
      taak.setVerwerkingGestartOp(java.time.Instant.now());
      taakRepository.save(taak);

      // Notificatie via service
      var future = clusteringExecutor.submit(() -> verwerkTaak(taak));
      actieveTaken.put(taak.getId(), future);
    }
  }

  public boolean annuleerTaak(Long taakId) {
    var future = actieveTaken.remove(taakId);
    if (future != null) {
      future.cancel(true);
      return true;
    }
    return false;
  }

  private void verwerkTaak(ClusteringTaak taak) {
    try {
      kernbezwaarService.clusterEenCategorie(
          taak.getProjectNaam(), taak.getCategorie(), taak.getId());
      taakService.markeerKlaar(taak.getId());
    } catch (ClusteringGeannuleerdException e) {
      log.info("Clustering geannuleerd voor categorie {} in project {}",
          taak.getCategorie(), taak.getProjectNaam());
    } catch (Exception e) {
      log.error("Clustering mislukt voor categorie {} in project {}",
          taak.getCategorie(), taak.getProjectNaam(), e);
      taakService.markeerFout(taak.getId(), e.getMessage());
    } finally {
      actieveTaken.remove(taak.getId());
    }
  }
}
```

**Step 4: Verifieer compilatie en bestaande tests**

Run: `./mvnw test -pl app`
Verwacht: Alle tests PASS

**Step 5: Commit**

```bash
git add app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/ClusteringWorker.java app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/ClusteringGeannuleerdException.java app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/config/ExtractieConfig.java app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/KernbezwaarService.java
git commit -m "feat: ClusteringWorker met async verwerking en annuleringsondersteuning"
```

---

### Task 6: KernbezwaarService refactor — per-categorie clustering

**Files:**
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/KernbezwaarService.java`
- Modify: `app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/KernbezwaarServiceTest.java`
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/GeextraheerdBezwaarRepository.java`

**Step 1: Voeg `findByProjectNaamAndCategorie` toe aan GeextraheerdBezwaarRepository**

Kijk hoe `findByProjectNaam` werkt (custom @Query met join op extractie_taak) en maak een vergelijkbare query met extra WHERE op categorie.

**Step 2: Schrijf falende test voor clusterEenCategorie**

In `KernbezwaarServiceTest.java`:

```java
@Test
void clusterEenCategorie_clustertAlleenBezwarenVanDieCategorie() {
    // Arrange: bezwaren in categorie "Geluid"
    // Mock embeddingPoort, clusteringPoort, repositories
    // Act: kernbezwaarService.clusterEenCategorie("project", "Geluid", 1L)
    // Assert: alleen Geluid-bezwaren verwerkt, thema aangemaakt met naam "Geluid"
}

@Test
void clusterEenCategorie_stoptBijAnnulering() {
    // Mock clusteringTaakService.isGeannuleerd(1L) → true
    // Assert: ClusteringGeannuleerdException gegooid
}
```

**Step 3: Implementeer clusterEenCategorie**

Refactor de bestaande `clusterCategorie` private methode:
- Maak publiek als `clusterEenCategorie(String projectNaam, String categorie, Long taakId)`
- Haal bezwaren op met `findByProjectNaamAndCategorie`
- Bouw lookups zelf (niet meer als parameter)
- Voeg annuleringscheck toe na embedding-generatie en na clustering
- Verwijder bestaand thema voor deze categorie voordat nieuw thema wordt aangemaakt

**Step 4: Pas de bestaande `groepeer()` methode aan**

De synchrone `groepeer()` blijft bestaan maar delegeert nu naar `clusterEenCategorie` per categorie (zonder taakId/annuleringscheck — `null` doorgeven en checks skippen bij null).

**Step 5: Run alle tests**

Run: `./mvnw test -pl app`
Verwacht: Alle tests PASS

**Step 6: Commit**

```bash
git add app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/KernbezwaarService.java app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/KernbezwaarServiceTest.java app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/GeextraheerdBezwaarRepository.java
git commit -m "refactor: KernbezwaarService ondersteunt per-categorie clustering met annulering"
```

---

### Task 7: ClusteringTaakController — REST endpoints

**Files:**
- Create: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/ClusteringTaakController.java`

**Step 1: Schrijf de controller**

```java
package be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar;

import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/projects")
public class ClusteringTaakController {

  private final ClusteringTaakService taakService;
  private final ClusteringWorker worker;
  private final GeextraheerdBezwaarRepository bezwaarRepository;

  public ClusteringTaakController(
      ClusteringTaakService taakService,
      ClusteringWorker worker,
      GeextraheerdBezwaarRepository bezwaarRepository) {
    this.taakService = taakService;
    this.worker = worker;
    this.bezwaarRepository = bezwaarRepository;
  }

  @GetMapping("/{naam}/clustering-taken")
  public ResponseEntity<Map<String, Object>> geefClusteringTaken(@PathVariable String naam) {
    // Haal alle unieke categorieën op uit bezwaren
    var categorien = bezwaarRepository.findDistinctCategorienByProjectNaam(naam);
    var taken = taakService.geefTaken(naam);

    // Maak DTOs voor categorieën zonder taak (status: todo)
    var taakPerCategorie = new java.util.HashMap<String, ClusteringTaakDto>();
    taken.forEach(t -> taakPerCategorie.put(t.categorie(), t));

    var resultaat = categorien.stream().map(cat -> {
      if (taakPerCategorie.containsKey(cat)) {
        return taakPerCategorie.get(cat);
      }
      int aantalBezwaren = bezwaarRepository.countByProjectNaamAndCategorie(naam, cat);
      return new ClusteringTaakDto(null, naam, cat, "todo", aantalBezwaren, null,
          null, null, null, null);
    }).toList();

    return ResponseEntity.ok(Map.of("categorien", resultaat));
  }

  @PostMapping("/{naam}/clustering-taken/{categorie}")
  public ResponseEntity<ClusteringTaakDto> startClustering(
      @PathVariable String naam, @PathVariable String categorie) {
    var dto = taakService.indienen(naam, categorie);
    return ResponseEntity.accepted().body(dto);
  }

  @PostMapping("/{naam}/clustering-taken")
  public ResponseEntity<Map<String, Object>> startAlleClustering(@PathVariable String naam) {
    var categorien = bezwaarRepository.findDistinctCategorienByProjectNaam(naam);
    var bestaandeTaken = taakService.geefTaken(naam);
    var actieveCategorieen = bestaandeTaken.stream()
        .filter(t -> "klaar".equals(t.status()) || "bezig".equals(t.status())
            || "wachtend".equals(t.status()))
        .map(ClusteringTaakDto::categorie)
        .collect(java.util.stream.Collectors.toSet());

    var gestart = categorien.stream()
        .filter(cat -> !actieveCategorieen.contains(cat))
        .map(cat -> taakService.indienen(naam, cat))
        .toList();

    return ResponseEntity.accepted().body(Map.of("gestart", gestart));
  }

  @DeleteMapping("/{naam}/clustering-taken/{categorie}")
  public ResponseEntity<?> verwijderOfAnnuleer(
      @PathVariable String naam, @PathVariable String categorie,
      @RequestParam(defaultValue = "false") boolean bevestigd) {
    // Probeer eerst te annuleren als het actief is
    var taakOpt = taakService.geefTaken(naam).stream()
        .filter(t -> t.categorie().equals(categorie))
        .findFirst();

    if (taakOpt.isPresent()) {
      var taak = taakOpt.get();
      if ("wachtend".equals(taak.status()) || "bezig".equals(taak.status())) {
        if (taak.id() != null) {
          worker.annuleerTaak(taak.id());
          taakService.annuleer(taak.id());
        }
        return ResponseEntity.noContent().build();
      }
    }

    // Anders: verwijder clustering-resultaat
    var resultaat = taakService.verwijderClustering(naam, categorie, bevestigd);
    if (resultaat.bevestigingNodig()) {
      return ResponseEntity.status(409)
          .body(Map.of("aantalAntwoorden", resultaat.aantalAntwoorden()));
    }
    return ResponseEntity.noContent().build();
  }
}
```

**Step 2: Voeg `findDistinctCategorienByProjectNaam` toe aan repository**

In `GeextraheerdBezwaarRepository.java`:
```java
@Query("SELECT DISTINCT g.categorie FROM GeextraheerdBezwaarEntiteit g " +
       "JOIN ExtractieTaak t ON g.taakId = t.id WHERE t.projectNaam = :projectNaam")
List<String> findDistinctCategorienByProjectNaam(@Param("projectNaam") String projectNaam);
```

**Step 3: Verifieer compilatie**

Run: `./mvnw compile -pl app`
Verwacht: BUILD SUCCESS

**Step 4: Commit**

```bash
git add app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/ClusteringTaakController.java app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/GeextraheerdBezwaarRepository.java
git commit -m "feat: ClusteringTaakController met REST endpoints voor per-categorie clustering"
```

---

### Task 8: Frontend — kernbezwaren-component refactor voor per-categorie pills

**Files:**
- Modify: `webapp/src/js/bezwaarschriften-kernbezwaren.js`

**Step 1: Voeg nieuwe state en imports toe**

Voeg bovenaan `VlPillComponent` import toe:
```javascript
import {VlPillComponent} from '@domg-wc/components/block/pill/vl-pill.component.js';
```

En voeg `VlPillComponent` toe aan `registerWebComponents(...)`.

Nieuwe state in constructor:
```javascript
this._clusteringTaken = []; // Array van ClusteringTaakDto per categorie
this._timerInterval = null;
```

**Step 2: Voeg WebSocket-listener toe**

De parent-component (`bezwaarschriften-projecten-overzicht.js`) handelt WebSocket-berichten af. Voeg een publieke methode toe:

```javascript
werkBijMetClusteringUpdate(taak) {
    const idx = this._clusteringTaken.findIndex(t => t.categorie === taak.categorie);
    if (idx >= 0) {
      this._clusteringTaken[idx] = taak;
    } else {
      this._clusteringTaken.push(taak);
    }
    this._renderInhoud();
    this._beheerTimer();
}
```

**Step 3: Voeg methode toe om clustering-taken op te halen**

```javascript
laadClusteringTaken(projectNaam) {
    this._projectNaam = projectNaam;
    fetch(`/api/v1/projects/${encodeURIComponent(projectNaam)}/clustering-taken`)
        .then(r => r.ok ? r.json() : null)
        .then(data => {
          if (data) {
            this._clusteringTaken = data.categorien;
            this._renderInhoud();
            this._beheerTimer();
          }
        });
}
```

**Step 4: Refactor _renderInhoud voor per-categorie weergave**

Vervang de huidige lege-staat en groepeer-knop logica:

```javascript
_renderInhoud() {
    const inhoud = this.shadowRoot.querySelector('#inhoud');
    if (!inhoud) return;
    inhoud.innerHTML = '';

    if (this._clusteringTaken.length === 0 && this._aantalBezwaren === 0) {
      inhoud.innerHTML = `<div class="lege-staat">
        <p>Nog geen bezwaren geëxtraheerd. Verwerk eerst documenten op de Documenten-tab.</p>
      </div>`;
      return;
    }

    // Header met globale knop
    const header = document.createElement('div');
    header.className = 'clustering-header';
    const globaleKnop = document.createElement('vl-button');
    globaleKnop.textContent = 'Cluster alle categorieën';
    globaleKnop.addEventListener('click', () => this._clusterAlles());
    header.appendChild(globaleKnop);
    inhoud.appendChild(header);

    // Per categorie een accordion
    this._clusteringTaken.forEach(taak => {
      const accordion = this._maakCategorieAccordion(taak);
      inhoud.appendChild(accordion);
    });
}
```

**Step 5: Maak pill + actieknop rendering per categorie**

```javascript
_maakCategorieAccordion(taak) {
    const wrapper = document.createElement('div');
    wrapper.className = 'categorie-wrapper';

    // Accordion header met pill en actieknop
    const headerEl = document.createElement('div');
    headerEl.className = 'categorie-header';

    const label = document.createElement('span');
    label.className = 'categorie-label';
    label.textContent = `${taak.categorie} (${taak.aantalBezwaren} bezwaren)`;

    headerEl.appendChild(label);
    headerEl.appendChild(this._maakStatusPill(taak));
    headerEl.appendChild(this._maakActieKnop(taak));
    wrapper.appendChild(headerEl);

    // Accordion inhoud alleen bij status 'klaar'
    if (taak.status === 'klaar') {
      const accordion = document.createElement('vl-accordion');
      const kernLabel = taak.aantalKernbezwaren === 1
          ? '1 kernbezwaar' : `${taak.aantalKernbezwaren} kernbezwaren`;
      accordion.setAttribute('toggle-text', `${taak.categorie} (${kernLabel})`);
      accordion.setAttribute('default-open', '');
      // Inhoud wordt geladen vanuit _themas data
      const thema = this._themas?.find(t => t.naam === taak.categorie);
      if (thema) {
        const kernWrapper = document.createElement('div');
        thema.kernbezwaren.forEach(kern => {
          kernWrapper.appendChild(this._maakKernbezwaarItem(kern));
        });
        accordion.appendChild(kernWrapper);
      }
      wrapper.appendChild(accordion);
    }

    return wrapper;
}
```

**Step 6: Maak pill-helper en actieknop-helper**

```javascript
_maakStatusPill(taak) {
    const pill = document.createElement('vl-pill');
    pill.style.fontVariantNumeric = 'tabular-nums';
    pill.style.minWidth = '180px';
    pill.style.display = 'inline-block';
    pill.dataset.categorie = taak.categorie;

    switch (taak.status) {
      case 'wachtend': case 'bezig':
        pill.setAttribute('type', 'warning');
        const span = document.createElement('span');
        span.className = 'timer-tekst';
        span.textContent = this._formatClusteringStatus(taak);
        pill.appendChild(span);
        break;
      case 'klaar':
        pill.setAttribute('type', 'success');
        pill.textContent = this._formatClusteringStatus(taak);
        break;
      case 'fout':
        pill.setAttribute('type', 'error');
        pill.textContent = 'Fout';
        break;
      default: // todo
        pill.textContent = 'Te clusteren';
    }
    return pill;
}

_maakActieKnop(taak) {
    switch (taak.status) {
      case 'todo':
        return this._maakPillKnop('\u25b6', 'Clustering starten',
            () => this._startClustering(taak.categorie));
      case 'wachtend': case 'bezig':
        return this._maakPillKnop('\u00d7', 'Clustering annuleren',
            () => this._annuleerClustering(taak.categorie));
      case 'klaar':
        return this._maakPillKnop('\u{1F5D1}', 'Clustering verwijderen',
            () => this._verwijderClustering(taak.categorie));
      case 'fout':
        return this._maakPillKnop('\u21bb', 'Opnieuw proberen',
            () => this._startClustering(taak.categorie));
      default:
        return document.createElement('span');
    }
}
```

**Step 7: Voeg API-aanroepen toe**

```javascript
_startClustering(categorie) {
    fetch(`/api/v1/projects/${encodeURIComponent(this._projectNaam)}/clustering-taken/${encodeURIComponent(categorie)}`, {
      method: 'POST',
    }).then(r => r.ok ? r.json() : null)
      .then(dto => { if (dto) this.werkBijMetClusteringUpdate(dto); });
}

_annuleerClustering(categorie) {
    fetch(`/api/v1/projects/${encodeURIComponent(this._projectNaam)}/clustering-taken/${encodeURIComponent(categorie)}`, {
      method: 'DELETE',
    }).then(() => this.laadClusteringTaken(this._projectNaam));
}

_verwijderClustering(categorie, bevestigd = false) {
    const url = `/api/v1/projects/${encodeURIComponent(this._projectNaam)}/clustering-taken/${encodeURIComponent(categorie)}${bevestigd ? '?bevestigd=true' : ''}`;
    fetch(url, { method: 'DELETE' })
        .then(response => {
          if (response.status === 409) {
            return response.json().then(data => {
              this._toonVerwijderBevestiging(categorie, data.aantalAntwoorden);
            });
          }
          this.laadClusteringTaken(this._projectNaam);
          this.laadKernbezwaren(this._projectNaam);
        });
}

_clusterAlles() {
    fetch(`/api/v1/projects/${encodeURIComponent(this._projectNaam)}/clustering-taken`, {
      method: 'POST',
    }).then(() => this.laadClusteringTaken(this._projectNaam));
}
```

**Step 8: Voeg timer-management toe (hergebruik bezwaren-tabel patroon)**

```javascript
_beheerTimer() {
    const heeftActief = this._clusteringTaken.some(
        t => t.status === 'wachtend' || t.status === 'bezig');
    if (heeftActief && !this._timerInterval) {
      this._timerInterval = setInterval(() => this._updateTimers(), 1000);
    } else if (!heeftActief && this._timerInterval) {
      clearInterval(this._timerInterval);
      this._timerInterval = null;
    }
}

_updateTimers() {
    const nu = Date.now();
    this._clusteringTaken.forEach(taak => {
      if (taak.status !== 'wachtend' && taak.status !== 'bezig') return;
      const pill = this.shadowRoot.querySelector(
          `vl-pill[data-categorie="${CSS.escape(taak.categorie)}"]`);
      if (!pill) return;
      const timerTekst = pill.querySelector('.timer-tekst');
      if (timerTekst) {
        timerTekst.textContent = this._formatClusteringStatus(taak, nu);
      }
    });
}

_formatClusteringStatus(taak, nu) {
    nu = nu || Date.now();
    if (taak.status === 'wachtend' && taak.aangemaaktOp) {
      const wachtMs = nu - new Date(taak.aangemaaktOp).getTime();
      return `Wachtend (${this._formatTijd(wachtMs)})`;
    }
    if (taak.status === 'bezig') {
      const wachtMs = taak.verwerkingGestartOp && taak.aangemaaktOp
          ? new Date(taak.verwerkingGestartOp).getTime() - new Date(taak.aangemaaktOp).getTime()
          : 0;
      const verwerkMs = taak.verwerkingGestartOp
          ? nu - new Date(taak.verwerkingGestartOp).getTime()
          : 0;
      return `Bezig (${this._formatTijd(wachtMs)} + ${this._formatTijd(verwerkMs)})`;
    }
    if (taak.status === 'klaar' && taak.verwerkingGestartOp && taak.verwerkingVoltooidOp) {
      const wachtMs = new Date(taak.verwerkingGestartOp).getTime() - new Date(taak.aangemaaktOp).getTime();
      const verwerkMs = new Date(taak.verwerkingVoltooidOp).getTime() - new Date(taak.verwerkingGestartOp).getTime();
      return `Klaar (${this._formatTijd(wachtMs)} + ${this._formatTijd(verwerkMs)})`;
    }
    return taak.status;
}

_formatTijd(ms) {
    const totaalSeconden = Math.floor(ms / 1000);
    const minuten = Math.floor(totaalSeconden / 60);
    const seconden = totaalSeconden % 60;
    return `${minuten}:${String(seconden).padStart(2, '0')}`;
}
```

**Step 9: Voeg CSS toe voor de nieuwe layout**

In de `<style>` sectie:
```css
.clustering-header {
    display: flex;
    justify-content: flex-end;
    margin-bottom: 1.5rem;
}
.categorie-wrapper {
    margin-bottom: 1rem;
}
.categorie-header {
    display: flex;
    align-items: center;
    gap: 1rem;
    padding: 0.75rem 0;
    border-bottom: 1px solid #e8ebee;
}
.categorie-label {
    flex: 1;
    font-weight: bold;
}
```

**Step 10: Voeg bevestigingsmodal toe voor verwijderen**

Hergebruik het bestaande modal-patroon (al in de shadow DOM template):

```javascript
_toonVerwijderBevestiging(categorie, aantalAntwoorden) {
    const modal = this.shadowRoot.querySelector('#consolidatie-waarschuwing');
    const inhoud = this.shadowRoot.querySelector('#consolidatie-waarschuwing-inhoud');
    const bevestigKnop = this.shadowRoot.querySelector('#consolidatie-waarschuwing-bevestig');
    if (!modal || !inhoud) return;

    inhoud.innerHTML = '';
    const p = document.createElement('p');
    p.textContent = `Er ${aantalAntwoorden === 1 ? 'is' : 'zijn'} ${aantalAntwoorden} antwoord${aantalAntwoorden === 1 ? '' : 'en'} geschreven voor kernbezwaren in categorie "${categorie}". Deze worden verwijderd.`;
    inhoud.appendChild(p);

    const bevestigHandler = () => {
      bevestigKnop.removeEventListener('vl-click', bevestigHandler);
      modal.off('close', sluitHandler);
      modal.close();
      this._verwijderClustering(categorie, true);
    };
    const sluitHandler = () => {
      bevestigKnop.removeEventListener('vl-click', bevestigHandler);
      modal.off('close', sluitHandler);
    };

    bevestigKnop.addEventListener('vl-click', bevestigHandler);
    modal.on('close', sluitHandler);
    modal.open();
}
```

**Step 11: Build en verifieer**

Run: `cd /Users/kenzo/Library/CloudStorage/Dropbox/1-Kenzo/4-Coding/bezwaarschriften/webapp && npm run build`
Verwacht: Build slaagt

**Step 12: Commit**

```bash
git add webapp/src/js/bezwaarschriften-kernbezwaren.js
git commit -m "feat: frontend per-categorie clustering UI met pills en timers"
```

---

### Task 9: Frontend — WebSocket integratie in parent component

**Files:**
- Modify: `webapp/src/js/bezwaarschriften-projecten-overzicht.js` (of de pagina die WebSocket-berichten ontvangt)

**Step 1: Voeg clustering-update handler toe aan WebSocket listener**

Zoek de bestaande WebSocket message handler die `taak-update` en `consolidatie-update` afhandelt. Voeg `clustering-update` toe:

```javascript
case 'clustering-update':
    const kernbezwarenTab = /* referentie naar bezwaarschriften-kernbezwaren element */;
    if (kernbezwarenTab) {
      kernbezwarenTab.werkBijMetClusteringUpdate(bericht.taak);
    }
    break;
```

**Step 2: Roep `laadClusteringTaken` aan wanneer de kernbezwaren-tab wordt geopend**

Bij tab-switch naar kernbezwaren, roep zowel `laadKernbezwaren` als `laadClusteringTaken` aan.

**Step 3: Build en verifieer**

Run: `cd /Users/kenzo/Library/CloudStorage/Dropbox/1-Kenzo/4-Coding/bezwaarschriften/webapp && npm run build`
Verwacht: Build slaagt

**Step 4: Commit**

```bash
git add webapp/src/js/bezwaarschriften-projecten-overzicht.js
git commit -m "feat: WebSocket clustering-update integratie in parent component"
```

---

### Task 10: Cypress tests — kernbezwaren clustering per categorie

**Files:**
- Modify: `webapp/test/bezwaarschriften-kernbezwaren.cy.js`

**Step 1: Schrijf testdata**

```javascript
const MOCK_CLUSTERING_TAKEN = {
  categorien: [
    { id: 1, categorie: 'Mobiliteit', status: 'klaar', aantalBezwaren: 42,
      aantalKernbezwaren: 3, aangemaaktOp: '2026-03-03T10:00:00Z',
      verwerkingGestartOp: '2026-03-03T10:00:01Z',
      verwerkingVoltooidOp: '2026-03-03T10:00:15Z' },
    { id: null, categorie: 'Milieu', status: 'todo', aantalBezwaren: 18,
      aantalKernbezwaren: null, aangemaaktOp: null,
      verwerkingGestartOp: null, verwerkingVoltooidOp: null },
    { id: 3, categorie: 'Geluid', status: 'bezig', aantalBezwaren: 25,
      aangemaaktOp: '2026-03-03T10:01:00Z',
      verwerkingGestartOp: '2026-03-03T10:01:02Z',
      verwerkingVoltooidOp: null, aantalKernbezwaren: null },
    { id: 4, categorie: 'Natuur', status: 'fout', aantalBezwaren: 6,
      aantalKernbezwaren: null, aangemaaktOp: '2026-03-03T10:00:00Z',
      verwerkingGestartOp: '2026-03-03T10:00:01Z',
      verwerkingVoltooidOp: '2026-03-03T10:00:03Z',
      foutmelding: 'Onvoldoende bezwaren voor clustering' },
  ],
};
```

**Step 2: Test pill-rendering per status**

```javascript
describe('bezwaarschriften-kernbezwaren — clustering per categorie', () => {
  beforeEach(() => {
    cy.intercept('GET', '/api/v1/projects/*/clustering-taken', {
      statusCode: 200, body: MOCK_CLUSTERING_TAKEN,
    }).as('getClusteringTaken');

    cy.intercept('GET', '/api/v1/projects/*/kernbezwaren', {
      statusCode: 200, body: MOCK_KERNBEZWAREN,
    }).as('getKernbezwaren');

    cy.mount(html`<bezwaarschriften-kernbezwaren></bezwaarschriften-kernbezwaren>`);
    cy.get('bezwaarschriften-kernbezwaren').then(el => {
      el[0].laadClusteringTaken('testproject');
      el[0].laadKernbezwaren('testproject');
    });
    cy.wait('@getClusteringTaken');
  });

  it('toont success-pill voor klare clustering', () => {
    cy.get('bezwaarschriften-kernbezwaren')
        .find('vl-pill[data-categorie="Mobiliteit"]')
        .should('have.attr', 'type', 'success');
  });

  it('toont play-knop voor todo categorie', () => {
    cy.get('bezwaarschriften-kernbezwaren')
        .find('vl-pill[data-categorie="Milieu"]')
        .should('contain.text', 'Te clusteren');
  });

  it('toont warning-pill met timer voor bezig categorie', () => {
    cy.get('bezwaarschriften-kernbezwaren')
        .find('vl-pill[data-categorie="Geluid"]')
        .should('have.attr', 'type', 'warning');
  });

  it('toont error-pill voor fout categorie', () => {
    cy.get('bezwaarschriften-kernbezwaren')
        .find('vl-pill[data-categorie="Natuur"]')
        .should('have.attr', 'type', 'error');
  });
});
```

**Step 3: Test start clustering**

```javascript
it('start clustering bij klik op play-knop', () => {
    cy.intercept('POST', '/api/v1/projects/testproject/clustering-taken/Milieu', {
      statusCode: 202,
      body: { id: 5, categorie: 'Milieu', status: 'wachtend', aantalBezwaren: 18 },
    }).as('startClustering');

    // Klik play-knop bij Milieu
    cy.get('bezwaarschriften-kernbezwaren')
        .find('.categorie-header')
        .contains('Milieu')
        .parent()
        .find('button[title="Clustering starten"]')
        .click();

    cy.wait('@startClustering');
});
```

**Step 4: Test verwijder clustering met bevestiging**

```javascript
it('toont bevestigingsmodal bij verwijderen clustering met antwoorden', () => {
    cy.intercept('DELETE', '/api/v1/projects/testproject/clustering-taken/Mobiliteit', {
      statusCode: 409,
      body: { aantalAntwoorden: 2 },
    }).as('verwijderClustering');

    cy.get('bezwaarschriften-kernbezwaren')
        .find('.categorie-header')
        .contains('Mobiliteit')
        .parent()
        .find('button[title="Clustering verwijderen"]')
        .click();

    cy.wait('@verwijderClustering');

    cy.get('bezwaarschriften-kernbezwaren')
        .find('#consolidatie-waarschuwing')
        .should('exist');
});
```

**Step 5: Test globale knop**

```javascript
it('cluster alle knop start alle niet-klare categorieen', () => {
    cy.intercept('POST', '/api/v1/projects/testproject/clustering-taken', {
      statusCode: 202,
      body: { gestart: [] },
    }).as('clusterAlles');

    cy.get('bezwaarschriften-kernbezwaren')
        .find('.clustering-header vl-button')
        .contains('Cluster alle categorieën')
        .click();

    cy.wait('@clusterAlles');
});
```

**Step 6: Run Cypress tests**

Run: `cd /Users/kenzo/Library/CloudStorage/Dropbox/1-Kenzo/4-Coding/bezwaarschriften/webapp && npx cypress run --component --spec test/bezwaarschriften-kernbezwaren.cy.js`
Verwacht: Alle tests PASS

**Step 7: Commit**

```bash
git add webapp/test/bezwaarschriften-kernbezwaren.cy.js
git commit -m "test: Cypress tests voor per-categorie clustering UI"
```

---

### Task 11: Backend build en integratietest

**Files:**
- Bestaande testbestanden

**Step 1: Run volledige backend build**

Run: `./mvnw clean verify -pl app`
Verwacht: BUILD SUCCESS, alle tests groen

**Step 2: Run volledige frontend build**

Run: `cd /Users/kenzo/Library/CloudStorage/Dropbox/1-Kenzo/4-Coding/bezwaarschriften/webapp && npm run build && cd .. && mvn process-resources -pl webapp -Denforcer.skip=true`
Verwacht: Succesvol

**Step 3: Run alle Cypress tests**

Run: `cd /Users/kenzo/Library/CloudStorage/Dropbox/1-Kenzo/4-Coding/bezwaarschriften/webapp && npx cypress run --component`
Verwacht: Alle tests PASS

**Step 4: Commit (indien fixes nodig waren)**

---

### Task 12: Terminologie-update — "clustering" i.p.v. "groepeer"

**Files:**
- Modify: `webapp/src/js/bezwaarschriften-kernbezwaren.js` (reeds gedaan in Task 8)
- Verify: geen resterende "groepeer" teksten in de UI

**Step 1: Zoek naar resterende "groepeer" referenties**

Zoek in alle frontend-bestanden naar "groepeer", "groepering", "Groepeer", "Groepering" en vervang door clustering-equivalenten waar nodig.

**Step 2: Verifieer dat de bestaande "groepeer" endpoint nog werkt**

De `POST /kernbezwaren/groepeer` endpoint blijft bestaan voor backwards-compatibiliteit maar wordt niet meer vanuit de frontend aangeroepen.

**Step 3: Commit**

```bash
git add -A
git commit -m "refactor: terminologie-update naar 'clustering' in hele UI"
```
