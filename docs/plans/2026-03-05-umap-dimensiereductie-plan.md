# UMAP Dimensiereductie Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Voeg UMAP-dimensiereductie toe als configureerbare tussenstap tussen embedding-generatie en HDBSCAN-clustering, zodat semantisch gelijkaardige bezwaren beter geclusterd worden.

**Architecture:** Nieuwe hexagonale port `DimensieReductiePoort` met `UmapDimensieReductieAdapter` (tag.bio:umap). `KernbezwaarService.clusterCategorie()` roept optioneel de reductie aan vóór HDBSCAN. Alle parameters configureerbaar via REST API en frontend UI.

**Tech Stack:** Java 21, tag.bio:umap:1.1.0, Spring Boot 3.x, Lit web components (@domg-wc)

**Design doc:** `docs/plans/2026-03-05-umap-dimensiereductie-design.md`

---

### Task 1: Maven dependency toevoegen

**Files:**
- Modify: `app/pom.xml:128-132` (na bestaande Tribuo dependency)

**Step 1: Voeg UMAP dependency toe**

In `app/pom.xml`, na de `tribuo-clustering-hdbscan` dependency (regel 132):

```xml
		<dependency>
			<groupId>tag.bio</groupId>
			<artifactId>umap</artifactId>
			<version>1.1.0</version>
		</dependency>
```

**Step 2: Verifieer dat het compileert**

Run: `mvn compile -pl app -DskipTests`
Expected: BUILD SUCCESS

**Step 3: Commit**

```
feat: voeg tag.bio:umap dependency toe voor dimensiereductie
```

---

### Task 2: DimensieReductiePoort interface

**Files:**
- Create: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/clustering/DimensieReductiePoort.java`

**Step 1: Schrijf de port interface**

```java
package be.vlaanderen.omgeving.bezwaarschriften.clustering;

import java.util.List;

/**
 * Port voor dimensiereductie van embedding-vectoren.
 *
 * <p>Reduceert hoog-dimensionale vectoren (bv. 1024D) naar een compactere
 * ruimte met behoud van semantische relaties, zodat clustering-algoritmen
 * beter presteren.
 */
public interface DimensieReductiePoort {

  /**
   * Reduceert de gegeven vectoren naar een lagere dimensie.
   *
   * @param vectoren lijst van embedding-vectoren
   * @return lijst van gereduceerde vectoren in dezelfde volgorde
   */
  List<float[]> reduceer(List<float[]> vectoren);
}
```

**Step 2: Verifieer dat het compileert**

Run: `mvn compile -pl app -DskipTests`
Expected: BUILD SUCCESS

**Step 3: Commit**

```
feat: voeg DimensieReductiePoort interface toe
```

---

### Task 3: ClusteringConfig uitbreiden met UMAP-parameters

**Files:**
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/clustering/ClusteringConfig.java:9-40`
- Modify: `app/src/main/resources/application-dev.yml:9-12`

**Step 1: Schrijf de test**

Create: `app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/clustering/ClusteringConfigTest.java`

```java
package be.vlaanderen.omgeving.bezwaarschriften.clustering;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ClusteringConfigTest {

  @Test
  void defaultUmapWaarden() {
    var config = new ClusteringConfig();

    assertThat(config.isUmapEnabled()).isTrue();
    assertThat(config.getUmapNComponents()).isEqualTo(5);
    assertThat(config.getUmapNNeighbors()).isEqualTo(15);
    assertThat(config.getUmapMinDist()).isEqualTo(0.1f);
  }

  @Test
  void umapWaardenAanpasbaar() {
    var config = new ClusteringConfig();

    config.setUmapEnabled(false);
    config.setUmapNComponents(10);
    config.setUmapNNeighbors(30);
    config.setUmapMinDist(0.5f);

    assertThat(config.isUmapEnabled()).isFalse();
    assertThat(config.getUmapNComponents()).isEqualTo(10);
    assertThat(config.getUmapNNeighbors()).isEqualTo(30);
    assertThat(config.getUmapMinDist()).isEqualTo(0.5f);
  }
}
```

**Step 2: Run test, verifieer dat het faalt**

Run: `mvn test -pl app -Dtest=ClusteringConfigTest`
Expected: FAIL — `isUmapEnabled()` bestaat niet

**Step 3: Voeg UMAP-velden toe aan ClusteringConfig**

Voeg toe aan `ClusteringConfig.java` na de bestaande velden:

```java
  private boolean umapEnabled = true;
  private int umapNComponents = 5;
  private int umapNNeighbors = 15;
  private float umapMinDist = 0.1f;

  public boolean isUmapEnabled() {
    return umapEnabled;
  }

  public void setUmapEnabled(boolean umapEnabled) {
    this.umapEnabled = umapEnabled;
  }

  public int getUmapNComponents() {
    return umapNComponents;
  }

  public void setUmapNComponents(int umapNComponents) {
    this.umapNComponents = umapNComponents;
  }

  public int getUmapNNeighbors() {
    return umapNNeighbors;
  }

  public void setUmapNNeighbors(int umapNNeighbors) {
    this.umapNNeighbors = umapNNeighbors;
  }

  public float getUmapMinDist() {
    return umapMinDist;
  }

  public void setUmapMinDist(float umapMinDist) {
    this.umapMinDist = umapMinDist;
  }
```

**Step 4: Voeg UMAP-config toe aan application-dev.yml**

Voeg toe na `cluster-selection-epsilon: 0.2`:

```yaml
    umap-enabled: true
    umap-n-components: 5
    umap-n-neighbors: 15
    umap-min-dist: 0.1
```

**Step 5: Run test, verifieer dat het slaagt**

Run: `mvn test -pl app -Dtest=ClusteringConfigTest`
Expected: BUILD SUCCESS

**Step 6: Commit**

```
feat: voeg UMAP-parameters toe aan ClusteringConfig
```

---

### Task 4: UmapDimensieReductieAdapter

**Files:**
- Create: `app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/clustering/UmapDimensieReductieAdapterTest.java`
- Create: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/clustering/UmapDimensieReductieAdapter.java`

**Step 1: Schrijf de tests**

```java
package be.vlaanderen.omgeving.bezwaarschriften.clustering;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UmapDimensieReductieAdapterTest {

  private UmapDimensieReductieAdapter adapter;

  @BeforeEach
  void setUp() {
    var config = new ClusteringConfig();
    config.setUmapNComponents(3);
    config.setUmapNNeighbors(5);
    config.setUmapMinDist(0.1f);
    adapter = new UmapDimensieReductieAdapter(config);
  }

  @Test
  void reduceertNaarJuisteAantalDimensies() {
    var vectoren = genereerRandomVectoren(20, 50);

    var resultaat = adapter.reduceer(vectoren);

    assertThat(resultaat).hasSize(20);
    resultaat.forEach(v -> assertThat(v.length).isEqualTo(3));
  }

  @Test
  void bewaartVolgorde() {
    var vectoren = genereerRandomVectoren(20, 50);

    var resultaat = adapter.reduceer(vectoren);

    assertThat(resultaat).hasSize(vectoren.size());
  }

  @Test
  void teWeinigDatapuntenGeeftOrigineleVectorenTerug() {
    // nNeighbors=5, dus < 6 datapunten is te weinig
    var vectoren = genereerRandomVectoren(3, 50);

    var resultaat = adapter.reduceer(vectoren);

    assertThat(resultaat).hasSize(3);
    // Originele vectoren terug (50D, niet 3D)
    resultaat.forEach(v -> assertThat(v.length).isEqualTo(50));
  }

  private List<float[]> genereerRandomVectoren(int aantal, int dimensies) {
    var random = new Random(42);
    var vectoren = new ArrayList<float[]>();
    for (int i = 0; i < aantal; i++) {
      var vector = new float[dimensies];
      for (int j = 0; j < dimensies; j++) {
        vector[j] = random.nextFloat();
      }
      vectoren.add(vector);
    }
    return vectoren;
  }
}
```

**Step 2: Run test, verifieer dat het faalt**

Run: `mvn test -pl app -Dtest=UmapDimensieReductieAdapterTest`
Expected: FAIL — `UmapDimensieReductieAdapter` bestaat niet

**Step 3: Schrijf de implementatie**

```java
package be.vlaanderen.omgeving.bezwaarschriften.clustering;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tagbio.umap.Umap;

/**
 * Dimensiereductie-adapter die UMAP toepast via tag.bio/umap.
 *
 * <p>Reduceert hoog-dimensionale embedding-vectoren naar een compactere ruimte
 * zodat HDBSCAN beter kan clusteren.
 */
@Component
public class UmapDimensieReductieAdapter implements DimensieReductiePoort {

  private static final Logger LOG =
      LoggerFactory.getLogger(UmapDimensieReductieAdapter.class);

  private final ClusteringConfig config;

  public UmapDimensieReductieAdapter(ClusteringConfig config) {
    this.config = config;
  }

  @Override
  public List<float[]> reduceer(List<float[]> vectoren) {
    int minimaalAantal = config.getUmapNNeighbors() + 1;
    if (vectoren.size() < minimaalAantal) {
      LOG.warn("Te weinig datapunten ({}) voor UMAP (minimaal {}), "
          + "gebruik originele vectoren", vectoren.size(), minimaalAantal);
      return vectoren;
    }

    var umap = new Umap();
    umap.setNumberComponents(config.getUmapNComponents());
    umap.setNumberNearestNeighbours(config.getUmapNNeighbors());
    umap.setMinDist(config.getUmapMinDist());
    umap.setVerbose(false);

    double[][] invoer = naarDoubleMatrix(vectoren);
    double[][] resultaat = umap.fitTransform(invoer);
    return naarFloatLijst(resultaat);
  }

  private double[][] naarDoubleMatrix(List<float[]> vectoren) {
    var matrix = new double[vectoren.size()][];
    for (int i = 0; i < vectoren.size(); i++) {
      var floats = vectoren.get(i);
      var doubles = new double[floats.length];
      for (int j = 0; j < floats.length; j++) {
        doubles[j] = floats[j];
      }
      matrix[i] = doubles;
    }
    return matrix;
  }

  private List<float[]> naarFloatLijst(double[][] matrix) {
    var lijst = new ArrayList<float[]>(matrix.length);
    for (var rij : matrix) {
      var floats = new float[rij.length];
      for (int j = 0; j < rij.length; j++) {
        floats[j] = (float) rij[j];
      }
      lijst.add(floats);
    }
    return lijst;
  }
}
```

**Step 4: Run test, verifieer dat het slaagt**

Run: `mvn test -pl app -Dtest=UmapDimensieReductieAdapterTest`
Expected: BUILD SUCCESS

**Step 5: Commit**

```
feat: voeg UmapDimensieReductieAdapter toe
```

---

### Task 5: KernbezwaarService integratie

**Files:**
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/KernbezwaarService.java:26-80` (constructor + velden)
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/KernbezwaarService.java:242-310` (clusterCategorie methode)
- Modify: `app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/KernbezwaarServiceTest.java`

**Step 1: Pas de test aan — voeg mock voor DimensieReductiePoort toe**

In `KernbezwaarServiceTest.java`, voeg toe:

```java
@Mock
private DimensieReductiePoort dimensieReductiePoort;

@Mock
private ClusteringConfig clusteringConfig;
```

Pas de constructor-aanroep van `KernbezwaarService` aan om `dimensieReductiePoort` en `clusteringConfig` mee te geven.

**Step 2: Schrijf een test voor UMAP-integratie**

Voeg toe aan `KernbezwaarServiceTest.java`:

```java
@Test
void clusterEenCategorieGebruiktUmapAlsIngeschakeld() {
    // Arrange: UMAP ingeschakeld
    when(clusteringConfig.isUmapEnabled()).thenReturn(true);
    when(clusteringConfig.getUmapNNeighbors()).thenReturn(15);

    // ... setup bezwaren met embeddings, mock clusteringPoort, etc.
    // De test verifieert dat dimensieReductiePoort.reduceer() wordt aangeroepen

    verify(dimensieReductiePoort).reduceer(anyList());
}

@Test
void clusterEenCategorieSlaatUmapOverAlsUitgeschakeld() {
    when(clusteringConfig.isUmapEnabled()).thenReturn(false);

    // ... setup en trigger clustering

    verify(dimensieReductiePoort, never()).reduceer(anyList());
}
```

**Step 3: Run test, verifieer dat het faalt**

Run: `mvn test -pl app -Dtest=KernbezwaarServiceTest`
Expected: FAIL — constructor accepteert `DimensieReductiePoort` niet

**Step 4: Pas KernbezwaarService aan**

Wijzigingen in `KernbezwaarService.java`:

1. Voeg velden toe:
```java
  private final DimensieReductiePoort dimensieReductiePoort;
  private final ClusteringConfig clusteringConfig;
```

2. Voeg toe aan constructor:
```java
  public KernbezwaarService(EmbeddingPoort embeddingPoort,
      ClusteringPoort clusteringPoort,
      DimensieReductiePoort dimensieReductiePoort,
      ClusteringConfig clusteringConfig,
      // ... bestaande parameters
```

3. In `clusterCategorie()`, na het aanmaken van `invoer` (rond regel 274) en vóór `clusteringPoort.cluster(invoer)`:

```java
    // Optionele UMAP-dimensiereductie
    if (clusteringConfig.isUmapEnabled()
        && invoer.size() >= clusteringConfig.getUmapNNeighbors() + 1) {
      var vectoren = invoer.stream()
          .map(ClusteringInvoer::embedding).toList();
      var gereduceerd = dimensieReductiePoort.reduceer(vectoren);
      invoer = IntStream.range(0, invoer.size())
          .mapToObj(i -> new ClusteringInvoer(
              invoer.get(i).bezwaarId(), gereduceerd.get(i)))
          .toList();
    }
```

4. Na clustering, herbereken centroiden in originele 1024D-ruimte. In de cluster-verwerkingsloop, vervang de centroid:

```java
    for (var cluster : clusterResultaat.clusters()) {
        var clusterBezwaren = cluster.bezwaarIds().stream()
            .map(bezwaarById::get)
            .toList();
        // Herbereken centroid in originele embedding-ruimte
        var origineleCentroid = berekenOrigineleCentroid(clusterBezwaren);
        var representatief = vindDichtstBijCentroid(clusterBezwaren, origineleCentroid);
```

Voeg private methode toe:

```java
  private float[] berekenOrigineleCentroid(
      List<GeextraheerdBezwaarEntiteit> bezwaren) {
    int dims = bezwaren.get(0).getEmbedding().length;
    var centroid = new float[dims];
    for (var bezwaar : bezwaren) {
      var emb = bezwaar.getEmbedding();
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

**Step 5: Run test, verifieer dat het slaagt**

Run: `mvn test -pl app -Dtest=KernbezwaarServiceTest`
Expected: BUILD SUCCESS

**Step 6: Commit**

```
feat: integreer UMAP-dimensiereductie in KernbezwaarService
```

---

### Task 6: ClusteringConfigController uitbreiden

**Files:**
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/clustering/ClusteringConfigController.java:14-41`

**Step 1: Schrijf de test**

Create: `app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/clustering/ClusteringConfigControllerTest.java`

```java
package be.vlaanderen.omgeving.bezwaarschriften.clustering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import be.vlaanderen.omgeving.bezwaarschriften.clustering.ClusteringConfigController.ConfigDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class ClusteringConfigControllerTest {

  @Spy
  private ClusteringConfig config;

  @InjectMocks
  private ClusteringConfigController controller;

  @Test
  void geefBevtUmapVelden() {
    var response = controller.geef();

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    var dto = response.getBody();
    assertThat(dto.umapEnabled()).isTrue();
    assertThat(dto.umapNComponents()).isEqualTo(5);
    assertThat(dto.umapNNeighbors()).isEqualTo(15);
    assertThat(dto.umapMinDist()).isEqualTo(0.1f);
  }

  @Test
  void updatePastUmapVeldenAan() {
    var dto = new ConfigDto(3, 2, 0.5, false, 10, 30, 0.5f);

    var response = controller.update(dto);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    verify(config).setUmapEnabled(false);
    verify(config).setUmapNComponents(10);
    verify(config).setUmapNNeighbors(30);
    verify(config).setUmapMinDist(0.5f);
  }
}
```

**Step 2: Run test, verifieer dat het faalt**

Run: `mvn test -pl app -Dtest=ClusteringConfigControllerTest`
Expected: FAIL — `ConfigDto` heeft geen UMAP-velden

**Step 3: Pas ClusteringConfigController aan**

```java
  @GetMapping
  public ResponseEntity<ConfigDto> geef() {
    return ResponseEntity.ok(new ConfigDto(
        config.getMinClusterSize(),
        config.getMinSamples(),
        config.getClusterSelectionEpsilon(),
        config.isUmapEnabled(),
        config.getUmapNComponents(),
        config.getUmapNNeighbors(),
        config.getUmapMinDist()));
  }

  @PutMapping
  public ResponseEntity<ConfigDto> update(@RequestBody ConfigDto dto) {
    config.setMinClusterSize(dto.minClusterSize());
    config.setMinSamples(dto.minSamples());
    config.setClusterSelectionEpsilon(dto.clusterSelectionEpsilon());
    config.setUmapEnabled(dto.umapEnabled());
    config.setUmapNComponents(dto.umapNComponents());
    config.setUmapNNeighbors(dto.umapNNeighbors());
    config.setUmapMinDist(dto.umapMinDist());
    return ResponseEntity.ok(dto);
  }

  public record ConfigDto(int minClusterSize, int minSamples,
      double clusterSelectionEpsilon,
      boolean umapEnabled, int umapNComponents,
      int umapNNeighbors, float umapMinDist) {}
```

**Step 4: Run test, verifieer dat het slaagt**

Run: `mvn test -pl app -Dtest=ClusteringConfigControllerTest`
Expected: BUILD SUCCESS

**Step 5: Commit**

```
feat: voeg UMAP-parameters toe aan ClusteringConfigController
```

---

### Task 7: Frontend — UMAP-parameters in config-UI

**Files:**
- Modify: `webapp/src/js/bezwaarschriften-kernbezwaren.js:806-853` (`_renderClusteringParams` methode)

**Step 1: Pas _renderClusteringParams aan**

Vervang de methode `_renderClusteringParams` (regels 806-853):

```javascript
  _renderClusteringParams(inhoud) {
    const balk = document.createElement('div');
    balk.className = 'clustering-params';

    const titel = document.createElement('span');
    titel.className = 'clustering-params-titel';
    titel.textContent = 'Clustering parameters:';
    balk.appendChild(titel);

    // Laad huidige waarden
    fetch('/api/v1/clustering-config')
        .then((r) => r.json())
        .then((config) => {
          // UMAP toggle
          const toggleWrapper = document.createElement('label');
          toggleWrapper.textContent = 'UMAP: ';
          const toggle = document.createElement('input');
          toggle.type = 'checkbox';
          toggle.checked = config.umapEnabled;
          toggleWrapper.appendChild(toggle);
          balk.appendChild(toggleWrapper);

          // UMAP parameters (zichtbaar als UMAP aan)
          const umapParams = [
            {key: 'umapNComponents', label: 'Dimensies', min: 2, step: 1, decimals: 0},
            {key: 'umapNNeighbors', label: 'Buren', min: 2, step: 1, decimals: 0},
            {key: 'umapMinDist', label: 'Min. afstand', min: 0, step: 0.05, decimals: 2},
          ];

          const umapContainer = document.createElement('span');
          umapContainer.className = 'umap-params';
          umapContainer.style.display = config.umapEnabled ? '' : 'none';

          umapParams.forEach(({key, label, min, step, decimals}) => {
            const wrapper = document.createElement('label');
            wrapper.textContent = label + ': ';
            const input = document.createElement('input');
            input.type = 'number';
            input.min = min;
            input.step = step;
            input.value = decimals === 0 ? config[key] : Number(config[key]).toFixed(decimals);
            input.addEventListener('change', () => {
              this._updateClusteringConfig(key,
                  decimals === 0 ? parseInt(input.value, 10) : parseFloat(input.value));
            });
            wrapper.appendChild(input);
            umapContainer.appendChild(wrapper);
          });

          balk.appendChild(umapContainer);

          toggle.addEventListener('change', () => {
            umapContainer.style.display = toggle.checked ? '' : 'none';
            this._updateClusteringConfig('umapEnabled', toggle.checked);
          });

          // HDBSCAN parameters
          const hdbscanParams = [
            {key: 'minClusterSize', label: 'Min. clustergrootte', min: 2, step: 1, decimals: 0},
            {key: 'minSamples', label: 'Min. samples', min: 1, step: 1, decimals: 0},
            {key: 'clusterSelectionEpsilon', label: 'Epsilon', min: 0, step: 0.05, decimals: 2},
          ];

          hdbscanParams.forEach(({key, label, min, step, decimals}) => {
            const wrapper = document.createElement('label');
            wrapper.textContent = label + ': ';
            const input = document.createElement('input');
            input.type = 'number';
            input.min = min;
            input.step = step;
            input.value = decimals === 0 ? config[key] : Number(config[key]).toFixed(decimals);
            input.addEventListener('change', () => {
              this._updateClusteringConfig(key,
                  decimals === 0 ? parseInt(input.value, 10) : parseFloat(input.value));
            });
            wrapper.appendChild(input);
            balk.appendChild(wrapper);
          });
        })
        .catch(() => {/* stil falen als config niet beschikbaar is */});

    inhoud.appendChild(balk);
  }

  _updateClusteringConfig(key, waarde) {
    fetch('/api/v1/clustering-config')
        .then((r) => r.json())
        .then((huidig) => {
          const nieuw = {...huidig, [key]: waarde};
          return fetch('/api/v1/clustering-config', {
            method: 'PUT',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify(nieuw),
          });
        });
  }
```

**Step 2: Build frontend**

Run: `cd webapp && npm run build`
Expected: BUILD SUCCESS (geen lint-errors)

**Step 3: Kopieer naar Spring Boot resources**

Run: `mvn process-resources -pl webapp -Denforcer.skip=true`
Expected: BUILD SUCCESS

**Step 4: Commit**

```
feat: voeg UMAP-parameters toe aan clustering config UI
```

---

### Task 8: Frontend Cypress test

**Files:**
- Create: `webapp/cypress/component/bezwaarschriften-kernbezwaren-clustering-params.cy.js` (of uitbreiden als er al een test is)

**Step 1: Schrijf Cypress component test**

Controleer eerst of er al Cypress-tests bestaan voor de clustering-params. Zo ja, breid die uit. Zo niet, maak een nieuwe test:

```javascript
describe('Clustering parameters UI', () => {
  beforeEach(() => {
    cy.intercept('GET', '/api/v1/clustering-config', {
      statusCode: 200,
      body: {
        minClusterSize: 5,
        minSamples: 3,
        clusterSelectionEpsilon: 0.2,
        umapEnabled: true,
        umapNComponents: 5,
        umapNNeighbors: 15,
        umapMinDist: 0.1,
      },
    }).as('getConfig');
  });

  it('toont UMAP toggle en parameters', () => {
    // Verify UMAP checkbox is checked en parameters zichtbaar
  });

  it('verbergt UMAP parameters als toggle uit', () => {
    // Uncheck toggle, verify parameters hidden
  });

  it('stuurt update bij wijzigen UMAP parameter', () => {
    cy.intercept('PUT', '/api/v1/clustering-config').as('updateConfig');
    // Wijzig umapNComponents, verify PUT request bevat nieuwe waarde
  });
});
```

**Step 2: Run Cypress test**

Run: `cd webapp && npm test`
Expected: PASS

**Step 3: Commit**

```
test: voeg Cypress test toe voor UMAP clustering parameters
```

---

### Task 9: Volledige build en integratietest

**Step 1: Run alle backend tests**

Run: `mvn test -pl app`
Expected: BUILD SUCCESS — alle tests slagen

**Step 2: Run integratietests (als Docker draait)**

Run: `mvn verify -pl app`
Expected: BUILD SUCCESS

**Step 3: Run frontend build + tests**

Run: `cd webapp && npm run build && npm test`
Expected: BUILD SUCCESS

**Step 4: Handmatige verificatie**

Start de applicatie:
```bash
docker compose up -d
mvn spring-boot:run -pl app -Pdev
```

1. Open de UI, ga naar kernbezwaren
2. Verifieer dat "Clustering parameters:" zichtbaar is met UMAP toggle
3. Toggle UMAP uit → UMAP-velden verdwijnen
4. Toggle UMAP aan → UMAP-velden verschijnen
5. Wijzig een UMAP parameter → verifieer via GET dat de waarde is opgeslagen
6. Voer een clustering uit → verifieer dat resultaten semantisch beter gegroepeerd zijn

**Step 5: Final commit (als er kleine fixes nodig waren)**

```
fix: correcties na handmatige verificatie
```

---

## Test- en verificatieplan

| Wat | Hoe | Verwacht |
|-----|-----|---------|
| UMAP reduceert dimensies | Unit test `UmapDimensieReductieAdapterTest` | 1024D → nComponents |
| Te weinig data → fallback | Unit test: < nNeighbors+1 punten | Originele vectoren retour |
| Config defaults | Unit test `ClusteringConfigTest` | enabled=true, nComponents=5, etc. |
| Config REST API | Unit test `ClusteringConfigControllerTest` | GET/PUT met UMAP-velden |
| UMAP aan/uit in service | Unit test `KernbezwaarServiceTest` | reduceer() wel/niet aangeroepen |
| Centroid herberekening | Unit test: centroid in originele ruimte | 1024D centroid, niet 5D |
| Frontend toggle | Cypress test | UMAP-params tonen/verbergen |
| Frontend config update | Cypress test | PUT request met juiste body |
| End-to-end | Handmatig | Betere clustering-resultaten |
