# Design: Manueel bezwaar toevoegen

**Datum:** 2026-03-02
**Status:** Goedgekeurd

## Samenvatting

Gebruikers kunnen in het bezwaar-extractie-side-panel manueel een bezwaar toevoegen met een samenvatting en een passage. De passage moet exact voorkomen in het originele document (na normalisatie). Manuele bezwaren zijn persistent in de database en kunnen verwijderd worden.

## Aanpak

**Optie A (gekozen):** Uitbreiding bestaande `geextraheerd_bezwaar` entiteit met een `manueel` boolean vlag. Geen aparte tabel — manuele en AI-bezwaren leven samen.

## Database-wijzigingen

### Tabel `geextraheerd_bezwaar`

| Kolom | Type | Default | Doel |
|-------|------|---------|------|
| `manueel` | boolean | false | Onderscheidt manueel vs AI-geextraheerd |

### Tabel `extractie_taak`

| Kolom | Type | Default | Doel |
|-------|------|---------|------|
| `heeft_manueel` | boolean | false | Stuurt ✍️ emoji aan in de tabel |

## API endpoints

### POST `/api/v1/projects/{naam}/extracties/{bestandsnaam}/bezwaren`

Voegt een manueel bezwaar toe.

**Request body:**
```json
{
  "samenvatting": "De geluidsnormen worden overschreden",
  "passage": "exacte tekst uit het document"
}
```

**Validatie (server-side):**
1. Normaliseer passage en documenttekst (whitespace → single space, lowercase, trim)
2. Check of genormaliseerde passage voorkomt in genormaliseerde documenttekst
3. Niet gevonden → 400 Bad Request met foutmelding
4. Gevonden → opslaan met `manueel=true`, `passageGevonden=true`

**Succes:** 201 Created met het aangemaakte bezwaar.

### DELETE `/api/v1/projects/{naam}/extracties/{bestandsnaam}/bezwaren/{id}`

Verwijdert een manueel bezwaar.

- Alleen bezwaren met `manueel=true` mogen verwijderd worden
- AI-bezwaar → 403 Forbidden

**Succes:** 204 No Content.

## DTO-uitbreidingen

### `ExtractieDetailDto.BezwaarDetail`

Nieuwe velden:
- `id` (Long) — nodig voor DELETE endpoint
- `manueel` (boolean) — stuurt frontend-rendering aan

### `BezwaarBestand` (tabel-response)

Nieuw veld:
- `heeftManueel` (boolean) — stuurt ✍️ emoji aan

## Frontend

### Side-panel header

- Ghost `vl-button-icon` met `icon="add"` naast het sluitkruisje
- Alleen zichtbaar bij status `extractie-klaar`

### Inline toevoeg-formulier

Bij klik op `+`:
1. Formulier verschijnt **bovenaan** de bezwarenlijst
2. Twee velden:
   - **Samenvatting** — `vl-textarea`
   - **Passage** — `vl-textarea`
3. Twee knoppen:
   - **Opslaan** — primaire `vl-button`
   - **Annuleren** — ghost `vl-button-icon` met `icon="close"`
4. Validatiefout: inline foutmelding onder passage-veld

### Bezwarenlijst

- Manuele bezwaren: `vl-button-icon` met `icon="bin"` (error variant) voor verwijderen
- Subtiel "Manueel" label boven de samenvatting van manuele bezwaren
- Direct verwijderen zonder bevestigingsdialog

### Tabel

- `heeftManueel === true`: ✍️ emoji vóór bestandsnaam (naast eventuele ⚠️)
- Tooltip: "Bevat manueel toegevoegde bezwaren"

### Data-flow na opslaan

1. POST → validatie → opslaan
2. Succes: formulier verdwijnt, bezwarenlijst herlaadt (GET details)
3. Tabeldata ook herladen (voor ✍️ en aantalBezwaren update)

## Foutafhandeling

| Scenario | Gedrag |
|----------|--------|
| Passage niet gevonden | 400 → inline foutmelding "Passage komt niet voor in het originele document" |
| Lege samenvatting/passage | Opslaan-knop disabled |
| Netwerk-fout | Generieke foutmelding "Opslaan mislukt, probeer opnieuw" |
| Herverwerking extractie | Alle bezwaren (incl. manuele) worden vervangen — bestaand gedrag |

## Testplan

### Backend tests

- POST bezwaar met geldige passage → 201, opgeslagen met `manueel=true`
- POST bezwaar met ongeldige passage → 400 met foutmelding
- POST bezwaar met lege velden → 400
- DELETE manueel bezwaar → 204
- DELETE AI-bezwaar → 403
- GET details bevat `manueel` vlag en `id` per bezwaar
- `heeftManueel` correct gezet op extractietaak

### Frontend Cypress tests

- `+` knop zichtbaar bij status `extractie-klaar`
- Formulier verschijnt bovenaan na klik op `+`
- Opslaan disabled bij lege velden
- Succesvolle opslag → formulier verdwijnt, bezwaar in lijst
- Foutmelding bij ongeldige passage
- Verwijder-knop alleen bij manuele bezwaren
- Verwijderen → bezwaar verdwijnt uit lijst
- ✍️ emoji in tabel bij `heeftManueel=true`
- Annuleren → formulier verdwijnt
