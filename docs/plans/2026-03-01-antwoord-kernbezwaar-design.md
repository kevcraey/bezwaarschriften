# Design: Antwoord op kernbezwaar

**Datum:** 2026-03-01
**Status:** Goedgekeurd

## Doel

Ambtenaren moeten per kernbezwaar een formeel weerwoord kunnen invoeren dat uiteindelijk in het beslissingsdocument terechtkomt.

## UI-ontwerp

### Knop-layout per kernbezwaar

Naast de bestaande vergrootglas-knop komt een pennetje-knop:

```
[Samenvatting van het kernbezwaar]          [🔍 (42)] [✏️]
```

### Indicator bij opgeslagen antwoord

Wanneer een antwoord is opgeslagen, wordt een visuele indicator getoond op het pennetje (vinkje of kleurwijziging). Onder de samenvatting verschijnt een preview van het antwoord (eerste ~2 regels).

```
[Samenvatting van het kernbezwaar]          [🔍 (42)] [✏️ ✓]
  ┌─ Antwoord ──────────────────────────┐
  │ Eerste ~2 regels van het antwoord...│
  └─────────────────────────────────────┘
```

### Editor (inline)

Klik op pennetje opent `vl-textarea-rich` inline onder het kernbezwaar:

```
[Samenvatting van het kernbezwaar]          [🔍 (42)] [✕]
  ┌─────────────────────────────────────┐
  │ [B] [I] [U] [S] [⟲] [⟳] | ...     │
  │                                     │
  │ Rich text editor                    │
  │                                     │
  └─────────────────────────────────────┘
  [Opslaan]
```

### Icoon-flow

- **Geen editor open:** ✏️ (pennetje) — klik opent editor
- **Editor open:** ✕ (kruisje) — klik sluit editor
- **Na opslaan:** editor blijft open, pennetje is X
- **Onopgeslagen wijzigingen + klik op X:** confirm-dialoog

### Opslaan

Expliciete "Opslaan"-knop onder de editor. Na opslaan blijft de editor open.

## Rich text editor

Gebruik `vl-textarea-rich` uit `@domg-wc/components` (reeds in dependencies). Dit is een TinyMCE-wrapper met Vlaams design systeem styling, Nederlandse taal.

## Datamodel

### Nieuw domein-record

```java
public record KernbezwaarAntwoord(Long kernbezwaarId, String inhoud) {}
```

### Uitbreiding Kernbezwaar record

```java
public record Kernbezwaar(Long id, String samenvatting,
    List<IndividueelBezwaarReferentie> individueleBezwaren,
    String antwoord) {}  // nullable, HTML of null
```

### JPA Entity

```java
@Entity
@Table(name = "kernbezwaar_antwoord")
public class KernbezwaarAntwoordEntiteit {
    @Id
    private Long kernbezwaarId;

    @Column(columnDefinition = "text", nullable = false)
    private String inhoud;

    private Instant bijgewerktOp;
}
```

### Liquibase migratie

Nieuwe tabel `kernbezwaar_antwoord`:

| Kolom | Type | Constraint |
|-------|------|------------|
| `kernbezwaar_id` | bigint | PK |
| `inhoud` | text | NOT NULL |
| `bijgewerkt_op` | timestamp with time zone | NOT NULL |

## REST API

### Opslaan/bijwerken antwoord

```
PUT /api/v1/projects/{naam}/kernbezwaren/{id}/antwoord
```

Request body:
```json
{ "inhoud": "<p>Het weerwoord...</p>" }
```

Response: `200 OK` met het opgeslagen `KernbezwaarAntwoord`.

### Kernbezwaren ophalen (uitbreiding)

```
GET /api/v1/projects/{naam}/kernbezwaren
```

Bestaand endpoint, uitgebreid: het `Kernbezwaar` record bevat nu het optionele `antwoord`-veld.

## Architectuur

```
Frontend (vl-textarea-rich)
    │
    ▼  PUT /api/v1/projects/{naam}/kernbezwaren/{id}/antwoord
KernbezwaarController
    │
    ▼
KernbezwaarService
    │  ├── slaAntwoordOp(projectNaam, kernbezwaarId, inhoud)
    │  └── haalKernbezwarenOp(projectNaam)  ← uitgebreid met antwoorden
    │
    ▼
KernbezwaarAntwoordRepository (Spring Data JPA)
    │
    ▼
PostgreSQL: kernbezwaar_antwoord tabel
```

## Foutafhandeling

- PUT faalt (netwerk/server): foutmelding via `vl-alert`, editor blijft open met inhoud intact
- Kernbezwaar-ID niet gevonden: `404 Not Found`
- Lege inhoud: frontend-validatie via `required` attribuut

## Scope-afbakening (YAGNI)

- Geen versiegeschiedenis van antwoorden
- Geen multi-user concurrent editing
- Geen concept/draft status
- Geen AI-suggesties voor antwoorden
