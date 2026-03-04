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

// Mock zonder actieve taken: knop "Alles verwijderen" is zichtbaar
const MOCK_CLUSTERING_KLAAR = {
  categorieen: [
    {
      categorie: 'Mobiliteit', status: 'klaar', taakId: 1, aantalBezwaren: 42,
      aantalKernbezwaren: 3, foutmelding: null,
      aangemaaktOp: '2026-03-03T10:00:00Z',
      verwerkingGestartOp: '2026-03-03T10:00:01Z',
      verwerkingVoltooidOp: '2026-03-03T10:00:15Z',
    },
    {
      categorie: 'Natuur', status: 'klaar', taakId: 2, aantalBezwaren: 6,
      aantalKernbezwaren: 2, foutmelding: null,
      aangemaaktOp: '2026-03-03T10:00:00Z',
      verwerkingGestartOp: '2026-03-03T10:00:01Z',
      verwerkingVoltooidOp: '2026-03-03T10:00:05Z',
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

  it('toont info-blok in menu-slot voor klare clustering', () => {
    cy.get('bezwaarschriften-kernbezwaren')
        .then(($el) => $el[0].laadClusteringTaken('testproject'));

    cy.wait('@clusteringTaken');

    cy.get('bezwaarschriften-kernbezwaren')
        .find('vl-accordion[data-categorie="Mobiliteit"] [slot="menu"] .klaar-info')
        .should('exist')
        .and('contain.text', 'bezwaren');
  });

  it('toont \'Te clusteren\' voor todo categorie', () => {
    cy.get('bezwaarschriften-kernbezwaren')
        .then(($el) => $el[0].laadClusteringTaken('testproject'));

    cy.wait('@clusteringTaken');

    cy.get('bezwaarschriften-kernbezwaren')
        .find('vl-accordion[data-categorie="Milieu"] vl-pill')
        .should('contain.text', 'Te clusteren');

    // Todo-pill heeft geen type="success", "error" of "warning"
    cy.get('bezwaarschriften-kernbezwaren')
        .find('vl-accordion[data-categorie="Milieu"] vl-pill')
        .should('not.have.attr', 'type', 'success')
        .and('not.have.attr', 'type', 'error')
        .and('not.have.attr', 'type', 'warning');
  });

  it('toont warning-pill voor bezig categorie', () => {
    cy.get('bezwaarschriften-kernbezwaren')
        .then(($el) => $el[0].laadClusteringTaken('testproject'));

    cy.wait('@clusteringTaken');

    cy.get('bezwaarschriften-kernbezwaren')
        .find('vl-accordion[data-categorie="Geluid"] vl-pill')
        .should('have.attr', 'type', 'warning')
        .and('contain.text', 'Bezig');
  });

  it('toont error-pill voor fout categorie', () => {
    cy.get('bezwaarschriften-kernbezwaren')
        .then(($el) => $el[0].laadClusteringTaken('testproject'));

    cy.wait('@clusteringTaken');

    cy.get('bezwaarschriften-kernbezwaren')
        .find('vl-accordion[data-categorie="Natuur"] vl-pill')
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
        .find('vl-accordion[data-categorie="Milieu"] vl-pill button[title="Clustering starten"]')
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

  it('annuleert clustering bij klik op annuleer-knop', () => {
    cy.intercept('DELETE', '/api/v1/projects/testproject/clustering-taken/Geluid', {
      statusCode: 200,
      body: {},
    }).as('annuleerClustering');

    cy.get('bezwaarschriften-kernbezwaren')
        .then(($el) => $el[0].laadClusteringTaken('testproject'));

    cy.wait('@clusteringTaken');

    // Klik op de annuleer-knop bij Geluid (bezig categorie)
    cy.get('bezwaarschriften-kernbezwaren')
        .find('vl-accordion[data-categorie="Geluid"] vl-pill button[title="Annuleer clustering"]')
        .click();

    cy.wait('@annuleerClustering');
  });

  it('toont verwijder-bevestiging modal bij klare categorie met antwoorden', () => {
    cy.intercept('DELETE', '/api/v1/projects/testproject/clustering-taken/Mobiliteit', {
      statusCode: 409,
      body: {aantalAntwoorden: 3},
    }).as('verwijderClustering');

    cy.get('bezwaarschriften-kernbezwaren')
        .then(($el) => $el[0].laadClusteringTaken('testproject'));

    cy.wait('@clusteringTaken');

    // Klik op de verwijder-knop bij Mobiliteit (klaar categorie)
    cy.get('bezwaarschriften-kernbezwaren')
        .find('vl-accordion[data-categorie="Mobiliteit"] [slot="menu"] button[title="Verwijder clustering"]')
        .click();

    cy.wait('@verwijderClustering');

    // Controleer dat de modal zichtbaar is met de waarschuwingstekst
    cy.get('bezwaarschriften-kernbezwaren')
        .find('#verwijder-bevestiging-inhoud')
        .should('contain.text', '3 antwoord(en)')
        .and('contain.text', 'verloren gaan');
  });

  it('toont herstart-knop bij fout categorie', () => {
    cy.get('bezwaarschriften-kernbezwaren')
        .then(($el) => $el[0].laadClusteringTaken('testproject'));

    cy.wait('@clusteringTaken');

    // Controleer dat de retry-knop bestaat bij Natuur (fout categorie)
    cy.get('bezwaarschriften-kernbezwaren')
        .find('vl-accordion[data-categorie="Natuur"] vl-pill button[title="Opnieuw clusteren"]')
        .should('exist');
  });

  it('update pill status via werkBijMetClusteringUpdate', () => {
    cy.get('bezwaarschriften-kernbezwaren')
        .then(($el) => $el[0].laadClusteringTaken('testproject'));

    cy.wait('@clusteringTaken');

    // Controleer initieel: Milieu is 'todo'
    cy.get('bezwaarschriften-kernbezwaren')
        .find('vl-accordion[data-categorie="Milieu"] vl-pill')
        .should('contain.text', 'Te clusteren');

    // Update Milieu naar 'wachtend'
    cy.get('bezwaarschriften-kernbezwaren')
        .then(($el) => $el[0].werkBijMetClusteringUpdate({
          categorie: 'Milieu',
          id: 5,
          status: 'wachtend',
          aantalBezwaren: 18,
          aantalKernbezwaren: null,
          aangemaaktOp: '2026-03-03T10:05:00Z',
        }));

    // Controleer dat de pill nu warning-type is met 'Wachtend'
    cy.get('bezwaarschriften-kernbezwaren')
        .find('vl-accordion[data-categorie="Milieu"] vl-pill')
        .should('have.attr', 'type', 'warning')
        .and('contain.text', 'Wachtend');
  });

  it('verbergt alles-verwijderen knop als er actieve taken zijn naast klare', () => {
    cy.intercept('GET', '/api/v1/projects/*/clustering-taken', {
      statusCode: 200,
      body: {
        categorieen: [
          {
            categorie: 'Mobiliteit', status: 'klaar', taakId: 1, aantalBezwaren: 42,
            aantalKernbezwaren: 3,
          },
          {
            categorie: 'Geluid', status: 'bezig', taakId: 2, aantalBezwaren: 25,
            aantalKernbezwaren: null,
          },
        ],
      },
    }).as('metActieveTaak');

    cy.get('bezwaarschriften-kernbezwaren')
        .then(($el) => $el[0].laadClusteringTaken('testproject'));

    cy.wait('@metActieveTaak');

    cy.get('bezwaarschriften-kernbezwaren')
        .find('#verwijder-alles-knop')
        .should('not.exist');
  });

  it('toont alles-verwijderen knop naast cluster-alles knop als er klare categorien zijn', () => {
    cy.intercept('GET', '/api/v1/projects/*/clustering-taken', {
      statusCode: 200,
      body: MOCK_CLUSTERING_KLAAR,
    }).as('alleenKlaar');

    cy.get('bezwaarschriften-kernbezwaren')
        .then(($el) => $el[0].laadClusteringTaken('testproject'));

    cy.wait('@alleenKlaar');

    cy.get('bezwaarschriften-kernbezwaren')
        .find('#verwijder-alles-knop')
        .should('exist')
        .and('have.attr', 'error', '');
  });

  it('verwijdert alles zonder antwoorden na klik op alles-verwijderen', () => {
    cy.intercept('GET', '/api/v1/projects/*/clustering-taken', {
      statusCode: 200,
      body: MOCK_CLUSTERING_KLAAR,
    }).as('alleenKlaar');

    cy.get('bezwaarschriften-kernbezwaren')
        .then(($el) => $el[0].laadClusteringTaken('testproject'));

    cy.wait('@alleenKlaar');

    cy.intercept('DELETE', '/api/v1/projects/testproject/clustering-taken', {
      statusCode: 200,
      body: {},
    }).as('verwijderAlles');

    cy.get('bezwaarschriften-kernbezwaren')
        .find('#verwijder-alles-knop')
        .click();

    cy.wait('@verwijderAlles').its('request.url')
        .should('include', '/clustering-taken');
  });

  it('toont bevestigingsmodal bij alles verwijderen als er antwoorden zijn', () => {
    cy.intercept('GET', '/api/v1/projects/*/clustering-taken', {
      statusCode: 200,
      body: MOCK_CLUSTERING_KLAAR,
    }).as('alleenKlaar');

    cy.intercept('DELETE', '/api/v1/projects/testproject/clustering-taken', {
      statusCode: 409,
      body: {aantalAntwoorden: 5},
    }).as('verwijderAlles');

    cy.get('bezwaarschriften-kernbezwaren')
        .then(($el) => $el[0].laadClusteringTaken('testproject'));

    cy.wait('@alleenKlaar');

    cy.get('bezwaarschriften-kernbezwaren')
        .find('#verwijder-alles-knop')
        .click();

    cy.wait('@verwijderAlles');

    cy.get('bezwaarschriften-kernbezwaren')
        .find('#verwijder-bevestiging-inhoud')
        .should('contain.text', '5 antwoord(en)')
        .and('contain.text', 'verloren gaan');
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

  it('toont verwijder en retry knop in menu-slot bij klare categorie', () => {
    cy.get('bezwaarschriften-kernbezwaren')
        .then(($el) => $el[0].laadClusteringTaken('testproject'));

    cy.wait('@clusteringTaken');

    cy.get('bezwaarschriften-kernbezwaren')
        .find('vl-accordion[data-categorie="Mobiliteit"] [slot="menu"] button[title="Verwijder clustering"]')
        .should('exist');

    cy.get('bezwaarschriften-kernbezwaren')
        .find('vl-accordion[data-categorie="Mobiliteit"] [slot="menu"] button[title="Opnieuw clusteren"]')
        .should('exist');
  });

  it('retry bij klaar verwijdert en herstart clustering', () => {
    cy.intercept('DELETE', '/api/v1/projects/testproject/clustering-taken/Mobiliteit', {
      statusCode: 200,
    }).as('verwijderClustering');

    cy.intercept('POST', '/api/v1/projects/testproject/clustering-taken/Mobiliteit', {
      statusCode: 202,
      body: {
        id: 10, projectNaam: 'testproject', categorie: 'Mobiliteit',
        status: 'wachtend', aantalBezwaren: 42,
        aangemaaktOp: '2026-03-04T10:00:00Z',
      },
    }).as('startClustering');

    cy.get('bezwaarschriften-kernbezwaren')
        .then(($el) => $el[0].laadClusteringTaken('testproject'));

    cy.wait('@clusteringTaken');

    cy.get('bezwaarschriften-kernbezwaren')
        .find('vl-accordion[data-categorie="Mobiliteit"] [slot="menu"] button[title="Opnieuw clusteren"]')
        .click();

    cy.wait('@verwijderClustering');
    cy.wait('@startClustering');
  });

  it('retry bij klaar toont modal als er antwoorden zijn', () => {
    cy.intercept('DELETE', '/api/v1/projects/testproject/clustering-taken/Mobiliteit', {
      statusCode: 409,
      body: {aantalAntwoorden: 3},
    }).as('verwijderClustering');

    cy.get('bezwaarschriften-kernbezwaren')
        .then(($el) => $el[0].laadClusteringTaken('testproject'));

    cy.wait('@clusteringTaken');

    cy.get('bezwaarschriften-kernbezwaren')
        .find('vl-accordion[data-categorie="Mobiliteit"] [slot="menu"] button[title="Opnieuw clusteren"]')
        .click();

    cy.wait('@verwijderClustering');

    cy.get('bezwaarschriften-kernbezwaren')
        .find('#verwijder-bevestiging-inhoud')
        .should('contain.text', '3 antwoord(en)');
  });

  it('retry bij klaar met bevestiging verwijdert en herstart clustering', () => {
    let deleteCount = 0;
    cy.intercept('DELETE', '/api/v1/projects/testproject/clustering-taken/Mobiliteit*', (req) => {
      deleteCount++;
      if (!req.url.includes('bevestigd=true')) {
        req.reply({statusCode: 409, body: {aantalAntwoorden: 3}});
      } else {
        req.reply({statusCode: 200});
      }
    }).as('verwijderClustering');

    cy.intercept('POST', '/api/v1/projects/testproject/clustering-taken/Mobiliteit', {
      statusCode: 202,
      body: {
        id: 10, projectNaam: 'testproject', categorie: 'Mobiliteit',
        status: 'wachtend', aantalBezwaren: 42,
        aangemaaktOp: '2026-03-04T10:00:00Z',
      },
    }).as('startClustering');

    cy.get('bezwaarschriften-kernbezwaren')
        .then(($el) => $el[0].laadClusteringTaken('testproject'));

    cy.wait('@clusteringTaken');

    // Klik retry
    cy.get('bezwaarschriften-kernbezwaren')
        .find('vl-accordion[data-categorie="Mobiliteit"] [slot="menu"] button[title="Opnieuw clusteren"]')
        .click();

    cy.wait('@verwijderClustering');

    // Modal verschijnt, klik bevestig
    cy.get('bezwaarschriften-kernbezwaren')
        .find('#verwijder-bevestiging-bevestig')
        .click();

    cy.wait('@verwijderClustering');
    cy.wait('@startClustering');
  });

  it('toont subtitle met aantal bezwaren voor todo categorie', () => {
    cy.get('bezwaarschriften-kernbezwaren')
        .then(($el) => $el[0].laadClusteringTaken('testproject'));

    cy.wait('@clusteringTaken');

    cy.get('bezwaarschriften-kernbezwaren')
        .find('vl-accordion[data-categorie="Milieu"] vl-alert')
        .should('have.attr', 'message')
        .and('include', '18 bezwaren');
  });

  it('toont subtitle met bezwaren en kernbezwaren voor klare categorie', () => {
    cy.intercept('GET', '/api/v1/projects/*/kernbezwaren', {
      statusCode: 200,
      body: {
        themas: [{
          naam: 'Mobiliteit',
          kernbezwaren: [
            {id: 1, samenvatting: 'Kern 1', individueleBezwaren: [], antwoord: null},
            {id: 2, samenvatting: 'Kern 2', individueleBezwaren: [], antwoord: null},
            {id: 3, samenvatting: 'Kern 3', individueleBezwaren: [], antwoord: null},
          ],
        }],
      },
    }).as('kernbezwarenKlaar');

    cy.get('bezwaarschriften-kernbezwaren')
        .then(($el) => {
          $el[0].laadClusteringTaken('testproject');
          $el[0].laadKernbezwaren('testproject');
        });

    cy.wait('@clusteringTaken');
    cy.wait('@kernbezwarenKlaar');

    cy.get('bezwaarschriften-kernbezwaren')
        .find('vl-accordion[data-categorie="Mobiliteit"] [slot="menu"] .klaar-info')
        .should('contain.text', '42 bezwaren')
        .and('contain.text', '3 kernbezwaren');
  });

  it('toont pill in menu-slot voor niet-klare categorie', () => {
    cy.get('bezwaarschriften-kernbezwaren')
        .then(($el) => $el[0].laadClusteringTaken('testproject'));

    cy.wait('@clusteringTaken');

    // Milieu is 'todo' — heeft wel een pill in het menu-slot
    cy.get('bezwaarschriften-kernbezwaren')
        .find('vl-accordion[data-categorie="Milieu"] vl-pill[slot="menu"]')
        .should('exist');

    // Mobiliteit is 'klaar' — geen pill, maar een info-alert in het menu-slot
    cy.get('bezwaarschriften-kernbezwaren')
        .find('vl-accordion[data-categorie="Mobiliteit"] vl-pill[slot="menu"]')
        .should('not.exist');
  });

  it('alle accordions zijn standaard dichtgeklapt', () => {
    cy.get('bezwaarschriften-kernbezwaren')
        .then(($el) => $el[0].laadClusteringTaken('testproject'));

    cy.wait('@clusteringTaken');

    cy.get('bezwaarschriften-kernbezwaren')
        .find('vl-accordion[default-open]')
        .should('not.exist');
  });

  it('toont categorienaam als toggle-text zonder aantal', () => {
    cy.get('bezwaarschriften-kernbezwaren')
        .then(($el) => $el[0].laadClusteringTaken('testproject'));

    cy.wait('@clusteringTaken');

    cy.get('bezwaarschriften-kernbezwaren')
        .find('vl-accordion[data-categorie="Mobiliteit"]')
        .should('have.attr', 'toggle-text', 'Mobiliteit');
  });
});
