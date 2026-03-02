# Design: Resultaten-tab met consolidatie-taaksysteem

**Datum:** 2026-03-02

## Doel

Nieuw tabblad "Resultaten" dat per document toont of alle kernbezwaar-antwoorden compleet zijn, en een consolidatie-job aanbiedt om gepersonaliseerde antwoorden te genereren. Dit ticket implementeert het taak-mechanisme en de UI; de daadwerkelijke LLM-consolidatie wordt gemockt, output/briefgeneratie is een apart ticket.

## Architectuurbeslissingen

1. **Parallel taaksysteem** — apart consolidatie-taaksysteem naast extractie (geen generalisatie)
2. **WebSocket hernoemen** — `/ws/extracties` wordt `/ws/taken` met type-discriminator (`taak-update` / `consolidatie-update`)
3. **Antwoorden-check per document** — keten: bestandsnaam → kernbezwaar_referentie → kernbezwaar → antwoord
4. **Geen output-opslag** — alleen status-transitie in dit ticket
5. **Mock-verwerker** — delay 2-5s + template-brief als placeholder tekst

## Backend

### Database

Nieuwe tabel `consolidatie_taak`:

| Kolom | Type | Nullable |
|-------|------|----------|
| id | bigint (PK, identity) | nee |
| project_naam | varchar | nee |
| bestandsnaam | varchar | nee |
| status | varchar (enum) | nee |
| aantal_pogingen | int | nee |
| max_pogingen | int | nee |
| foutmelding | text | ja |
| aangemaakt_op | timestamp | nee |
| verwerking_gestart_op | timestamp | ja |
| afgerond_op | timestamp | ja |
| versie | int | nee |

### Entiteiten en services

- `ConsolidatieTaak` — JPA-entiteit (zelfde structuur als `ExtractieTaak`)
- `ConsolidatieTaakStatus` — enum: `WACHTEND`, `BEZIG`, `KLAAR`, `FOUT`
- `ConsolidatieTaakDto` — DTO met statusNaarString mapping
- `ConsolidatieTaakRepository` — Spring Data JPA
- `ConsolidatieTaakService` — indienen, oppakken, markeerKlaar, markeerFout, verwijderTaak, verwerkOnafgeronde
- `ConsolidatieWorker` — scheduled polling, pakt taken op, roept verwerker aan
- `ConsolidatieVerwerker` — interface voor de verwerking
- `MockConsolidatieVerwerker` — delay 2-5s + template-brief placeholder
- `ConsolidatieNotificatie` — interface (geimplementeerd door WebSocket handler)

### Antwoorden-check

`ConsolidatieController` berekent per document de antwoord-status:

- Query: via bestandsnaam → `kernbezwaar_referentie` (distinct kernbezwaar_id) → check of `kernbezwaar_antwoord` record bestaat
- Retourneert per document: `{aantalMetAntwoord, totaalKernbezwaren}`
- Afgeleide status: `aantalMetAntwoord < totaalKernbezwaren` → "onvolledig", `==` → "volledig"
- Als consolidatie-taak bestaat → taak-status overschrijft de berekende status

### Controller

`ConsolidatieController` (`/api/v1/projects`):

| Endpoint | Methode | Beschrijving |
|----------|---------|-------------|
| `/{naam}/consolidaties` | GET | Lijst van documenten met antwoord-status en consolidatie-status |
| `/{naam}/consolidaties` | POST | Dien consolidatie-taken in (body: `{bestandsnamen}`) |
| `/{naam}/consolidaties/verwerken` | POST | Verwerk onafgeronde (fout → retry, volledig → nieuw) |
| `/{naam}/consolidaties/{taakId}` | DELETE | Annuleer taak |

### WebSocket

- `/ws/extracties` hernoemen naar `/ws/taken`
- `ExtractieWebSocketHandler` hernoemen naar `TaakWebSocketHandler`
- Bestaande berichten: `{type: "taak-update", taak: {...}}`
- Nieuwe berichten: `{type: "consolidatie-update", taak: {...}}`

## Frontend

### Resultaten-tabel (`bezwaarschriften-resultaten-tabel.js`)

4 kolommen:

| Kolom | Renderer | Sorteerbaar |
|-------|----------|-------------|
| Selectie | checkbox | nee |
| Bestandsnaam | download-link | ja |
| Antwoorden | "X/N" gecentreerd | ja |
| Status | pill met inline actieknoppen | ja |

Statussen en pills:

| Status | Label | Pill-type | Inline knoppen |
|--------|-------|-----------|----------------|
| onvolledig | Onvolledig | (default) | — |
| volledig | Volledig | (default) | ▶ Start |
| wachtend | Wachtend (timer) | warning | × Annuleer |
| bezig | Bezig (timer) | warning | × Annuleer |
| klaar | Klaar | success | — |
| fout | Fout | error | ↻ Opnieuw |

Filter: bestandsnaam-zoek + status-dropdown. Paginering: 50 items.

### Project-selectie uitbreiding

- 3e `vl-tabs-pane` met id `resultaten`, titel "Resultaten"
- Knop "Consolideren" bovenaan (verborgen als geen selectie en geen verwerkbare items)
- Tab-titel toont voortgang: `Resultaten (X/N)` of `✔️ Resultaten (N/N)`
- WebSocket: filter berichten op `type` voor juiste tabel
- Bij `antwoord-voortgang` event van kernbezwaren-component: refresh resultaten-data

### WebSocket-migratie (frontend)

- URL wijzigt van `/ws/extracties` naar `/ws/taken`
- `onmessage`: filter op `data.type === 'taak-update'` (extractie) vs `data.type === 'consolidatie-update'` (consolidatie)

## Niet in scope

- Daadwerkelijke LLM-consolidatie (mock only)
- Output/briefgeneratie (apart ticket)
- Validatie en scoring
- Opslag van consolidatie-resultaat (alleen status-transitie)
