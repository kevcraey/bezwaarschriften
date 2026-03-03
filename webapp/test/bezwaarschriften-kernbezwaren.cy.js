import {html} from 'lit';
import '../src/js/bezwaarschriften-kernbezwaren';

const MOCK_CLUSTERING_TAKEN = {
  categorieen: [
    {
      categorie: 'Mobiliteit', status: 'klaar', taakId: 1, aantalBezwaren: 42,
      aantalKernbezwaren: 3, foutmelding: null,
      aangemaaktOp: '2026-03-03T10:00:00Z',
      verwerkingGestartOp: '2026-03-03T10:00:01Z',
      verwerkingVoltooidOp: '2026-03-03T10:00:15Z',
    },
    {
      categorie: 'Milieu', status: 'todo', taakId: null, aantalBezwaren: 18,
      aantalKernbezwaren: null, foutmelding: null,
    },
    {
      categorie: 'Geluid', status: 'bezig', taakId: 3, aantalBezwaren: 25,
      aantalKernbezwaren: null, foutmelding: null,
      aangemaaktOp: '2026-03-03T10:01:00Z',
      verwerkingGestartOp: '2026-03-03T10:01:02Z',
    },
    {
      categorie: 'Natuur', status: 'fout', taakId: 4, aantalBezwaren: 6,
      aantalKernbezwaren: null, foutmelding: 'Timeout bij clustering',
      aangemaaktOp: '2026-03-03T10:00:00Z',
      verwerkingGestartOp: '2026-03-03T10:00:01Z',
      verwerkingVoltooidOp: '2026-03-03T10:00:03Z',
    },
  ],
};

describe('bezwaarschriften-kernbezwaren clustering per categorie', () => {
  beforeEach(() => {
    cy.intercept('GET', '/api/v1/projects/*/kernbezwaren', {
      statusCode: 404,
    }).as('kernbezwaren');

    cy.intercept('GET', '/api/v1/projects/*/clustering-taken', {
      statusCode: 200,
      body: MOCK_CLUSTERING_TAKEN,
    }).as('clusteringTaken');

    cy.mount(html`<bezwaarschriften-kernbezwaren></bezwaarschriften-kernbezwaren>`);
  });

  it('toont success-pill voor klare clustering', () => {
    cy.get('bezwaarschriften-kernbezwaren')
        .then(($el) => $el[0].laadClusteringTaken('testproject'));

    cy.wait('@clusteringTaken');

    cy.get('bezwaarschriften-kernbezwaren')
        .find('.categorie-wrapper[data-categorie="Mobiliteit"] vl-pill')
        .should('have.attr', 'type', 'success')
        .and('contain.text', 'Klaar');
  });

  it('toont \'Te clusteren\' voor todo categorie', () => {
    cy.get('bezwaarschriften-kernbezwaren')
        .then(($el) => $el[0].laadClusteringTaken('testproject'));

    cy.wait('@clusteringTaken');

    cy.get('bezwaarschriften-kernbezwaren')
        .find('.categorie-wrapper[data-categorie="Milieu"] vl-pill')
        .should('contain.text', 'Te clusteren');

    // Todo-pill heeft geen type="success", "error" of "warning"
    cy.get('bezwaarschriften-kernbezwaren')
        .find('.categorie-wrapper[data-categorie="Milieu"] vl-pill')
        .should('not.have.attr', 'type', 'success')
        .and('not.have.attr', 'type', 'error')
        .and('not.have.attr', 'type', 'warning');
  });

  it('toont warning-pill voor bezig categorie', () => {
    cy.get('bezwaarschriften-kernbezwaren')
        .then(($el) => $el[0].laadClusteringTaken('testproject'));

    cy.wait('@clusteringTaken');

    cy.get('bezwaarschriften-kernbezwaren')
        .find('.categorie-wrapper[data-categorie="Geluid"] vl-pill')
        .should('have.attr', 'type', 'warning')
        .and('contain.text', 'Bezig');
  });

  it('toont error-pill voor fout categorie', () => {
    cy.get('bezwaarschriften-kernbezwaren')
        .then(($el) => $el[0].laadClusteringTaken('testproject'));

    cy.wait('@clusteringTaken');

    cy.get('bezwaarschriften-kernbezwaren')
        .find('.categorie-wrapper[data-categorie="Natuur"] vl-pill')
        .should('have.attr', 'type', 'error')
        .and('contain.text', 'Fout');
  });

  it('start clustering bij klik op play-knop', () => {
    cy.intercept('POST', '/api/v1/projects/testproject/clustering-taken/Milieu', {
      statusCode: 202,
      body: {
        id: 5, projectNaam: 'testproject', categorie: 'Milieu',
        status: 'wachtend', aantalBezwaren: 18,
        aangemaaktOp: '2026-03-03T10:05:00Z',
      },
    }).as('startClustering');

    cy.get('bezwaarschriften-kernbezwaren')
        .then(($el) => $el[0].laadClusteringTaken('testproject'));

    cy.wait('@clusteringTaken');

    // Zoek de play-knop bij Milieu (todo categorie)
    cy.get('bezwaarschriften-kernbezwaren')
        .find('.categorie-wrapper[data-categorie="Milieu"] button[title="Clustering starten"]')
        .click();

    cy.wait('@startClustering');
  });

  it('cluster alle knop start alle categorieen', () => {
    cy.intercept('POST', '/api/v1/projects/testproject/clustering-taken', {
      statusCode: 202,
      body: [
        {
          id: 5, projectNaam: 'testproject', categorie: 'Milieu',
          status: 'wachtend', aantalBezwaren: 18,
          aangemaaktOp: '2026-03-03T10:05:00Z',
        },
        {
          id: 6, projectNaam: 'testproject', categorie: 'Natuur',
          status: 'wachtend', aantalBezwaren: 6,
          aangemaaktOp: '2026-03-03T10:05:00Z',
        },
      ],
    }).as('clusterAlles');

    cy.get('bezwaarschriften-kernbezwaren')
        .then(($el) => $el[0].laadClusteringTaken('testproject'));

    cy.wait('@clusteringTaken');

    cy.get('bezwaarschriften-kernbezwaren')
        .find('#cluster-alles-knop')
        .click();

    cy.wait('@clusterAlles');
  });

  it('toont globale knop bovenaan rechts', () => {
    cy.get('bezwaarschriften-kernbezwaren')
        .then(($el) => $el[0].laadClusteringTaken('testproject'));

    cy.wait('@clusteringTaken');

    cy.get('bezwaarschriften-kernbezwaren')
        .find('.clustering-header')
        .should('exist');

    cy.get('bezwaarschriften-kernbezwaren')
        .find('.clustering-header #cluster-alles-knop')
        .should('exist')
        .and('contain.text', 'Cluster alle');
  });
});
