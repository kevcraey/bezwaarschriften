# Design: Dual Embedding met Clustering Bron-keuze

**Datum:** 2026-03-06
**Status:** Goedgekeurd

## Probleem

Momenteel wordt bij extractie alleen de passage-tekst geëmbed. Bij het clusteren is er geen keuze: HDBSCAN werkt altijd op passage-embeddings. We willen experimenteren met clusteren op samenvattingen vs. passages om te zien welke betere clusters oplevert.

## Oplossing

Sla bij extractie **twee embeddings** op per bezwaar (passage + samenvatting). Voeg een project-brede config-toggle toe waarmee de gebruiker kiest welke embedding-bron voor clustering wordt gebruikt.

## Database

- Hernoem kolom `embedding` → `embedding_passage` op tabel `geextraheerd_bezwaar`
- Voeg kolom `embedding_samenvatting` (vector(1024)) toe
- Flyway-migratie

## Entiteit: GeextraheerdBezwaarEntiteit

- Verwijder veld `embedding`
- Twee nieuwe velden: `embeddingPassage` (float[]) en `embeddingSamenvatting` (float[])
- Twee losse getters: `getEmbeddingPassage()` en `getEmbeddingSamenvatting()`

## Extractie: ExtractieTaakService

### markeerKlaar

Genereer twee batches embeddings:
1. Passage-teksten → `embeddingPassage`
2. Samenvattingen → `embeddingSamenvatting`

### voegManueelBezwaarToe

Twee embeddings genereren: passage-tekst + samenvatting.

## Clustering Config

- `ClusteringConfig`: nieuw veld `boolean clusterOpPassages = true`
- `ClusteringConfigController`: accepteert het nieuwe veld in PUT
- Frontend: checkbox "Cluster op passages" in clustering config UI (aangevinkt = passages, uitgevinkt = samenvattingen)

## KernbezwaarService

### clusterCategorie

- Bij het samenstellen van `ClusteringInvoer`: kies `embeddingPassage` of `embeddingSamenvatting` op basis van `clusteringConfig.isClusterOpPassages()`
- Legacy-pad (bezwaren zonder embedding): genereer beide embeddings, gebruik de juiste
- `berekenOrigineleCentroid` en `vindDichtstBijCentroid`: gebruiken dezelfde embedding-bron als de clustering

## Geen migratie van bestaande data

Omdat het project in actieve ontwikkeling is, worden bestaande bestanden opnieuw geüpload. Geen backfill nodig.
