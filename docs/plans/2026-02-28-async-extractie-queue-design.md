# Async Extractie Queue — Design

## Context

Extractie van individuele bezwaren uit documenten zal op termijn door een LLM gebeuren. Dat kan minuten duren per bestand. Het systeem moet vele verwerkingen tegelijk aankunnen, met zichtbare voortgang en timing.

## Beslissingen

| Vraag | Keuze |
|---|---|
| Frontend updates | WebSockets (native, geen STOMP) |
| Queue-persistentie | Database-backed (PostgreSQL) |
| Concurrency | Configureerbaar (default 3) |
| Mock delay | Random (5-30s, configureerbaar) |
| Fout-afhandeling | Automatische retry met limiet (default 3) |
| Architectuur | Aanpak A: Spring Task Queue + WebSocket |

## Data Model

### ExtractieTaak (JPA entity, tabel: `extractie_taak`)

| Veld | Type | Beschrijving |
|---|---|---|
| id | Long | Auto-generated PK |
| projectNaam | String, not null | Naam van het project |
| bestandsnaam | String, not null | Naam van het bezwaarbestand |
| status | Enum | WACHTEND, BEZIG, KLAAR, FOUT |
| aantalPogingen | int, default 0 | Huidige pogingteller |
| maxPogingen | int, default 3 | Maximum aantal pogingen |
| aantalWoorden | Integer, nullable | Woordentelling na verwerking |
| aantalBezwaren | Integer, nullable | Aantal geextraheerde bezwaren |
| foutmelding | String, nullable | Foutbericht bij FOUT-status |
| aangemaaktOp | Instant, not null | Moment van indienen (start wachtrij) |
| verwerkingGestartOp | Instant, nullable | Moment dat worker taak oppikt |
| afgerondOp | Instant, nullable | Moment klaar of definitief fout |
| versie | int, default 0 | @Version voor optimistic locking |

### Statusflow

```
WACHTEND --> BEZIG --> KLAAR
                   --> FOUT (aantalPogingen < maxPogingen --> terug naar WACHTEND)
                   --> FOUT (definitief als aantalPogingen >= maxPogingen)
```

### Database schema

```sql
CREATE TABLE extractie_taak (
    id BIGSERIAL PRIMARY KEY,
    project_naam VARCHAR(255) NOT NULL,
    bestandsnaam VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL,
    aantal_pogingen INT NOT NULL DEFAULT 0,
    max_pogingen INT NOT NULL DEFAULT 3,
    aantal_woorden INT,
    aantal_bezwaren INT,
    foutmelding TEXT,
    aangemaakt_op TIMESTAMP WITH TIME ZONE NOT NULL,
    verwerking_gestart_op TIMESTAMP WITH TIME ZONE,
    afgerond_op TIMESTAMP WITH TIME ZONE,
    versie INT NOT NULL DEFAULT 0
);
```

## Backend Architectuur

### Nieuwe componenten

```
be.vlaanderen.omgeving.bezwaarschriften.project
├── ExtractieTaak.java              (JPA entity)
├── ExtractieTaakRepository.java    (Spring Data JPA)
├── ExtractieTaakService.java       (queue-operaties: indienen, status ophalen)
├── ExtractieWorker.java            (@Scheduled, pikt taken op en voert uit)
├── ExtractieVerwerker.java         (interface — de daadwerkelijke extractie-logica)
├── MockExtractieVerwerker.java     (implementatie met random delay + mock resultaten)
└── ExtractieWebSocketHandler.java  (pusht status-updates naar connected clients)
```

### Flow

1. **Indienen:** POST `/api/v1/projects/{naam}/extracties` met `{bestandsnamen: [...]}`. `ExtractieTaakService` maakt per bestand een `ExtractieTaak` met status WACHTEND en `aangemaaktOp = Instant.now()`.

2. **Worker:** `ExtractieWorker` draait `@Scheduled(fixedDelay = 1000)`. Elke seconde:
   - Tel hoeveel taken status BEZIG hebben
   - Als minder dan `max-concurrent`: pak oudste WACHTEND-taken op (optimistic lock via `@Version`)
   - Zet status op BEZIG, `verwerkingGestartOp = Instant.now()`
   - Voer `ExtractieVerwerker.verwerk()` uit op thread pool
   - Bij succes: KLAAR + resultaat opslaan
   - Bij fout: `aantalPogingen++`, als < max terug naar WACHTEND, anders FOUT definitief
   - Na elke statuswijziging: WebSocket-bericht sturen

3. **WebSocket:** Elke statuswijziging stuurt een bericht naar alle connected clients.

## WebSocket Protocol

### Endpoint

`ws://localhost:8080/ws/extracties`

Native WebSocket, geen STOMP/SockJS.

### Berichten (server naar client)

```json
{
  "type": "taak-update",
  "taak": {
    "id": 42,
    "projectNaam": "windmolens",
    "bestandsnaam": "bezwaar-001.txt",
    "status": "bezig",
    "aantalPogingen": 1,
    "aangemaaktOp": "2026-02-28T14:30:00Z",
    "verwerkingGestartOp": "2026-02-28T14:30:23Z",
    "aantalWoorden": null,
    "aantalBezwaren": null,
    "foutmelding": null
  }
}
```

Geen client-naar-server berichten nodig.

### Reconnect

Exponential backoff (1s, 2s, 4s, max 30s). Bij reconnect: GET `/api/v1/projects/{naam}/extracties` om gemiste updates te synchroniseren.

## Frontend

### Tabel status-weergave

| Status | Weergave |
|---|---|
| Wachtend | `Wachtend (MM:SS)` — timer telt op vanaf `aangemaaktOp` |
| Bezig | `Bezig (MM:SS + MM:SS)` — wachttijd (vast) + verwerkingstijd (lopend) |
| Klaar | `Extractie klaar (N woorden)` — statisch |
| Fout | `Fout` — statisch |

Timer via `setInterval` (1s), alleen actief als er WACHTEND/BEZIG taken zijn.

### Checkbox-gedrag

- WACHTEND/BEZIG: disabled (al in verwerking)
- TODO/KLAAR/FOUT: enabled
- NIET_ONDERSTEUND: disabled

### WebSocket-verbinding

Geopend bij `connectedCallback()` in `bezwaarschriften-project-selectie.js`. Bij binnenkomend `taak-update` bericht wordt de betreffende rij bijgewerkt.

## API Endpoints

### Nieuw

```
POST /api/v1/projects/{naam}/extracties
  Body: { "bestandsnamen": ["bezwaar-001.txt", "bezwaar-002.txt"] }
  Response: { "taken": [...] }

GET /api/v1/projects/{naam}/extracties
  Response: { "taken": [...] }
```

### Verwijderd

```
POST /api/v1/projects/{naam}/bezwaren/{bestandsnaam}/extraheer
POST /api/v1/projects/{naam}/verwerk
```

### Ongewijzigd

```
GET /api/v1/projects/{naam}/bezwaren
  Status afgeleid uit meest recente ExtractieTaak in database
```

## Configuratie

```yaml
bezwaarschriften:
  extractie:
    max-concurrent: 3
    max-pogingen: 3
    mock:
      min-delay-seconden: 5
      max-delay-seconden: 30
```

## Testen

- **Unit tests:** State transitions (WACHTEND/BEZIG/KLAAR/FOUT), retry-flow, max-pogingen. Instant mock (geen delay).
- **Integratie tests:** Worker met korte delays, concurrency-limiet verificatie.
- **WebSocket tests:** Statuswijzigingen correct als JSON-berichten ontvangen.
- **Handmatige validatie:** Default config (5-30s delay), timers in browser, queue-gedrag, retry-flow.
