import {html} from 'lit';
import '../src/js/bezwaarschriften-project-selectie';

describe('bezwaarschriften-project-selectie kernbezwaren tab titel', () => {
  beforeEach(() => {
    // Stub WebSocket zodat de component geen verbinding probeert te maken
    cy.stub(window, 'WebSocket').returns({
      onmessage: null,
      onopen: null,
      onclose: null,
      onerror: null,
      close: cy.stub(),
      send: cy.stub(),
    });

    // Intercept API calls die automatisch afgevuurd worden
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

    // Stel project in om de component te activeren
    cy.get('bezwaarschriften-project-selectie')
        .then(($el) => {
          $el[0].projectNaam = 'testproject';
        });

    cy.wait('@bezwaren');
  });

  it('reset tab titel naar "Kernbezwaren" als totaal 0 wordt', () => {
    // Eerst: stel een teller in via antwoord-voortgang event met totaal > 0
    cy.get('bezwaarschriften-project-selectie')
        .then(($el) => {
          $el[0].shadowRoot.dispatchEvent(new CustomEvent('antwoord-voortgang', {
            bubbles: true,
            detail: {aantalMetAntwoord: 2, totaal: 5},
          }));
        });

    // Controleer dat de teller zichtbaar is
    cy.get('bezwaarschriften-project-selectie')
        .find('vl-tabs')
        .shadow()
        .find('slot[name="kernbezwaren-title-slot"]')
        .should('contain.text', '2/5');

    // Dispatch event met totaal = 0 (alle documenten verwijderd)
    cy.get('bezwaarschriften-project-selectie')
        .then(($el) => {
          $el[0].shadowRoot.dispatchEvent(new CustomEvent('antwoord-voortgang', {
            bubbles: true,
            detail: {aantalMetAntwoord: 0, totaal: 0},
          }));
        });

    // Tab titel moet gereset zijn naar enkel "Kernbezwaren" zonder teller
    cy.get('bezwaarschriften-project-selectie')
        .find('vl-tabs')
        .shadow()
        .find('slot[name="kernbezwaren-title-slot"]')
        .should('have.text', 'Kernbezwaren')
        .and('not.contain.text', '/');
  });

  it('toont groene teller als alle kernbezwaren beantwoord zijn', () => {
    cy.get('bezwaarschriften-project-selectie')
        .then(($el) => {
          $el[0].shadowRoot.dispatchEvent(new CustomEvent('antwoord-voortgang', {
            bubbles: true,
            detail: {aantalMetAntwoord: 5, totaal: 5},
          }));
        });

    cy.get('bezwaarschriften-project-selectie')
        .find('vl-tabs')
        .shadow()
        .find('slot[name="kernbezwaren-title-slot"]')
        .should('contain.text', '5/5')
        .and('have.css', 'color', 'rgb(14, 124, 58)');
  });
});
