# Passage-deduplicatie Implementatieplan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Passage-deduplicatie verplaatsen van frontend naar backend, configureerbaar als pre-processing (voor HDBSCAN) of post-processing (na HDBSCAN).

**Architecture:** Nieuwe `PassageDeduplicatieService` groepeert bezwaren met vrijwel identieke passages (Dice-coefficient >= 0.9). Resultaat wordt gepersisteerd in `passage_groep` + `passage_groep_lid` tabellen. `KernbezwaarService` gebruikt de groepen als input voor HDBSCAN (modus A) of groepeert na clustering (modus B). Frontend rendert gegroepeerde data direct uit de API.

**Tech Stack:** Java 21, Spring Boot 3.x, JPA/Hibernate, Liquibase, Lit web components, Cypress

**Design document:** `docs/plans/2026-03-06-passage-deduplicatie-design.md`

---

## Task 1: Liquibase migratie voor nieuw datamodel

**Files:**
- Create: `app/src/main/resources/config/liquibase/changelog/20260306-passage-deduplicatie.xml`
- Modify: `app/src/main/resources/config/liquibase/master.xml`

**Step 1: Schrijf de Liquibase changeset**

Naamconventie volgt bestaand patroon: `YYYYMMDD-beschrijving.xml`, changeSet id idem, author `kenzo`.

```xml
<?xml version="1.0" encoding="utf-8"?>
<databaseChangeLog
    xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-3.1.xsd">

  <changeSet id="20260306-passage-deduplicatie" author="kenzo">

    <!-- Nieuwe tabel: passage_groep -->
    <createTable tableName="passage_groep">
      <column name="id" type="bigserial" autoIncrement="true">
        <constraints primaryKey="true" nullable="false"/>
      </column>
      <column name="clustering_taak_id" type="bigint">
        <constraints nullable="false"/>
      </column>
      <column name="passage" type="text">
        <constraints nullable="false"/>
      </column>
      <column name="samenvatting" type="text">
        <constraints nullable="false"/>
      </column>
      <column name="categorie" type="varchar(50)">
        <constraints nullable="false"/>
      </column>
      <column name="score_percentage" type="int"/>
    </createTable>

    <addForeignKeyConstraint
        baseTableName="passage_groep" baseColumnNames="clustering_taak_id"
        referencedTableName="clustering_taak" referencedColumnNames="id"
        constraintName="fk_passage_groep_clustering_taak"
        onDelete="CASCADE"/>

    <!-- Nieuwe tabel: passage_groep_lid -->
    <createTable tableName="passage_groep_lid">
      <column name="id" type="bigserial" autoIncrement="true">
        <constraints primaryKey="true" nullable="false"/>
      </column>
      <column name="passage_groep_id" type="bigint">
        <constraints nullable="false"/>
      </column>
      <column name="bezwaar_id" type="bigint">
        <constraints nullable="false"/>
      </column>
      <column name="bestandsnaam" type="varchar(255)">
        <constraints nullable="false"/>
      </column>
    </createTable>

    <addForeignKeyConstraint
        baseTableName="passage_groep_lid" baseColumnNames="passage_groep_id"
        referencedTableName="passage_groep" referencedColumnNames="id"
        constraintName="fk_passage_groep_lid_groep"
        onDelete="CASCADE"/>

    <addForeignKeyConstraint
        baseTableName="passage_groep_lid" baseColumnNames="bezwaar_id"
        referencedTableName="geextraheerd_bezwaar" referencedColumnNames="id"
        constraintName="fk_passage_groep_lid_bezwaar"
        onDelete="CASCADE"/>

    <!-- Nieuw veld op clustering_taak -->
    <addColumn tableName="clustering_taak">
      <column name="deduplicatie_voor_clustering" type="boolean" defaultValueBoolean="false">
        <constraints nullable="false"/>
      </column>
    </addColumn>

    <!-- kernbezwaar_referentie: voeg passage_groep_id toe -->
    <addColumn tableName="kernbezwaar_referentie">
      <column name="passage_groep_id" type="bigint"/>
    </addColumn>

    <addForeignKeyConstraint
        baseTableName="kernbezwaar_referentie" baseColumnNames="passage_groep_id"
        referencedTableName="passage_groep" referencedColumnNames="id"
        constraintName="fk_kernbezwaar_ref_passage_groep"
        onDelete="CASCADE"/>

    <!-- Verwijder oude kolommen van kernbezwaar_referentie -->
    <dropColumn tableName="kernbezwaar_referentie" columnName="bezwaar_id"/>
    <dropColumn tableName="kernbezwaar_referentie" columnName="bestandsnaam"/>
    <dropColumn tableName="kernbezwaar_referentie" columnName="passage"/>
    <dropColumn tableName="kernbezwaar_referentie" columnName="score"/>

    <!-- Maak passage_groep_id verplicht nu oude kolommen weg zijn -->
    <addNotNullConstraint tableName="kernbezwaar_referentie"
        columnName="passage_groep_id" columnDataType="bigint"/>

    <!-- Verwijder bestaande thema's (herclustering vereist) -->
    <delete tableName="thema"/>

  </changeSet>

</databaseChangeLog>
```

**Step 2: Registreer in master.xml**

Voeg toe na de laatste `<include>`:
```xml
<include file="config/liquibase/changelog/20260306-passage-deduplicatie.xml"/>
```

**Step 3: Commit**

```
feat: liquibase migratie voor passage-deduplicatie datamodel
```

---

## Task 2: JPA entiteiten en repositories

**Files:**
- Create: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/PassageGroepEntiteit.java`
- Create: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/PassageGroepLidEntiteit.java`
- Create: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/PassageGroepRepository.java`
- Create: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/PassageGroepLidRepository.java`
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/KernbezwaarReferentieEntiteit.java`
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/ClusteringTaak.java`

**Step 1: Schrijf integratie-test**

Test: `app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/PassageGroepPersistentieTest.java`

```java
@DataJpaTest
class PassageGroepPersistentieTest extends BaseBezwaarschriftenIntegrationTest {

  @Autowired PassageGroepRepository groepRepository;
  @Autowired PassageGroepLidRepository lidRepository;

  @Test
  void slaatPassageGroepMetLedenOp() {
    var groep = new PassageGroepEntiteit();
    groep.setClusteringTaakId(1L); // dummy, geen FK-check in test
    groep.setPassage("De geluidshinder is onaanvaardbaar");
    groep.setSamenvatting("Bezwaar tegen geluidshinder");
    groep.setCategorie("Milieu");
    groep = groepRepository.save(groep);

    var lid = new PassageGroepLidEntiteit();
    lid.setPassageGroepId(groep.getId());
    lid.setBezwaarId(100L);
    lid.setBestandsnaam("bezwaar1.pdf");
    lidRepository.save(lid);

    var leden = lidRepository.findByPassageGroepId(groep.getId());
    assertThat(leden).hasSize(1);
    assertThat(leden.get(0).getBestandsnaam()).isEqualTo("bezwaar1.pdf");
  }
}
```

**Step 2: Run test, verifieer dat het faalt**

```bash
mvn test -pl app -Dtest=PassageGroepPersistentieTest -Dsurefire.failIfNoSpecifiedTests=false
```

**Step 3: Implementeer entiteiten**

`PassageGroepEntiteit.java`:
```java
@Entity
@Table(name = "passage_groep")
public class PassageGroepEntiteit {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "clustering_taak_id", nullable = false)
  private Long clusteringTaakId;

  @Column(name = "passage", columnDefinition = "text", nullable = false)
  private String passage;

  @Column(name = "samenvatting", columnDefinition = "text", nullable = false)
  private String samenvatting;

  @Column(name = "categorie", length = 50, nullable = false)
  private String categorie;

  @Column(name = "score_percentage")
  private Integer scorePercentage;

  // getters + setters
}
```

`PassageGroepLidEntiteit.java`:
```java
@Entity
@Table(name = "passage_groep_lid")
public class PassageGroepLidEntiteit {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "passage_groep_id", nullable = false)
  private Long passageGroepId;

  @Column(name = "bezwaar_id", nullable = false)
  private Long bezwaarId;

  @Column(name = "bestandsnaam", nullable = false)
  private String bestandsnaam;

  // getters + setters
}
```

`PassageGroepRepository.java`:
```java
@Repository
public interface PassageGroepRepository extends JpaRepository<PassageGroepEntiteit, Long> {
  List<PassageGroepEntiteit> findByClusteringTaakId(Long clusteringTaakId);
  void deleteByClusteringTaakId(Long clusteringTaakId);
}
```

`PassageGroepLidRepository.java`:
```java
@Repository
public interface PassageGroepLidRepository extends JpaRepository<PassageGroepLidEntiteit, Long> {
  List<PassageGroepLidEntiteit> findByPassageGroepId(Long passageGroepId);
  List<PassageGroepLidEntiteit> findByPassageGroepIdIn(List<Long> passageGroepIds);
  void deleteByBezwaarIdIn(List<Long> bezwaarIds);
}
```

**Wijzig `KernbezwaarReferentieEntiteit`:** vervang `bezwaarId`, `bestandsnaam`, `passage`, `score` door `passageGroepId`:
```java
@Entity
@Table(name = "kernbezwaar_referentie")
public class KernbezwaarReferentieEntiteit {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "kernbezwaar_id", nullable = false)
  private Long kernbezwaarId;

  @Column(name = "passage_groep_id", nullable = false)
  private Long passageGroepId;

  // getters + setters (verwijder oude)
}
```

**Wijzig `ClusteringTaak`:** voeg toe:
```java
@Column(name = "deduplicatie_voor_clustering", nullable = false)
private boolean deduplicatieVoorClustering = false;

// getter + setter
```

**Step 4: Run test, verifieer dat het slaagt**

```bash
mvn test -pl app -Dtest=PassageGroepPersistentieTest
```

**Step 5: Commit**

```
feat: JPA entiteiten en repositories voor passage-groepen
```

---

## Task 3: DTO records herstructureren

**Files:**
- Create: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/PassageGroepDocument.java`
- Create: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/PassageGroepDto.java`
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/IndividueelBezwaarReferentie.java`
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/Thema.java`
- Modify: `app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/ThemaTest.java`

**Step 1: Schrijf unit test**

Pas `ThemaTest` aan zodat het de nieuwe structuur valideert:
```java
@Test
void themaBevatKernbezwarenMetPassageGroepen() {
  var doc1 = new PassageGroepDocument(1L, "bezwaar1.pdf");
  var doc2 = new PassageGroepDocument(2L, "bezwaar2.pdf");
  var groep = new PassageGroepDto(10L, "Geluidshinder passage...", List.of(doc1, doc2));
  var bezwaar = new IndividueelBezwaarReferentie("Bezwaar over geluid", 85, groep);
  var kern = new Kernbezwaar(1L, "Geluidshinder", List.of(bezwaar), null);
  var thema = new Thema("Milieu", List.of(kern), true);

  assertThat(thema.passageDeduplicatieVoorClustering()).isTrue();
  assertThat(kern.individueleBezwaren()).hasSize(1);
  var ib = kern.individueleBezwaren().get(0);
  assertThat(ib.samenvatting()).isEqualTo("Bezwaar over geluid");
  assertThat(ib.scorePercentage()).isEqualTo(85);
  assertThat(ib.passageGroep().documenten()).hasSize(2);
  assertThat(ib.passageGroep().passage()).isEqualTo("Geluidshinder passage...");
}
```

**Step 2: Run test, verifieer dat het faalt**

```bash
mvn test -pl app -Dtest=ThemaTest
```

**Step 3: Implementeer records**

`PassageGroepDocument.java`:
```java
public record PassageGroepDocument(Long bezwaarId, String bestandsnaam) {}
```

`PassageGroepDto.java`:
```java
public record PassageGroepDto(Long id, String passage, List<PassageGroepDocument> documenten) {}
```

`IndividueelBezwaarReferentie.java` (herstructureer):
```java
public record IndividueelBezwaarReferentie(
    String samenvatting,
    Integer scorePercentage,
    PassageGroepDto passageGroep) {}
```

`Thema.java` (voeg deduplicatie-vlag toe):
```java
public record Thema(String naam, List<Kernbezwaar> kernbezwaren,
    Boolean passageDeduplicatieVoorClustering) {}
```

**Step 4: Fix compilatiefouten**

Na het herstructureren van `IndividueelBezwaarReferentie` zullen er compilatiefouten zijn in:
- `KernbezwaarService.java` (bouwReferenties, slaKernbezwaarOp, geefKernbezwaren)
- `KernbezwaarServiceTest.java`
- `KernbezwaarControllerTest.java`
- `bezwaarschriften-kernbezwaren.js` (frontend)

Laat deze fouten tijdelijk bestaan — ze worden opgelost in task 5-7. Zorg dat `ThemaTest` slaagt.

**Alternatieve aanpak als compilatie blokkeert:** maak de nieuwe records naast de bestaande (bv. `IndividueelBezwaar` naast `IndividueelBezwaarReferentie`) en verwijder de oude pas in task 7 wanneer alle consumers zijn gemigreerd.

**Step 5: Run test**

```bash
mvn test -pl app -Dtest=ThemaTest
```

**Step 6: Commit**

```
feat: DTO records voor passage-groep structuur
```

---

## Task 4: PassageDeduplicatieService

**Files:**
- Create: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/PassageDeduplicatieService.java`
- Create: `app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/PassageDeduplicatieServiceTest.java`

Dit is pure logica zonder Spring-dependencies (behalve `@Component`). Exact hetzelfde Dice-coefficient algoritme als de huidige frontend `passage-groepering.js`.

**Step 1: Schrijf unit tests**

```java
class PassageDeduplicatieServiceTest {

  private final PassageDeduplicatieService service = new PassageDeduplicatieService();

  @Test
  void identiekeTekstenWordenGegroepeerd() {
    var bezwaren = List.of(
        maakBezwaar(1L, 10L, 1, "De geluidshinder is onaanvaardbaar"),
        maakBezwaar(2L, 20L, 1, "De geluidshinder is onaanvaardbaar"));
    var passageLookup = Map.of(
        10L, Map.of(1, "De geluidshinder is onaanvaardbaar"),
        20L, Map.of(1, "De geluidshinder is onaanvaardbaar"));
    var bestandsnaamLookup = Map.of(10L, "doc1.pdf", 20L, "doc2.pdf");

    var groepen = service.groepeer(bezwaren, passageLookup, bestandsnaamLookup);

    assertThat(groepen).hasSize(1);
    assertThat(groepen.get(0).leden()).hasSize(2);
  }

  @Test
  void verschillendeTekstenWordenNietGegroepeerd() {
    var bezwaren = List.of(
        maakBezwaar(1L, 10L, 1, "Geluidshinder"),
        maakBezwaar(2L, 20L, 1, "Verkeersoverlast"));
    var passageLookup = Map.of(
        10L, Map.of(1, "De geluidshinder is onaanvaardbaar"),
        20L, Map.of(1, "Het verkeer veroorzaakt overlast"));
    var bestandsnaamLookup = Map.of(10L, "doc1.pdf", 20L, "doc2.pdf");

    var groepen = service.groepeer(bezwaren, passageLookup, bestandsnaamLookup);

    assertThat(groepen).hasSize(2);
    assertThat(groepen.get(0).leden()).hasSize(1);
  }

  @Test
  void binaIdentiekeTekstenWordenGegroepeerd() {
    // Dice >= 0.9 bij minimale wijziging
    var bezwaren = List.of(
        maakBezwaar(1L, 10L, 1, "Geluidshinder"),
        maakBezwaar(2L, 20L, 1, "Geluidshinder variant"));
    var passageLookup = Map.of(
        10L, Map.of(1, "De geluidshinder is volkomen onaanvaardbaar voor de buurt"),
        20L, Map.of(1, "De geluidshinder is volkomen onaanvaardbaar voor de buurt."));
    var bestandsnaamLookup = Map.of(10L, "doc1.pdf", 20L, "doc2.pdf");

    var groepen = service.groepeer(bezwaren, passageLookup, bestandsnaamLookup);

    assertThat(groepen).hasSize(1);
  }

  @Test
  void langstePassageWordtRepresentatief() {
    var bezwaren = List.of(
        maakBezwaar(1L, 10L, 1, "Kort"),
        maakBezwaar(2L, 20L, 1, "Langere samenvatting"));
    var passageLookup = Map.of(
        10L, Map.of(1, "Korte passage"),
        20L, Map.of(1, "Korte passage met meer tekst"));
    var bestandsnaamLookup = Map.of(10L, "doc1.pdf", 20L, "doc2.pdf");

    var groepen = service.groepeer(bezwaren, passageLookup, bestandsnaamLookup);

    // Langste passage is representatief
    assertThat(groepen.get(0).passage()).isEqualTo("Korte passage met meer tekst");
    assertThat(groepen.get(0).samenvatting()).isEqualTo("Langere samenvatting");
  }

  @Test
  void legeInvoerGeeftLegeOutput() {
    var groepen = service.groepeer(List.of(), Map.of(), Map.of());
    assertThat(groepen).isEmpty();
  }

  @Test
  void diceCoefficient_identiekeTeksten() {
    assertThat(service.berekenDiceCoefficient("abc", "abc")).isEqualTo(1.0);
  }

  @Test
  void diceCoefficient_totaalVerschillend() {
    assertThat(service.berekenDiceCoefficient("abc", "xyz")).isEqualTo(0.0);
  }

  private GeextraheerdBezwaarEntiteit maakBezwaar(Long id, Long taakId,
      int passageNr, String samenvatting) {
    var b = new GeextraheerdBezwaarEntiteit();
    b.setId(id);
    b.setTaakId(taakId);
    b.setPassageNr(passageNr);
    b.setSamenvatting(samenvatting);
    return b;
  }
}
```

**Step 2: Run test, verifieer dat het faalt**

```bash
mvn test -pl app -Dtest=PassageDeduplicatieServiceTest
```

**Step 3: Implementeer de service**

```java
@Component
public class PassageDeduplicatieService {

  private static final double GELIJKENIS_DREMPEL = 0.9;

  public record DeduplicatieGroep(
      String passage,
      String samenvatting,
      GeextraheerdBezwaarEntiteit representatief,
      List<DeduplicatieLid> leden) {}

  public record DeduplicatieLid(
      Long bezwaarId,
      String bestandsnaam) {}

  public List<DeduplicatieGroep> groepeer(
      List<GeextraheerdBezwaarEntiteit> bezwaren,
      Map<Long, Map<Integer, String>> passageLookup,
      Map<Long, String> bestandsnaamLookup) {

    if (bezwaren.isEmpty()) {
      return List.of();
    }

    var groepen = new ArrayList<GroepBouwer>();

    for (var bezwaar : bezwaren) {
      var passageTekst = geefPassageTekst(bezwaar, passageLookup);
      var genormaliseerd = normaliseer(passageTekst);
      boolean gevonden = false;

      for (var groep : groepen) {
        if (berekenDiceCoefficient(genormaliseerd, groep.genormaliseerdePassage)
            >= GELIJKENIS_DREMPEL) {
          groep.voegToe(bezwaar, passageTekst,
              bestandsnaamLookup.getOrDefault(bezwaar.getTaakId(), "onbekend"));
          gevonden = true;
          break;
        }
      }

      if (!gevonden) {
        var nieuweGroep = new GroepBouwer(passageTekst, genormaliseerd, bezwaar,
            bestandsnaamLookup.getOrDefault(bezwaar.getTaakId(), "onbekend"));
        groepen.add(nieuweGroep);
      }
    }

    return groepen.stream().map(GroepBouwer::bouw).toList();
  }

  double berekenDiceCoefficient(String a, String b) {
    if (a.equals(b)) return 1.0;
    if (a.length() < 2 || b.length() < 2) return 0.0;

    var bigrammenA = bigrammen(a);
    var bigrammenB = bigrammen(b);
    int overlap = 0;
    var kopie = new ArrayList<>(bigrammenB);
    for (var bigram : bigrammenA) {
      int index = kopie.indexOf(bigram);
      if (index >= 0) {
        overlap++;
        kopie.remove(index);
      }
    }
    return (2.0 * overlap) / (bigrammenA.size() + bigrammenB.size());
  }

  private String normaliseer(String tekst) {
    return tekst == null ? "" : tekst.toLowerCase().trim();
  }

  private List<String> bigrammen(String tekst) {
    var result = new ArrayList<String>();
    for (int i = 0; i < tekst.length() - 1; i++) {
      result.add(tekst.substring(i, i + 2));
    }
    return result;
  }

  private String geefPassageTekst(GeextraheerdBezwaarEntiteit bezwaar,
      Map<Long, Map<Integer, String>> passageLookup) {
    var taakPassages = passageLookup.get(bezwaar.getTaakId());
    if (taakPassages != null) {
      var tekst = taakPassages.get(bezwaar.getPassageNr());
      if (tekst != null) return tekst;
    }
    return bezwaar.getSamenvatting();
  }

  private static class GroepBouwer {
    String passage;
    String genormaliseerdePassage;
    GeextraheerdBezwaarEntiteit representatief;
    List<DeduplicatieLid> leden = new ArrayList<>();

    GroepBouwer(String passage, String genormaliseerd,
        GeextraheerdBezwaarEntiteit bezwaar, String bestandsnaam) {
      this.passage = passage;
      this.genormaliseerdePassage = genormaliseerd;
      this.representatief = bezwaar;
      this.leden.add(new DeduplicatieLid(bezwaar.getId(), bestandsnaam));
    }

    void voegToe(GeextraheerdBezwaarEntiteit bezwaar, String passageTekst,
        String bestandsnaam) {
      leden.add(new DeduplicatieLid(bezwaar.getId(), bestandsnaam));
      if (passageTekst.length() > passage.length()) {
        passage = passageTekst;
        representatief = bezwaar;
      }
    }

    DeduplicatieGroep bouw() {
      return new DeduplicatieGroep(passage, representatief.getSamenvatting(),
          representatief, List.copyOf(leden));
    }
  }
}
```

**Step 4: Run tests, verifieer dat ze slagen**

```bash
mvn test -pl app -Dtest=PassageDeduplicatieServiceTest
```

**Step 5: Commit**

```
feat: PassageDeduplicatieService met Dice-coefficient groepering
```

---

## Task 5: ClusteringTaak + Controller uitbreiden

**Files:**
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/ClusteringTaakService.java`
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/ClusteringTaakController.java`
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/ClusteringTaakDto.java` (of equivalent)
- Test: `app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/ClusteringTaakControllerTest.java`

**Step 1: Schrijf test**

Voeg test toe voor het doorgeven van de deduplicatie-parameter bij het indienen:

```java
@Test
void indienenMetDeduplicatieVoorClustering() {
  when(taakService.indienen(eq("project"), eq("Milieu"), eq(true)))
      .thenReturn(maakTaakDto());

  var result = controller.startClustering("project", "Milieu", true);

  assertThat(result.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
  verify(taakService).indienen("project", "Milieu", true);
}
```

**Step 2: Run test, verifieer dat het faalt**

```bash
mvn test -pl app -Dtest=ClusteringTaakControllerTest#indienenMetDeduplicatieVoorClustering
```

**Step 3: Implementeer**

`ClusteringTaakService.indienen` uitbreiden met `boolean deduplicatieVoorClustering` parameter:
```java
@Transactional
public ClusteringTaakDto indienen(String projectNaam, String categorie,
    boolean deduplicatieVoorClustering) {
  // Bestaande logica...
  var taak = new ClusteringTaak();
  taak.setProjectNaam(projectNaam);
  taak.setCategorie(categorie);
  taak.setStatus(ClusteringTaakStatus.WACHTEND);
  taak.setDeduplicatieVoorClustering(deduplicatieVoorClustering);
  taak.setAangemaaktOp(Instant.now());
  // ...
}
```

Controller POST endpoint: voeg `@RequestParam` toe:
```java
@PostMapping("/{naam}/clustering-taken/{categorie}")
public ResponseEntity<ClusteringTaakDto> startClustering(
    @PathVariable String naam,
    @PathVariable String categorie,
    @RequestParam(defaultValue = "true") boolean deduplicatieVoorClustering) {
  var taak = taakService.indienen(naam, categorie, deduplicatieVoorClustering);
  return ResponseEntity.accepted().body(taak);
}
```

Voeg `deduplicatieVoorClustering` ook toe aan `ClusteringTaakDto` en het categorie-overzicht.

**Step 4: Run tests**

```bash
mvn test -pl app -Dtest=ClusteringTaakControllerTest
```

**Step 5: Commit**

```
feat: deduplicatieVoorClustering parameter bij clustering-start
```

---

## Task 6: KernbezwaarService — modus A (deduplicatie voor clustering)

**Files:**
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/KernbezwaarService.java`
- Test: `app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/KernbezwaarServiceTest.java`

Dit is de kernwijziging. `clusterCategorie` wordt gesplitst op basis van `deduplicatieVoorClustering`.

**Step 1: Schrijf test voor modus A**

```java
@Test
void clusterCategorie_metDeduplicatie_groepeertIdentiekePassages() {
  // Arrange: 3 bezwaren, waarvan 2 identieke passages
  var b1 = maakBezwaarMetEmbedding(1L, 10L, 1, "Geluid", "Milieu", EMBEDDING_A);
  var b2 = maakBezwaarMetEmbedding(2L, 20L, 1, "Geluid", "Milieu", EMBEDDING_A);
  var b3 = maakBezwaarMetEmbedding(3L, 30L, 1, "Verkeer", "Milieu", EMBEDDING_B);

  when(bezwaarRepository.findByProjectNaamAndCategorie("proj", "Milieu"))
      .thenReturn(List.of(b1, b2, b3));

  // Passages: b1 en b2 identiek, b3 anders
  setupPassageLookup(Map.of(
      10L, Map.of(1, "De geluidshinder is onaanvaardbaar"),
      20L, Map.of(1, "De geluidshinder is onaanvaardbaar"),
      30L, Map.of(1, "Het verkeer is gevaarlijk")));

  // ClusteringTaak met deduplicatie=true
  var taak = maakClusteringTaak(true);
  when(clusteringTaakRepository.findById(99L)).thenReturn(Optional.of(taak));

  // HDBSCAN ontvangt 2 items (niet 3!) → 1 cluster
  when(clusteringPoort.cluster(argThat(invoer -> invoer.size() == 2)))
      .thenReturn(new ClusteringResultaat(
          List.of(new Cluster(0, List.of(/* passage_groep IDs */), new float[0])),
          List.of()));

  // Act
  var thema = service.clusterEenCategorie("proj", "Milieu", 99L);

  // Assert: HDBSCAN kreeg 2 items (gegroepeerd), niet 3
  verify(clusteringPoort).cluster(argThat(invoer -> invoer.size() == 2));
}
```

**Step 2: Run test, verifieer dat het faalt**

```bash
mvn test -pl app -Dtest=KernbezwaarServiceTest#clusterCategorie_metDeduplicatie_groepeertIdentiekePassages
```

**Step 3: Implementeer modus A in `clusterCategorie`**

Kernwijzigingen:
1. Haal `deduplicatieVoorClustering` op van de `ClusteringTaak`
2. Als `true`: roep `PassageDeduplicatieService.groepeer()` aan
3. Persisteer `PassageGroepEntiteit` + `PassageGroepLidEntiteit` records
4. Gebruik de representatieve bezwaren als HDBSCAN-input (1 per groep)
5. Na HDBSCAN: bereken centroid-scores per groep, sla op in `passage_groep.score_percentage`
6. Bouw `KernbezwaarReferentieEntiteit` met `passageGroepId`

De methode `clusterCategorie` krijgt een extra parameter `boolean deduplicatieVoorClustering` (of leest het van de `ClusteringTaak`).

Pseudocode voor het gewijzigde deel:
```java
// Na embedding-generatie, voor HDBSCAN:
if (deduplicatieVoorClustering) {
  var deduplicatieGroepen = deduplicatieService.groepeer(bezwaren, passageLookup, bestandsnaamLookup);

  // Persisteer passage groepen
  var groepEntiteiten = new ArrayList<PassageGroepEntiteit>();
  for (var groep : deduplicatieGroepen) {
    var entiteit = new PassageGroepEntiteit();
    entiteit.setClusteringTaakId(taakId);
    entiteit.setPassage(groep.passage());
    entiteit.setSamenvatting(groep.samenvatting());
    entiteit.setCategorie(categorieNaam);
    entiteit = passageGroepRepository.save(entiteit);

    for (var lid : groep.leden()) {
      var lidEntiteit = new PassageGroepLidEntiteit();
      lidEntiteit.setPassageGroepId(entiteit.getId());
      lidEntiteit.setBezwaarId(lid.bezwaarId());
      lidEntiteit.setBestandsnaam(lid.bestandsnaam());
      passageGroepLidRepository.save(lidEntiteit);
    }
    groepEntiteiten.add(entiteit);
  }

  // HDBSCAN input: 1 per groep, embedding van representatief bezwaar
  var clusterInvoer = IntStream.range(0, deduplicatieGroepen.size())
      .mapToObj(i -> new ClusteringInvoer(
          groepEntiteiten.get(i).getId(),
          geefEmbedding(deduplicatieGroepen.get(i).representatief())))
      .toList();

  // Na HDBSCAN: centroid-score per groep
  // kernbezwaar_referentie linkt naar passage_groep_id
}
```

**Step 4: Run tests**

```bash
mvn test -pl app -Dtest=KernbezwaarServiceTest
```

**Step 5: Commit**

```
feat: passage-deduplicatie voor clustering (modus A) in KernbezwaarService
```

---

## Task 7: KernbezwaarService — modus B (deduplicatie na clustering)

**Files:**
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/KernbezwaarService.java`
- Test: `app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/KernbezwaarServiceTest.java`

**Step 1: Schrijf test voor modus B**

```java
@Test
void clusterCategorie_zonderDeduplicatie_groepeertNaClustering() {
  // Arrange: 3 bezwaren, waarvan 2 identieke passages
  // HDBSCAN ontvangt alle 3, clustert ze in 1 cluster
  // Na clustering: groepering op passage-gelijkenis

  // Assert: HDBSCAN kreeg 3 items
  verify(clusteringPoort).cluster(argThat(invoer -> invoer.size() == 3));
  // Maar het kernbezwaar heeft 2 passage-groepen (2 identieke samengevoegd)
}
```

**Step 2: Run test, verifieer dat het faalt**

**Step 3: Implementeer modus B**

Wanneer `deduplicatieVoorClustering == false`:
1. Elke bezwaar → eigen `PassageGroepEntiteit` met 1 lid
2. HDBSCAN clustert alle individuele groepen (identiek aan oud gedrag)
3. Na HDBSCAN, per kernbezwaar: groepeer de `PassageGroepEntiteit` records op passage-gelijkenis
4. Voeg `passage_groep_lid` records samen bij identieke passages
5. Verwijder overbodige `PassageGroepEntiteit` records

**Step 4: Run tests**

```bash
mvn test -pl app -Dtest=KernbezwaarServiceTest
```

**Step 5: Commit**

```
feat: passage-deduplicatie na clustering (modus B) in KernbezwaarService
```

---

## Task 8: KernbezwaarService.geefKernbezwaren aanpassen

**Files:**
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/KernbezwaarService.java`
- Test: `app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/KernbezwaarServiceTest.java`

**Step 1: Schrijf test**

```java
@Test
void geefKernbezwaren_assembleertPassageGroepen() {
  // Setup: thema + kernbezwaar + referentie met passage_groep_id
  // + passage_groep + passage_groep_lid records

  var result = service.geefKernbezwaren("proj");

  assertThat(result).isPresent();
  var thema = result.get().get(0);
  assertThat(thema.passageDeduplicatieVoorClustering()).isTrue();
  var kern = thema.kernbezwaren().get(0);
  var ib = kern.individueleBezwaren().get(0);
  assertThat(ib.samenvatting()).isEqualTo("Bezwaar over geluid");
  assertThat(ib.scorePercentage()).isEqualTo(85);
  assertThat(ib.passageGroep().documenten()).hasSize(2);
}
```

**Step 2: Run test, verifieer dat het faalt**

**Step 3: Implementeer**

De `geefKernbezwaren` methode laadt nu:
1. Thema's + kernbezwaren (zoals nu)
2. Referenties → nu met `passageGroepId`
3. PassageGroepEntiteiten via de IDs uit de referenties
4. PassageGroepLidEntiteiten per groep
5. ClusteringTaak voor `deduplicatieVoorClustering` vlag

Assembleert de nieuwe DTO-structuur:
```java
var ib = new IndividueelBezwaarReferentie(
    groep.getSamenvatting(),
    groep.getScorePercentage(),
    new PassageGroepDto(groep.getId(), groep.getPassage(),
        leden.stream()
            .map(l -> new PassageGroepDocument(l.getBezwaarId(), l.getBestandsnaam()))
            .toList()));
```

Sortering: op `scorePercentage` aflopend (null achteraan), zoals nu.

**Step 4: Run tests**

```bash
mvn test -pl app -Dtest=KernbezwaarServiceTest
```

**Step 5: Commit**

```
feat: geefKernbezwaren assembleert passage-groep DTO structuur
```

---

## Task 9: Cascade verwijdering aanpassen

**Files:**
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/KernbezwaarService.java`
- Test: `app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/CascadeVerwijderingIntegrationTest.java`

**Step 1: Schrijf integratietest**

```java
@Test
@DisplayName("Bij documentverwijdering worden passage_groep_lid records verwijderd en lege groepen opgeruimd")
void passageGroepWordenOpgeruimdBijDocumentVerwijdering() {
  // Setup: 2 documenten, passage_groep met 2 leden (1 per document)
  // Verwijder 1 document
  // Assert: passage_groep_lid voor dat document is weg
  // Assert: passage_groep bestaat nog (heeft nog 1 lid)
}

@Test
@DisplayName("Passage_groep zonder leden wordt verwijderd")
void legePassageGroepWordtVerwijderd() {
  // Setup: passage_groep met 1 lid
  // Verwijder het document
  // Assert: passage_groep is verwijderd (geen leden meer)
}
```

**Step 2: Run test, verifieer dat het faalt**

```bash
mvn verify -pl app -Dtest=CascadeVerwijderingIntegrationTest
```

**Step 3: Implementeer**

Update `ruimOpNaBestandenVerwijdering`:
```java
public void ruimOpNaBestandenVerwijdering(String projectNaam, List<String> bestandsnamen) {
  for (String bestandsnaam : bestandsnamen) {
    // Verwijder passage_groep_lid records voor dit bestand
    passageGroepLidRepository.deleteByBestandsnaam(bestandsnaam, projectNaam);
  }
  // Verwijder passage_groepen zonder leden
  passageGroepRepository.deleteZonderLeden(projectNaam);
  // Verwijder kernbezwaar_referenties naar verwijderde passage_groepen
  referentieRepository.deleteByPassageGroepIdNotIn(projectNaam);
  // Bestaande opruimlogica
  kernbezwaarRepository.deleteZonderReferenties(projectNaam);
  themaRepository.deleteZonderKernbezwaren(projectNaam);
  clusteringTaakRepository.deleteZonderThema(projectNaam);
}
```

Voeg de benodigde repository-methodes toe (custom `@Query`).

**Step 4: Run tests**

```bash
mvn verify -pl app -Dtest=CascadeVerwijderingIntegrationTest
```

**Step 5: Commit**

```
feat: cascade verwijdering voor passage-groepen
```

---

## Task 10: Frontend — rendering en toggle

**Files:**
- Modify: `webapp/src/js/bezwaarschriften-kernbezwaren.js`
- Delete: `webapp/src/js/passage-groepering.js` (of verwijder import + gebruik)
- Test: `webapp/test/bezwaarschriften-kernbezwaren.cy.js`

**Step 1: Schrijf Cypress test voor nieuwe datastructuur**

```javascript
const MOCK_THEMAS_GEGROEPEERD = {
  themas: [{
    naam: 'Milieu',
    passageDeduplicatieVoorClustering: true,
    kernbezwaren: [{
      id: 1,
      samenvatting: 'Geluidshinder',
      antwoord: null,
      individueleBezwaren: [{
        samenvatting: 'De geluidshinder is onaanvaardbaar',
        scorePercentage: 85,
        passageGroep: {
          id: 42,
          passage: 'De geluidshinder is onaanvaardbaar...',
          documenten: [
            {bezwaarId: 1, bestandsnaam: 'bezwaar1.pdf'},
            {bezwaarId: 5, bestandsnaam: 'bezwaar2.pdf'},
          ],
        },
      }],
    }],
  }],
};

it('toont passage-groepen met meerdere documenten', () => {
  cy.intercept('GET', '/api/v1/projects/*/kernbezwaren', {
    statusCode: 200,
    body: MOCK_THEMAS_GEGROEPEERD,
  }).as('kernbezwaren');

  cy.mount(html`<bezwaarschriften-kernbezwaren></bezwaarschriften-kernbezwaren>`);
  cy.get('bezwaarschriften-kernbezwaren')
      .then(($el) => $el[0].laadKernbezwaren('testproject'));
  cy.wait('@kernbezwaren');

  // Passage-tekst zichtbaar
  cy.get('bezwaarschriften-kernbezwaren')
      .shadow()
      .find('[data-testid="passage-groep"]')
      .should('contain.text', 'De geluidshinder');

  // Aantal documenten zichtbaar
  cy.get('bezwaarschriften-kernbezwaren')
      .shadow()
      .find('[data-testid="passage-groep"]')
      .should('contain.text', '2 documenten');
});
```

**Step 2: Schrijf Cypress test voor deduplicatie-toggle**

```javascript
it('stuurt deduplicatieVoorClustering parameter mee bij clustering-start', () => {
  cy.intercept('POST', '/api/v1/projects/*/clustering-taken/*', (req) => {
    expect(req.query.deduplicatieVoorClustering).to.equal('true');
    req.reply({statusCode: 202, body: MOCK_TAAK});
  }).as('startClustering');

  // ... mount, klik op start-knop
  cy.wait('@startClustering');
});
```

**Step 3: Run tests, verifieer dat ze falen**

```bash
cd webapp && npm test
```

**Step 4: Implementeer frontend wijzigingen**

1. Verwijder `import {groepeerPassages} from './passage-groepering.js'`
2. Vervang alle aanroepen van `groepeerPassages(kern.individueleBezwaren)` door directe rendering van `kern.individueleBezwaren`
3. Render per `individueelBezwaar`: samenvatting, scorePercentage, passage, lijst documenten
4. Voeg toggle/checkbox toe bij clustering-start UI (default: aan)
5. Voeg `deduplicatieVoorClustering` parameter toe aan de POST-call

**Step 5: Run tests**

```bash
cd webapp && npm test
```

**Step 6: Build frontend + Maven process-resources**

```bash
cd webapp && npm run build
mvn process-resources -pl webapp -Denforcer.skip=true
```

**Step 7: Commit**

```
feat: frontend rendert passage-groepen uit backend, toggle voor deduplicatie
```

---

## Task 11: Volledige build + integratietests

**Step 1: Run alle backend tests**

```bash
mvn clean test -pl app
```

Fix eventuele compilatiefouten of falende tests.

**Step 2: Run integratietests**

```bash
mvn clean verify -pl app
```

**Step 3: Run frontend tests**

```bash
cd webapp && npm test
```

**Step 4: Volledige build**

```bash
mvn clean install
```

**Step 5: Commit**

```
chore: alle tests groen na passage-deduplicatie implementatie
```

---

## Test- en verificatieplan

### Unit tests (nieuw)
- [ ] `PassageGroepPersistentieTest`: opslaan en ophalen passage-groepen
- [ ] `PassageDeduplicatieServiceTest`: Dice-coefficient, groepering, edge cases
- [ ] `KernbezwaarServiceTest`: modus A (deduplicatie voor clustering)
- [ ] `KernbezwaarServiceTest`: modus B (deduplicatie na clustering)
- [ ] `KernbezwaarServiceTest`: geefKernbezwaren assembleert nieuwe DTO
- [ ] `ThemaTest`: nieuwe record-structuur

### Integratietests (nieuw/aangepast)
- [ ] `CascadeVerwijderingIntegrationTest`: passage_groep opruiming bij documentverwijdering
- [ ] `CascadeVerwijderingIntegrationTest`: lege passage_groep wordt verwijderd

### Frontend tests (nieuw/aangepast)
- [ ] Cypress: rendert passage-groepen met meerdere documenten
- [ ] Cypress: toont scorePercentage per passage-groep
- [ ] Cypress: toggle voor deduplicatie-modus bij clustering-start
- [ ] Cypress: stuurt parameter mee bij POST clustering-taak

### Handmatige verificatie
- [ ] Start applicatie lokaal (`mvn spring-boot:run -pl app -Pdev`)
- [ ] Upload 3+ bezwaarschriften met overlappende passages
- [ ] Start clustering MET deduplicatie → controleer dat identieke passages gegroepeerd zijn
- [ ] Start clustering ZONDER deduplicatie → controleer dat resultaat vergelijkbaar is maar passages achteraf gegroepeerd
- [ ] Verwijder een document → controleer cascade-verwijdering van passage_groep_lid
- [ ] Controleer in database dat passage_groep + passage_groep_lid records correct zijn
