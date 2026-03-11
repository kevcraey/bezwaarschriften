# Obscuro Pseudonimisering Integratie

## Doel

Na tekst-extractie wordt de geextraheerde tekst gepseudonimiseerd via de Obscuro-service voordat deze wordt opgeslagen in `bezwaren-text/`. De mapping-ID voor de-pseudonimisering wordt opgeslagen in de database.

## Scope

- Pseudonimisering toepassen na tekst-extractie, voor opslag
- Mapping-ID persisteren in `tekst_extractie_taak`
- Obscuro als Docker container naast de applicatie draaien
- E2E test voor de volledige happy path
- **Buiten scope**: de-pseudonimisering UI/API, briefgeneratie-integratie

## Architectuur

### Hexagonale structuur

```
TekstExtractieService
    ↓ (na extractie, voor opslag)
    PseudonimiseringPoort (interface)
        ↓
    ObscuroAdapter (HTTP → Obscuro API)
```

### Nieuwe componenten

| Component | Package | Verantwoordelijkheid |
|---|---|---|
| `PseudonimiseringPoort` | `tekstextractie` | Port: `pseudonimiseer(tekst) → PseudonimiseringResultaat` |
| `PseudonimiseringResultaat` | `tekstextractie` | Record: `gepseudonimiseerdeTekst` + `mappingId` |
| `ObscuroAdapter` | `tekstextractie.adapter` | HTTP client naar Obscuro `/pseudonymize` |
| `ObscuroConfiguratie` | `config` | Spring config: URL, TTL, timeouts |

### Aangepaste componenten

| Component | Wijziging |
|---|---|
| `TekstExtractieService` | Na extractie → `pseudonimiseringPoort.pseudonimiseer()` aanroepen, mapping-ID opslaan |
| `TekstExtractieTaak` | Nieuw veld `pseudonimiseringMappingId` |
| `docker-compose.yml` | Obscuro container toevoegen |

## Datamodel

### Wijziging `tekst_extractie_taak`

```sql
ALTER TABLE tekst_extractie_taak
  ADD COLUMN pseudonimisering_mapping_id VARCHAR(36);
```

Nullable — bestaande records hebben geen mapping.

## API-interactie met Obscuro

### Pseudonimiseren

```
POST http://obscuro:8000/pseudonymize
Content-Type: application/json

{
  "text": "<geextraheerde tekst>",
  "ttl_seconds": 31536000
}

→ Response:
{
  "text": "<gepseudonimiseerde tekst>",
  "mapping_id": "550e8400-e29b-41d4-a716-446655440000"
}
```

### HTTP Client

- Spring `RestClient` (synchroon, past bij bestaande verwerkingsflow)
- Connect timeout: 30s (configureerbaar)
- Read timeout: 120s (configureerbaar, grote documenten)
- Fout → taak status `MISLUKT` met foutmelding

## Configuratie

### application.yml

```yaml
bezwaarschriften:
  pseudonimisering:
    url: http://localhost:8000
    ttl-seconds: 31536000
    connect-timeout: 30s
    read-timeout: 120s
```

### docker-compose.yml

```yaml
obscuro:
  image: ghcr.io/kevcraey/obscuro-service:latest
  ports:
    - "8000:8000"
  environment:
    - PYTHONUNBUFFERED=1
    - TTL_SECONDS=31536000
  restart: unless-stopped
  healthcheck:
    test: python -c "import urllib.request; urllib.request.urlopen('http://localhost:8000/health')"
    interval: 30s
    timeout: 10s
    retries: 3
    start_period: 60s
```

## Aangepaste flow

```mermaid
graph TD
    A[TekstExtractieWorker pikt taak op] --> B[Extraheer tekst - PDFBox/OCR]
    B --> C[PseudonimiseringPoort.pseudonimiseer]
    C --> D{Succes?}
    D -->|Ja| E[Sla gepseudonimiseerde tekst op in bezwaren-text/]
    E --> F[Sla mappingId op in tekst_extractie_taak]
    F --> G[Status → KLAAR]
    D -->|Nee| H[Status → MISLUKT met foutmelding]
```

## E2E Test

### Aanpak

- Testcontainers: `GenericContainer` met Obscuro Docker image
- Volledige happy path: upload PDF → extractie → pseudonimisering → opslag → verificatie
- Draait als onderdeel van `mvn verify`

### Testdata

PDF met bekende PII: naam, IBAN, adres. Zodat we kunnen verifiëren dat tokens (`{persoon_1}`, `{rekeningnummer_1}`) in de output staan.

### Verificatiepunten

1. Tekst in `bezwaren-text/` bevat pseudonimiseringstokens i.p.v. originele PII
2. `pseudonimisering_mapping_id` is opgeslagen in de database (niet null, geldig UUID)
3. Taakstatus is `KLAAR`
4. Extractiemethode is correct ingevuld

## Foutafhandeling

| Scenario | Gedrag |
|---|---|
| Obscuro niet bereikbaar | Taak → `MISLUKT`, foutmelding bevat connectie-error |
| Obscuro retourneert HTTP 4xx/5xx | Taak → `MISLUKT`, foutmelding bevat statuscode + body |
| Tekst te lang (>100.000 tekens) | Obscuro retourneert 400, taak → `MISLUKT` |
| Timeout | Taak → `MISLUKT`, foutmelding bevat timeout-info |
