# Design: Projecten landing page

## Doel

In plaats van meteen op de projectdetailpagina uit te komen, komt de gebruiker eerst op een landingspagina met een overzichtstabel van alle projecten. Van daaruit kan de gebruiker:

- Een project **toevoegen** (modal met naamveld)
- Een project **verwijderen** (bevestigingsmodal met waarschuwing)
- **Doorklikken** naar een project (huidige detailpagina)

## Routing

Hash-based routing in `bezwaarschriften-landingspagina`, naar het patroon van pasberekening (`pas-hash-routing`):

| Hash | Component | Beschrijving |
|------|-----------|--------------|
| `#/` of leeg | `bezwaarschriften-projecten-overzicht` | Overzichtstabel van projecten |
| `#/project/{naam}` | `bezwaarschriften-project-selectie` | Detailpagina van een project |

De landingspagina luistert op `window.hashchange` en rendert het juiste component. De `<vl-template>` (header, banner) blijft in de landingspagina. Titel en introductietekst zijn dynamisch:

- **Overzicht**: "Bezwaarschriften" + welkomsttekst
- **Detail**: projectnaam als `<h1>`, geen welkomsttekst

## Nieuw component: `bezwaarschriften-projecten-overzicht`

### Tabel

`vl-rich-data-table` met twee kolommen:

| Kolom | Inhoud |
|-------|--------|
| Naam | Klikbare link (`<a href="#/project/{naam}">`) |
| Aantal documenten | Getal |

### Actieknoppen

Boven de tabel:

- **"Project toevoegen"** — opent modal met tekstveld voor projectnaam
- **"Project verwijderen"** — actief bij geselecteerde rij, opent bevestigingsmodal die toont hoeveel documenten verloren gaan

### Modals

**Toevoegen-modal:**
- Titel: "Project toevoegen"
- Veld: projectnaam (verplicht)
- Knop: "Toevoegen"
- Validatie: niet-lege naam

**Verwijder-modal:**
- Titel: "Project verwijderen"
- Tekst: "Weet je zeker dat je project '{naam}' wilt verwijderen? {N} document(en) en bijhorende extractie-resultaten worden permanent verwijderd."
- Knop: "Verwijderen" (error-stijl)

## Refactoring: `bezwaarschriften-project-selectie`

Huidige situatie: component bevat een `<vl-select>` dropdown voor projectselectie en laadt zelf de projectenlijst.

Nieuwe situatie:
- **Weg**: `<vl-select>` dropdown, `_laadProjecten()`, selectie event handling
- **Nieuw**: property `projectNaam` die van buitenaf gezet wordt door de router
- `connectedCallback` laadt direct de bezwaren voor `this.projectNaam`
- Navigatie terug naar overzicht via breadcrumb of "Terug"-link

## Backend API-uitbreiding

### Nieuwe endpoints

| Method | Endpoint | Body | Response | Doel |
|--------|----------|------|----------|------|
| `POST` | `/api/v1/projects` | `{"naam": "..."}` | `201 Created` | Project aanmaken |
| `DELETE` | `/api/v1/projects/{naam}` | — | `204 No Content` | Project + alle data verwijderen |

### Aangepast endpoint

`GET /api/v1/projects` retourneert nu objecten met documentaantal:

```json
{
  "projecten": [
    {"naam": "project-zultewaregem", "aantalDocumenten": 42},
    {"naam": "project-gent", "aantalDocumenten": 7}
  ]
}
```

### ProjectPoort uitbreiding

Nieuwe methoden op de port interface:

- `void maakProjectAan(String naam)` — maakt projectmap + bezwaren-submap aan
- `boolean verwijderProject(String naam)` — verwijdert hele projectmap recursief

### ProjectService uitbreiding

- `maakProjectAan(String naam)` — delegeert naar `ProjectPoort`
- `verwijderProject(String naam)` — verwijdert eerst alle `ExtractieTaak`-records uit de database, daarna de map via `ProjectPoort`

## Technische details

- Routing: `window.addEventListener('hashchange', ...)` + `window.location.hash`
- Navigatie: `<a href="#/project/{naam}">` voor doorklikken, `<a href="#/">` voor terug
- Component communicatie: properties (data flows down)
- Geen externe routing library nodig
