# Centroid Matching Noise Post-Processing Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Na HDBSCAN-clustering worden noise-bezwaren via centroid matching automatisch of handmatig aan kernbezwaren toegewezen. Categorieen en thema's worden verwijderd als abstractielaag.

**Architecture:** CentroidMatchingService (puur vectorwiskunde) wordt aangeroepen na HDBSCAN in KernbezwaarService. Datamodel vereenvoudigt van Project->Thema->Kernbezwaar naar Project->Kernbezwaar. Frontend toont toewijzingsmethode-badges, paginering (15/pagina), en handmatige toewijzing via dropdown met top-5 suggesties.

**Tech Stack:** Java 21, Spring Boot 3.x, JPA/Hibernate, Liquibase, Lit web components (@domg-wc), Cypress

**Design:** `docs/plans/2026-03-06-centroid-matching-noise-design.md`

---

## Task 1: Liquibase migratie — schema-wijzigingen

**Files:**
- Create: `app/src/main/resources/config/liquibase/changelog/20260306-centroid-matching.xml`
- Modify: `app/src/main/resources/config/liquibase/master.xml`

**Step 1: Schrijf de migratie**

```xml
<?xml version="1.0" encoding="utf-8"?>
<databaseChangeLog
    xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-3.1.xsd">

  <!-- 1. Voeg project_naam toe aan kernbezwaar (gevuld vanuit thema) -->
  <changeSet id="20260306-cm-1" author="kenzo">
    <addColumn tableName="kernbezwaar">
      <column name="project_naam" type="varchar(255)"/>
    </addColumn>
    <sql>
      UPDATE kernbezwaar k
      SET project_naam = (SELECT t.project_naam FROM thema t WHERE t.id = k.thema_id)
    </sql>
    <addNotNullConstraint tableName="kernbezwaar" columnName="project_naam"/>
  </changeSet>

  <!-- 2. Voeg toewijzingsmethode toe aan kernbezwaar_referentie -->
  <changeSet id="20260306-cm-2" author="kenzo">
    <addColumn tableName="kernbezwaar_referentie">
      <column name="toewijzingsmethode" type="varchar(20)" defaultValue="HDBSCAN">
        <constraints nullable="false"/>
      </column>
    </addColumn>
  </changeSet>

  <!-- 3. Verwijder categorie van geextraheerd_bezwaar -->
  <changeSet id="20260306-cm-3" author="kenzo">
    <dropColumn tableName="geextraheerd_bezwaar" columnName="categorie"/>
  </changeSet>

  <!-- 4. Verwijder categorie van clustering_taak -->
  <changeSet id="20260306-cm-4" author="kenzo">
    <dropColumn tableName="clustering_taak" columnName="categorie"/>
  </changeSet>

  <!-- 5. Drop FK en thema_id van kernbezwaar, drop thema tabel -->
  <changeSet id="20260306-cm-5" author="kenzo">
    <dropForeignKeyConstraint baseTableName="kernbezwaar"
        constraintName="fk_kernbezwaar_thema"/>
    <dropColumn tableName="kernbezwaar" columnName="thema_id"/>
    <dropTable tableName="thema"/>
  </changeSet>

</databaseChangeLog>
```

**Step 2: Voeg toe aan master.xml**

Voeg na de laatste `<include>` in `app/src/main/resources/config/liquibase/master.xml` toe:

```xml
  <include file="config/liquibase/changelog/20260306-centroid-matching.xml"/>
```

**Step 3: Commit**

```bash
git add app/src/main/resources/config/liquibase/
git commit -m "feat: liquibase migratie voor centroid matching en verwijdering categorieen/thema's"
```

---

## Task 2: ToewijzingsMethode enum + entity-updates

**Files:**
- Create: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/ToewijzingsMethode.java`
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/KernbezwaarReferentieEntiteit.java`
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/IndividueelBezwaarReferentie.java`
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/KernbezwaarEntiteit.java`

**Step 1: Maak ToewijzingsMethode enum**

```java
package be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar;

public enum ToewijzingsMethode {
  HDBSCAN, CENTROID_FALLBACK, MANUEEL
}
```

**Step 2: Update KernbezwaarReferentieEntiteit**

Voeg toe aan de entiteit:

```java
@Enumerated(EnumType.STRING)
@Column(name = "toewijzingsmethode", nullable = false, length = 20)
private ToewijzingsMethode toewijzingsmethode = ToewijzingsMethode.HDBSCAN;
```

Plus getter/setter.

**Step 3: Update KernbezwaarEntiteit**

Vervang `themaId` door `projectNaam`:

```java
// VERWIJDER:
@Column(name = "thema_id", nullable = false)
private Long themaId;

// VERVANG DOOR:
@Column(name = "project_naam", nullable = false)
private String projectNaam;
```

Plus getter/setter aanpassen.

**Step 4: Update IndividueelBezwaarReferentie record**

```java
public record IndividueelBezwaarReferentie(
    Long bezwaarId,
    String bestandsnaam,
    String passage,
    Integer scorePercentage,
    ToewijzingsMethode toewijzingsmethode) {}
```

**Step 5: Commit**

```bash
git add app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/ToewijzingsMethode.java \
       app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/KernbezwaarReferentieEntiteit.java \
       app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/IndividueelBezwaarReferentie.java \
       app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/KernbezwaarEntiteit.java
git commit -m "feat: ToewijzingsMethode enum en entity-updates voor centroid matching"
```

---

## Task 3: CentroidMatchingService — tests + implementatie

**Files:**
- Create: `app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/clustering/CentroidMatchingServiceTest.java`
- Create: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/clustering/CentroidMatchingService.java`

**Step 1: Schrijf de testen**

De service is puur vectorwiskunde. Test cases:

```java
@ExtendWith(MockitoExtension.class)
class CentroidMatchingServiceTest {

  private CentroidMatchingService service;

  @BeforeEach
  void setUp() {
    service = new CentroidMatchingService();
  }

  @Test
  void noiseBovenDrempelWordtToegewezen() {
    // Cluster met centroid [1,0,0], noise-bezwaar [0.99,0.1,0]
    // Cosine similarity ~0.995 -> boven drempel 0.85
    var clusters = List.of(
        new Cluster(1, List.of(10L), new float[]{1f, 0f, 0f}));
    var noiseEmbeddings = Map.of(20L, new float[]{0.99f, 0.1f, 0f});
    var resultaat = service.wijsNoiseToe(clusters, noiseEmbeddings, 0.85);
    assertThat(resultaat.toegewezenPerCluster().get(1)).contains(20L);
    assertThat(resultaat.resterendeNoise()).isEmpty();
    assertThat(resultaat.toewijzingen().get(20L).methode())
        .isEqualTo(ToewijzingsMethode.CENTROID_FALLBACK);
  }

  @Test
  void noiseOnderDrempelBlijftNoise() {
    // Cluster met centroid [1,0,0], noise-bezwaar [0,1,0]
    // Cosine similarity = 0.0 -> onder drempel 0.85
    var clusters = List.of(
        new Cluster(1, List.of(10L), new float[]{1f, 0f, 0f}));
    var noiseEmbeddings = Map.of(30L, new float[]{0f, 1f, 0f});
    var resultaat = service.wijsNoiseToe(clusters, noiseEmbeddings, 0.85);
    assertThat(resultaat.toegewezenPerCluster()).isEmpty();
    assertThat(resultaat.resterendeNoise()).contains(30L);
  }

  @Test
  void noiseWordtAanDichtstbijzijndeClusterToegewezen() {
    // Twee clusters, noise is dichter bij cluster 2
    var clusters = List.of(
        new Cluster(1, List.of(10L), new float[]{1f, 0f, 0f}),
        new Cluster(2, List.of(11L), new float[]{0f, 1f, 0f}));
    var noiseEmbeddings = Map.of(20L, new float[]{0.1f, 0.99f, 0f});
    var resultaat = service.wijsNoiseToe(clusters, noiseEmbeddings, 0.85);
    assertThat(resultaat.toegewezenPerCluster().get(2)).contains(20L);
    assertThat(resultaat.toegewezenPerCluster().containsKey(1)).isFalse();
  }

  @Test
  void legeNoiseGeeftLeegResultaat() {
    var clusters = List.of(
        new Cluster(1, List.of(10L), new float[]{1f, 0f, 0f}));
    var resultaat = service.wijsNoiseToe(clusters, Map.of(), 0.85);
    assertThat(resultaat.toegewezenPerCluster()).isEmpty();
    assertThat(resultaat.resterendeNoise()).isEmpty();
  }

  @Test
  void legeClustersLatenAlleNoiseStaan() {
    var noiseEmbeddings = Map.of(20L, new float[]{1f, 0f, 0f});
    var resultaat = service.wijsNoiseToe(List.of(), noiseEmbeddings, 0.85);
    assertThat(resultaat.resterendeNoise()).contains(20L);
  }

  @Test
  void berekenTop5GeeftGesorteerdeResultaten() {
    var bezwaarEmbedding = new float[]{0.9f, 0.1f, 0f};
    var centroids = Map.of(
        1L, new float[]{1f, 0f, 0f},
        2L, new float[]{0f, 1f, 0f},
        3L, new float[]{0.8f, 0.2f, 0f});
    var suggesties = service.berekenTop5Suggesties(bezwaarEmbedding, centroids);
    assertThat(suggesties).hasSizeLessThanOrEqualTo(5);
    // Eerste suggestie moet cluster 1 of 3 zijn (dichtstbij)
    assertThat(suggesties.get(0).score()).isGreaterThan(suggesties.get(1).score());
  }
}
```

**Step 2: Run testen, verifieer dat ze falen**

```bash
mvn test -pl app -Dtest=CentroidMatchingServiceTest -DfailIfNoTests=false
```

Expected: FAIL (klasse bestaat nog niet)

**Step 3: Implementeer CentroidMatchingService**

```java
package be.vlaanderen.omgeving.bezwaarschriften.clustering;

import be.vlaanderen.omgeving.bezwaarschriften.clustering.ClusteringPoort.Cluster;
import be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar.ToewijzingsMethode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class CentroidMatchingService {

  public CentroidMatchingResultaat wijsNoiseToe(
      List<Cluster> clusters,
      Map<Long, float[]> noiseEmbeddings,
      double threshold) {

    var toegewezenPerCluster = new HashMap<Integer, List<Long>>();
    var resterendeNoise = new ArrayList<Long>();
    var toewijzingen = new HashMap<Long, Toewijzing>();

    for (var entry : noiseEmbeddings.entrySet()) {
      Long bezwaarId = entry.getKey();
      float[] embedding = entry.getValue();

      int besteCluster = -1;
      double hoogsteScore = Double.NEGATIVE_INFINITY;

      for (var cluster : clusters) {
        double score = cosinusGelijkenis(embedding, cluster.centroid());
        if (score > hoogsteScore) {
          hoogsteScore = score;
          besteCluster = cluster.label();
        }
      }

      if (besteCluster >= 0 && hoogsteScore >= threshold) {
        toegewezenPerCluster
            .computeIfAbsent(besteCluster, k -> new ArrayList<>())
            .add(bezwaarId);
        toewijzingen.put(bezwaarId,
            new Toewijzing(ToewijzingsMethode.CENTROID_FALLBACK, hoogsteScore));
      } else {
        resterendeNoise.add(bezwaarId);
      }
    }

    return new CentroidMatchingResultaat(
        toegewezenPerCluster, resterendeNoise, toewijzingen);
  }

  public List<Suggestie> berekenTop5Suggesties(
      float[] bezwaarEmbedding, Map<Long, float[]> centroidsPerKernbezwaar) {
    return centroidsPerKernbezwaar.entrySet().stream()
        .map(e -> new Suggestie(e.getKey(),
            cosinusGelijkenis(bezwaarEmbedding, e.getValue())))
        .sorted(Comparator.comparingDouble(Suggestie::score).reversed())
        .limit(5)
        .toList();
  }

  private double cosinusGelijkenis(float[] a, float[] b) {
    double dot = 0.0;
    double normA = 0.0;
    double normB = 0.0;
    for (int i = 0; i < a.length; i++) {
      dot += a[i] * b[i];
      normA += a[i] * a[i];
      normB += b[i] * b[i];
    }
    double deler = Math.sqrt(normA) * Math.sqrt(normB);
    return deler == 0.0 ? 0.0 : dot / deler;
  }

  public record CentroidMatchingResultaat(
      Map<Integer, List<Long>> toegewezenPerCluster,
      List<Long> resterendeNoise,
      Map<Long, Toewijzing> toewijzingen) {}

  public record Toewijzing(ToewijzingsMethode methode, double score) {}

  public record Suggestie(Long kernbezwaarId, double score) {}
}
```

**Step 4: Run testen, verifieer dat ze slagen**

```bash
mvn test -pl app -Dtest=CentroidMatchingServiceTest
```

Expected: alle 6 testen PASS

**Step 5: Commit**

```bash
git add app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/clustering/CentroidMatchingServiceTest.java \
       app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/clustering/CentroidMatchingService.java
git commit -m "feat: CentroidMatchingService met tests voor noise post-processing"
```

---

## Task 4: ClusteringConfig — threshold toevoegen

**Files:**
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/clustering/ClusteringConfig.java`
- Modify: `app/src/main/resources/application-dev.yml` (of `application.yml`)

**Step 1: Voeg centroidMatchingThreshold toe**

In `ClusteringConfig.java`, voeg toe:

```java
private double centroidMatchingThreshold = 0.85;

public double getCentroidMatchingThreshold() {
  return centroidMatchingThreshold;
}

public void setCentroidMatchingThreshold(double centroidMatchingThreshold) {
  this.centroidMatchingThreshold = centroidMatchingThreshold;
}
```

**Step 2: Optioneel toevoegen aan dev config**

In `application-dev.yml`:

```yaml
bezwaarschriften:
  clustering:
    centroid-matching-threshold: 0.85
```

**Step 3: Commit**

```bash
git add app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/clustering/ClusteringConfig.java
git commit -m "feat: centroid-matching-threshold configuratie (default 0.85)"
```

---

## Task 5: Verwijder Thema-laag uit domein

**Files:**
- Delete: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/ThemaEntiteit.java`
- Delete: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/ThemaRepository.java`
- Delete: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/Thema.java`
- Delete: `app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/ThemaTest.java`
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/KernbezwaarRepository.java`
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/KernbezwaarReferentieRepository.java`

**Step 1: Update KernbezwaarRepository**

Vervang `themaId`-queries door `projectNaam`:

```java
public interface KernbezwaarRepository extends JpaRepository<KernbezwaarEntiteit, Long> {

  List<KernbezwaarEntiteit> findByProjectNaam(String projectNaam);

  int countByProjectNaam(String projectNaam);

  @Modifying(clearAutomatically = true)
  @Query("DELETE FROM KernbezwaarEntiteit k "
      + "WHERE k.projectNaam = :projectNaam "
      + "AND k.id NOT IN ("
      + "  SELECT DISTINCT r.kernbezwaarId FROM KernbezwaarReferentieEntiteit r)")
  void deleteZonderReferenties(@Param("projectNaam") String projectNaam);

  void deleteByProjectNaam(String projectNaam);
}
```

**Step 2: Verwijder ThemaEntiteit, ThemaRepository, Thema record, ThemaTest**

Verwijder de bestanden:
- `app/src/main/java/.../kernbezwaar/ThemaEntiteit.java`
- `app/src/main/java/.../kernbezwaar/ThemaRepository.java`
- `app/src/main/java/.../kernbezwaar/Thema.java`
- `app/src/test/java/.../kernbezwaar/ThemaTest.java`

**Step 3: Commit**

```bash
git add -A
git commit -m "refactor: verwijder Thema-laag, kernbezwaar direct gekoppeld aan project"
```

**Opmerking:** Na deze task compileert het project nog NIET. Tasks 6-8 lossen de compile-errors op.

---

## Task 6: Verwijder categorie uit GeextraheerdBezwaarEntiteit + repository

**Files:**
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/GeextraheerdBezwaarEntiteit.java`
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/GeextraheerdBezwaarRepository.java`

**Step 1: Verwijder categorie veld uit entiteit**

In `GeextraheerdBezwaarEntiteit.java`: verwijder het `categorie` veld, getter en setter.

**Step 2: Vereenvoudig repository queries**

In `GeextraheerdBezwaarRepository.java`:
- Verwijder `countByProjectNaamAndCategorie`
- Verwijder `findByProjectNaamAndCategorie`
- Verwijder `findDistinctCategorienByProjectNaam`
- Voeg toe: `countByProjectNaam` (analoog aan `findByProjectNaam` maar count)

```java
@Query("SELECT COUNT(b) FROM GeextraheerdBezwaarEntiteit b "
    + "WHERE b.taakId IN ("
    + "  SELECT t.id FROM ExtractieTaak t "
    + "  WHERE t.projectNaam = :projectNaam AND t.status = 'KLAAR')")
int countByProjectNaam(@Param("projectNaam") String projectNaam);
```

**Step 3: Commit**

```bash
git add app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/
git commit -m "refactor: verwijder categorie uit GeextraheerdBezwaarEntiteit en repository"
```

---

## Task 7: Verwijder categorie uit ClusteringTaak + vereenvoudig service/worker/controller

**Files:**
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/ClusteringTaak.java`
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/ClusteringTaakDto.java`
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/ClusteringTaakRepository.java`
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/ClusteringTaakService.java`
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/ClusteringWorker.java`
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/ClusteringTaakController.java`

**Step 1: ClusteringTaak — verwijder categorie**

Verwijder het `categorie` veld + getter/setter uit `ClusteringTaak.java`.

**Step 2: ClusteringTaakDto — verwijder categorie**

Verwijder `categorie` uit het record. Update `van()` factory method.

**Step 3: ClusteringTaakRepository — vereenvoudig**

- Verwijder `findByProjectNaamAndCategorie`
- Verwijder `deleteZonderThema`
- Voeg toe: `Optional<ClusteringTaak> findByProjectNaam(String projectNaam)` (retourneert enkel element, want een taak per project)

Let op: de bestaande `findByProjectNaam` retourneert `List`. Verander naar `Optional` of hernoem.

**Step 4: ClusteringTaakService — vereenvoudig**

Kernwijzigingen:
- `indienen(projectNaam, categorie)` wordt `indienen(projectNaam)`
- Verwijder `geefCategorieOverzicht()`
- Verwijder `indienenAlleNietActieve()`
- Verwijder `CategorieStatus` record
- `geefTaken()` retourneert de enkele taak voor het project
- `verwijderClustering()` verliest `categorie` parameter
- `verwijderAlleClusteringen()` → `verwijderClustering()` (er is nog maar een)
- `markeerKlaar()` en `markeerFout()` gebruiken `countByProjectNaam` i.p.v. categorie-count
- Alle verwijzingen naar `ThemaRepository` verwijderen; vervangen door `KernbezwaarRepository.deleteByProjectNaam()`

**Step 5: ClusteringWorker — vereenvoudig**

In `verwerkTaak()`: roep `kernbezwaarService.clusterProject(taak.getProjectNaam(), taak.getId())` aan (niet meer `clusterEenCategorie`).

**Step 6: ClusteringTaakController — vereenvoudig**

- `startCategorie()` → `startClustering()`: `POST /{naam}/clustering-taken`
- Verwijder `startAlleCategorieen()`
- `verwijderCategorie()` → `verwijderClustering()`: `DELETE /{naam}/clustering-taken`
- Verwijder `CategorieOverzichtResponse`
- `geefOverzicht()` → `geefTaak()`: `GET /{naam}/clustering-taken` retourneert een enkele `ClusteringTaakDto`

**Step 7: Commit**

```bash
git add app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/
git commit -m "refactor: verwijder categorie uit clustering-taak flow"
```

---

## Task 8: KernbezwaarService — herschrijf met centroid matching

**Files:**
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/KernbezwaarService.java`
- Modify: `app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/KernbezwaarServiceTest.java`

**Step 1: Herschrijf KernbezwaarService**

Kernwijzigingen:
- Verwijder `ThemaRepository` dependency
- Voeg `CentroidMatchingService` dependency toe
- `groepeer()` → verwijderd (legacy)
- `clusterEenCategorie()` → `clusterProject(String projectNaam, Long taakId)`
- `clusterCategorie()` → herschrijf als `clusterAlles()`:
  1. Haal alle bezwaren op via `findByProjectNaam()`
  2. Genereer embeddings
  3. HDBSCAN clustering
  4. **Nieuw:** `centroidMatchingService.wijsNoiseToe()` met originele embeddings en threshold
  5. Sla kernbezwaren op met `projectNaam` (niet themaId)
  6. Bij `bouwReferenties()`: geef toewijzingsmethode mee
  7. Bij `slaKernbezwaarOp()`: sla toewijzingsmethode op
- `geefKernbezwaren()` → vereenvoudig: query op `projectNaam`, geen thema-groepering
- `ruimOpNaDocumentVerwijdering()` → verwijder thema-gerelateerde opruiming
- `ruimAllesOpVoorProject()` → vereenvoudig

**Belangrijk detail — clusterAlles post-processing:**

```java
// Na HDBSCAN
var clusterResultaat = clusteringPoort.cluster(clusterInvoer);

// Centroid matching op originele embeddings
var noiseEmbeddings = new HashMap<Long, float[]>();
for (Long noiseId : clusterResultaat.noiseIds()) {
  noiseEmbeddings.put(noiseId, geefEmbedding(bezwaarById.get(noiseId)));
}
var matchResultaat = centroidMatchingService.wijsNoiseToe(
    clusterResultaat.clusters(), noiseEmbeddings,
    clusteringConfig.getCentroidMatchingThreshold());
```

**Step 2: Voeg nieuwe methods toe voor handmatige toewijzing en suggesties**

```java
public List<CentroidMatchingService.Suggestie> geefSuggesties(
    String projectNaam, Long bezwaarId) {
  // Laad embedding van het bezwaar
  // Bereken centroids per kernbezwaar
  // Roep centroidMatchingService.berekenTop5Suggesties() aan
}

@Transactional
public void wijsToeAanKernbezwaar(Long referentieId, Long doelKernbezwaarId) {
  // Update referentie: kernbezwaarId = doelKernbezwaarId, methode = MANUEEL
  // Ruim leeg noise-kernbezwaar op
}
```

**Step 3: Update bouwReferenties en slaKernbezwaarOp**

`bouwReferenties` krijgt extra parameter `Map<Long, ToewijzingsMethode> methoden`:

```java
private List<IndividueelBezwaarReferentie> bouwReferenties(
    List<GeextraheerdBezwaarEntiteit> bezwaren,
    Map<Long, Map<Integer, String>> passageLookup,
    Map<Long, String> bestandsnaamLookup,
    Map<Long, Double> scores,
    Map<Long, ToewijzingsMethode> methoden) {
  // ...
  ToewijzingsMethode methode = methoden.getOrDefault(b.getId(), ToewijzingsMethode.HDBSCAN);
  return new IndividueelBezwaarReferentie(
      b.getId(), bestandsnaam, passage, scorePercentage, methode);
}
```

`slaKernbezwaarOp` slaat de toewijzingsmethode op:

```java
refEntiteit.setToewijzingsmethode(ref.toewijzingsmethode());
```

**Step 4: Update testen**

Herschrijf `KernbezwaarServiceTest` om de nieuwe flow te reflecteren. De mock voor `ThemaRepository` wordt verwijderd, `CentroidMatchingService` wordt gemockt.

**Step 5: Run testen**

```bash
mvn test -pl app -Dtest=KernbezwaarServiceTest
```

**Step 6: Commit**

```bash
git add app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/KernbezwaarService.java \
       app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/KernbezwaarServiceTest.java
git commit -m "feat: KernbezwaarService met centroid matching en zonder categorieen/thema's"
```

---

## Task 9: KernbezwaarController — nieuwe endpoints + vereenvoudigde response

**Files:**
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/KernbezwaarController.java`
- Modify: `app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/KernbezwaarControllerTest.java`

**Step 1: Update bestaande endpoints**

- `groepeer()` → verwijderd (legacy)
- `geefKernbezwaren()` retourneert `List<Kernbezwaar>` i.p.v. `ThemasResponse`
- Verwijder `ThemasResponse` record

```java
@GetMapping("/{naam}/kernbezwaren")
public ResponseEntity<List<Kernbezwaar>> geefKernbezwaren(@PathVariable String naam) {
  return kernbezwaarService.geefKernbezwaren(naam)
      .map(ResponseEntity::ok)
      .orElse(ResponseEntity.notFound().build());
}
```

**Step 2: Voeg endpoint toe voor top-5 suggesties**

```java
@GetMapping("/{naam}/noise/{bezwaarId}/suggesties")
public ResponseEntity<List<SuggestieResponse>> geefSuggesties(
    @PathVariable String naam,
    @PathVariable Long bezwaarId) {
  var suggesties = kernbezwaarService.geefSuggesties(naam, bezwaarId);
  var response = suggesties.stream()
      .map(s -> new SuggestieResponse(s.kernbezwaarId(),
          (int) Math.round(s.score() * 100),
          kernbezwaarService.geefSamenvatting(s.kernbezwaarId())))
      .toList();
  return ResponseEntity.ok(response);
}

record SuggestieResponse(Long kernbezwaarId, int scorePercentage, String samenvatting) {}
```

**Step 3: Voeg endpoint toe voor handmatige toewijzing**

```java
@PutMapping("/{naam}/referenties/{referentieId}/toewijzing")
public ResponseEntity<Void> wijsToe(
    @PathVariable String naam,
    @PathVariable Long referentieId,
    @RequestBody ToewijzingRequest request) {
  kernbezwaarService.wijsToeAanKernbezwaar(referentieId, request.kernbezwaarId());
  return ResponseEntity.ok().build();
}

record ToewijzingRequest(Long kernbezwaarId) {}
```

**Step 4: Update testen**

**Step 5: Run testen**

```bash
mvn test -pl app -Dtest=KernbezwaarControllerTest
```

**Step 6: Commit**

```bash
git add app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/KernbezwaarController.java \
       app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/KernbezwaarControllerTest.java
git commit -m "feat: KernbezwaarController met suggesties en handmatige toewijzing endpoints"
```

---

## Task 10: Overige compile-fixes en test-updates

**Files:**
- Modify: diverse testbestanden die ThemaEntiteit/categorie refereren
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/clustering/ClusteringConfigController.java`
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieTaakService.java` (als die categorie gebruikt)
- Modify: alle integratietesten die categorie/thema refereren

**Step 1: Zoek alle referenties naar verwijderde types**

```bash
grep -rn "ThemaEntiteit\|ThemaRepository\|Thema\b.*record\|\.categorie\|getCategorie\|setCategorie\|\.themaId\|getThemaId\|setThemaId\|ThemasResponse\|CategorieStatus\|CategorieOverzichtResponse" \
  app/src/main/java app/src/test/java --include="*.java" | grep -v ".class"
```

**Step 2: Fix alle compile-errors**

Dit omvat:
- `CascadeVerwijderingIntegrationTest` — herschrijf zonder thema's
- `ClusteringTaakServiceTest` — herschrijf zonder categorie
- `ClusteringTaakControllerTest` — herschrijf zonder categorie
- `KernbezwaarPersistentieTest` — herschrijf zonder thema
- Consolidatie-gerelateerde code — check of die categorie/thema gebruikt
- Extractie-gerelateerde code — check `categorie` referenties in de extractie-agent/pipeline

**Step 3: Verwijder categorie uit extractie-agent prompts**

Zoek in de codebase naar waar `categorie` wordt gezet op `GeextraheerdBezwaarEntiteit`. Dit zit waarschijnlijk in de extractie-service/agent. Verwijder die toewijzing.

**Step 4: Build en run alle testen**

```bash
mvn clean install -pl app
```

Expected: BUILD SUCCESS

**Step 5: Commit**

```bash
git add -A
git commit -m "fix: compile-errors en test-updates na verwijdering categorieen/thema's"
```

---

## Task 11: Frontend — verwijder categorieen, toon flat kernbezwaren

**Files:**
- Modify: `webapp/src/js/bezwaarschriften-kernbezwaren.js`

**Step 1: Verwijder categorie-gerelateerde code**

- Verwijder `_renderCategorieOverzicht()` method
- Verwijder `_clusterAlles()` en `_verwijderAlles()` (categorie-iteratie)
- Verwijder `_clusteringTaken` state (was per-categorie)
- Verwijder `_maakStatusPill()` voor categorie-status
- Verwijder `_renderClusteringParams()` accordion per categorie

**Step 2: Herschrijf `_renderInhoud()`**

Nieuwe flow:
1. Geen bezwaren + niet klaar → toon lege staat
2. Bezwaren maar niet geclusterd → toon "Cluster bezwaren" knop
3. Clustering actief → toon status
4. Clustering klaar → toon flat lijst kernbezwaren

**Step 3: Update `laadKernbezwaren()`**

Response is nu `List<Kernbezwaar>` (niet meer `{themas: [...]}`).

```javascript
async laadKernbezwaren(projectNaam) {
  this._projectNaam = projectNaam;
  const response = await fetch(`/api/v1/projects/${projectNaam}/kernbezwaren`);
  if (response.ok) {
    this._kernbezwaren = await response.json();
    this._renderInhoud();
  }
}
```

**Step 4: Update `laadClusteringTaken()`**

Response is nu een enkele `ClusteringTaakDto` (niet meer een lijst met categorieen).

**Step 5: Update `_startClustering()`**

```javascript
async _startClustering() {
  const response = await fetch(
    `/api/v1/projects/${this._projectNaam}/clustering-taken`,
    { method: 'POST' });
  // ...
}
```

**Step 6: Update `werkBijMetClusteringUpdate()`**

Verwerk een enkele taak-update (niet meer per categorie).

**Step 7: Build en test**

```bash
cd webapp && npm run build
```

**Step 8: Commit**

```bash
git add webapp/src/js/bezwaarschriften-kernbezwaren.js
git commit -m "feat: frontend zonder categorieen, flat kernbezwaren lijst"
```

---

## Task 12: Frontend — side panel met paginering en toewijzingsmethode

**Files:**
- Modify: `webapp/src/js/bezwaarschriften-kernbezwaren.js`

**Step 1: Voeg CSS toe voor badges en paginering**

```css
.toewijzing-badge {
  display: inline-block;
  font-size: 0.75rem;
  padding: 0.15rem 0.5rem;
  border-radius: 3px;
  margin-left: 0.5rem;
}
.toewijzing-badge--hdbscan {
  background: #e8ebee;
  color: #687483;
}
.toewijzing-badge--centroid {
  background: #fff3e0;
  color: #e65100;
}
.toewijzing-badge--manueel {
  background: #e3f2fd;
  color: #1565c0;
}
.paginering {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 1rem;
  padding: 1rem 0;
  border-top: 1px solid #e8ebee;
}
```

**Step 2: Herschrijf `_toonPassages()` met paginering**

```javascript
_toonPassages(kernbezwaar) {
  const sideSheet = this.shadowRoot.querySelector('#side-sheet');
  const inhoud = this.shadowRoot.querySelector('#side-sheet-inhoud');
  const titelEl = this.shadowRoot.querySelector('#side-sheet-titel');
  if (!sideSheet || !inhoud) return;

  titelEl.textContent = kernbezwaar.samenvatting;
  this._huidigKernbezwaar = kernbezwaar;
  this._huidigePagina = 1;
  this._renderPassagePagina();
  sideSheet.open();
  this.classList.add('side-sheet-open');
}

_renderPassagePagina() {
  const inhoud = this.shadowRoot.querySelector('#side-sheet-inhoud');
  if (!inhoud) return;
  inhoud.innerHTML = '';

  const bezwaren = this._huidigKernbezwaar.individueleBezwaren;
  const groepen = groepeerPassages(bezwaren);
  const PAGE_SIZE = 15;
  const totalPages = Math.ceil(groepen.length / PAGE_SIZE);
  const start = (this._huidigePagina - 1) * PAGE_SIZE;
  const paginaGroepen = groepen.slice(start, start + PAGE_SIZE);

  // Aantal label
  const aantalLabel = document.createElement('p');
  aantalLabel.textContent = `${bezwaren.length} bezwar${bezwaren.length === 1 ? '' : 'en'}, ${groepen.length} passage${groepen.length === 1 ? '' : 's'}:`;
  inhoud.appendChild(aantalLabel);

  // Passages
  paginaGroepen.forEach((groep) => {
    const groepEl = document.createElement('div');
    groepEl.className = 'passage-groep';

    const passage = document.createElement('div');
    passage.className = 'passage-tekst';
    passage.textContent = `"${groep.passage}"`;
    groepEl.appendChild(passage);

    // Toewijzingsmethode badge (van eerste bezwaar in groep)
    const methode = groep.bezwaren[0]?.toewijzingsmethode;
    if (methode && methode !== 'HDBSCAN') {
      const badge = document.createElement('span');
      badge.className = `toewijzing-badge toewijzing-badge--${methode === 'CENTROID_FALLBACK' ? 'centroid' : 'manueel'}`;
      badge.textContent = methode === 'CENTROID_FALLBACK' ? 'Automatisch toegewezen' : 'Handmatig';
      passage.appendChild(badge);
    }

    // Score
    const score = groep.bezwaren[0]?.scorePercentage;
    if (score != null) {
      const scoreBadge = document.createElement('span');
      scoreBadge.className = 'toewijzing-badge toewijzing-badge--hdbscan';
      scoreBadge.textContent = `${score}%`;
      passage.appendChild(scoreBadge);
    }

    // Document links
    const docContainer = document.createElement('div');
    docContainer.className = 'passage-documenten';
    groep.bezwaren.forEach((ref) => {
      docContainer.appendChild(this._maakDocumentLink(ref));
    });
    groepEl.appendChild(docContainer);
    inhoud.appendChild(groepEl);
  });

  // Paginering
  if (totalPages > 1) {
    const pager = document.createElement('div');
    pager.className = 'paginering';

    const prevBtn = document.createElement('vl-button');
    prevBtn.textContent = 'Vorige';
    prevBtn.setAttribute('ghost', '');
    if (this._huidigePagina <= 1) prevBtn.setAttribute('disabled', '');
    prevBtn.addEventListener('click', () => {
      this._huidigePagina--;
      this._renderPassagePagina();
    });

    const label = document.createElement('span');
    label.textContent = `${this._huidigePagina} / ${totalPages}`;

    const nextBtn = document.createElement('vl-button');
    nextBtn.textContent = 'Volgende';
    nextBtn.setAttribute('ghost', '');
    if (this._huidigePagina >= totalPages) nextBtn.setAttribute('disabled', '');
    nextBtn.addEventListener('click', () => {
      this._huidigePagina++;
      this._renderPassagePagina();
    });

    pager.appendChild(prevBtn);
    pager.appendChild(label);
    pager.appendChild(nextBtn);
    inhoud.appendChild(pager);
  }
}
```

**Step 3: Build**

```bash
cd webapp && npm run build
```

**Step 4: Commit**

```bash
git add webapp/src/js/bezwaarschriften-kernbezwaren.js
git commit -m "feat: side panel met paginering (15/pagina) en toewijzingsmethode badges"
```

---

## Task 13: Frontend — handmatige toewijzing dropdown voor noise

**Files:**
- Modify: `webapp/src/js/bezwaarschriften-kernbezwaren.js`

**Step 1: Voeg CSS toe voor dropdown**

```css
.toewijzen-dropdown {
  margin-top: 0.5rem;
  padding: 0.75rem;
  background: #f5f6f7;
  border: 1px solid #e8ebee;
  border-radius: 4px;
}
.suggestie-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0.5rem;
  cursor: pointer;
  border-radius: 3px;
}
.suggestie-item:hover {
  background: #e8ebee;
}
.suggestie-score {
  font-weight: bold;
  color: #687483;
}
```

**Step 2: Voeg "Toewijzen" knop toe per noise-passage**

In `_renderPassagePagina()`, detecteer of het huidige kernbezwaar "Niet-geclusterde bezwaren" is. Zo ja, voeg per passage-groep een "Toewijzen" knop toe.

```javascript
if (this._huidigKernbezwaar.samenvatting === 'Niet-geclusterde bezwaren') {
  const toewijzenKnop = document.createElement('vl-button');
  toewijzenKnop.textContent = 'Toewijzen';
  toewijzenKnop.setAttribute('ghost', '');
  toewijzenKnop.addEventListener('click', () =>
    this._toonSuggesties(groep.bezwaren[0], groepEl));
  groepEl.appendChild(toewijzenKnop);
}
```

**Step 3: Implementeer `_toonSuggesties()`**

```javascript
async _toonSuggesties(bezwaar, groepEl) {
  const bestaand = groepEl.querySelector('.toewijzen-dropdown');
  if (bestaand) { bestaand.remove(); return; }

  const response = await fetch(
    `/api/v1/projects/${this._projectNaam}/noise/${bezwaar.bezwaarId}/suggesties`);
  if (!response.ok) return;
  const suggesties = await response.json();

  const dropdown = document.createElement('div');
  dropdown.className = 'toewijzen-dropdown';

  suggesties.forEach((s) => {
    const item = document.createElement('div');
    item.className = 'suggestie-item';

    const tekst = document.createElement('span');
    tekst.textContent = s.samenvatting;
    tekst.style.flex = '1';
    tekst.style.marginRight = '1rem';

    const score = document.createElement('span');
    score.className = 'suggestie-score';
    score.textContent = `${s.scorePercentage}%`;

    item.appendChild(tekst);
    item.appendChild(score);
    item.addEventListener('click', () =>
      this._voerToewijzingUit(bezwaar, s.kernbezwaarId));
    dropdown.appendChild(item);
  });

  groepEl.appendChild(dropdown);
}
```

**Step 4: Implementeer `_voerToewijzingUit()`**

```javascript
async _voerToewijzingUit(bezwaar, kernbezwaarId) {
  // Zoek de referentie-ID (we moeten dit meesturen vanuit de backend of afleiden)
  const response = await fetch(
    `/api/v1/projects/${this._projectNaam}/referenties/${bezwaar.referentieId}/toewijzing`,
    {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ kernbezwaarId }),
    });
  if (response.ok) {
    // Herlaad kernbezwaren
    await this.laadKernbezwaren(this._projectNaam);
  }
}
```

**Let op:** De `referentieId` moet meekomen vanuit de backend. Voeg `referentieId` toe aan het `IndividueelBezwaarReferentie` record en de mapping in `geefKernbezwaren()`:

In `KernbezwaarService.geefKernbezwaren()`:
```java
new IndividueelBezwaarReferentie(
    re.getId(),         // referentieId (NIEUW)
    re.getBezwaarId(),
    re.getBestandsnaam(),
    re.getPassage(),
    scorePercentage,
    re.getToewijzingsmethode());
```

Update het record:
```java
public record IndividueelBezwaarReferentie(
    Long referentieId,
    Long bezwaarId,
    String bestandsnaam,
    String passage,
    Integer scorePercentage,
    ToewijzingsMethode toewijzingsmethode) {}
```

**Step 5: Build**

```bash
cd webapp && npm run build
```

**Step 6: Commit**

```bash
git add webapp/src/js/bezwaarschriften-kernbezwaren.js \
       app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/IndividueelBezwaarReferentie.java \
       app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/KernbezwaarService.java
git commit -m "feat: handmatige toewijzing van noise-bezwaren via dropdown met top-5 suggesties"
```

---

## Task 14: Cypress frontend testen

**Files:**
- Create: `webapp/cypress/component/kernbezwaren-paginering.cy.js`
- Create: `webapp/cypress/component/kernbezwaren-toewijzing.cy.js`

**Step 1: Test paginering**

Test dat bij >15 passages paginering verschijnt, navigatie werkt, en counts kloppen.

**Step 2: Test toewijzingsmethode badges**

Test dat badges correct getoond worden voor HDBSCAN/CENTROID_FALLBACK/MANUEEL.

**Step 3: Test handmatige toewijzing**

Mock de suggesties-API, verifieer dat dropdown verschijnt, en dat toewijzing-call correct is.

**Step 4: Run testen**

```bash
cd webapp && npm test
```

**Step 5: Commit**

```bash
git add webapp/cypress/
git commit -m "test: Cypress testen voor paginering en handmatige toewijzing"
```

---

## Task 15: Volledige build + integratietesten

**Step 1: Backend build**

```bash
mvn clean install -pl app
```

**Step 2: Frontend build**

```bash
cd webapp && npm run build
mvn process-resources -pl webapp -Denforcer.skip=true
```

**Step 3: Integratietesten (vereist Docker)**

```bash
mvn verify -pl app
```

**Step 4: Fix eventuele failures**

**Step 5: Final commit**

```bash
git add -A
git commit -m "chore: build en integratietest fixes"
```

---

## Task 16: C4 documentatie updaten

**Files:**
- Modify: `docs/c4-c2-containers.md`

**Step 1: Update C2 diagram**

Verwijder "Thema" uit het datamodel. Voeg "CentroidMatchingService" toe als component. Update de flow-beschrijving.

**Step 2: Commit**

```bash
git add docs/
git commit -m "docs: C4 C2 update voor centroid matching en verwijdering thema-laag"
```

---

## Verificatieplan

Na alle tasks:

1. `mvn clean install -pl app` — alle unit tests groen
2. `mvn verify -pl app` — integratietesten groen (Docker vereist)
3. `cd webapp && npm test` — Cypress testen groen
4. `cd webapp && npm run build` — frontend builds zonder errors
5. Handmatige test:
   - Start app lokaal (`docker compose up -d && mvn spring-boot:run -pl app -Pdev`)
   - Upload testdocumenten
   - Cluster bezwaren
   - Verifieer dat noise-bezwaren boven drempel automatisch zijn toegewezen (CENTROID_FALLBACK badge)
   - Verifieer dat resterende noise "Toewijzen" knop heeft
   - Klik "Toewijzen", verifieer top-5 dropdown met scores
   - Wijs manueel toe, verifieer MANUEEL badge
   - Verifieer paginering bij >15 passages
   - Verifieer dat categorieen nergens meer zichtbaar zijn
