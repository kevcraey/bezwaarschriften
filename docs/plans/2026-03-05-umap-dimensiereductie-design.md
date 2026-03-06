# UMAP Dimensiereductie voor Clustering Pipeline

**Datum:** 2026-03-05
**Status:** Goedgekeurd

## Probleem

HDBSCAN krijgt ruwe 1024D bge-m3 embeddings en functioneert als exacte deduplicator i.p.v. semantische cluster-engine. In hoog-dimensionale ruimtes liggen alle punten even ver van elkaar (curse of dimensionality), waardoor HDBSCAN geen semantische wolken kan detecteren.

## Oplossing

Drietrapsraket naar BERTopic-patroon:

```
Embedding (1024D) → UMAP (→ 5-10D) → HDBSCAN
```

UMAP (Uniform Manifold Approximation and Projection) comprimeert de 1024 dimensies naar een compacte ruimte met behoud van semantische relaties.

## Architectuur

### Benadering: Nieuwe DimensieReductiePoort

Aparte hexagonale port, past in bestaande architectuur. `ClusteringPoort` blijft ongewijzigd.

### Nieuwe componenten

| Component | Package | Rol |
|-----------|---------|-----|
| `DimensieReductiePoort` | `clustering/` | Port interface: `List<float[]> reduceer(List<float[]> vectoren)` |
| `UmapDimensieReductieAdapter` | `clustering/` | Implementatie via `tag.bio:umap:1.1.0` |

### Wat niet wijzigt

- `ClusteringPoort` interface
- `TribuoClusteringAdapter` (krijgt kleinere vectoren)
- `ClusteringResultaat`, `Cluster`, `ClusteringInvoer` records

### Wijzigingen in bestaande code

- `KernbezwaarService.clusterCategorie()` — optionele UMAP-stap tussen embedding-ophalen en HDBSCAN
- `ClusteringConfig` — UMAP-properties toevoegen
- `ClusteringConfigController` — UMAP-velden in DTO
- Frontend `bezwaarschriften-kernbezwaren.js` — UMAP toggle + 3 parameter-velden

## DimensieReductiePoort

```java
public interface DimensieReductiePoort {
  List<float[]> reduceer(List<float[]> vectoren);
}
```

Alle UMAP-parameters komen uit `ClusteringConfig` — de aanroeper hoeft niets te weten over UMAP-specifieke parameters.

## ClusteringConfig uitbreiding

```java
// Bestaand
private int minClusterSize = 5;
private int minSamples = 3;
private double clusterSelectionEpsilon = 0.0;

// Nieuw
private boolean umapEnabled = true;
private int umapNComponents = 5;
private int umapNNeighbors = 15;
private float umapMinDist = 0.1f;
```

Defaults gebaseerd op BERTopic-standaarden.

### application-dev.yml

```yaml
bezwaarschriften:
  clustering:
    min-cluster-size: 5
    min-samples: 3
    cluster-selection-epsilon: 0.2
    umap-enabled: true
    umap-n-components: 5
    umap-n-neighbors: 15
    umap-min-dist: 0.1
```

## Pipeline-wijziging in KernbezwaarService

In `clusterCategorie()`, tussen embedding-ophalen en HDBSCAN:

```java
var invoer = bezwaren.stream()
    .map(b -> new ClusteringInvoer(b.getId(), b.getEmbedding()))
    .toList();

if (config.isUmapEnabled()) {
    var vectoren = invoer.stream()
        .map(ClusteringInvoer::embedding).toList();
    var gereduceerd = dimensieReductiePoort.reduceer(vectoren);
    invoer = IntStream.range(0, invoer.size())
        .mapToObj(i -> new ClusteringInvoer(
            invoer.get(i).bezwaarId(), gereduceerd.get(i)))
        .toList();
}

var clusterResultaat = clusteringPoort.cluster(invoer);
```

### Centroid-herberekening

Na clustering worden de centroiden herberekend in de originele 1024D-ruimte. `vindDichtstBijCentroid()` werkt dan op 1024D embeddings met 1024D centroiden — geen mismatch.

## Frontend

- Titel: "Clustering parameters:" (was "HDBSCAN parameters:")
- `vl-toggle` voor UMAP aan/uit
- Drie inputvelden (zichtbaar als UMAP aan): `n_components`, `n_neighbors`, `min_dist`
- PUT/GET naar `/api/v1/clustering-config` met alle 7 velden

## Error handling

- **Te weinig datapunten:** Als `bezwaren.size() < nNeighbors + 1`, sla UMAP over en ga direct naar HDBSCAN. Log-waarschuwing.
- **Epsilon tuning:** Afstanden in 5D zijn anders dan in 1024D. De UI maakt epsilon aanpasbaar.

## Dependency

```xml
<dependency>
    <groupId>tag.bio</groupId>
    <artifactId>umap</artifactId>
    <version>1.1.0</version>
</dependency>
```

Pure Java, geen native dependencies. Performance: seconden voor ~500 vectoren van 1024D.
