# Full-stack Cypress E2E Test: Pseudonimisering Pipeline

## Doel

Eén Cypress E2E test die de volledige pipeline door de UI valideert:
upload → tekst-extractie + pseudonimisering → bezwaar-extractie → clustering → antwoord opslaan.

## Scope

- **Tekst-extractie + pseudonimisering:** Echte Obscuro service (Testcontainer of docker-compose).
- **Bezwaar-extractie:** AI afgemockt via `FixtureChatModel` (bestaand mechanisme, laadt responses uit fixture-bestanden).
- **Embeddings:** Mock Ollama-server (WireMock container of lichtgewicht stub) die vaste vectoren retourneert.
- **Clustering:** Echte HDBSCAN op mock-embeddings.
- **Antwoorden:** Opslaan via UI.

## Infrastructuur

### Vereisten om de E2E test te draaien

1. `docker compose up -d` (PostgreSQL + Obscuro)
2. Mock Ollama stub starten (of toevoegen aan docker-compose)
3. App starten met dev profiel + FixtureChatModel: `mvn spring-boot:run -pl app -Pdev`
4. `npx cypress run --e2e` (of `npx cypress open --e2e`)

### Cypress configuratie

Toevoegen aan `cypress.config.js`:

```javascript
e2e: {
  baseUrl: 'http://localhost:8080',
  specPattern: 'test/e2e/**/*.spec.js',
  supportFile: 'cypress/support/e2e.js',
  viewportWidth: 1920,
  viewportHeight: 1080,
  defaultCommandTimeout: 30000,  // langere timeouts voor async taken
  videosFolder: './target/cypress/e2e/videos',
  screenshotsFolder: './target/cypress/e2e/screenshots',
}
```

## Testdata

Synthetische fixture-bestanden gebaseerd op stikstofdecreet bezwaarschriften:

- **PDF fixture:** `webapp/test/e2e/fixtures/bezwaar-stikstof-pii.pdf` — Bevat fictieve PII (naam, adres, IBAN).
- **TXT fixture:** `webapp/test/e2e/fixtures/bezwaar-stikstof-pii.txt` — Zelfde inhoud als PDF, plain text.
- **AI fixture responses:** `testdata/e2e-cypress/bezwaren/*.json` — Vooraf gedefinieerde extractie-resultaten.
- **Document tekst:** `testdata/e2e-cypress/documenten/*.txt` — Gepseudonimiseerde tekst (zoals opgeslagen door Obscuro).

## Testflow

```
1. Navigeer naar app → projecten overzicht
2. Upload PDF + TXT via vl-upload component
3. Wacht op WebSocket "tekst-extractie-update" → status "tekst-extractie-klaar"
4. Klik oog-icoon (👁) → verifieer dat tekst GEEN originele PII bevat
5. Selecteer bestanden → klik "Verwerking starten"
6. Wacht op WebSocket "taak-update" → status "extractie-klaar"
7. Navigeer naar "Kernbezwaren" tab
8. Klik "Clustering starten"
9. Wacht op WebSocket "clustering-update" → status "KLAAR"
10. Verifieer dat kernbezwaren verschijnen in de lijst
11. Klik op een kernbezwaar → schrijf antwoord → sla op
12. Verifieer dat antwoord is opgeslagen
```

## Verificatiepunten

| Stap | Wat wordt geverifieerd |
|------|----------------------|
| 3 | Tekst-extractie slaagt (status KLAAR) |
| 4 | PII is vervangen door tokens (geen namen, IBAN in tekst) |
| 6 | Bezwaar-extractie slaagt (AI fixture response verwerkt) |
| 9 | Clustering voltooid (kernbezwaren aangemaakt) |
| 12 | Antwoord opgeslagen en zichtbaar |

## Mock Ollama

Lichtgewicht Express.js of Python stub die:
- `POST /api/embeddings` accepteert
- Vaste 1024-dimensionale vector retourneert: `{ "embedding": [0.1, 0.2, ..., 0.1] }`
- Geen machine learning nodig — enkel deterministische responses

Alternatief: WireMock container met stubs.

## Beperkingen

- **Consolidatie (brieven generatie):** Out of scope — vereist extra AI-calls en PDF-generatie.
- **De-pseudonimisering:** Out of scope (toekomstige feature).
- **Multi-user scenario's:** Niet getest.
- **Foutscenario's:** Enkel happy path. Foutpaden worden al gedekt door unit/integratietests.

## Bestandsstructuur

```
webapp/
  cypress/
    support/
      e2e.js                                    # E2E support file
  test/
    e2e/
      fixtures/
        bezwaar-stikstof-pii.pdf               # Synthetische PDF met PII
        bezwaar-stikstof-pii.txt               # Synthetische TXT met PII
      pseudonimisering-volledige-pipeline.spec.js  # De E2E test
testdata/
  e2e-cypress/
    documenten/
      bezwaar-stikstof-pii.txt                 # Gepseudonimiseerde versie
    bezwaren/
      bezwaar-stikstof-pii.json                # Fixture AI response
```
