# Bezwaarschriften

AI-geassisteerde verwerking van bezwaarschriften. Java 21 + Spring Boot 3.x. Hexagonale architectuur.

## Algemene regels

- Om code te lezen gebruik je jcodemunch mcp server
  - jcodemunch get_file_content: parameter heet file_path, NIET path. Fout: path: "...". Correct: file_path: "...".
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

Alle documentatie staat in `docs/`

## Code review

- Gebruik altijd de decibel-code-review skill en fix de opmerkingen.

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

- Als je testen moet schrijven, zo weinig mogelijk afmocken, komen teveel bugs door op die manier.
- Doel van de testen is het beschermen van de functionaliteit, alle belangrijke functionaliteit is afgetest.
- Als je een busines regel hebt moeten wijzigen en er is geen test bijgeschreven of aangepast, dan zijn er testen te weinig.

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
npm run test:e2e                        # Cypress E2E tests
npm run format:fix                      # eslint auto-fix
```

### E2E tests (`webapp/`)

Vereisten: Spring Boot draaiend op :8080, Docker (PostgreSQL + Obscuro), mock Ollama.

```bash
docker compose up -d                                        # PostgreSQL + Obscuro
node webapp/test/e2e/mock-ollama.js &                       # mock Ollama (port 11434)
mvn spring-boot:run -pl app -Dspring.profiles.active=dev,e2e -Denforcer.skip=true -Dcheckstyle.skip=true  # backend met e2e profiel
cd webapp && npm run test:e2e                               # E2E tests draaien
```

Het `e2e` profiel (`application-e2e.yml`) verlaagt clustering-drempels en wijst testdata naar `webapp/test/e2e/testdata/`.

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
docker compose up -d    # PostgreSQL + pgvector + Obscuro
```

Dev-gebruikers (via `cumuli.security.enable.mock=true`):

- `gebruiker` / `gebruiker` → rol: BezwaarschriftenGebruiker

Input-bestanden plaatsen in `input/` (geconfigureerd via `bezwaarschriften.input.folder`).
