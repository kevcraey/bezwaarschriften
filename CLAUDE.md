# Bezwaarschriften

AI-geassisteerde verwerking van bezwaarschriften. Java 21 + Spring Boot 3.x. Hexagonale architectuur.

## Algemene regels

- Om code te lezen gebruik je jcodemunch mcp server
- Gebruik voor implementatie altijd de richtlijnen in `richtlijnen/`, de titels spreken voor zich, lees enkel de inhoud als het relevant is voor de taak.
- We schrijven liever een test teveel dan eentje te weinig.
- We houden qua documentatie altijd een up-to-date C1 en C2 model bij in `docs/` in mermaid.
- Lever nooit een feature op zonder zelf te builden en alle testen te passen. Kan mij niet schelen dat de failures er al waren, eerst fixen, dan aanbieden.
- Een belangrijk deel van een implementatieplan is het schrijven van een test- en verificatieplan.
- We schrijven leesbare code: ik ben geen ontwikkelaar, maar kan de code wel lezen als die goed gestructureerd is en geschreven is vanuit de functionele vereisten eerst.
- Werk altijd in een worktree tijdens implementatie, voor de validatie verwijder je de worktree en check je de feature branch uit.
- Nooit direct op main werken, altijd feature branches maken. Na merge met main ruim je de branch op.
- Na oplevering van een feature test ik altijd: eerst worktree verwijderen, dan checkout van feature branch, dan aanbieden om te testen.

## Documentatie

- C4 C1 + C2 up-to-date in `docs/`
- domeinmodel up-to-date in `docs/`
- diagrammen maken we in mermaid

## Code review

- Gebruik altijd de code-review skill en fix de opmerkingen.

## Front-end ontwikkeling

- storybook van het design system: https://flux.omgeving.vlaanderen.be
- repo van het design system: https://github.com/milieuinfo/flux-web-components
- We schrijven ook front-end testen, niet alleen back-end. Zie richtlijnen voor details.
- We schrijven front-end testen met Cypress.
- Gebruik voor de front-end enkel componenten uit de component library (@domg-wc). Wijk enkel op expliciete vraag van deze regel af.
- `vl-button`: boolean attributes, NIET `type="..."`. Error-stijl: `<vl-button error="">`.
- `vl-button` tekst: via child content (`btn.textContent`), NIET via `label` attribuut.
- `vl-tabs`: gebruik `disable-links` attribuut bij hash-routing.
- `vl-rich-data-table`: geen `vl-search-filter` slot voor client-side filtering (toont "0 resultaten").
- Documentatie: `richtlijnen/frontend.md`
- BELANGRIJK: iedere keer als je CSS schrijft, vraag u af: is er geen standaard manier om dat te doen in de webcomponentenbibliotheek? Custom CSS zou heel uitzonderlijk moeten zijn.

## Testen

- als je testen moet schrijven, zo weinig mogelijk afmocken, komen teveel bugs door op die manier.

## Gotchas

- Technical debt bijhouden in `specs/technical-debt.md`. Wees zeer summier in uw omschrijvingen.
- Testcontainers integratietests vereisen een draaiende Docker daemon.

## Commands

### Backend

```bash
mvn clean install -DskipTests          # build zonder tests
mvn test -pl app                        # unit tests
mvn verify -pl app                      # integratietests (Testcontainers, vereist Docker)
mvn spring-boot:run -pl app -Pdev      # lokaal draaien (dev profiel)
```

### Frontend (`webapp/`)

```bash
npm install                             # dependencies
npm run build                           # lint + webpack
npm run start                           # dev server op :9000
npm test                                # Cypress component tests
npm run format:fix                      # eslint auto-fix
```

**Na `npm run build`:** ook `mvn process-resources -pl webapp -Denforcer.skip=true` draaien zodat `target/classes` bijwerkt (Spring Boot serveert daaruit).

## Architectuur

```
app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/
├── project/      # ingestie, extractie, upload, WebSocket
├── clustering/   # HDBSCAN clustering van bezwaren
├── kernbezwaar/  # kernbezwaren + antwoorden
├── consolidatie/ # validatie + brieven generatie
├── ingestie/     # bestandsverwerking
├── domain/       # domeinmodel (IndividueelBezwaar, etc.)
└── config/       # Spring configuratie

webapp/src/js/    # Lit web components (@domg-wc)
specs/            # user stories, QA-plannen, FA's per ticket
docs/             # C4 architectuurdocumentatie (c4-c1-*.md, c4-c2-*.md)
richtlijnen/      # codestijl + architectuurrichtlijnen (ALTIJD eerst lezen!)
```

## Lokale omgeving

Vereisten: Docker, Java 21, Node.js, Maven.

```bash
docker compose up -d    # PostgreSQL + pgvector
```

Dev-gebruikers (via `cumuli.security.enable.mock=true`):

- `gebruiker` / `gebruiker` → rol: BezwaarschriftenGebruiker

Input-bestanden plaatsen in `input/` (geconfigureerd via `bezwaarschriften.input.folder`).
