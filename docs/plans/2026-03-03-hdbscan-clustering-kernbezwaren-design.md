# Design: HDBSCAN Clustering van Individuele Bezwaren naar Kernbezwaren

**Datum:** 2026-03-03
**Status:** Goedgekeurd

## User Story

Als gebruiker wil ik dat alle geextraheerde individuele bezwaren automatisch wiskundig gegroepeerd worden tot overkoepelende Kernbezwaren, zodat ik efficient in bulk kan antwoorden op veelvoorkomende thema's en direct zie welke bezwaren volstrekt uniek zijn (noise).

## Overzicht

Vervangt de huidige `MockKernbezwaarAdapter` (hardcoded data) door een wiskundige clustering-pipeline:

1. **Vectorisatie** — originele bezwaarteksten omzetten naar embeddings via Ollama/OpenAI
2. **Opslag** — embeddings persisteren in PostgreSQL via pgvector
3. **Clustering** — HDBSCAN toepassen per categorie via Tribuo
4. **Centroid-selectie** — per cluster het bezwaar dichtst bij het zwaartepunt selecteren als representant
5. **Noise handling** — niet-geclusterde bezwaren worden elk een eigen kernbezwaar

## Architectuur

### Port-interfaces (hexagonaal)

```
ClusteringPoort (interface)
  cluster(List<ClusteringInvoer>) -> ClusteringResultaat

  ClusteringInvoer: record(Long bezwaarId, double[] embedding)
  ClusteringResultaat: record(List<Cluster> clusters, List<Long> noiseIds)
  Cluster: record(int label, List<Long> bezwaarIds, double[] centroid)

EmbeddingPoort (interface)
  genereerEmbeddings(List<String> teksten) -> List<double[]>
```

De `ClusteringPoort` abstraheert het clustering-algoritme. Dit maakt het mogelijk om later te switchen naar een Python-sidecar, een ander algoritme, of een andere library zonder impact op de rest van de codebase.

### Adapters

- `TribuoClusteringAdapter implements ClusteringPoort` — HDBSCAN via Tribuo
- `WebClientEmbeddingAdapter implements EmbeddingPoort` — Ollama/OpenAI via WebClient

### Mapping naar bestaand datamodel

| Concept | Entiteit | Invulling |
|---|---|---|
| Categorie | `ThemaEntiteit.naam` | = `GeextraheerdBezwaarEntiteit.categorie` |
| Cluster | `KernbezwaarEntiteit` | samenvatting = originele tekst dichtst bij centroid |
| Noise | `KernbezwaarEntiteit` | elk noise-item = eigen kernbezwaar, thema "Andere" |
| Referentie | `KernbezwaarReferentieEntiteit` | link naar individueel bezwaar |

## Pipeline Flow

```
KernbezwaarService.groepeer(projectNaam):

1. Haal alle GeextraheerdBezwaarEntiteiten op voor het project (via taakId)
2. Groepeer per categorie
3. Per categorie:
   a. Haal originele passageteksten op (join via taakId + passageNr -> ExtractiePassageEntiteit)
   b. EmbeddingPoort.genereerEmbeddings(teksten)
   c. Sla embeddings op in geextraheerd_bezwaar.embedding (pgvector)
   d. ClusteringPoort.cluster(bezwaarIds + embeddings)
   e. Per cluster:
      - Bereken centroid (gemiddelde vector)
      - Vind bezwaar dichtst bij centroid (cosinus-gelijkenis)
      - Maak KernbezwaarEntiteit(samenvatting = originele tekst van dat bezwaar)
   f. Noise-items: elk wordt een eigen Kernbezwaar
4. Persisteer Thema's (= categorieen) en Kernbezwaren
5. Retourneer resultaat
```

## Configuratie

```yaml
bezwaarschriften:
  clustering:
    min-cluster-size: 5
    min-samples: 3
  embedding:
    provider: ollama        # of 'openai'
    model: bge-m3
    dimensions: 1024
```

## Dependencies

```xml
<!-- Tribuo HDBSCAN -->
<dependency>
    <groupId>org.tribuo</groupId>
    <artifactId>tribuo-clustering-hdbscan</artifactId>
    <version>4.3.2</version>
</dependency>

<!-- pgvector Java -->
<dependency>
    <groupId>com.pgvector</groupId>
    <artifactId>pgvector</artifactId>
    <version>0.1.6</version>
</dependency>
```

## Database

### Liquibase migration

```sql
ALTER TABLE geextraheerd_bezwaar
  ADD COLUMN embedding vector(1024);

CREATE INDEX idx_geextraheerd_bezwaar_embedding
  ON geextraheerd_bezwaar
  USING ivfflat (embedding vector_cosine_ops)
  WITH (lists = 100);
```

Hibernate type mapping via `pgvector-java` custom UserType.

## Bestanden

| Bestand | Actie |
|---|---|
| `ClusteringPoort.java` | Nieuw — port interface |
| `TribuoClusteringAdapter.java` | Nieuw — HDBSCAN adapter |
| `EmbeddingPoort.java` | Nieuw — port interface |
| `WebClientEmbeddingAdapter.java` | Nieuw — Ollama/OpenAI adapter |
| `VectorType.java` | Nieuw — Hibernate UserType voor pgvector |
| `KernbezwaarService.java` | Wijzig — nieuwe groepeer() logica |
| `GeextraheerdBezwaarEntiteit.java` | Wijzig — embedding kolom toevoegen |
| `KernbezwaarPoort.java` | Verwijder |
| `MockKernbezwaarAdapter.java` | Verwijder |
| `application.yml` | Wijzig — clustering config |
| Liquibase migration | Nieuw — embedding kolom + index |
| `app/pom.xml` | Wijzig — Tribuo + pgvector dependencies |

## Bronnen

- [Tribuo HDBSCAN](https://tribuo.org/learn/4.3/tutorials/clustering-hdbscan-tribuo-v4.html)
- [pgvector-java](https://github.com/pgvector/pgvector-java)
- [HDBSCAN paper](https://www.mdpi.com/2076-3417/12/5/2405)
