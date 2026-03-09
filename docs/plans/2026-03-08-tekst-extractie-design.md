# Design: Tekst-extractie uit PDF en TXT

## Context

Bezwaarschriften komen primair als PDF aan. De huidige pipeline accepteert alleen `.txt`-bestanden. We moeten PDF's omzetten naar platte tekst met kwaliteitscontrole en OCR-fallback, zodat de rest van de pipeline (AI-extractie, embeddings, clustering) kan werken met betrouwbare tekst.

## Scope

- PDF-ondersteuning via PDFBox (digitale extractie) + Tess4j (OCR-fallback)
- Deterministische kwaliteitscontrole op geëxtraheerde tekst
- Asynchrone verwerking via taak-queue (zoals bestaande `ExtractieWorker`)
- Beide bestanden bewaren: origineel + geëxtraheerde tekst
- Extractiemethode (Digitaal/OCR) bijhouden voor traceability
- Gate-mechanisme: AI-extractie mag niet starten als tekst-extractie niet geslaagd is
- Documentatie in `docs/text-extractie.md`

## Architectuur

### Nieuw package

`be.vlaanderen.omgeving.bezwaarschriften.tekstextractie`

### Componenten

| Component | Verantwoordelijkheid |
|---|---|
| `TekstExtractieTaak` | JPA-entiteit: status, methode, foutmelding, timestamps |
| `TekstExtractieTaakStatus` | Enum: WACHTEND, BEZIG, KLAAR, MISLUKT, OCR_NIET_BESCHIKBAAR |
| `ExtractieMethode` | Enum: DIGITAAL, OCR |
| `TekstKwaliteitsControle` | Deterministische validatie van geëxtraheerde tekst |
| `PdfTekstExtractor` | PDFBox extractie + OCR-fallback via Tess4j |
| `TekstExtractieService` | Orkestrator: ontvangt taak, delegeert extractie, slaat tekst op |
| `TekstExtractieWorker` | Async polling van WACHTEND-taken |
| `TekstExtractieTaakRepository` | Spring Data JPA repository |

### Folderstructuur

```
{project}/bezwaren-orig/   → originele bestanden (pdf, txt)
{project}/bezwaren-text/   → geëxtraheerde platte tekst (.txt)
```

## Verwerkingsflow

```
Upload PDF/TXT
    │
    ├─ Bestand opslaan in {project}/bezwaren-orig/
    ├─ TekstExtractieTaak aanmaken (status: WACHTEND)
    │
    └─ TekstExtractieWorker pikt taak op (async)
        │
        ├─ .txt bestand
        │   └─ Lees tekst → kwaliteitscontrole
        │       ├─ OK → opslaan in bezwaren-text/, status KLAAR, methode DIGITAAL
        │       └─ FAIL → status MISLUKT
        │
        └─ .pdf bestand
            └─ PDFTextStripper → normaliseer witruimte → kwaliteitscontrole
                ├─ OK → opslaan in bezwaren-text/, status KLAAR, methode DIGITAAL
                └─ FAIL → OCR-fallback
                    ├─ Tesseract beschikbaar?
                    │   ├─ JA → render pagina's 300 DPI → OCR → kwaliteitscontrole
                    │   │   ├─ OK → opslaan in bezwaren-text/, status KLAAR, methode OCR
                    │   │   └─ FAIL → status MISLUKT
                    │   └─ NEE → status OCR_NIET_BESCHIKBAAR
```

## Kwaliteitscontrole

Deterministische criteria (zelfde voor alle extractiepaden):

| Criterium | Drempelwaarde |
|---|---|
| Minimaal aantal woorden | 100 |
| Alfanumerieke ratio (excl. spaties) | >= 70% |
| Klinker/letter ratio | 20% - 60% |

## Datamodel

### Nieuwe tabel: `tekst_extractie_taak`

| Kolom | Type | Beschrijving |
|---|---|---|
| id | BIGINT PK | Auto-increment |
| project_naam | VARCHAR NOT NULL | Projectnaam |
| bestandsnaam | VARCHAR NOT NULL | Originele bestandsnaam |
| status | VARCHAR NOT NULL | WACHTEND, BEZIG, KLAAR, MISLUKT, OCR_NIET_BESCHIKBAAR |
| extractie_methode | VARCHAR | DIGITAAL, OCR (null als niet verwerkt) |
| foutmelding | TEXT | Reden van falen |
| aangemaakt_op | TIMESTAMP NOT NULL | Aanmaaktijdstip |
| verwerking_gestart_op | TIMESTAMP | Start verwerking |
| afgerond_op | TIMESTAMP | Einde verwerking |
| versie | INT NOT NULL | Optimistic locking |

## API-aanpassingen

### BezwaarBestandDto

Nieuw veld: `extractieMethode` (String: "Digitaal", "OCR", null)

### Gate-mechanisme

`ExtractieTaakService` controleert bij aanmaken AI-extractietaak of er een `TekstExtractieTaak` met status `KLAAR` bestaat. Zo niet → weigeren.

## Frontend

- Nieuwe kolom "Methode" in documententabel: "Digitaal", "OCR", of "-"
- Status-pill voor tekst-extractie statussen
- "Start extractie" knop disabled als tekst-extractie niet geslaagd

## Dependencies

- `org.apache.pdfbox:pdfbox` — PDF digitale tekstextractie
- `net.sourceforge.tess4j:tess4j` — Tesseract OCR wrapper

## Aanpassingen bestaande code

- `ProjectService.uploadBezwaren()`: accepteert `.pdf` + `.txt`, slaat op in `bezwaren-orig/`
- `BestandssysteemIngestieAdapter.leesBestand()`: leest uit `bezwaren-text/`
- `BezwaarBestandStatus`: nieuwe waarde `TEKST_EXTRACTIE_MISLUKT`
- `ProjectPoort`/`BestandssysteemProjectAdapter`: ondersteuning voor gescheiden orig/text folders
