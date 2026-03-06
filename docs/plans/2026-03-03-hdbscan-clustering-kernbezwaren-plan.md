# HDBSCAN Clustering Kernbezwaren — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Replace the mock kernbezwaar grouping with a real HDBSCAN-based clustering pipeline that vectorizes objections, clusters them per category, and creates core objections from cluster centroids.

**Architecture:** Hexagonal — two new ports (`EmbeddingPoort`, `ClusteringPoort`) with adapters (`WebClientEmbeddingAdapter`, `TribuoClusteringAdapter`). The existing `KernbezwaarPoort` and `MockKernbezwaarAdapter` are removed. `KernbezwaarService.groepeer()` is rewritten to orchestrate the new pipeline. Embeddings are persisted in PostgreSQL via pgvector.

**Tech Stack:** Java 21, Spring Boot 2.7 (Hibernate 5 / javax.persistence), Tribuo 4.3.2 (HDBSCAN), pgvector-java 0.1.6, PostgreSQL with pgvector extension, WebClient for embedding API calls.

**Important constraints:**
- Project uses `javax.persistence` (Hibernate 5), NOT `jakarta.persistence`. Do NOT use `hibernate-vector` module (requires Hibernate 6.4+).
- Google Checkstyle is enforced — all code must pass `mvn checkstyle:check`.
- Tests use Mockito for unit tests and Testcontainers (PostgreSQL) for integration tests.

---

### Task 1: Add Maven dependencies

**Files:**
- Modify: `app/pom.xml`

**Step 1: Add Tribuo HDBSCAN and pgvector-java dependencies**

Add these to `app/pom.xml` in the `<dependencies>` section, before the `<!-- Test -->` comment:

```xml
<!-- Clustering -->
<dependency>
    <groupId>org.tribuo</groupId>
    <artifactId>tribuo-clustering-hdbscan</artifactId>
    <version>4.3.2</version>
</dependency>

<!-- pgvector -->
<dependency>
    <groupId>com.pgvector</groupId>
    <artifactId>pgvector</artifactId>
    <version>0.1.6</version>
</dependency>
```

**Step 2: Verify it compiles**

Run: `cd /Users/kenzo/Library/CloudStorage/Dropbox/1-Kenzo/4-Coding/bezwaarschriften && mvn compile -pl app -q`
Expected: BUILD SUCCESS

**Step 3: Commit**

```bash
git add app/pom.xml
git commit -m "build: add Tribuo HDBSCAN and pgvector-java dependencies"
```

---

### Task 2: Liquibase migration — embedding column on geextraheerd_bezwaar

**Files:**
- Create: `app/src/main/resources/config/liquibase/changelog/20260303-bezwaar-embedding.xml`
- Modify: `app/src/main/resources/config/liquibase/master.xml`

**Step 1: Create migration file**

```xml
<?xml version="1.0" encoding="utf-8"?>
<databaseChangeLog
    xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-3.1.xsd">

  <changeSet id="20260303-embedding" author="kenzo">
    <preConditions onFail="MARK_RAN">
      <sqlCheck expectedResult="1">
        SELECT count(*) FROM pg_extension WHERE extname = 'vector'
      </sqlCheck>
    </preConditions>

    <sql>
      ALTER TABLE geextraheerd_bezwaar ADD COLUMN embedding vector(1024);
    </sql>
  </changeSet>

</databaseChangeLog>
```

**Step 2: Add include to master.xml**

Add this line at the end of master.xml, before `</databaseChangeLog>`:

```xml
  <include file="config/liquibase/changelog/20260303-bezwaar-embedding.xml"/>
```

**Step 3: Commit**

```bash
git add app/src/main/resources/config/liquibase/
git commit -m "db: add embedding vector column to geextraheerd_bezwaar"
```

---

### Task 3: Hibernate UserType for pgvector

This is needed because the project uses Hibernate 5 (`javax.persistence`) which has no built-in vector support. We create a custom `UserType` that maps `float[]` in Java to PostgreSQL's `vector` type via pgvector-java's `PGvector` class.

**Files:**
- Create: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/config/VectorType.java`
- Test: `app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/config/VectorTypeTest.java`

**Step 1: Write the unit test**

```java
package be.vlaanderen.omgeving.bezwaarschriften.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class VectorTypeTest {

  private final VectorType vectorType = new VectorType();

  @Test
  void returnedClassIsFloatArray() {
    assertThat(vectorType.returnedClass()).isEqualTo(float[].class);
  }

  @Test
  void deepCopyCreatesIndependentArray() {
    var original = new float[]{1.0f, 2.0f, 3.0f};
    var copy = (float[]) vectorType.deepCopy(original);
    assertThat(copy).isEqualTo(original);
    copy[0] = 99.0f;
    assertThat(original[0]).isEqualTo(1.0f);
  }

  @Test
  void deepCopyOfNullReturnsNull() {
    assertThat(vectorType.deepCopy(null)).isNull();
  }

  @Test
  void equalsComparesArrayContents() {
    var a = new float[]{1.0f, 2.0f};
    var b = new float[]{1.0f, 2.0f};
    var c = new float[]{3.0f, 4.0f};
    assertThat(vectorType.equals(a, b)).isTrue();
    assertThat(vectorType.equals(a, c)).isFalse();
    assertThat(vectorType.equals(null, null)).isTrue();
    assertThat(vectorType.equals(a, null)).isFalse();
  }
}
```

**Step 2: Run test to verify it fails**

Run: `cd /Users/kenzo/Library/CloudStorage/Dropbox/1-Kenzo/4-Coding/bezwaarschriften && mvn test -pl app -Dtest=VectorTypeTest -q`
Expected: FAIL — class not found

**Step 3: Implement VectorType**

```java
package be.vlaanderen.omgeving.bezwaarschriften.config;

import com.pgvector.PGvector;
import java.io.Serializable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Arrays;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.usertype.UserType;

/**
 * Hibernate 5 UserType dat float[] mapt op PostgreSQL vector (pgvector).
 */
public class VectorType implements UserType {

  @Override
  public int[] sqlTypes() {
    return new int[]{Types.OTHER};
  }

  @Override
  public Class<?> returnedClass() {
    return float[].class;
  }

  @Override
  public boolean equals(Object x, Object y) {
    if (x == y) {
      return true;
    }
    if (x == null || y == null) {
      return false;
    }
    return Arrays.equals((float[]) x, (float[]) y);
  }

  @Override
  public int hashCode(Object x) {
    return Arrays.hashCode((float[]) x);
  }

  @Override
  public Object nullSafeGet(ResultSet rs, String[] names,
      SharedSessionContractImplementor session, Object owner)
      throws SQLException {
    var value = rs.getString(names[0]);
    if (value == null) {
      return null;
    }
    var pgVector = new PGvector(value);
    return pgVector.toArray();
  }

  @Override
  public void nullSafeSet(PreparedStatement st, Object value, int index,
      SharedSessionContractImplementor session)
      throws SQLException {
    if (value == null) {
      st.setNull(index, Types.OTHER);
    } else {
      var pgVector = new PGvector((float[]) value);
      st.setObject(index, pgVector);
    }
  }

  @Override
  public Object deepCopy(Object value) {
    if (value == null) {
      return null;
    }
    return ((float[]) value).clone();
  }

  @Override
  public boolean isMutable() {
    return true;
  }

  @Override
  public Serializable disassemble(Object value) {
    return (float[]) deepCopy(value);
  }

  @Override
  public Object assemble(Serializable cached, Object owner) {
    return deepCopy(cached);
  }

  @Override
  public Object replace(Object original, Object target, Object owner) {
    return deepCopy(original);
  }
}
```

**Step 4: Run test to verify it passes**

Run: `cd /Users/kenzo/Library/CloudStorage/Dropbox/1-Kenzo/4-Coding/bezwaarschriften && mvn test -pl app -Dtest=VectorTypeTest -q`
Expected: PASS

**Step 5: Commit**

```bash
git add app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/config/VectorType.java
git add app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/config/VectorTypeTest.java
git commit -m "feat: add Hibernate 5 UserType for pgvector float[] mapping"
```

---

### Task 4: Add embedding field to GeextraheerdBezwaarEntiteit

**Files:**
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/GeextraheerdBezwaarEntiteit.java`

**Step 1: Add embedding field**

Add these imports at the top of the file:

```java
import org.hibernate.annotations.Type;
```

Add this field and getter/setter after the `manueel` field (before the `getId()` method):

```java
  @Type(type = "be.vlaanderen.omgeving.bezwaarschriften.config.VectorType")
  @Column(name = "embedding", columnDefinition = "vector(1024)")
  private float[] embedding;
```

And the getter/setter at the end (before the closing `}`):

```java
  public float[] getEmbedding() {
    return embedding;
  }

  public void setEmbedding(float[] embedding) {
    this.embedding = embedding;
  }
```

**Step 2: Verify it compiles**

Run: `cd /Users/kenzo/Library/CloudStorage/Dropbox/1-Kenzo/4-Coding/bezwaarschriften && mvn compile -pl app -q`
Expected: BUILD SUCCESS

**Step 3: Commit**

```bash
git add app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/GeextraheerdBezwaarEntiteit.java
git commit -m "feat: add embedding vector field to GeextraheerdBezwaarEntiteit"
```

---

### Task 5: Create EmbeddingPoort and WebClientEmbeddingAdapter

**Files:**
- Create: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/clustering/EmbeddingPoort.java`
- Create: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/clustering/EmbeddingConfig.java`
- Create: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/clustering/WebClientEmbeddingAdapter.java`
- Test: `app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/clustering/WebClientEmbeddingAdapterTest.java`

**Step 1: Create the port interface**

```java
package be.vlaanderen.omgeving.bezwaarschriften.clustering;

import java.util.List;

/**
 * Port voor het genereren van vector-embeddings uit tekst.
 */
public interface EmbeddingPoort {

  /**
   * Genereert embeddings voor een lijst teksten.
   *
   * @param teksten de teksten om te vectoriseren
   * @return lijst van embedding-vectoren in dezelfde volgorde als de invoer
   */
  List<float[]> genereerEmbeddings(List<String> teksten);
}
```

**Step 2: Create configuration properties class**

```java
package be.vlaanderen.omgeving.bezwaarschriften.clustering;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuratie voor de embedding-provider.
 */
@Component
@ConfigurationProperties(prefix = "bezwaarschriften.embedding")
public class EmbeddingConfig {

  private String provider = "ollama";
  private String model = "bge-m3";
  private int dimensions = 1024;
  private String ollamaUrl = "http://localhost:11434";
  private String openaiUrl = "https://api.openai.com/v1";
  private String openaiKey = "";

  public String getProvider() {
    return provider;
  }

  public void setProvider(String provider) {
    this.provider = provider;
  }

  public String getModel() {
    return model;
  }

  public void setModel(String model) {
    this.model = model;
  }

  public int getDimensions() {
    return dimensions;
  }

  public void setDimensions(int dimensions) {
    this.dimensions = dimensions;
  }

  public String getOllamaUrl() {
    return ollamaUrl;
  }

  public void setOllamaUrl(String ollamaUrl) {
    this.ollamaUrl = ollamaUrl;
  }

  public String getOpenaiUrl() {
    return openaiUrl;
  }

  public void setOpenaiUrl(String openaiUrl) {
    this.openaiUrl = openaiUrl;
  }

  public String getOpenaiKey() {
    return openaiKey;
  }

  public void setOpenaiKey(String openaiKey) {
    this.openaiKey = openaiKey;
  }
}
```

**Step 3: Write the adapter test**

```java
package be.vlaanderen.omgeving.bezwaarschriften.clustering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

class WebClientEmbeddingAdapterTest {

  private MockWebServer mockServer;
  private WebClientEmbeddingAdapter adapter;

  @BeforeEach
  void setUp() throws IOException {
    mockServer = new MockWebServer();
    mockServer.start();

    var config = new EmbeddingConfig();
    config.setProvider("ollama");
    config.setModel("bge-m3");
    config.setDimensions(3);
    config.setOllamaUrl(mockServer.url("/").toString());

    adapter = new WebClientEmbeddingAdapter(
        WebClient.builder().build(), config);
  }

  @AfterEach
  void tearDown() throws IOException {
    mockServer.shutdown();
  }

  @Test
  void genereertEmbeddingsViaOllama() {
    mockServer.enqueue(new MockResponse()
        .setBody("{\"embedding\":[0.1,0.2,0.3]}")
        .addHeader("Content-Type", "application/json"));
    mockServer.enqueue(new MockResponse()
        .setBody("{\"embedding\":[0.4,0.5,0.6]}")
        .addHeader("Content-Type", "application/json"));

    var resultaat = adapter.genereerEmbeddings(
        List.of("tekst een", "tekst twee"));

    assertThat(resultaat).hasSize(2);
    assertThat(resultaat.get(0)).containsExactly(0.1f, 0.2f, 0.3f);
    assertThat(resultaat.get(1)).containsExactly(0.4f, 0.5f, 0.6f);
  }

  @Test
  void legeInvoerGeeftLeegResultaat() {
    var resultaat = adapter.genereerEmbeddings(List.of());
    assertThat(resultaat).isEmpty();
  }
}
```

Note: this test uses `okhttp3.mockwebserver.MockWebServer`. Add this test dependency to `app/pom.xml` if not already present:

```xml
<dependency>
    <groupId>com.squareup.okhttp3</groupId>
    <artifactId>mockwebserver</artifactId>
    <scope>test</scope>
</dependency>
```

Spring Boot 2.7 manages `okhttp3` via WebFlux, so the version is managed. Check if `mockwebserver` is already transitively available. If not, add it explicitly.

**Step 4: Run test to verify it fails**

Run: `cd /Users/kenzo/Library/CloudStorage/Dropbox/1-Kenzo/4-Coding/bezwaarschriften && mvn test -pl app -Dtest=WebClientEmbeddingAdapterTest -q`
Expected: FAIL — class not found

**Step 5: Implement WebClientEmbeddingAdapter**

```java
package be.vlaanderen.omgeving.bezwaarschriften.clustering;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Embedding-adapter die via WebClient een Ollama of OpenAI embedding API aanroept.
 */
@Component
public class WebClientEmbeddingAdapter implements EmbeddingPoort {

  private final WebClient webClient;
  private final EmbeddingConfig config;

  public WebClientEmbeddingAdapter(WebClient.Builder webClientBuilder,
      EmbeddingConfig config) {
    this.webClient = webClientBuilder.build();
    this.config = config;
  }

  @Override
  public List<float[]> genereerEmbeddings(List<String> teksten) {
    if (teksten.isEmpty()) {
      return List.of();
    }
    var resultaat = new ArrayList<float[]>();
    for (var tekst : teksten) {
      resultaat.add(genereerEmbedding(tekst));
    }
    return resultaat;
  }

  private float[] genereerEmbedding(String tekst) {
    if ("ollama".equals(config.getProvider())) {
      return ollamaEmbedding(tekst);
    }
    return openaiEmbedding(tekst);
  }

  @SuppressWarnings("unchecked")
  private float[] ollamaEmbedding(String tekst) {
    var body = Map.of("model", config.getModel(), "prompt", tekst);
    var response = webClient.post()
        .uri(config.getOllamaUrl() + "/api/embeddings")
        .bodyValue(body)
        .retrieve()
        .bodyToMono(Map.class)
        .block();
    var embedding = (List<Number>) response.get("embedding");
    return toFloatArray(embedding);
  }

  @SuppressWarnings("unchecked")
  private float[] openaiEmbedding(String tekst) {
    var body = Map.of(
        "model", config.getModel(),
        "input", tekst);
    var response = webClient.post()
        .uri(config.getOpenaiUrl() + "/embeddings")
        .header("Authorization", "Bearer " + config.getOpenaiKey())
        .bodyValue(body)
        .retrieve()
        .bodyToMono(Map.class)
        .block();
    var data = (List<Map<String, Object>>) response.get("data");
    var embedding = (List<Number>) data.get(0).get("embedding");
    return toFloatArray(embedding);
  }

  private float[] toFloatArray(List<Number> numbers) {
    var result = new float[numbers.size()];
    for (int i = 0; i < numbers.size(); i++) {
      result[i] = numbers.get(i).floatValue();
    }
    return result;
  }
}
```

**Step 6: Run test to verify it passes**

Run: `cd /Users/kenzo/Library/CloudStorage/Dropbox/1-Kenzo/4-Coding/bezwaarschriften && mvn test -pl app -Dtest=WebClientEmbeddingAdapterTest -q`
Expected: PASS

**Step 7: Commit**

```bash
git add app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/clustering/
git add app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/clustering/
git commit -m "feat: add EmbeddingPoort with WebClient adapter for Ollama/OpenAI"
```

---

### Task 6: Create ClusteringPoort and TribuoClusteringAdapter

**Files:**
- Create: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/clustering/ClusteringPoort.java`
- Create: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/clustering/ClusteringConfig.java`
- Create: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/clustering/TribuoClusteringAdapter.java`
- Test: `app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/clustering/TribuoClusteringAdapterTest.java`

**Step 1: Create the port interface with value types**

```java
package be.vlaanderen.omgeving.bezwaarschriften.clustering;

import java.util.List;

/**
 * Port voor het clusteren van vectoren.
 *
 * <p>Abstraheert het clustering-algoritme zodat de implementatie
 * vervangbaar is (bv. Tribuo HDBSCAN, Python sidecar, ander algoritme).
 */
public interface ClusteringPoort {

  /**
   * Clustert de gegeven vectoren.
   *
   * @param invoer lijst van bezwaar-IDs met hun embedding-vector
   * @return resultaat met clusters en noise-IDs
   */
  ClusteringResultaat cluster(List<ClusteringInvoer> invoer);

  /** Invoer: een bezwaar-ID gekoppeld aan zijn embedding-vector. */
  record ClusteringInvoer(Long bezwaarId, float[] embedding) {}

  /** Een cluster met label, leden en zwaartepunt. */
  record Cluster(int label, List<Long> bezwaarIds, float[] centroid) {}

  /** Resultaat: clusters + noise-items. */
  record ClusteringResultaat(List<Cluster> clusters, List<Long> noiseIds) {}
}
```

**Step 2: Create configuration properties class**

```java
package be.vlaanderen.omgeving.bezwaarschriften.clustering;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuratie voor het clustering-algoritme.
 */
@Component
@ConfigurationProperties(prefix = "bezwaarschriften.clustering")
public class ClusteringConfig {

  private int minClusterSize = 5;
  private int minSamples = 3;

  public int getMinClusterSize() {
    return minClusterSize;
  }

  public void setMinClusterSize(int minClusterSize) {
    this.minClusterSize = minClusterSize;
  }

  public int getMinSamples() {
    return minSamples;
  }

  public void setMinSamples(int minSamples) {
    this.minSamples = minSamples;
  }
}
```

**Step 3: Write the adapter test**

```java
package be.vlaanderen.omgeving.bezwaarschriften.clustering;

import static org.assertj.core.api.Assertions.assertThat;

import be.vlaanderen.omgeving.bezwaarschriften.clustering.ClusteringPoort.ClusteringInvoer;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TribuoClusteringAdapterTest {

  private TribuoClusteringAdapter adapter;

  @BeforeEach
  void setUp() {
    var config = new ClusteringConfig();
    config.setMinClusterSize(2);
    config.setMinSamples(2);
    adapter = new TribuoClusteringAdapter(config);
  }

  @Test
  void clustertVectorenDichtBijElkaar() {
    // Twee groepen van vectoren: groep A rond (1,0,0), groep B rond (0,1,0)
    var invoer = List.of(
        new ClusteringInvoer(1L, new float[]{1.0f, 0.0f, 0.0f}),
        new ClusteringInvoer(2L, new float[]{0.99f, 0.01f, 0.0f}),
        new ClusteringInvoer(3L, new float[]{0.98f, 0.02f, 0.0f}),
        new ClusteringInvoer(4L, new float[]{0.0f, 1.0f, 0.0f}),
        new ClusteringInvoer(5L, new float[]{0.01f, 0.99f, 0.0f}),
        new ClusteringInvoer(6L, new float[]{0.02f, 0.98f, 0.0f})
    );

    var resultaat = adapter.cluster(invoer);

    // Verwacht minimaal 1 cluster (HDBSCAN kan meer of minder vinden
    // afhankelijk van parameters, maar met minClusterSize=2 en duidelijk
    // gescheiden groepen zouden het er 2 moeten zijn)
    assertThat(resultaat.clusters().size() + resultaat.noiseIds().size())
        .isGreaterThan(0);
    // Alle 6 bezwaren moeten ergens terecht komen
    var alleIds = new ArrayList<Long>();
    resultaat.clusters().forEach(c -> alleIds.addAll(c.bezwaarIds()));
    alleIds.addAll(resultaat.noiseIds());
    assertThat(alleIds).containsExactlyInAnyOrder(1L, 2L, 3L, 4L, 5L, 6L);
  }

  @Test
  void teWeinigItemsGeeftAllesAlsNoise() {
    var invoer = List.of(
        new ClusteringInvoer(1L, new float[]{1.0f, 0.0f, 0.0f})
    );

    var resultaat = adapter.cluster(invoer);

    assertThat(resultaat.clusters()).isEmpty();
    assertThat(resultaat.noiseIds()).containsExactly(1L);
  }

  @Test
  void legeInvoerGeeftLeegResultaat() {
    var resultaat = adapter.cluster(List.of());

    assertThat(resultaat.clusters()).isEmpty();
    assertThat(resultaat.noiseIds()).isEmpty();
  }

  @Test
  void clusterHeeftCentroid() {
    var invoer = List.of(
        new ClusteringInvoer(1L, new float[]{1.0f, 0.0f}),
        new ClusteringInvoer(2L, new float[]{1.0f, 0.0f}),
        new ClusteringInvoer(3L, new float[]{1.0f, 0.0f}),
        new ClusteringInvoer(4L, new float[]{1.0f, 0.0f}),
        new ClusteringInvoer(5L, new float[]{1.0f, 0.0f})
    );

    var resultaat = adapter.cluster(invoer);

    if (!resultaat.clusters().isEmpty()) {
      var cluster = resultaat.clusters().get(0);
      assertThat(cluster.centroid()).isNotNull();
      assertThat(cluster.centroid().length).isEqualTo(2);
    }
  }
}
```

**Step 4: Run test to verify it fails**

Run: `cd /Users/kenzo/Library/CloudStorage/Dropbox/1-Kenzo/4-Coding/bezwaarschriften && mvn test -pl app -Dtest=TribuoClusteringAdapterTest -q`
Expected: FAIL — class not found

**Step 5: Implement TribuoClusteringAdapter**

```java
package be.vlaanderen.omgeving.bezwaarschriften.clustering;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.tribuo.MutableDataset;
import org.tribuo.clustering.ClusterID;
import org.tribuo.clustering.ClusteringFactory;
import org.tribuo.clustering.hdbscan.HdbscanTrainer;
import org.tribuo.data.columnar.RowProcessor;
import org.tribuo.data.columnar.processors.field.DoubleFieldProcessor;
import org.tribuo.data.columnar.processors.response.EmptyResponseProcessor;
import org.tribuo.data.csv.CSVDataSource;
import org.tribuo.impl.ArrayExample;
import org.tribuo.provenance.SimpleDataSourceProvenance;

/**
 * Clustering-adapter die HDBSCAN toepast via Tribuo.
 */
@Component
public class TribuoClusteringAdapter implements ClusteringPoort {

  private final ClusteringConfig config;

  public TribuoClusteringAdapter(ClusteringConfig config) {
    this.config = config;
  }

  @Override
  public ClusteringResultaat cluster(List<ClusteringInvoer> invoer) {
    if (invoer.isEmpty()) {
      return new ClusteringResultaat(List.of(), List.of());
    }
    if (invoer.size() < config.getMinClusterSize()) {
      return new ClusteringResultaat(List.of(),
          invoer.stream().map(ClusteringInvoer::bezwaarId).toList());
    }

    var dataset = bouwDataset(invoer);
    var trainer = new HdbscanTrainer(config.getMinClusterSize());
    var model = trainer.train(dataset);

    var labels = model.getClusterLabels();
    return verwerkLabels(invoer, labels);
  }

  private MutableDataset<ClusterID> bouwDataset(List<ClusteringInvoer> invoer) {
    var factory = new ClusteringFactory();
    var provenance = new SimpleDataSourceProvenance(
        "bezwaar-embeddings", factory);
    var dataset = new MutableDataset<>(provenance, factory);

    for (var item : invoer) {
      int dims = item.embedding().length;
      var featureNames = new String[dims];
      var featureValues = new double[dims];
      for (int i = 0; i < dims; i++) {
        featureNames[i] = "d" + i;
        featureValues[i] = item.embedding()[i];
      }
      dataset.add(new ArrayExample<>(
          new ClusterID(ClusterID.UNASSIGNED), featureNames, featureValues));
    }
    return dataset;
  }

  private ClusteringResultaat verwerkLabels(List<ClusteringInvoer> invoer,
      List<Integer> labels) {
    Map<Integer, List<Long>> clusterMap = new HashMap<>();
    Map<Integer, List<float[]>> clusterVectors = new HashMap<>();
    var noiseIds = new ArrayList<Long>();

    for (int i = 0; i < invoer.size(); i++) {
      var bezwaarId = invoer.get(i).bezwaarId();
      var label = labels.get(i);

      if (label == 0) {
        // Label 0 = outlier/noise in Tribuo HDBSCAN
        noiseIds.add(bezwaarId);
      } else {
        clusterMap.computeIfAbsent(label, k -> new ArrayList<>()).add(bezwaarId);
        clusterVectors.computeIfAbsent(label, k -> new ArrayList<>())
            .add(invoer.get(i).embedding());
      }
    }

    var clusters = new ArrayList<Cluster>();
    for (var entry : clusterMap.entrySet()) {
      var centroid = berekenCentroid(clusterVectors.get(entry.getKey()));
      clusters.add(new Cluster(entry.getKey(), entry.getValue(), centroid));
    }

    return new ClusteringResultaat(clusters, noiseIds);
  }

  private float[] berekenCentroid(List<float[]> vectoren) {
    int dims = vectoren.get(0).length;
    var centroid = new float[dims];
    for (var vector : vectoren) {
      for (int i = 0; i < dims; i++) {
        centroid[i] += vector[i];
      }
    }
    for (int i = 0; i < dims; i++) {
      centroid[i] /= vectoren.size();
    }
    return centroid;
  }
}
```

**Step 6: Run test to verify it passes**

Run: `cd /Users/kenzo/Library/CloudStorage/Dropbox/1-Kenzo/4-Coding/bezwaarschriften && mvn test -pl app -Dtest=TribuoClusteringAdapterTest -q`
Expected: PASS (some tests may need parameter tuning — adjust `minClusterSize` in test setup if HDBSCAN doesn't find clusters with the test data)

**Step 7: Commit**

```bash
git add app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/clustering/
git add app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/clustering/
git commit -m "feat: add ClusteringPoort with Tribuo HDBSCAN adapter"
```

---

### Task 7: Add repository query for bezwaren per project

We need a way to find all `GeextraheerdBezwaarEntiteit` records for a project. The chain is: project → extractie_taak (by projectNaam, status KLAAR) → geextraheerd_bezwaar (by taakId).

**Files:**
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/GeextraheerdBezwaarRepository.java`

**Step 1: Add query method**

Add this method to `GeextraheerdBezwaarRepository`:

```java
  @Query("SELECT b FROM GeextraheerdBezwaarEntiteit b "
      + "WHERE b.taakId IN ("
      + "  SELECT t.id FROM ExtractieTaak t "
      + "  WHERE t.projectNaam = :projectNaam AND t.status = 'KLAAR')")
  List<GeextraheerdBezwaarEntiteit> findByProjectNaam(
      @Param("projectNaam") String projectNaam);
```

Add the required imports:

```java
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
```

**Step 2: Verify it compiles**

Run: `cd /Users/kenzo/Library/CloudStorage/Dropbox/1-Kenzo/4-Coding/bezwaarschriften && mvn compile -pl app -q`
Expected: BUILD SUCCESS

**Step 3: Commit**

```bash
git add app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/GeextraheerdBezwaarRepository.java
git commit -m "feat: add findByProjectNaam query to GeextraheerdBezwaarRepository"
```

---

### Task 8: Rewrite KernbezwaarService.groepeer()

This is the main orchestration change. The new `groepeer()`:
1. Gets all `GeextraheerdBezwaarEntiteit` for the project
2. Groups by `categorie`
3. Per category: gets original passage texts, generates embeddings, stores them, clusters, creates kernbezwaren
4. Noise items per category: each becomes its own kernbezwaar

**Files:**
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/KernbezwaarService.java`
- Modify: `app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/KernbezwaarServiceTest.java`

**Step 1: Update the test first**

Replace the `KernbezwaarServiceTest` with:

```java
package be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import be.vlaanderen.omgeving.bezwaarschriften.clustering.ClusteringPoort;
import be.vlaanderen.omgeving.bezwaarschriften.clustering.ClusteringPoort.Cluster;
import be.vlaanderen.omgeving.bezwaarschriften.clustering.ClusteringPoort.ClusteringResultaat;
import be.vlaanderen.omgeving.bezwaarschriften.clustering.EmbeddingPoort;
import be.vlaanderen.omgeving.bezwaarschriften.project.ExtractiePassageEntiteit;
import be.vlaanderen.omgeving.bezwaarschriften.project.ExtractiePassageRepository;
import be.vlaanderen.omgeving.bezwaarschriften.project.ExtractieTaak;
import be.vlaanderen.omgeving.bezwaarschriften.project.ExtractieTaakRepository;
import be.vlaanderen.omgeving.bezwaarschriften.project.GeextraheerdBezwaarEntiteit;
import be.vlaanderen.omgeving.bezwaarschriften.project.GeextraheerdBezwaarRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KernbezwaarServiceTest {

  @Mock private EmbeddingPoort embeddingPoort;
  @Mock private ClusteringPoort clusteringPoort;
  @Mock private GeextraheerdBezwaarRepository bezwaarRepository;
  @Mock private ExtractiePassageRepository passageRepository;
  @Mock private ExtractieTaakRepository taakRepository;
  @Mock private KernbezwaarAntwoordRepository antwoordRepository;
  @Mock private ThemaRepository themaRepository;
  @Mock private KernbezwaarRepository kernbezwaarRepository;
  @Mock private KernbezwaarReferentieRepository referentieRepository;

  private KernbezwaarService service;

  @BeforeEach
  void setUp() {
    service = new KernbezwaarService(embeddingPoort, clusteringPoort,
        bezwaarRepository, passageRepository, taakRepository,
        antwoordRepository, themaRepository,
        kernbezwaarRepository, referentieRepository);
  }

  @Test
  void groepeertBezwarenPerCategorie() {
    // Arrange: 2 bezwaren in categorie "milieu"
    var bezwaar1 = maakBezwaar(1L, 10L, 1, "milieu");
    var bezwaar2 = maakBezwaar(2L, 10L, 2, "milieu");
    when(bezwaarRepository.findByProjectNaam("windmolens"))
        .thenReturn(List.of(bezwaar1, bezwaar2));

    var taak = new ExtractieTaak();
    taak.setId(10L);
    taak.setBestandsnaam("doc1.txt");
    when(taakRepository.findById(10L)).thenReturn(Optional.of(taak));

    var passage1 = maakPassage(10L, 1, "originele tekst bezwaar 1");
    var passage2 = maakPassage(10L, 2, "originele tekst bezwaar 2");
    when(passageRepository.findByTaakId(10L))
        .thenReturn(List.of(passage1, passage2));

    var emb1 = new float[]{1.0f, 0.0f};
    var emb2 = new float[]{0.9f, 0.1f};
    when(embeddingPoort.genereerEmbeddings(anyList()))
        .thenReturn(List.of(emb1, emb2));

    when(clusteringPoort.cluster(anyList()))
        .thenReturn(new ClusteringResultaat(
            List.of(new Cluster(1, List.of(1L, 2L),
                new float[]{0.95f, 0.05f})),
            List.of()));

    when(themaRepository.save(any())).thenAnswer(inv -> {
      var e = (ThemaEntiteit) inv.getArgument(0);
      e.setId(100L);
      return e;
    });
    when(kernbezwaarRepository.save(any())).thenAnswer(inv -> {
      var e = (KernbezwaarEntiteit) inv.getArgument(0);
      e.setId(200L);
      return e;
    });
    when(referentieRepository.save(any())).thenAnswer(inv -> {
      var e = (KernbezwaarReferentieEntiteit) inv.getArgument(0);
      e.setId(300L);
      return e;
    });

    // Act
    var resultaat = service.groepeer("windmolens");

    // Assert
    assertThat(resultaat).hasSize(1);
    assertThat(resultaat.get(0).naam()).isEqualTo("milieu");
    assertThat(resultaat.get(0).kernbezwaren()).hasSize(1);
    verify(themaRepository).deleteByProjectNaam("windmolens");
  }

  @Test
  void noiseItemsWordenEigenKernbezwaar() {
    var bezwaar1 = maakBezwaar(1L, 10L, 1, "milieu");
    when(bezwaarRepository.findByProjectNaam("windmolens"))
        .thenReturn(List.of(bezwaar1));

    var taak = new ExtractieTaak();
    taak.setId(10L);
    taak.setBestandsnaam("doc1.txt");
    when(taakRepository.findById(10L)).thenReturn(Optional.of(taak));

    var passage = maakPassage(10L, 1, "uniek bezwaar tekst");
    when(passageRepository.findByTaakId(10L))
        .thenReturn(List.of(passage));

    when(embeddingPoort.genereerEmbeddings(anyList()))
        .thenReturn(List.of(new float[]{1.0f}));

    // Alles is noise
    when(clusteringPoort.cluster(anyList()))
        .thenReturn(new ClusteringResultaat(List.of(), List.of(1L)));

    when(themaRepository.save(any())).thenAnswer(inv -> {
      var e = (ThemaEntiteit) inv.getArgument(0);
      e.setId(100L);
      return e;
    });
    when(kernbezwaarRepository.save(any())).thenAnswer(inv -> {
      var e = (KernbezwaarEntiteit) inv.getArgument(0);
      e.setId(200L);
      return e;
    });
    when(referentieRepository.save(any())).thenAnswer(inv -> {
      var e = (KernbezwaarReferentieEntiteit) inv.getArgument(0);
      e.setId(300L);
      return e;
    });

    var resultaat = service.groepeer("windmolens");

    // Noise items krijgen thema "milieu" (hun categorie), elk wordt eigen kernbezwaar
    assertThat(resultaat).hasSize(1);
    assertThat(resultaat.get(0).kernbezwaren()).hasSize(1);
    assertThat(resultaat.get(0).kernbezwaren().get(0).samenvatting())
        .isEqualTo("uniek bezwaar tekst");
  }

  @Test
  void laadtKernbezwarenUitDatabase() {
    var themaEntiteit = new ThemaEntiteit();
    themaEntiteit.setId(10L);
    themaEntiteit.setProjectNaam("windmolens");
    themaEntiteit.setNaam("Mobiliteit");
    when(themaRepository.findByProjectNaam("windmolens"))
        .thenReturn(List.of(themaEntiteit));

    var kernEntiteit = new KernbezwaarEntiteit();
    kernEntiteit.setId(20L);
    kernEntiteit.setThemaId(10L);
    kernEntiteit.setSamenvatting("Verkeershinder");
    when(kernbezwaarRepository.findByThemaIdIn(List.of(10L)))
        .thenReturn(List.of(kernEntiteit));

    when(referentieRepository.findByKernbezwaarIdIn(List.of(20L)))
        .thenReturn(List.of());

    when(antwoordRepository.findByKernbezwaarIdIn(List.of(20L)))
        .thenReturn(List.of());

    var resultaat = service.geefKernbezwaren("windmolens");

    assertThat(resultaat).isPresent();
    assertThat(resultaat.get()).hasSize(1);
    assertThat(resultaat.get().get(0).naam()).isEqualTo("Mobiliteit");
  }

  @Test
  void slaatAntwoordOp() {
    when(antwoordRepository.save(any())).thenReturn(new KernbezwaarAntwoordEntiteit());

    service.slaAntwoordOp(42L, "<p>Weerwoord</p>");

    verify(antwoordRepository).save(any());
  }

  private GeextraheerdBezwaarEntiteit maakBezwaar(Long id, Long taakId,
      int passageNr, String categorie) {
    var e = new GeextraheerdBezwaarEntiteit();
    e.setId(id);
    e.setTaakId(taakId);
    e.setPassageNr(passageNr);
    e.setSamenvatting("samenvatting " + id);
    e.setCategorie(categorie);
    return e;
  }

  private ExtractiePassageEntiteit maakPassage(Long taakId, int passageNr,
      String tekst) {
    var e = new ExtractiePassageEntiteit();
    e.setTaakId(taakId);
    e.setPassageNr(passageNr);
    e.setTekst(tekst);
    return e;
  }
}
```

**Step 2: Run test to verify it fails**

Run: `cd /Users/kenzo/Library/CloudStorage/Dropbox/1-Kenzo/4-Coding/bezwaarschriften && mvn test -pl app -Dtest=KernbezwaarServiceTest -q`
Expected: FAIL — constructor signature mismatch

**Step 3: Rewrite KernbezwaarService**

Replace the content of `KernbezwaarService.java` with:

```java
package be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar;

import be.vlaanderen.omgeving.bezwaarschriften.clustering.ClusteringPoort;
import be.vlaanderen.omgeving.bezwaarschriften.clustering.ClusteringPoort.ClusteringInvoer;
import be.vlaanderen.omgeving.bezwaarschriften.clustering.EmbeddingPoort;
import be.vlaanderen.omgeving.bezwaarschriften.project.ExtractiePassageEntiteit;
import be.vlaanderen.omgeving.bezwaarschriften.project.ExtractiePassageRepository;
import be.vlaanderen.omgeving.bezwaarschriften.project.ExtractieTaakRepository;
import be.vlaanderen.omgeving.bezwaarschriften.project.GeextraheerdBezwaarEntiteit;
import be.vlaanderen.omgeving.bezwaarschriften.project.GeextraheerdBezwaarRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestreert de groepering van individuele bezwaren tot thema's en kernbezwaren
 * via HDBSCAN-clustering op vector-embeddings.
 */
@Service
public class KernbezwaarService {

  private final EmbeddingPoort embeddingPoort;
  private final ClusteringPoort clusteringPoort;
  private final GeextraheerdBezwaarRepository bezwaarRepository;
  private final ExtractiePassageRepository passageRepository;
  private final ExtractieTaakRepository taakRepository;
  private final KernbezwaarAntwoordRepository antwoordRepository;
  private final ThemaRepository themaRepository;
  private final KernbezwaarRepository kernbezwaarRepository;
  private final KernbezwaarReferentieRepository referentieRepository;

  public KernbezwaarService(EmbeddingPoort embeddingPoort,
      ClusteringPoort clusteringPoort,
      GeextraheerdBezwaarRepository bezwaarRepository,
      ExtractiePassageRepository passageRepository,
      ExtractieTaakRepository taakRepository,
      KernbezwaarAntwoordRepository antwoordRepository,
      ThemaRepository themaRepository,
      KernbezwaarRepository kernbezwaarRepository,
      KernbezwaarReferentieRepository referentieRepository) {
    this.embeddingPoort = embeddingPoort;
    this.clusteringPoort = clusteringPoort;
    this.bezwaarRepository = bezwaarRepository;
    this.passageRepository = passageRepository;
    this.taakRepository = taakRepository;
    this.antwoordRepository = antwoordRepository;
    this.themaRepository = themaRepository;
    this.kernbezwaarRepository = kernbezwaarRepository;
    this.referentieRepository = referentieRepository;
  }

  /**
   * Groepeert de individuele bezwaren van een project tot thema's en kernbezwaren
   * via embedding-vectorisatie en HDBSCAN-clustering.
   *
   * @param projectNaam Naam van het project
   * @return Lijst van thema's met kernbezwaren
   */
  @Transactional
  public List<Thema> groepeer(String projectNaam) {
    var alleBezwaren = bezwaarRepository.findByProjectNaam(projectNaam);
    if (alleBezwaren.isEmpty()) {
      return List.of();
    }

    // Bouw lookup: taakId -> passageNr -> originele tekst
    var passageTeksten = bouwPassageLookup(alleBezwaren);

    // Bouw lookup: taakId -> bestandsnaam
    var bestandsnamen = bouwBestandsnaamLookup(alleBezwaren);

    // Groepeer per categorie
    var perCategorie = alleBezwaren.stream()
        .collect(Collectors.groupingBy(GeextraheerdBezwaarEntiteit::getCategorie));

    // Verwijder bestaande data
    themaRepository.deleteByProjectNaam(projectNaam);

    var resultaat = new ArrayList<Thema>();
    for (var entry : perCategorie.entrySet()) {
      var categorie = entry.getKey();
      var bezwaren = entry.getValue();

      var thema = clusterCategorie(projectNaam, categorie,
          bezwaren, passageTeksten, bestandsnamen);
      resultaat.add(thema);
    }
    return resultaat;
  }

  private Thema clusterCategorie(String projectNaam, String categorie,
      List<GeextraheerdBezwaarEntiteit> bezwaren,
      Map<Long, Map<Integer, String>> passageTeksten,
      Map<Long, String> bestandsnamen) {

    // Haal originele teksten op
    var teksten = bezwaren.stream()
        .map(b -> passageTeksten
            .getOrDefault(b.getTaakId(), Map.of())
            .getOrDefault(b.getPassageNr(), b.getSamenvatting()))
        .toList();

    // Genereer embeddings
    var embeddings = embeddingPoort.genereerEmbeddings(teksten);

    // Sla embeddings op
    for (int i = 0; i < bezwaren.size(); i++) {
      bezwaren.get(i).setEmbedding(embeddings.get(i));
    }
    bezwaarRepository.saveAll(bezwaren);

    // Cluster
    var invoer = new ArrayList<ClusteringInvoer>();
    for (int i = 0; i < bezwaren.size(); i++) {
      invoer.add(new ClusteringInvoer(
          bezwaren.get(i).getId(), embeddings.get(i)));
    }
    var clusterResultaat = clusteringPoort.cluster(invoer);

    // Maak thema
    var themaEntiteit = new ThemaEntiteit();
    themaEntiteit.setProjectNaam(projectNaam);
    themaEntiteit.setNaam(categorie);
    themaEntiteit = themaRepository.save(themaEntiteit);

    // Bouw lookup bezwaarId -> bezwaar + tekst
    var bezwaarMap = new HashMap<Long, GeextraheerdBezwaarEntiteit>();
    var tekstMap = new HashMap<Long, String>();
    for (int i = 0; i < bezwaren.size(); i++) {
      bezwaarMap.put(bezwaren.get(i).getId(), bezwaren.get(i));
      tekstMap.put(bezwaren.get(i).getId(), teksten.get(i));
    }

    var kernbezwaren = new ArrayList<Kernbezwaar>();

    // Per cluster: maak kernbezwaar
    for (var cluster : clusterResultaat.clusters()) {
      var representantId = vindDichtstBijCentroid(
          cluster.bezwaarIds(), embeddings, bezwaren, cluster.centroid());
      var samenvatting = tekstMap.get(representantId);

      var kern = slaKernbezwaarOp(themaEntiteit.getId(), samenvatting,
          cluster.bezwaarIds(), bezwaarMap, bestandsnamen, tekstMap);
      kernbezwaren.add(kern);
    }

    // Noise: elk een eigen kernbezwaar
    for (var noiseId : clusterResultaat.noiseIds()) {
      var samenvatting = tekstMap.get(noiseId);
      var kern = slaKernbezwaarOp(themaEntiteit.getId(), samenvatting,
          List.of(noiseId), bezwaarMap, bestandsnamen, tekstMap);
      kernbezwaren.add(kern);
    }

    return new Thema(categorie, kernbezwaren);
  }

  private Long vindDichtstBijCentroid(List<Long> bezwaarIds,
      List<float[]> embeddings, List<GeextraheerdBezwaarEntiteit> bezwaren,
      float[] centroid) {
    var idToEmbedding = new HashMap<Long, float[]>();
    for (int i = 0; i < bezwaren.size(); i++) {
      idToEmbedding.put(bezwaren.get(i).getId(), embeddings.get(i));
    }

    Long dichtstBij = bezwaarIds.get(0);
    double maxGelijkenis = -1;
    for (var id : bezwaarIds) {
      var embedding = idToEmbedding.get(id);
      if (embedding != null) {
        var gelijkenis = cosinusGelijkenis(embedding, centroid);
        if (gelijkenis > maxGelijkenis) {
          maxGelijkenis = gelijkenis;
          dichtstBij = id;
        }
      }
    }
    return dichtstBij;
  }

  private double cosinusGelijkenis(float[] a, float[] b) {
    double dot = 0;
    double normA = 0;
    double normB = 0;
    for (int i = 0; i < a.length; i++) {
      dot += a[i] * b[i];
      normA += a[i] * a[i];
      normB += b[i] * b[i];
    }
    if (normA == 0 || normB == 0) {
      return 0;
    }
    return dot / (Math.sqrt(normA) * Math.sqrt(normB));
  }

  private Kernbezwaar slaKernbezwaarOp(Long themaId, String samenvatting,
      List<Long> bezwaarIds,
      Map<Long, GeextraheerdBezwaarEntiteit> bezwaarMap,
      Map<Long, String> bestandsnamen,
      Map<Long, String> tekstMap) {

    var kernEntiteit = new KernbezwaarEntiteit();
    kernEntiteit.setThemaId(themaId);
    kernEntiteit.setSamenvatting(samenvatting);
    kernEntiteit = kernbezwaarRepository.save(kernEntiteit);

    var referenties = new ArrayList<IndividueelBezwaarReferentie>();
    for (var bezwaarId : bezwaarIds) {
      var bezwaar = bezwaarMap.get(bezwaarId);
      var refEntiteit = new KernbezwaarReferentieEntiteit();
      refEntiteit.setKernbezwaarId(kernEntiteit.getId());
      refEntiteit.setBezwaarId(bezwaarId);
      refEntiteit.setBestandsnaam(
          bestandsnamen.getOrDefault(bezwaar.getTaakId(), "onbekend"));
      refEntiteit.setPassage(tekstMap.getOrDefault(bezwaarId, ""));
      referentieRepository.save(refEntiteit);
      referenties.add(new IndividueelBezwaarReferentie(
          bezwaarId, refEntiteit.getBestandsnaam(), refEntiteit.getPassage()));
    }

    return new Kernbezwaar(
        kernEntiteit.getId(), samenvatting, referenties, null);
  }

  private Map<Long, Map<Integer, String>> bouwPassageLookup(
      List<GeextraheerdBezwaarEntiteit> bezwaren) {
    var taakIds = bezwaren.stream()
        .map(GeextraheerdBezwaarEntiteit::getTaakId)
        .distinct()
        .toList();
    var lookup = new HashMap<Long, Map<Integer, String>>();
    for (var taakId : taakIds) {
      var passages = passageRepository.findByTaakId(taakId);
      var passageMap = passages.stream()
          .collect(Collectors.toMap(
              ExtractiePassageEntiteit::getPassageNr,
              ExtractiePassageEntiteit::getTekst));
      lookup.put(taakId, passageMap);
    }
    return lookup;
  }

  private Map<Long, String> bouwBestandsnaamLookup(
      List<GeextraheerdBezwaarEntiteit> bezwaren) {
    var taakIds = bezwaren.stream()
        .map(GeextraheerdBezwaarEntiteit::getTaakId)
        .distinct()
        .toList();
    var lookup = new HashMap<Long, String>();
    for (var taakId : taakIds) {
      taakRepository.findById(taakId)
          .ifPresent(taak -> lookup.put(taakId, taak.getBestandsnaam()));
    }
    return lookup;
  }

  /**
   * Geeft eerder berekende kernbezwaren voor een project.
   */
  public Optional<List<Thema>> geefKernbezwaren(String projectNaam) {
    var themaEntiteiten = themaRepository.findByProjectNaam(projectNaam);
    if (themaEntiteiten.isEmpty()) {
      return Optional.empty();
    }

    var themaIds = themaEntiteiten.stream()
        .map(ThemaEntiteit::getId).toList();
    var kernEntiteiten = kernbezwaarRepository.findByThemaIdIn(themaIds);
    var kernIds = kernEntiteiten.stream()
        .map(KernbezwaarEntiteit::getId).toList();
    var refEntiteiten = referentieRepository.findByKernbezwaarIdIn(kernIds);

    var refPerKern = refEntiteiten.stream()
        .collect(Collectors.groupingBy(
            KernbezwaarReferentieEntiteit::getKernbezwaarId));

    var kernPerThema = kernEntiteiten.stream()
        .collect(Collectors.groupingBy(KernbezwaarEntiteit::getThemaId));

    var themas = themaEntiteiten.stream()
        .map(te -> {
          var kernen = kernPerThema.getOrDefault(te.getId(), List.of())
              .stream()
              .map(ke -> {
                var refs = refPerKern.getOrDefault(ke.getId(), List.of())
                    .stream()
                    .map(re -> new IndividueelBezwaarReferentie(
                        re.getBezwaarId(), re.getBestandsnaam(),
                        re.getPassage()))
                    .toList();
                return new Kernbezwaar(
                    ke.getId(), ke.getSamenvatting(), refs, null);
              })
              .toList();
          return new Thema(te.getNaam(), kernen);
        })
        .toList();

    return Optional.of(verrijkMetAntwoorden(themas));
  }

  private List<Thema> verrijkMetAntwoorden(List<Thema> themas) {
    var alleIds = themas.stream()
        .flatMap(t -> t.kernbezwaren().stream())
        .map(Kernbezwaar::id)
        .toList();
    var antwoorden = antwoordRepository.findByKernbezwaarIdIn(alleIds);
    var antwoordMap = antwoorden.stream()
        .collect(Collectors.toMap(
            KernbezwaarAntwoordEntiteit::getKernbezwaarId,
            KernbezwaarAntwoordEntiteit::getInhoud));
    return themas.stream()
        .map(thema -> new Thema(thema.naam(),
            thema.kernbezwaren().stream()
                .map(kern -> new Kernbezwaar(kern.id(), kern.samenvatting(),
                    kern.individueleBezwaren(),
                    antwoordMap.get(kern.id())))
                .toList()))
        .toList();
  }

  /**
   * Slaat een antwoord op voor een kernbezwaar.
   */
  public void slaAntwoordOp(Long kernbezwaarId, String inhoud) {
    if (inhoud == null || inhoud.isBlank()) {
      if (antwoordRepository.existsById(kernbezwaarId)) {
        antwoordRepository.deleteById(kernbezwaarId);
      }
      return;
    }
    var entiteit = new KernbezwaarAntwoordEntiteit();
    entiteit.setKernbezwaarId(kernbezwaarId);
    entiteit.setInhoud(inhoud);
    entiteit.setBijgewerktOp(Instant.now());
    antwoordRepository.save(entiteit);
  }
}
```

**Step 4: Run test to verify it passes**

Run: `cd /Users/kenzo/Library/CloudStorage/Dropbox/1-Kenzo/4-Coding/bezwaarschriften && mvn test -pl app -Dtest=KernbezwaarServiceTest -q`
Expected: PASS

**Step 5: Commit**

```bash
git add app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/KernbezwaarService.java
git add app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/KernbezwaarServiceTest.java
git commit -m "feat: rewrite KernbezwaarService with HDBSCAN clustering pipeline"
```

---

### Task 9: Remove KernbezwaarPoort and MockKernbezwaarAdapter

**Files:**
- Delete: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/KernbezwaarPoort.java`
- Delete: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/MockKernbezwaarAdapter.java`
- Delete: `app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/MockKernbezwaarAdapterTest.java`
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/project/ProjectService.java` (remove `geefBezwaartekstenVoorGroepering` method and `KernbezwaarPoort` import)

**Step 1: Delete the files**

```bash
rm app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/KernbezwaarPoort.java
rm app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/MockKernbezwaarAdapter.java
rm app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/MockKernbezwaarAdapterTest.java
```

**Step 2: Update ProjectService.java**

Remove the `import be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar.KernbezwaarPoort;` import.

Remove the entire `geefBezwaartekstenVoorGroepering` method (lines 153-166 approximately).

**Step 3: Fix any remaining compile errors**

Run: `cd /Users/kenzo/Library/CloudStorage/Dropbox/1-Kenzo/4-Coding/bezwaarschriften && mvn compile -pl app -q`

Fix any remaining references to `KernbezwaarPoort` found by the compiler. Check:
- `ProjectService.java` — remove import and method
- Any other files that reference `KernbezwaarPoort.BezwaarInvoer`

**Step 4: Run all tests**

Run: `cd /Users/kenzo/Library/CloudStorage/Dropbox/1-Kenzo/4-Coding/bezwaarschriften && mvn test -pl app -q`
Expected: PASS (some tests may fail if they reference the removed mock — fix accordingly)

**Step 5: Commit**

```bash
git add -A
git commit -m "refactor: remove KernbezwaarPoort and MockKernbezwaarAdapter"
```

---

### Task 10: Add configuration to application.yml

**Files:**
- Modify: `app/src/main/resources/application-dev.yml`

**Step 1: Add clustering and embedding config**

Add this section to `application-dev.yml`:

```yaml
bezwaarschriften:
  clustering:
    min-cluster-size: 5
    min-samples: 3
  embedding:
    provider: ollama
    model: bge-m3
    dimensions: 1024
    ollama-url: http://localhost:11434
```

Keep the existing `bezwaarschriften` config entries (input, testdata, extractie). Merge the new keys into the existing block.

**Step 2: Commit**

```bash
git add app/src/main/resources/application-dev.yml
git commit -m "config: add clustering and embedding configuration"
```

---

### Task 11: Full build and test verification

**Step 1: Run full build**

Run: `cd /Users/kenzo/Library/CloudStorage/Dropbox/1-Kenzo/4-Coding/bezwaarschriften && mvn clean verify -pl app`

Expected: BUILD SUCCESS, all tests pass.

**Step 2: Fix any remaining issues**

If tests fail, investigate and fix. Common issues:
- Checkstyle violations (line length, import order, Javadoc)
- Missing mocks for new dependencies in existing tests
- Integration tests that need pgvector Docker image

**Step 3: Final commit if fixes were needed**

```bash
git add -A
git commit -m "fix: resolve build issues after HDBSCAN integration"
```

---

## Test & Verification Plan

| Test | Doel | Commando |
|---|---|---|
| `VectorTypeTest` | Hibernate UserType correctheid | `mvn test -Dtest=VectorTypeTest` |
| `WebClientEmbeddingAdapterTest` | Embedding API calls correct | `mvn test -Dtest=WebClientEmbeddingAdapterTest` |
| `TribuoClusteringAdapterTest` | HDBSCAN clustering correct | `mvn test -Dtest=TribuoClusteringAdapterTest` |
| `KernbezwaarServiceTest` | Orchestratie correct | `mvn test -Dtest=KernbezwaarServiceTest` |
| Full build | Alles compileert en past | `mvn clean verify -pl app` |

## Risico's

1. **Tribuo HDBSCAN performance** — Bij zeer grote aantallen bezwaren (>10.000) kan O(N²) traag worden. Monitor dit en overweeg Python sidecar als fallback.
2. **pgvector type mapping** — De custom `VectorType` is specifiek voor Hibernate 5. Bij upgrade naar Spring Boot 3 / Hibernate 6 kan `hibernate-vector` module worden gebruikt.
3. **Embedding API rate limits** — Bij OpenAI provider op rate limits letten. Ollama lokaal heeft dit probleem niet.
