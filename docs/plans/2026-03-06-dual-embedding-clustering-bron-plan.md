# Dual Embedding met Clustering Bron-keuze - Implementatieplan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Bij extractie zowel de passage als de samenvatting embedden, en bij clustering kunnen kiezen welke embedding-bron te gebruiken.

**Architecture:** Twee embedding-kolommen op `geextraheerd_bezwaar` (`embedding_passage` + `embedding_samenvatting`). Een project-brede toggle in `ClusteringConfig` bepaalt welke bron HDBSCAN gebruikt. Frontend: checkbox in de clustering parameters balk.

**Tech Stack:** Java 21, Spring Boot 3.x, Liquibase, Lit web components (@domg-wc), Cypress

**Design:** `docs/plans/2026-03-06-dual-embedding-clustering-bron-design.md`

---

### Task 1: Liquibase-migratie — hernoem embedding kolom en voeg samenvatting-embedding toe

**Files:**
- Create: `app/src/main/resources/config/liquibase/changelog/20260306-dual-embedding.xml`
- Modify: `app/src/main/resources/config/liquibase/master.xml:19` (voeg include toe)

**Step 1: Maak de Liquibase changelog**

```xml
<?xml version="1.0" encoding="utf-8"?>
<databaseChangeLog
    xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-3.1.xsd">

  <changeSet id="20260306-hernoem-embedding-naar-passage" author="kenzo">
    <preConditions onFail="MARK_RAN" onError="MARK_RAN">
      <dbms type="postgresql"/>
    </preConditions>
    <sql>
      ALTER TABLE geextraheerd_bezwaar RENAME COLUMN embedding TO embedding_passage;
    </sql>
  </changeSet>

  <changeSet id="20260306-embedding-samenvatting" author="kenzo">
    <preConditions onFail="MARK_RAN" onError="MARK_RAN">
      <dbms type="postgresql"/>
    </preConditions>
    <sql>
      ALTER TABLE geextraheerd_bezwaar ADD COLUMN IF NOT EXISTS embedding_samenvatting vector(1024);
    </sql>
  </changeSet>

</databaseChangeLog>
```

**Step 2: Voeg de include toe in master.xml**

Na regel 19 (`20260304-verwijder-geannuleerd.xml`) toevoegen:

```xml
  <include file="config/liquibase/changelog/20260306-dual-embedding.xml"/>
```

**Step 3: Commit**

```bash
git add app/src/main/resources/config/liquibase/
git commit -m "feat: liquibase migratie voor dual embedding kolommen"
```

---

### Task 2: Entiteit aanpassen — twee embedding-velden

**Files:**
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/GeextraheerdBezwaarEntiteit.java:39-43,93-105`

**Step 1: Vervang het `embedding` veld door twee velden**

Vervang het bestaande `embedding` veld (regel 39-43) en bijbehorende getter/setter (regel 93-105):

Oud:
```java
@Type(type = "be.vlaanderen.omgeving.bezwaarschriften.config.VectorType")
@Column(name = "embedding", columnDefinition = "vector(1024)")
private float[] embedding;
```

Nieuw:
```java
@Type(type = "be.vlaanderen.omgeving.bezwaarschriften.config.VectorType")
@Column(name = "embedding_passage", columnDefinition = "vector(1024)")
private float[] embeddingPassage;

@Type(type = "be.vlaanderen.omgeving.bezwaarschriften.config.VectorType")
@Column(name = "embedding_samenvatting", columnDefinition = "vector(1024)")
private float[] embeddingSamenvatting;
```

Vervang de getter/setter:

Oud:
```java
public float[] getEmbedding() { return embedding; }
public void setEmbedding(float[] embedding) { this.embedding = embedding; }
```

Nieuw:
```java
public float[] getEmbeddingPassage() { return embeddingPassage; }
public void setEmbeddingPassage(float[] embeddingPassage) { this.embeddingPassage = embeddingPassage; }

public float[] getEmbeddingSamenvatting() { return embeddingSamenvatting; }
public void setEmbeddingSamenvatting(float[] embeddingSamenvatting) { this.embeddingSamenvatting = embeddingSamenvatting; }
```

**Step 2: Fix compilatiefouten**

Na deze wijziging zullen er compilatiefouten zijn in bestanden die `getEmbedding()`/`setEmbedding()` aanroepen. Dat fixen we in de volgende taken. Verifieer met:

```bash
mvn compile -pl app -DskipTests 2>&1 | grep "cannot find symbol" | head -20
```

Verwacht: compilatiefouten in `ExtractieTaakService`, `KernbezwaarService`, en tests.

**Step 3: Commit**

```bash
git add app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/GeextraheerdBezwaarEntiteit.java
git commit -m "refactor: vervang embedding door embeddingPassage en embeddingSamenvatting"
```

---

### Task 3: ClusteringConfig — voeg clusterOpPassages toggle toe

**Files:**
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/clustering/ClusteringConfig.java:11` (nieuw veld)

**Step 1: Voeg het veld toe aan ClusteringConfig**

Na de bestaande velden (regel ~17, na `umapMinDist`) toevoegen:

```java
private boolean clusterOpPassages = true;

public boolean isClusterOpPassages() {
  return clusterOpPassages;
}

public void setClusterOpPassages(boolean clusterOpPassages) {
  this.clusterOpPassages = clusterOpPassages;
}
```

**Step 2: Commit**

```bash
git add app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/clustering/ClusteringConfig.java
git commit -m "feat: clusterOpPassages toggle in ClusteringConfig"
```

---

### Task 4: ClusteringConfigController — ConfigDto uitbreiden

**Files:**
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/clustering/ClusteringConfigController.java:25-47`

**Step 1: Pas de ConfigDto record en de GET/PUT methoden aan**

Voeg `clusterOpPassages` toe aan de `ConfigDto` record:

```java
public record ConfigDto(int minClusterSize, int minSamples,
    double clusterSelectionEpsilon,
    boolean umapEnabled, int umapNComponents,
    int umapNNeighbors, float umapMinDist,
    boolean clusterOpPassages) {}
```

Pas de `geef()` methode aan — voeg toe na `config.getUmapMinDist()`:
```java
config.isClusterOpPassages()
```

Pas de `update()` methode aan — voeg toe na `config.setUmapMinDist(dto.umapMinDist())`:
```java
config.setClusterOpPassages(dto.clusterOpPassages());
```

**Step 2: Commit**

```bash
git add app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/clustering/ClusteringConfigController.java
git commit -m "feat: clusterOpPassages in clustering config REST API"
```

---

### Task 5: ExtractieTaakService — dual embedding bij markeerKlaar

**Files:**
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieTaakService.java:225-240`

**Step 1: Pas de embedding-generatie aan in markeerKlaar**

Het huidige blok (regel ~225-240) genereert één embedding per bezwaar op de passage-tekst. Vervang dit door twee batch-calls:

Oud:
```java
// Genereer embeddings voor alle bezwaren (batch) en sla op
if (!bezwaarEntiteiten.isEmpty()) {
  var teksten = bezwaarEntiteiten.stream()
      .map(b -> {
        var tekst = passageMap.get(b.getPassageNr());
        return tekst != null ? tekst : b.getSamenvatting();
      })
      .toList();
  var embeddings = embeddingPoort.genereerEmbeddings(teksten);
  for (int i = 0; i < bezwaarEntiteiten.size(); i++) {
    bezwaarEntiteiten.get(i).setEmbedding(embeddings.get(i));
    bezwaarRepository.save(bezwaarEntiteiten.get(i));
  }
}
```

Nieuw:
```java
// Genereer embeddings voor alle bezwaren (batch): passage + samenvatting
if (!bezwaarEntiteiten.isEmpty()) {
  var passageTeksten = bezwaarEntiteiten.stream()
      .map(b -> {
        var tekst = passageMap.get(b.getPassageNr());
        return tekst != null ? tekst : b.getSamenvatting();
      })
      .toList();
  var samenvattingen = bezwaarEntiteiten.stream()
      .map(GeextraheerdBezwaarEntiteit::getSamenvatting)
      .toList();
  var passageEmbeddings = embeddingPoort.genereerEmbeddings(passageTeksten);
  var samenvattingEmbeddings = embeddingPoort.genereerEmbeddings(samenvattingen);
  for (int i = 0; i < bezwaarEntiteiten.size(); i++) {
    bezwaarEntiteiten.get(i).setEmbeddingPassage(passageEmbeddings.get(i));
    bezwaarEntiteiten.get(i).setEmbeddingSamenvatting(samenvattingEmbeddings.get(i));
    bezwaarRepository.save(bezwaarEntiteiten.get(i));
  }
}
```

**Step 2: Commit**

```bash
git add app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieTaakService.java
git commit -m "feat: dual embedding (passage + samenvatting) bij extractie"
```

---

### Task 6: ExtractieTaakService — dual embedding bij voegManueelBezwaarToe

**Files:**
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieTaakService.java:463-466`

**Step 1: Vervang de enkele embedding-call**

Oud:
```java
var embedding = embeddingPoort.genereerEmbeddings(List.of(passageTekst)).get(0);
opgeslagen.setEmbedding(embedding);
bezwaarRepository.save(opgeslagen);
```

Nieuw:
```java
var embeddings = embeddingPoort.genereerEmbeddings(List.of(passageTekst, samenvatting));
opgeslagen.setEmbeddingPassage(embeddings.get(0));
opgeslagen.setEmbeddingSamenvatting(embeddings.get(1));
bezwaarRepository.save(opgeslagen);
```

**Step 2: Commit**

```bash
git add app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ExtractieTaakService.java
git commit -m "feat: dual embedding bij manueel bezwaar toevoegen"
```

---

### Task 7: KernbezwaarService — gebruik config voor embedding-bron bij clustering

**Files:**
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/KernbezwaarService.java:253-371`

**Step 1: Helper-methode toevoegen voor embedding-selectie**

Voeg een private helper toe na `cosinusGelijkenis` (rond regel 385):

```java
private float[] geefEmbedding(GeextraheerdBezwaarEntiteit bezwaar) {
  return clusteringConfig.isClusterOpPassages()
      ? bezwaar.getEmbeddingPassage()
      : bezwaar.getEmbeddingSamenvatting();
}
```

**Step 2: Pas clusterCategorie aan**

In `clusterCategorie` (regel 253-342), vervang overal `b.getEmbedding()` door `geefEmbedding(b)`:

1. **Legacy embedding-generatie** (regel ~263-275): Bij bezwaren zonder embedding, genereer nu beide:

Oud:
```java
var zonderEmbedding = bezwaren.stream()
    .filter(b -> b.getEmbedding() == null)
    .toList();
if (!zonderEmbedding.isEmpty()) {
  var teksten = zonderEmbedding.stream()
      .map(b -> geefPassageTekst(b, passageLookup))
      .toList();
  var embeddings = embeddingPoort.genereerEmbeddings(teksten);
  transactionTemplate.executeWithoutResult(status -> {
    for (int i = 0; i < zonderEmbedding.size(); i++) {
      zonderEmbedding.get(i).setEmbedding(embeddings.get(i));
    }
    bezwaarRepository.saveAll(zonderEmbedding);
  });
}
```

Nieuw:
```java
var zonderEmbedding = bezwaren.stream()
    .filter(b -> b.getEmbeddingPassage() == null)
    .toList();
if (!zonderEmbedding.isEmpty()) {
  var passageTeksten = zonderEmbedding.stream()
      .map(b -> geefPassageTekst(b, passageLookup))
      .toList();
  var samenvattingen = zonderEmbedding.stream()
      .map(GeextraheerdBezwaarEntiteit::getSamenvatting)
      .toList();
  var passageEmbeddings = embeddingPoort.genereerEmbeddings(passageTeksten);
  var samenvattingEmbeddings = embeddingPoort.genereerEmbeddings(samenvattingen);
  transactionTemplate.executeWithoutResult(status -> {
    for (int i = 0; i < zonderEmbedding.size(); i++) {
      zonderEmbedding.get(i).setEmbeddingPassage(passageEmbeddings.get(i));
      zonderEmbedding.get(i).setEmbeddingSamenvatting(samenvattingEmbeddings.get(i));
    }
    bezwaarRepository.saveAll(zonderEmbedding);
  });
}
```

2. **ClusteringInvoer** (regel ~284): vervang `b.getEmbedding()` door `geefEmbedding(b)`:

```java
var origineleInvoer = bezwaren.stream()
    .map(b -> new ClusteringInvoer(b.getId(), geefEmbedding(b)))
    .toList();
```

**Step 3: Pas berekenOrigineleCentroid aan (regel 344-357)**

Vervang `bezwaar.getEmbedding()` door `geefEmbedding(bezwaar)`:

```java
private float[] berekenOrigineleCentroid(List<GeextraheerdBezwaarEntiteit> bezwaren) {
  int dims = geefEmbedding(bezwaren.get(0)).length;
  var centroid = new float[dims];
  for (var bezwaar : bezwaren) {
    var emb = geefEmbedding(bezwaar);
    for (int i = 0; i < dims; i++) {
      centroid[i] += emb[i];
    }
  }
  for (int i = 0; i < dims; i++) {
    centroid[i] /= bezwaren.size();
  }
  return centroid;
}
```

**Step 4: Pas vindDichtstBijCentroid aan (regel 359-371)**

Vervang `bezwaar.getEmbedding()` door `geefEmbedding(bezwaar)`:

```java
private GeextraheerdBezwaarEntiteit vindDichtstBijCentroid(
    List<GeextraheerdBezwaarEntiteit> bezwaren, float[] centroid) {
  GeextraheerdBezwaarEntiteit dichtstbij = null;
  double hoogsteGelijkenis = Double.NEGATIVE_INFINITY;
  for (var bezwaar : bezwaren) {
    double gelijkenis = cosinusGelijkenis(geefEmbedding(bezwaar), centroid);
    if (gelijkenis > hoogsteGelijkenis) {
      hoogsteGelijkenis = gelijkenis;
      dichtstbij = bezwaar;
    }
  }
  return dichtstbij;
}
```

**Step 5: Verifieer dat het compileert**

```bash
mvn compile -pl app -DskipTests
```

Verwacht: geen compilatiefouten.

**Step 6: Commit**

```bash
git add app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/KernbezwaarService.java
git commit -m "feat: embedding-bron selectie op basis van clusterOpPassages config"
```

---

### Task 8: Unit tests aanpassen — KernbezwaarServiceTest

**Files:**
- Modify: `app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/KernbezwaarServiceTest.java`

**Step 1: Pas de hulpmethode `maakBezwaarMetEmbedding` aan (regel 607-612)**

Oud:
```java
private GeextraheerdBezwaarEntiteit maakBezwaarMetEmbedding(Long id, Long taakId,
    int passageNr, String samenvatting, String categorie, float[] embedding) {
  var b = maakBezwaar(id, taakId, passageNr, samenvatting, categorie);
  b.setEmbedding(embedding);
  return b;
}
```

Nieuw:
```java
private GeextraheerdBezwaarEntiteit maakBezwaarMetEmbedding(Long id, Long taakId,
    int passageNr, String samenvatting, String categorie, float[] embedding) {
  var b = maakBezwaar(id, taakId, passageNr, samenvatting, categorie);
  b.setEmbeddingPassage(embedding);
  b.setEmbeddingSamenvatting(embedding);
  return b;
}
```

**Step 2: Fix alle overige `setEmbedding`/`getEmbedding` referenties**

Zoek en vervang in het testbestand:
- `b.setEmbedding(` → `b.setEmbeddingPassage(` (en voeg `b.setEmbeddingSamenvatting(` toe)
- `b.getEmbedding()` → `b.getEmbeddingPassage()`

Tip: gebruik `grep -n "setEmbedding\|getEmbedding" KernbezwaarServiceTest.java` om alle plekken te vinden.

**Step 3: Voeg een test toe voor clustering op samenvattingen**

Voeg een nieuwe test toe die verifieert dat bij `clusterOpPassages = false` de samenvatting-embeddings worden gebruikt:

```java
@Test
void gebruiktSamenvattingEmbeddingsBijClusterOpPassagesFalse() {
  // Arrange: bezwaren met verschillende passage- en samenvatting-embeddings
  when(clusteringConfig.isClusterOpPassages()).thenReturn(false);

  var passageEmb = new float[]{1.0f, 0.0f, 0.0f};
  var samenvattingEmb = new float[]{0.0f, 1.0f, 0.0f};

  var b1 = maakBezwaar(1L, 100L, 1, "samenvatting 1", "milieu");
  b1.setEmbeddingPassage(passageEmb);
  b1.setEmbeddingSamenvatting(samenvattingEmb);
  var b2 = maakBezwaar(2L, 100L, 2, "samenvatting 2", "milieu");
  b2.setEmbeddingPassage(passageEmb);
  b2.setEmbeddingSamenvatting(samenvattingEmb);

  when(bezwaarRepository.findByProjectNaamAndCategorie("test", "milieu"))
      .thenReturn(List.of(b1, b2));
  when(taakRepository.findAllById(any())).thenReturn(List.of(maakTaak(100L, "test", "doc.pdf")));
  when(passageRepository.findByTaakIdIn(any())).thenReturn(List.of());
  when(clusteringConfig.isUmapEnabled()).thenReturn(false);

  var clusterResultaat = new ClusterResultaat(
      List.of(new ClusterResultaat.Cluster(List.of(1L, 2L))), List.of());
  when(clusteringPoort.cluster(any())).thenReturn(clusterResultaat);
  when(themaRepository.save(any())).thenAnswer(inv -> {
    var t = inv.getArgument(0, ThemaEntiteit.class);
    t.setId(1L);
    return t;
  });
  when(kernbezwaarRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

  // Act
  service.clusterEenCategorie("test", "milieu", null);

  // Assert: verifieer dat de clustering-invoer de samenvatting-embedding bevat
  var invoerCaptor = ArgumentCaptor.forClass(List.class);
  verify(clusteringPoort).cluster(invoerCaptor.capture());
  @SuppressWarnings("unchecked")
  var invoer = (List<ClusteringInvoer>) invoerCaptor.getValue();
  assertArrayEquals(samenvattingEmb, invoer.get(0).embedding());
}
```

**Step 4: Draai de tests**

```bash
mvn test -pl app -Dtest=KernbezwaarServiceTest
```

Verwacht: alle tests slagen.

**Step 5: Commit**

```bash
git add app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/KernbezwaarServiceTest.java
git commit -m "test: pas KernbezwaarServiceTest aan voor dual embedding"
```

---

### Task 9: Overige test-compilatiefouten fixen

**Files:**
- Modify: alle testbestanden die `setEmbedding`/`getEmbedding` gebruiken

**Step 1: Zoek alle resterende referenties**

```bash
cd app && grep -rn "setEmbedding\|getEmbedding" src/test/ --include="*.java" | grep -v "Passage\|Samenvatting"
```

**Step 2: Fix elke referentie**

- `setEmbedding(x)` → `setEmbeddingPassage(x)` + `setEmbeddingSamenvatting(x)`
- `getEmbedding()` → `getEmbeddingPassage()` (of `getEmbeddingSamenvatting()` afhankelijk van context)

**Step 3: Draai alle tests**

```bash
mvn test -pl app
```

Verwacht: alle tests slagen.

**Step 4: Commit**

```bash
git add -A app/src/test/
git commit -m "test: fix alle resterende embedding-referenties in tests"
```

---

### Task 10: Frontend — checkbox "Cluster op passages" toevoegen

**Files:**
- Modify: `webapp/src/js/bezwaarschriften-kernbezwaren.js:806-887` (`_renderClusteringParams`)

**Step 1: Voeg een checkbox toe na de titel**

In `_renderClusteringParams`, na het toevoegen van de titel (regel ~815) en voor de fetch-call (regel ~816), voeg de checkbox toe **in de `.then(config => ...)` callback**, direct na de UMAP-toggle blok (na regel ~858). Voeg toe net voor het HDBSCAN parameters blok:

```javascript
          // Cluster op passages toggle
          const passageToggleWrapper = document.createElement('label');
          passageToggleWrapper.textContent = 'Cluster op passages: ';
          const passageToggle = document.createElement('input');
          passageToggle.type = 'checkbox';
          passageToggle.checked = config.clusterOpPassages;
          passageToggle.addEventListener('change', () => {
            this._updateClusteringConfig('clusterOpPassages', passageToggle.checked);
          });
          passageToggleWrapper.appendChild(passageToggle);
          balk.appendChild(passageToggleWrapper);
```

**Step 2: Build de frontend**

```bash
cd webapp && npm run build
```

Verwacht: geen fouten.

**Step 3: Commit**

```bash
git add webapp/src/js/bezwaarschriften-kernbezwaren.js
git commit -m "feat: checkbox 'Cluster op passages' in clustering config UI"
```

---

### Task 11: Cypress test voor de nieuwe checkbox

**Files:**
- Modify: `webapp/test/bezwaarschriften-kernbezwaren.cy.js`

**Step 1: Voeg een test toe voor de checkbox**

Zoek de bestaande test(s) voor clustering parameters (zoek naar `clusteringConfig` of `umapEnabled` in het testbestand) en voeg een vergelijkbare test toe:

```javascript
it('toont de cluster-op-passages checkbox met juiste standaardwaarde', () => {
  cy.intercept('GET', '/api/v1/clustering-config', {
    statusCode: 200,
    body: {
      minClusterSize: 5,
      minSamples: 3,
      clusterSelectionEpsilon: 0.0,
      umapEnabled: true,
      umapNComponents: 5,
      umapNNeighbors: 15,
      umapMinDist: 0.1,
      clusterOpPassages: true,
    },
  }).as('getConfig');

  // Trigger rendering van de clustering params (afhankelijk van bestaande test setup)
  // ... mount/render component ...

  cy.wait('@getConfig');
  cy.get('.clustering-params label').contains('Cluster op passages').find('input[type="checkbox"]').should('be.checked');
});

it('stuurt update bij wijzigen cluster-op-passages checkbox', () => {
  cy.intercept('GET', '/api/v1/clustering-config', {
    statusCode: 200,
    body: {
      minClusterSize: 5, minSamples: 3, clusterSelectionEpsilon: 0.0,
      umapEnabled: true, umapNComponents: 5, umapNNeighbors: 15, umapMinDist: 0.1,
      clusterOpPassages: true,
    },
  }).as('getConfig');

  cy.intercept('PUT', '/api/v1/clustering-config', {statusCode: 200, body: {}}).as('putConfig');

  // ... mount/render component ...

  cy.wait('@getConfig');
  cy.get('.clustering-params label').contains('Cluster op passages').find('input[type="checkbox"]').uncheck();
  cy.wait('@putConfig').its('request.body').should('have.property', 'clusterOpPassages', false);
});
```

Pas de test setup aan zodat het aansluit bij het bestaande patroon in het Cypress testbestand.

**Step 2: Draai de Cypress tests**

```bash
cd webapp && npm test
```

Verwacht: alle tests slagen.

**Step 3: Commit**

```bash
git add webapp/test/bezwaarschriften-kernbezwaren.cy.js
git commit -m "test: Cypress test voor cluster-op-passages checkbox"
```

---

### Task 12: Volledige build en integratie-verificatie

**Files:** geen nieuwe wijzigingen

**Step 1: Full backend build**

```bash
mvn clean install -DskipTests && mvn test -pl app
```

Verwacht: build slaagt, alle tests slagen.

**Step 2: Frontend build + copy naar target**

```bash
cd webapp && npm run build && cd .. && mvn process-resources -pl webapp -Denforcer.skip=true
```

**Step 3: Frontend tests**

```bash
cd webapp && npm test
```

Verwacht: alle tests slagen.

**Step 4: Commit (indien er nog ongecommitte fixes zijn)**

---

## Test- en Verificatieplan

| Test | Type | Verificatie |
|------|------|-------------|
| Bestaande KernbezwaarServiceTest tests slagen | Unit | `mvn test -Dtest=KernbezwaarServiceTest` |
| Nieuwe test: clustering gebruikt samenvatting-embedding als config zo staat | Unit | `mvn test -Dtest=KernbezwaarServiceTest#gebruiktSamenvattingEmbeddingsBijClusterOpPassagesFalse` |
| ExtractieTaakService tests slagen (dual embedding) | Unit | `mvn test -Dtest=ExtractieTaakServiceTest` |
| ClusteringConfigController accepteert clusterOpPassages | Unit/Integratie | Bestaande test of handmatig via curl |
| Cypress: checkbox toont juiste default | Component | `npm test` |
| Cypress: checkbox stuurt PUT met clusterOpPassages | Component | `npm test` |
| Liquibase-migratie draait zonder fouten | Integratie | `mvn verify -pl app` (Testcontainers) |
