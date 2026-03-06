# Design: Thema's & Kernbezwaren

## Doel

Na extractie van individuele bezwaren: groepeer ze in thema's en cluster per thema tot kernbezwaren. De UI toont dit overzicht met de mogelijkheid om door te klikken naar de onderliggende passages.

## Domeinmodel

```
Thema
├── naam: String ("Geluid", "Mobiliteit", ...)
├── kernbezwaren: List<Kernbezwaar>

Kernbezwaar
├── id: Long
├── samenvatting: String (LLM-gegenereerde representatieve tekst)
├── individueleBezwaren: List<IndividueelBezwaarReferentie>

IndividueelBezwaarReferentie
├── bezwaarId: Long
├── bestandsnaam: String (herkomst-document)
├── passage: String (citaat uit origineel document)
```

Groepering verloopt in twee stappen:
1. Thema-indeling — elk individueel bezwaar krijgt een thema
2. Kernbezwaar-clustering — per thema worden gelijkaardige bezwaren gebundeld tot 1 kernbezwaar met samenvatting

## Backend: Ports & Adapters

### Port

```java
// Package: be.decibel.bezwaarschriften.kernbezwaar
public interface KernbezwaarPoort {
    List<Thema> groepeer(List<IndividueelBezwaar> bezwaren);
}
```

### Mock adapter

`MockKernbezwaarAdapter` retourneert hardcoded thema's:
- Geluid (2 kernbezwaren, elk met 2-3 individuele bezwaren)
- Mobiliteit (2 kernbezwaren)
- Geurhinder (1 kernbezwaar)

Met mock passages als: "De geluidsoverlast is ondraaglijk, vooral 's nachts wanneer het vrachtverkeer..."

### Service

`KernbezwaarService` orchestreert: haalt individuele bezwaren op, roept `KernbezwaarPoort.groepeer()` aan, geeft resultaat terug.

### REST endpoints

```
POST /api/v1/projects/{naam}/kernbezwaren/groepeer
  → triggert groepering, retourneert List<Thema>

GET  /api/v1/projects/{naam}/kernbezwaren
  → retourneert eerder berekende kernbezwaren (of 404 als nog niet gegroepeerd)
```

## Frontend: Kernbezwaren-tab

### Drie staten

**Staat 1 — Geen extractie gedaan:**
Tekst: "Nog geen bezwaren geextraheerd. Verwerk eerst documenten op de Documenten-tab."

**Staat 2 — Extractie klaar, niet gegroepeerd:**
Tekst: "N individuele bezwaren gevonden. Voer groepering tot thema's en kernbezwaren uit om verder te gaan."
Knop: "Groepeer bezwaren"

**Staat 3 — Gegroepeerd:**
Accordeons per thema met kernbezwaren.

### Accordeon-layout

Elk thema is een `vl-accordion` met in de titel: themanaam + aantal kernbezwaren.
Binnen de accordion: lijst van kernbezwaren, elk met:
- Samenvatting-tekst
- Vergrootglas-knop met aantal individuele bezwaren (bijv. "(5)")

### Side-sheet

Klik op vergrootglas opent een `vl-side-sheet` met:
- Bovenaan: het kernbezwaar als context (samenvatting)
- Daaronder per individueel bezwaar:
  - Bronbestand (bestandsnaam)
  - Passage/citaat uit het originele document

## Mock-strategie

Mock op service-niveau (hexagonale architectuur). De `MockKernbezwaarAdapter` implementeert `KernbezwaarPoort` en retourneert hardcoded data. REST endpoints en frontend zijn volledig echt. Later wordt alleen de adapter vervangen door een AI-implementatie.
