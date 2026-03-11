# Documentatie

Deze gids definieert de standaard voor projectdocumentatie. Het doel is een "Lean & Mean" documentatiestructuur die dicht bij de code leeft.

Als de bestaande documentatie niet voldoet aan onderstaande spec, **ALTIJD** fixen!
---
## Diagrammen (Mermaid Only)

Diagrammen staan inline in de Markdown bestanden. Gebruik geen statische afbeeldingen (png/jpg).

- **C4 Model:** C1 (Context) en C2 (Container) in `/architecture/c4-model.md`.
- **Domeinmodel:** Entiteiten en relaties in `/architecture/domain-model.md`.
- **Workflows:** Gebruik `graph TD` in `/guides/`.
- **States:** Gebruik `stateDiagram-v2` in `/reference/` voor object-statussen.

---
## Mapstructuur `/docs`


| Map             | Inhoud                                  | Structuur         |
| --------------- | --------------------------------------- | ----------------- |
| `/architecture` | `<br />                                 | Mermaid + MD      |
| `               |  | Index + Detail MD |
|                 | Business Rules Index +`/rules`submap    | Index + Detail MD |
| `/guides`       | Workflows, How-to's, Onboarding         | Mermaid + MD      |
|                 | State-diagrammen, API-specs             | Mermaid + MD      |

### `/architecture`

We genereren geen C3 (Component) of C4 (Code) diagrammen, tenzij expliciet gevraagd.

- `c4-c1-systeemcontext.md`
   - Toon alleen de gebruiker, ons systeem en externe afhankelijkheden (API's, banken, etc.). 
- `c4-c2-containers.md`
   - Toon de harde grenzen (Web App, Database, Background Job, API Service). Geen interne klassen of mappen.
- `domain-model.md`

### `/decisions`
ADR, FDR, TDR Indexen +`/records`submap
We werken met een index voor het snelle overzicht en losse bestanden voor de diepgang.

- `decisions/adr-index.md`
- `decisions/fdr-index.md`
- `decisions/tdr-index.md` 

Bevat enkel een overzichtstabel:

| ID      | Titel              | Datum      | Korte samenvatting (1 zin)                  |
| ------- | ------------------ | ---------- | ------------------------------------------- |
| ADR-001 | Keuze voor Mermaid | 2026-03-11 | Diagrammen als code voor onderhoudbaarheid. |

De detailbestanden staan in `/decisions/records/[ID].md` (bijv. `ADR-001.md`).

Gebruik dit minimale format:

- **Context:** Waarom was dit nodig? (Probleemstelling).
- **Besluit:** De gekozen oplossing/actie.
- **Consequentie:** Wat zijn de gevolgen of trade-offs?

### `/business-rules`
Dezelfde logica geldt voor business regels om de kernlogica gescheiden te houden van technische implementatie.

- **Index (`business-rules-index.md`):** Een lijst met `[BR-id] | Naam | De regel in 1 zin`.
- **Detail (`/rules/BR-xxx.md`):** Alleen nodig bij complexe berekeningen of wettelijke uitzonderingen.


### `/guides`
Focus op de "Happy Path" en snelle actie.

1. **Titel:** Actiegericht (bv. "Hoe voeg ik een API-endpoint toe").
2. **Stappen:** Kort, genummerd en technisch concreet.
3. **Check:** Hoe weet de gebruiker dat het resultaat correct is?

### `/reference`
State-diagrammen, API-specs.

---
## Lean Checklist

- **Token-safe:** Is de info gesplitst? (Index vs Detail).
- **No-Epistles:** Is de tekst to-the-point en kan die niet korter zonder informatieverlies?
- **Visual-First:** Is er een Mermaid diagram voor complexe logica?

