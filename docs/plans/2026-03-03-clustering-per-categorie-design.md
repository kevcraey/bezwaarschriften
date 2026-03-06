# Clustering per categorie — Design

## Context

De clustering wordt momenteel synchroon uitgevoerd voor alle categorieën tegelijk. De gebruiker heeft geen controle over individuele categorieën en kan niet zien wat er gaande is per categorie. Dit ontwerp voegt per-categorie onafhankelijke clustering-taken toe met een async backend en een pill-UI in de accordion-header.

## Beslissingen

- **Per categorie onafhankelijk** — elke categorie heeft een eigen play/cancel/delete knop
- **Altijd beschikbaar** — de play-knop is zichtbaar zolang er bezwaren in de categorie zitten, ongeacht extractie-status
- **Waarschuwing bij verwijderen** — als er antwoorden zijn bij kernbezwaren, toon bevestigingsmodal. Geen archivering; antwoorden worden verwijderd
- **Taak-entiteit** — nieuw `clustering_taak` tabel met status-tracking, persistent over server-restarts
- **Globale knop behouden** — "Cluster alle categorieën" start alle niet-klare/bezig categorieën. Terminologie: "clustering" i.p.v. "groepeer"
- **Pill in accordion-header** — exact hetzelfde patroon als de extractie-taken: timer, cancel, retry knoppen in pill
- **Globale knop rechts bovenaan** — niet gecentreerd in een leeg blok

## Datamodel

### Nieuwe tabel: `clustering_taak`

| Kolom | Type | Beschrijving |
|-------|------|-------------|
| `id` | `BIGSERIAL PK` | |
| `project_naam` | `VARCHAR(255)` | |
| `categorie` | `VARCHAR(50)` | De categorienaam |
| `status` | `VARCHAR(20)` | `todo` / `wachtend` / `bezig` / `klaar` / `fout` / `geannuleerd` |
| `aangemaakt_op` | `TIMESTAMP` | Taak aangemaakt |
| `verwerking_gestart_op` | `TIMESTAMP` | Clustering gestart |
| `verwerking_voltooid_op` | `TIMESTAMP` | Clustering voltooid |
| `foutmelding` | `TEXT` | Foutmelding bij status=fout |

### Bestaand model (ongewijzigd)

`thema` → `kernbezwaar` → `kernbezwaar_referentie` / `kernbezwaar_antwoord`. Cascade-delete op thema. Bij herclustering van één categorie wordt alleen het thema van die categorie verwijderd.

## Backend API

| Method | Pad | Beschrijving |
|--------|-----|-------------|
| `GET` | `/api/v1/projects/{naam}/clustering-taken` | Alle categorieën met clustering-status. Categorieën zonder taak krijgen status `todo`. |
| `POST` | `/api/v1/projects/{naam}/clustering-taken/{categorie}` | Start clustering voor één categorie (async). |
| `POST` | `/api/v1/projects/{naam}/clustering-taken` | Bulk: start alle categorieën. |
| `DELETE` | `/api/v1/projects/{naam}/clustering-taken/{categorie}` | Annuleer lopende of verwijder afgeronde clustering. 409 bij antwoorden tenzij `?bevestigd=true`. |

### Response `GET /clustering-taken`

```json
{
  "categorien": [
    {
      "categorie": "Geluid",
      "aantalBezwaren": 42,
      "status": "klaar",
      "aantalKernbezwaren": 7,
      "aangemaaktOp": "2026-03-03T10:00:00Z",
      "verwerkingGestartOp": "2026-03-03T10:00:01Z",
      "verwerkingVoltooidOp": "2026-03-03T10:00:15Z"
    }
  ]
}
```

## Async verwerking

- `KernbezwaarService.clusterCategorie()` wordt `@Async` via Spring `TaskExecutor`
- Methode krijgt `ClusteringTaak`-ID, updatet status: `wachtend` → `bezig` → `klaar`/`fout`
- Periodieke check op `geannuleerd`-status in database; vroegtijdige stop bij annulering
- Elke categorie heeft eigen transactie (`REQUIRES_NEW`)
- Bij fout: status → `fout`, foutmelding opgeslagen

## Frontend UX

### Layout

```
┌─────────────────────────────────────────────────────────────┐
│                    [Cluster alle categorieën]  (rechts)      │
│                                                             │
│  ▼ Geluid (42 bezwaren)    [pill: Klaar 0:02+0:13]    [🗑]  │
│  ┌──────────────────────────────────────────────────────┐   │
│  │  7 kernbezwaren                                      │   │
│  │  ✔ Geluidshinder door nachtelijk verkeer (12)  🔍 ✏  │   │
│  │    ...                                               │   │
│  └──────────────────────────────────────────────────────┘   │
│                                                             │
│  ▶ Verkeer (18 bezwaren)   [Te clusteren]             [▶]   │
│  ▶ Lucht (25 bezwaren)     [pill: Bezig 0:01+0:05]   [×]   │
│  ▶ Natuur (6 bezwaren)     [pill: Fout]               [↻]   │
└─────────────────────────────────────────────────────────────┘
```

### Pill-statussen

| Status | Pill type | Knoppen |
|--------|-----------|---------|
| `todo` | (geen) | ▶ start |
| `wachtend` | warning | timer + × annuleer |
| `bezig` | warning | wacht+verwerk timer + × annuleer |
| `klaar` | success | 🗑 verwijder |
| `fout` | error | ↻ opnieuw |

### Interactie

- Accordion opent alleen bij `klaar`-status
- Delete bij `klaar`: bevestigingsmodal als er antwoorden zijn
- Globale knop start alle categorieën die niet `klaar`/`bezig`/`wachtend` zijn
- Polling elke 2 seconden bij actieve taken

## Teststrategie

### Backend

- `KernbezwaarServiceTest`: clustering per categorie, herclustering, annulering, foutafhandeling
- `ClusteringTaakController` integratietest: alle endpoints
- `ClusteringTaakRepository`: CRUD

### Frontend (Cypress)

- Pill-rendering per status
- Start, annuleer, verwijder clustering per categorie
- Bevestigingsmodal bij verwijderen met antwoorden
- Globale knop
- Timer-updates
- Accordion alleen open bij klaar
