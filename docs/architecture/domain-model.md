# Domeinmodel

**Laatst bijgewerkt:** 2026-03-17

---

## Entity Relationship Diagram

```mermaid
erDiagram
    BezwaarDocument {
        Long id PK
        String projectNaam
        String bestandsnaam
        TekstExtractieStatus tekstExtractieStatus
        BezwaarExtractieStatus bezwaarExtractieStatus
        String extractieMethode
        Integer aantalWoorden
        boolean heeftPassagesDieNietInTekstVoorkomen
        boolean heeftManueel
        String foutmelding
    }

    IndividueelBezwaar {
        Long id PK
        Long documentId FK
        String samenvatting
        String passageTekst
        boolean passageGevonden
        boolean manueel
        float[] embeddingPassage
        float[] embeddingSamenvatting
    }

    KernbezwaarEntiteit {
        Long id PK
        String projectNaam
        String samenvatting
    }

    KernbezwaarReferentieEntiteit {
        Long id PK
        ToewijzingsMethode toewijzingsMethode
    }

    KernbezwaarAntwoordEntiteit {
        Long kernbezwaarId PK
        String inhoud
        Instant bijgewerktOp
    }

    PassageGroepEntiteit {
        Long id PK
        Long clusteringTaakId FK
        String passage
        String samenvatting
        String categorie
        Integer scorePercentage
    }

    PassageGroepLidEntiteit {
        Long id PK
        Long passageGroepId FK
        Long bezwaarId FK
    }

    ClusteringTaak {
        Long id PK
        String projectNaam
        ClusteringTaakStatus status
        int aantalBezwaren
        int aantalClusters
    }

    ConsolidatieTaak {
        Long id PK
        String projectNaam
        String bestandsnaam
        ConsolidatieTaakStatus status
    }

    PseudonimiseringChunk {
        Long id PK
        Long documentId FK
        int volgnummer
        String mappingId
    }

    BezwaarDocument ||--|{ IndividueelBezwaar : "bevat"
    BezwaarDocument ||--o{ PseudonimiseringChunk : "pseudonimisering"
    IndividueelBezwaar ||--o{ PassageGroepLidEntiteit : "lid van"
    PassageGroepEntiteit ||--|{ PassageGroepLidEntiteit : "leden"
    KernbezwaarEntiteit ||--|{ KernbezwaarReferentieEntiteit : "referenties"
    KernbezwaarReferentieEntiteit }|--|| PassageGroepEntiteit : "verwijst naar"
    KernbezwaarEntiteit ||--o| KernbezwaarAntwoordEntiteit : "antwoord"
    ClusteringTaak ||--|{ PassageGroepEntiteit : "produceert"
```

---

## Entiteiten per package

| Package | Entiteit | Tabel | Kernvelden |
|---------|----------|-------|------------|
| `project` | `BezwaarDocument` | `bezwaar_document` | projectNaam, bestandsnaam, tekstExtractieStatus, bezwaarExtractieStatus |
| `project` | `IndividueelBezwaar` | `individueel_bezwaar` | documentId, samenvatting, passageTekst, embeddingPassage, embeddingSamenvatting |
| `tekstextractie` | `PseudonimiseringChunk` | `pseudonimisering_chunk` | documentId, volgnummer, mappingId |
| `kernbezwaar` | `KernbezwaarEntiteit` | `kernbezwaar` | projectNaam, samenvatting |
| `kernbezwaar` | `KernbezwaarReferentieEntiteit` | `kernbezwaar_referentie` | toewijzingsMethode |
| `kernbezwaar` | `KernbezwaarAntwoordEntiteit` | `kernbezwaar_antwoord` | inhoud, bijgewerktOp |
| `kernbezwaar` | `PassageGroepEntiteit` | `passage_groep` | passage, samenvatting, categorie, scorePercentage |
| `kernbezwaar` | `PassageGroepLidEntiteit` | `passage_groep_lid` | bezwaarId |
| `kernbezwaar` | `ClusteringTaak` | `clustering_taak` | status, aantalBezwaren, aantalClusters |
| `consolidatie` | `ConsolidatieTaak` | `consolidatie_taak` | projectNaam, bestandsnaam, status |

---

## Verwijderde entiteiten (Plan 1)

| Entiteit | Reden |
|----------|-------|
| `BezwaarBestandEntiteit` | Opgegaan in `BezwaarDocument` |
| `TekstExtractieTaak` | Efemeer — status geabsorbeerd in `BezwaarDocument` |
| `ExtractieTaak` | Efemeer — status geabsorbeerd in `BezwaarDocument` |
| `ExtractiePassageEntiteit` | `passageTekst` is nu veld op `IndividueelBezwaar` |
| `BezwaarBestandStatus` | Vervangen door `TekstExtractieStatus` + `BezwaarExtractieStatus` |
