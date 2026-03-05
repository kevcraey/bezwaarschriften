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

  it('toont bezwaren-tekst in pill voor klare clustering', () => {
    cy.get('bezwaarschriften-kernbezwaren')
        .then(($el) => $el[0].laadClusteringTaken('testproject'));

    cy.wait('@clusteringTaken');

    cy.get('bezwaarschriften-kernbezwaren')
        .find('vl-accordion[data-categorie="Mobiliteit"] vl-pill[slot="menu"]')
        .should('have.attr', 'type', 'success')
        .and('contain.text', '42');
  });

  it('toont bezwaren-aantal in pill voor todo categorie', () => {
    cy.get('bezwaarschriften-kernbezwaren')
        .then(($el) => $el[0].laadClusteringTaken('testproject'));

    cy.wait('@clusteringTaken');

    cy.get('bezwaarschriften-kernbezwaren')
        .find('vl-accordion[data-categorie="Milieu"] vl-pill')
        .should('contain.text', '18');

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

    // Controleer initieel: Milieu is 'todo' — toont bezwaren-aantal
    cy.get('bezwaarschriften-kernbezwaren')
        .find('vl-accordion[data-categorie="Milieu"] vl-pill')
        .should('contain.text', '18');

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
        .find('vl-accordion[data-categorie="Milieu"] vl-pill[slot="menu"]')
        .should('contain.text', '18');
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
        .find('vl-accordion[data-categorie="Mobiliteit"] vl-pill[slot="menu"]')
        .should('contain.text', '42')
        .and('contain.text', '3');
  });

  it('toont pill in menu-slot voor alle categorieen', () => {
    cy.get('bezwaarschriften-kernbezwaren')
        .then(($el) => $el[0].laadClusteringTaken('testproject'));

    cy.wait('@clusteringTaken');

    cy.get('bezwaarschriften-kernbezwaren')
        .find('vl-accordion[data-categorie="Milieu"] vl-pill[slot="menu"]')
        .should('exist');

    cy.get('bezwaarschriften-kernbezwaren')
        .find('vl-accordion[data-categorie="Mobiliteit"] vl-pill[slot="menu"]')
        .should('exist')
        .and('have.attr', 'type', 'success');
  });

  it('alle accordions zijn standaard dichtgeklapt', () => {
    cy.get('bezwaarschriften-kernbezwaren')
        .then(($el) => $el[0].laadClusteringTaken('testproject'));

    cy.wait('@clusteringTaken');

    cy.get('bezwaarschriften-kernbezwaren')
        .find('vl-accordion[default-open]')
        .should('not.exist');
  });

  it('toont teller in (totaal|groepen) formaat op search-knop', () => {
    cy.intercept('GET', '/api/v1/projects/*/kernbezwaren', {
      statusCode: 200,
      body: {
        themas: [{
          naam: 'Mobiliteit',
          kernbezwaren: [{
            id: 1,
            samenvatting: 'Geluidshinder',
            antwoord: null,
            individueleBezwaren: [
              {bestandsnaam: '001.txt', passage: 'Te veel geluid'},
              {bestandsnaam: '002.txt', passage: 'Te veel geluid'},
              {bestandsnaam: '003.txt', passage: 'Te veel geluid'},
              {bestandsnaam: '004.txt', passage: 'Verkeer is gevaarlijk'},
              {bestandsnaam: '005.txt', passage: 'Fietspaden ontbreken'},
            ],
          }],
        }],
      },
    }).as('kernbezwaren');

    cy.get('bezwaarschriften-kernbezwaren')
        .then(($el) => {
          $el[0].laadClusteringTaken('testproject');
          $el[0].laadKernbezwaren('testproject');
        });

    cy.wait('@clusteringTaken');
    cy.wait('@kernbezwaren');

    // Search-knop toont (5|3): 5 individuele bezwaren, 3 unieke groepen
    cy.get('bezwaarschriften-kernbezwaren')
        .find('.kernbezwaar-actie vl-button[icon="search"]')
        .should('contain.text', '(5|3)');
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

describe('side panel passage-deduplicatie', () => {
  const MOCK_MET_DUPLICATEN = {
    themas: [{
      naam: 'Mobiliteit',
      kernbezwaren: [{
        id: 1,
        samenvatting: 'Geluidshinder',
        antwoord: null,
        individueleBezwaren: [
          {bestandsnaam: '001.txt', passage: 'Te veel geluid in de buurt'},
          {bestandsnaam: '002.txt', passage: 'Te veel geluid in de buurt'},
          {bestandsnaam: '003.txt', passage: 'Te veel geluid in de buurt'},
          {bestandsnaam: '004.txt', passage: 'Te veel geluid in de buurt'},
          {bestandsnaam: '005.txt', passage: 'Te veel geluid in de buurt'},
          {bestandsnaam: '006.txt', passage: 'Te veel geluid in de buurt'},
          {bestandsnaam: '007.txt', passage: 'Te veel geluid in de buurt'},
          {bestandsnaam: '008.txt', passage: 'Verkeer is gevaarlijk'},
        ],
      }],
    }],
  };

  beforeEach(() => {
    cy.intercept('GET', '/api/v1/projects/*/clustering-taken', {
      statusCode: 200,
      body: {
        categorieen: [{
          categorie: 'Mobiliteit', status: 'klaar', taakId: 1,
          aantalBezwaren: 8, aantalKernbezwaren: 1,
          aangemaaktOp: '2026-03-03T10:00:00Z',
          verwerkingGestartOp: '2026-03-03T10:00:01Z',
          verwerkingVoltooidOp: '2026-03-03T10:00:15Z',
        }],
      },
    }).as('clusteringTaken');

    cy.intercept('GET', '/api/v1/projects/*/kernbezwaren', {
      statusCode: 200,
      body: MOCK_MET_DUPLICATEN,
    }).as('kernbezwaren');

    cy.mount(html`<bezwaarschriften-kernbezwaren></bezwaarschriften-kernbezwaren>`);

    cy.get('bezwaarschriften-kernbezwaren')
        .then(($el) => {
          $el[0].laadClusteringTaken('testproject');
          $el[0].laadKernbezwaren('testproject');
        });

    cy.wait('@clusteringTaken');
    cy.wait('@kernbezwaren');

    // Open de accordion zodat kernbezwaren zichtbaar worden
    cy.get('bezwaarschriften-kernbezwaren')
        .find('vl-accordion[data-categorie="Mobiliteit"]')
        .click();
  });

  it('toont gegroepeerde passage slechts 1x met documentenlijst', () => {
    cy.get('bezwaarschriften-kernbezwaren')
        .find('.kernbezwaar-actie vl-button[icon="search"]')
        .click();

    // 2 groepen: geluid (7x) + verkeer (1x)
    cy.get('bezwaarschriften-kernbezwaren')
        .find('.passage-groep .passage-tekst')
        .should('have.length', 2);

    // Eerste groep toont max 5 documenten
    cy.get('bezwaarschriften-kernbezwaren')
        .find('.passage-groep')
        .first()
        .find('.passage-document-link')
        .should('have.length', 5);

    // Plus "... (7 documenten)" link
    cy.get('bezwaarschriften-kernbezwaren')
        .find('.passage-groep')
        .first()
        .find('.passage-toon-alle')
        .should('contain.text', '7 documenten');
  });

  it('toont alle documenten na klik op "Toon alle"', () => {
    cy.get('bezwaarschriften-kernbezwaren')
        .find('.kernbezwaar-actie vl-button[icon="search"]')
        .click();

    cy.get('bezwaarschriften-kernbezwaren')
        .find('.passage-groep')
        .first()
        .find('.passage-toon-alle')
        .click();

    // Nu alle 7 documenten zichtbaar
    cy.get('bezwaarschriften-kernbezwaren')
        .find('.passage-groep')
        .first()
        .find('.passage-document-link')
        .should('have.length', 7);

    // "Toon alle" link verdwenen
    cy.get('bezwaarschriften-kernbezwaren')
        .find('.passage-groep')
        .first()
        .find('.passage-toon-alle')
        .should('not.exist');
  });

  it('toont header met totaal en groepen-aantal', () => {
    cy.get('bezwaarschriften-kernbezwaren')
        .find('.kernbezwaar-actie vl-button[icon="search"]')
        .click();

    cy.get('bezwaarschriften-kernbezwaren')
        .find('#side-sheet-inhoud')
        .should('contain.text', '8 individuele bezwaren')
        .and('contain.text', '2 unieke passages');
  });

  it('toont enkel document als passage uniek is', () => {
    cy.get('bezwaarschriften-kernbezwaren')
        .find('.kernbezwaar-actie vl-button[icon="search"]')
        .click();

    // Tweede groep (verkeer) heeft 1 document, geen "Toon alle"
    cy.get('bezwaarschriften-kernbezwaren')
        .find('.passage-groep')
        .last()
        .find('.passage-document-link')
        .should('have.length', 1);

    cy.get('bezwaarschriften-kernbezwaren')
        .find('.passage-groep')
        .last()
        .find('.passage-toon-alle')
        .should('not.exist');
  });
});

describe('bezwaarschriften-kernbezwaren clustering parameters', () => {
  const MOCK_CONFIG = {
    minClusterSize: 5,
    minSamples: 3,
    clusterSelectionEpsilon: 0.2,
    umapEnabled: true,
    umapNComponents: 5,
    umapNNeighbors: 15,
    umapMinDist: 0.1,
  };

  beforeEach(() => {
    cy.intercept('GET', '/api/v1/projects/*/kernbezwaren', {
      statusCode: 404,
    }).as('kernbezwaren');

    cy.intercept('GET', '/api/v1/projects/*/clustering-taken', {
      statusCode: 200,
      body: {
        categorieen: [
          {
            categorie: 'Mobiliteit', status: 'klaar', taakId: 1,
            aantalBezwaren: 10, aantalKernbezwaren: 3, foutmelding: null,
            aangemaaktOp: '2026-03-03T10:00:00Z',
            verwerkingGestartOp: '2026-03-03T10:00:01Z',
            verwerkingVoltooidOp: '2026-03-03T10:00:15Z',
          },
        ],
      },
    }).as('clusteringTaken');

    cy.intercept('GET', '/api/v1/clustering-config', {
      statusCode: 200,
      body: MOCK_CONFIG,
    }).as('getConfig');

    cy.mount(html`<bezwaarschriften-kernbezwaren></bezwaarschriften-kernbezwaren>`);

    cy.get('bezwaarschriften-kernbezwaren')
        .then(($el) => $el[0].laadClusteringTaken('testproject'));

    cy.wait('@clusteringTaken');
    cy.wait('@getConfig');
  });

  it('toont clustering parameters titel', () => {
    cy.get('bezwaarschriften-kernbezwaren')
        .find('.clustering-params-titel')
        .should('contain.text', 'Clustering parameters:');
  });

  it('toont UMAP toggle als aangevinkt', () => {
    cy.get('bezwaarschriften-kernbezwaren')
        .find('.clustering-params input[type="checkbox"]')
        .should('be.checked');
  });

  it('toont UMAP parameter-velden als UMAP aan', () => {
    cy.get('bezwaarschriften-kernbezwaren')
        .find('.umap-params')
        .should('be.visible');
  });

  it('verbergt UMAP parameter-velden als UMAP uit', () => {
    cy.intercept('PUT', '/api/v1/clustering-config', {
      statusCode: 200,
      body: {...MOCK_CONFIG, umapEnabled: false},
    }).as('updateConfig');

    cy.get('bezwaarschriften-kernbezwaren')
        .find('.clustering-params input[type="checkbox"]')
        .uncheck();

    cy.get('bezwaarschriften-kernbezwaren')
        .find('.umap-params')
        .should('not.be.visible');
  });

  it('stuurt update bij wijzigen UMAP parameter', () => {
    cy.intercept('PUT', '/api/v1/clustering-config', (req) => {
      expect(req.body.umapNComponents).to.equal(10);
      req.reply({statusCode: 200, body: req.body});
    }).as('updateConfig');

    cy.get('bezwaarschriften-kernbezwaren')
        .find('.umap-params input[type="number"]')
        .first()
        .clear()
        .type('10')
        .trigger('change');

    cy.wait('@updateConfig');
  });
});
