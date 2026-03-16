import {html} from 'lit';
import '../src/js/bezwaarschriften-tekst-panel';

describe('bezwaarschriften-tekst-panel', () => {
  beforeEach(() => {
    cy.mount(html`<bezwaarschriften-tekst-panel></bezwaarschriften-tekst-panel>`);
  });

  it('is initieel gesloten', () => {
    cy.get('bezwaarschriften-tekst-panel')
        .its(0)
        .then((el) => {
          expect(el.isOpen).to.be.false;
        });

    cy.get('bezwaarschriften-tekst-panel')
        .shadow()
        .find('vl-side-sheet#tekst-side-sheet')
        .should('not.have.attr', 'open');
  });

  it('opent panel met bestandsnaam bij aanroep van open()', () => {
    cy.intercept('GET', '/api/v1/projects/testproject/tekst-extracties/bezwaar.pdf/tekst', {
      statusCode: 200,
      body: {bestandsnaam: 'bezwaar.pdf', tekst: 'De aanvrager stelt dat...'},
    });

    cy.get('bezwaarschriften-tekst-panel')
        .its(0)
        .then((el) => {
          el.open('testproject', 'bezwaar.pdf');
        });

    cy.get('bezwaarschriften-tekst-panel')
        .shadow()
        .find('vl-side-sheet#tekst-side-sheet')
        .should('have.attr', 'open');

    cy.get('bezwaarschriften-tekst-panel')
        .shadow()
        .find('h2')
        .should('contain.text', 'bezwaar.pdf');

    cy.get('bezwaarschriften-tekst-panel')
        .shadow()
        .find('.side-sheet-body')
        .should('contain.text', 'De aanvrager stelt dat...');
  });

  it('sluit panel bij aanroep van sluit()', () => {
    cy.intercept('GET', '/api/v1/projects/testproject/tekst-extracties/bezwaar.pdf/tekst', {
      statusCode: 200,
      body: {bestandsnaam: 'bezwaar.pdf', tekst: 'Tekst'},
    });

    cy.get('bezwaarschriften-tekst-panel')
        .its(0)
        .then((el) => {
          el.open('testproject', 'bezwaar.pdf');
        });

    cy.get('bezwaarschriften-tekst-panel')
        .shadow()
        .find('vl-side-sheet#tekst-side-sheet')
        .should('have.attr', 'open');

    cy.get('bezwaarschriften-tekst-panel')
        .its(0)
        .then((el) => {
          el.sluit();
        });

    cy.get('bezwaarschriften-tekst-panel')
        .shadow()
        .find('vl-side-sheet#tekst-side-sheet')
        .should('not.have.attr', 'open');
  });

  it('toont foutmelding als API-call faalt', () => {
    cy.intercept('GET', '/api/v1/projects/testproject/tekst-extracties/bezwaar.pdf/tekst', {
      statusCode: 500,
      body: 'Server error',
    });

    cy.get('bezwaarschriften-tekst-panel')
        .its(0)
        .then((el) => {
          el.open('testproject', 'bezwaar.pdf');
        });

    cy.get('bezwaarschriften-tekst-panel')
        .shadow()
        .find('vl-alert[type="error"]')
        .should('exist');
  });

  it('dispatcht tekst-panel-geopend event bij open()', () => {
    cy.intercept('GET', '/api/v1/projects/testproject/tekst-extracties/bezwaar.pdf/tekst', {
      statusCode: 200,
      body: {bestandsnaam: 'bezwaar.pdf', tekst: 'Tekst'},
    });

    cy.get('bezwaarschriften-tekst-panel')
        .its(0)
        .then((el) => {
          const eventPromise = new Promise((resolve) => {
            el.addEventListener('tekst-panel-geopend', () => resolve(true));
          });
          el.open('testproject', 'bezwaar.pdf');
          return eventPromise;
        })
        .should('be.true');
  });

  it('dispatcht tekst-panel-gesloten event bij sluit()', () => {
    cy.intercept('GET', '/api/v1/projects/testproject/tekst-extracties/bezwaar.pdf/tekst', {
      statusCode: 200,
      body: {bestandsnaam: 'bezwaar.pdf', tekst: 'Tekst'},
    });

    cy.get('bezwaarschriften-tekst-panel')
        .its(0)
        .then((el) => {
          el.open('testproject', 'bezwaar.pdf');
        });

    cy.get('bezwaarschriften-tekst-panel')
        .its(0)
        .then((el) => {
          const eventPromise = new Promise((resolve) => {
            el.addEventListener('tekst-panel-gesloten', () => resolve(true));
          });
          el.sluit();
          return eventPromise;
        })
        .should('be.true');
  });
});
