/**
 * E2E test: volledige pipeline met pseudonimisering.
 *
 * Vereisten:
 * - Spring Boot draait op :8080 met profiel dev,e2e
 * - Docker: PostgreSQL + Obscuro (docker compose up -d)
 * - Mock Ollama: node webapp/test/e2e/mock-ollama.js (port 11434)
 *
 * Test flow:
 * 1. Upload bezwaarschrift met PII (PDF + TXT)
 * 2. Tekst-extractie + pseudonimisering via Obscuro
 * 3. Bezwaar-extractie (MockExtractieVerwerker, dev profiel)
 * 4. Clustering via mock Ollama embeddings
 * 5. Verificatie: PII niet zichtbaar, kernbezwaren aanwezig
 */

const PROJECT_NAAM = 'e2e-cypress';
const PII_TOKENS = [
  'Jan Peeters',
  'Karel Van Damme',
  'Kerkstraat 45',
  'Dorpsstraat 12',
  '9000 Gent',
  '9100 Sint-Niklaas',
  'BE68 5390 0754 7034',
  '0478 12 34 56',
  'jan.peeters@email.be',
];

describe('Pseudonimisering volledige pipeline', () => {
  before(() => {
    // Verwijder eventueel bestaand e2e-cypress project data
    cy.request({
      method: 'GET',
      url: `/api/v1/projects/${PROJECT_NAAM}/bezwaren`,
      failOnStatusCode: false,
    }).then((response) => {
      if (response.status === 200 && response.body.bezwaren && response.body.bezwaren.length > 0) {
        const bestandsnamen = response.body.bezwaren.map((b) => b.bestandsnaam);
        cy.request({
          method: 'DELETE',
          url: `/api/v1/projects/${PROJECT_NAAM}/bezwaren`,
          body: {bestandsnamen},
          headers: {'Content-Type': 'application/json'},
          failOnStatusCode: false,
        });
      }
    });
  });

  it('verwerkt bezwaarschrift met PII door volledige pipeline', () => {
    // --- Stap 1: Navigeer naar project ---
    cy.visit(`/#project/${PROJECT_NAAM}`);
    cy.get('bezwaarschriften-project-selectie', {timeout: 10000}).should('exist');

    // --- Stap 2: Upload bestanden ---
    cy.get('#toevoegen-knop').click();
    cy.get('#upload-modal').should('be.visible');

    // Selecteer bestanden via hidden file input in vl-upload
    cy.get('#bestand-upload')
        .find('input[type="file"]')
        .selectFile([
          'test/e2e/fixtures/bezwaar-stikstof-pii.pdf',
          'test/e2e/fixtures/bezwaar-stikstof-pii.txt',
        ], {force: true});

    cy.get('#upload-verzend-knop').click();

    // Wacht tot upload bevestigd is (modal sluit)
    cy.get('#upload-modal', {timeout: 15000}).should('not.be.visible');

    // --- Stap 3: Wacht op tekst-extractie ---
    // Na upload krijgen bestanden status 'tekst-extractie-wachtend',
    // dan 'tekst-extractie-bezig', dan 'tekst-extractie-klaar'.
    // Wacht tot alle documenten tekst-extractie-klaar of later zijn.
    wachtOpStatus(['tekst-extractie-klaar', 'todo', 'extractie-klaar'], 2, 120000);

    // --- Stap 4: Start bezwaar-extractie ---
    // Na tekst-extractie worden bestanden 'tekst-extractie-klaar' of 'todo'.
    // Klik op "Verwerken" om extractie te starten.
    cy.get('#verwerken-knop', {timeout: 10000})
        .should('be.visible')
        .click();

    // --- Stap 5: Wacht op extractie-klaar ---
    wachtOpStatus(['extractie-klaar'], 2, 120000);

    // --- Stap 6: Verifieer PII is niet zichtbaar ---
    // Controleer dat geen van de PII-tokens op de pagina staat.
    cy.get('bezwaarschriften-project-selectie').then(($el) => {
      const pageText = $el[0].shadowRoot.textContent;
      PII_TOKENS.forEach((token) => {
        expect(pageText).not.to.include(token);
      });
    });

    // --- Stap 7: Ga naar Kernbezwaren tab ---
    cy.get('vl-tabs').shadow().find('button, a').contains('Kernbezwaren').click();

    // Wacht tot kernbezwaren-component geladen is
    cy.get('#kernbezwaren-component', {timeout: 10000}).should('exist');

    // --- Stap 8: Start clustering ---
    cy.get('#kernbezwaren-component')
        .find('#groepeer-knop', {timeout: 15000})
        .should('be.visible')
        .click();

    // --- Stap 9: Wacht op clustering resultaat ---
    // Clustering toont eerst een loading state, daarna de kernbezwaren.
    // Wacht tot er kernbezwaar-items verschijnen of een success alert.
    cy.get('#kernbezwaren-component', {timeout: 120000})
        .find('vl-alert[type="success"]')
        .should('exist');

    // --- Stap 10: Verifieer kernbezwaren ---
    cy.get('#kernbezwaren-component')
        .find('.kernbezwaar-item')
        .should('have.length.greaterThan', 0);

    // Verifieer ook dat de kernbezwaren geen PII bevatten
    cy.get('#kernbezwaren-component').then(($el) => {
      const kernText = $el[0].shadowRoot.textContent;
      PII_TOKENS.forEach((token) => {
        expect(kernText).not.to.include(token);
      });
    });
  });
});

/**
 * Wacht tot alle documenten in de tabel een van de verwachte statussen hebben.
 * Pollt via de API om de huidige status te controleren.
 */
function wachtOpStatus(verwachteStatussen, aantalDocumenten, timeout) {
  const startTime = Date.now();

  function poll() {
    cy.request({
      url: `/api/v1/projects/${PROJECT_NAAM}/bezwaren`,
      failOnStatusCode: false,
    }).then((response) => {
      if (response.status !== 200 || !response.body.bezwaren) {
        if (Date.now() - startTime < timeout) {
          cy.wait(2000).then(poll);
        } else {
          throw new Error(`Timeout: kon bezwaren niet ophalen na ${timeout}ms`);
        }
        return;
      }

      const bezwaren = response.body.bezwaren;
      const alleKlaar = bezwaren.length >= aantalDocumenten &&
          bezwaren.every((b) => verwachteStatussen.includes(b.status));

      if (alleKlaar) {
        return; // Klaar!
      }

      const heeftFouten = bezwaren.some((b) =>
        b.status === 'fout' || b.status === 'tekst-extractie-mislukt');
      if (heeftFouten) {
        const foutBezwaren = bezwaren.filter((b) =>
          b.status === 'fout' || b.status === 'tekst-extractie-mislukt');
        throw new Error(
            `Verwerking mislukt voor: ${foutBezwaren.map((b) => `${b.bestandsnaam} (${b.status})`).join(', ')}`,
        );
      }

      if (Date.now() - startTime < timeout) {
        cy.wait(2000).then(poll);
      } else {
        const statussen = bezwaren.map((b) => `${b.bestandsnaam}: ${b.status}`).join(', ');
        throw new Error(
            `Timeout na ${timeout}ms. Verwacht: ${verwachteStatussen.join('/')}. Huidig: ${statussen}`,
        );
      }
    });
  }

  poll();
}
