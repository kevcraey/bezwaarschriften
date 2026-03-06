import {html} from 'lit';
import '../src/js/bezwaarschriften-project-selectie';

describe('bezwaarschriften-project-selectie bulk delete', () => {
  beforeEach(() => {
    cy.stub(window, 'WebSocket').returns({
      onmessage: null,
      onopen: null,
      onclose: null,
      onerror: null,
      close: cy.stub(),
      send: cy.stub(),
    });

    cy.intercept('GET', '/api/v1/projects/*/bezwaren', {
      statusCode: 200,
      body: {bezwaren: []},
    }).as('bezwaren');

    cy.intercept('GET', '/api/v1/projects/*/extracties', {
      statusCode: 200,
      body: {taken: []},
    }).as('extracties');

    cy.intercept('GET', '/api/v1/projects/*/kernbezwaren', {
      statusCode: 404,
    }).as('kernbezwaren');

    cy.intercept('GET', '/api/v1/projects/*/clustering-taken', {
      statusCode: 200,
      body: {categorieen: []},
    }).as('clusteringTaken');

    cy.intercept('GET', '/api/v1/projects/*/consolidaties', {
      statusCode: 200,
      body: {documenten: []},
    }).as('consolidaties');

    cy.mount(html`<bezwaarschriften-project-selectie></bezwaarschriften-project-selectie>`);

    cy.get('bezwaarschriften-project-selectie')
        .then(($el) => {
          $el[0].projectNaam = 'testproject';
        });

    cy.wait('@bezwaren');
  });

  it('stuurt een enkel bulk DELETE verzoek met JSON body', () => {
    cy.intercept('DELETE', '/api/v1/projects/testproject/bezwaren', (req) => {
      expect(req.headers['content-type']).to.include('application/json');
      expect(req.body).to.deep.equal({bestandsnamen: ['bestand1.pdf', 'bestand2.pdf']});
      req.reply({statusCode: 200, body: {aantalVerwijderd: 2}});
    }).as('bulkDelete');

    // Herlaad-intercept na verwijdering
    cy.intercept('GET', '/api/v1/projects/testproject/bezwaren', {
      statusCode: 200,
      body: {bezwaren: []},
    }).as('herlaadBezwaren');

    cy.get('bezwaarschriften-project-selectie')
        .then(($el) => {
          $el[0]._verwijderBestanden(['bestand1.pdf', 'bestand2.pdf']);
        });

    cy.wait('@bulkDelete');
    cy.wait('@herlaadBezwaren');
  });

  it('stuurt geen verzoek als bestandsnamen leeg is', () => {
    let aantalCalls = 0;
    cy.intercept('DELETE', '/api/v1/projects/*/bezwaren', () => {
      aantalCalls++;
    }).as('delete');

    cy.get('bezwaarschriften-project-selectie')
        .then(($el) => {
          $el[0]._verwijderBestanden([]);
          $el[0]._verwijderBestanden(null);
          $el[0]._verwijderBestanden(undefined);
        });

    // Wacht even om te bevestigen dat er geen calls zijn
    // eslint-disable-next-line cypress/no-unnecessary-waiting
    cy.wait(100).then(() => {
      expect(aantalCalls).to.equal(0);
    });
  });

  it('toont foutmelding bij mislukte verwijdering', () => {
    cy.intercept('DELETE', '/api/v1/projects/testproject/bezwaren', {
      statusCode: 500,
      body: {message: 'Server error'},
    }).as('bulkDeleteFout');

    cy.get('bezwaarschriften-project-selectie')
        .then(($el) => {
          $el[0]._verwijderBestanden(['bestand1.pdf']);
        });

    cy.wait('@bulkDeleteFout');

    cy.get('bezwaarschriften-project-selectie')
        .then(($el) => {
          const foutEl = $el[0].shadowRoot.querySelector('#fout-melding');
          expect(foutEl).to.not.be.null;
          expect(foutEl.hidden).to.be.false;
          expect(foutEl.textContent).to.contain('Verwijdering mislukt');
        });
  });
});
