# Domeinmodel

**Laatst bijgewerkt:** 2026-03-12

---

## Entity Relationship Diagram

```mermaid
erDiagram
    BezwaarBestandEntiteit {
        Long id PK
        String projectNaam
        String bestandsnaam
        BezwaarBestandStatus status
    }

    TekstExtractieTaak {
        Long id PK
        String projectNaam
        String bestandsnaam
        TekstExtractieTaakStatus status
        ExtractieMethode extractieMethode
        String pseudonimiseringMappingId
        String foutmelding
    }

    ExtractieTaak {
        Long id PK
        String projectNaam
        String bestandsnaam
        ExtractieTaakStatus status
        String foutmelding
    }

    GeextraheerdBezwaarEntiteit {
        Long id PK
        String projectNaam
        String bestandsnaam
        String bezwaarTekst
        float[] embedding
    }

    ExtractiePassageEntiteit {
        Long id PK
        String passageTekst
        int startPositie
        int eindPositie
    }

    IndividueelBezwaar {
        Long id PK
        String tekst
        float[] embedding
    }

    KernbezwaarEntiteit {
        Long id PK
        String projectNaam
        String samenvatting
    }

    KernbezwaarReferentieEntiteit {
        Long id PK
        ToewijzingsMethode toewijzingsMethode
        Double score
    }

    KernbezwaarAntwoordEntiteit {
        Long id PK
        String antwoordTekst
    }

    PassageGroepEntiteit {
        Long id PK
        String label
    }

    PassageGroepLidEntiteit {
        Long id PK
        Double score
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
        ConsolidatieTaakStatus status
    }

    BezwaarBestandEntiteit ||--o| TekstExtractieTaak : "tekst-extractie"
    BezwaarBestandEntiteit ||--o| ExtractieTaak : "bezwaar-extractie"
    ExtractieTaak ||--|{ GeextraheerdBezwaarEntiteit : "extraheert"
    ExtractieTaak ||--|{ ExtractiePassageEntiteit : "passages"
    GeextraheerdBezwaarEntiteit }|--|| ExtractiePassageEntiteit : "passage"
    KernbezwaarEntiteit ||--|{ KernbezwaarReferentieEntiteit : "referenties"
    KernbezwaarReferentieEntiteit }|--|| GeextraheerdBezwaarEntiteit : "bezwaar"
    KernbezwaarEntiteit ||--o| KernbezwaarAntwoordEntiteit : "antwoord"
    KernbezwaarEntiteit ||--|{ PassageGroepEntiteit : "passage-groepen"
    PassageGroepEntiteit ||--|{ PassageGroepLidEntiteit : "leden"
    PassageGroepLidEntiteit }|--|| ExtractiePassageEntiteit : "passage"
```

---

## Entiteiten per package

| Package | Entiteit | Tabel | Kernvelden |
|---------|----------|-------|------------|
| `project` | `BezwaarBestandEntiteit` | `bezwaar_bestand` | projectNaam, bestandsnaam, status |
| `project` | `ExtractieTaak` | `extractie_taak` | projectNaam, bestandsnaam, status |
| `project` | `GeextraheerdBezwaarEntiteit` | `geextraheerd_bezwaar` | bezwaarTekst, embedding |
| `project` | `ExtractiePassageEntiteit` | `extractie_passage` | passageTekst, start/eindPositie |
| `tekstextractie` | `TekstExtractieTaak` | `tekst_extractie_taak` | status, extractieMethode, **pseudonimiseringMappingId** |
| `domain` | `IndividueelBezwaar` | `individueel_bezwaar` | tekst, embedding |
| `kernbezwaar` | `KernbezwaarEntiteit` | `kernbezwaar` | projectNaam, samenvatting |
| `kernbezwaar` | `KernbezwaarReferentieEntiteit` | `kernbezwaar_referentie` | toewijzingsMethode, score |
| `kernbezwaar` | `KernbezwaarAntwoordEntiteit` | `kernbezwaar_antwoord` | antwoordTekst |
| `kernbezwaar` | `PassageGroepEntiteit` | `passage_groep` | label |
| `kernbezwaar` | `PassageGroepLidEntiteit` | `passage_groep_lid` | score |
| `kernbezwaar` | `ClusteringTaak` | `clustering_taak` | status, aantalBezwaren, aantalClusters |
| `consolidatie` | `ConsolidatieTaak` | `consolidatie_taak` | projectNaam, status |
