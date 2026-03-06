import {html} from 'lit';
import '../src/js/bezwaarschriften-kernbezwaren';

// --- Mock data ---

const MOCK_CLUSTERING_WACHTEND = {
  id: 1,
  projectNaam: 'testproject',
  status: 'wachtend',
  aantalBezwaren: 42,
  aangemaaktOp: '2026-03-03T10:00:00Z',
};

const MOCK_CLUSTERING_BEZIG = {
  id: 2,
  projectNaam: 'testproject',
  status: 'bezig',
  aantalBezwaren: 42,
  aangemaaktOp: '2026-03-03T10:00:00Z',
  verwerkingGestartOp: '2026-03-03T10:00:05Z',
};

const MOCK_CLUSTERING_KLAAR = {
  id: 3,
  projectNaam: 'testproject',
  status: 'klaar',
  aantalBezwaren: 42,
  aantalKernbezwaren: 3,
  aangemaaktOp: '2026-03-03T10:00:00Z',
  verwerkingGestartOp: '2026-03-03T10:00:01Z',
  verwerkingVoltooidOp: '2026-03-03T10:00:15Z',
};

const MOCK_CLUSTERING_FOUT = {
  id: 4,
  projectNaam: 'testproject',
  status: 'fout',
  aantalBezwaren: 42,
  foutmelding: 'Timeout bij clustering',
  aangemaaktOp: '2026-03-03T10:00:00Z',
  verwerkingGestartOp: '2026-03-03T10:00:01Z',
};

const MOCK_KERNBEZWAREN = {
  kernbezwaren: [
    {
      id: 1, samenvatting: 'Te veel geluidshinder',
      individueleBezwaren: [
        {referentieId: 10, bezwaarId: 100, bestandsnaam: '001.txt', passage: 'Te veel geluid',
          scorePercentage: 95, toewijzingsmethode: 'HDBSCAN'},
        {referentieId: 11, bezwaarId: 101, bestandsnaam: '002.txt', passage: 'Te veel geluid',
          scorePercentage: 90, toewijzingsmethode: 'HDBSCAN'},
        {referentieId: 12, bezwaarId: 102, bestandsnaam: '003.txt', passage: 'Geluidsoverlast door verkeer',
          scorePercentage: 88, toewijzingsmethode: 'CENTROID_FALLBACK'},
      ],
      antwoord: null,
    },
    {
      id: 2, samenvatting: 'Gevaarlijk verkeer',
      individueleBezwaren: [
        {referentieId: 20, bezwaarId: 200, bestandsnaam: '004.txt', passage: 'Verkeer is gevaarlijk',
          scorePercentage: 92, toewijzingsmethode: 'HDBSCAN'},
      ],
      antwoord: 'Bestaand antwoord',
    },
    {
      id: 99, samenvatting: 'Niet-geclusterde bezwaren',
      individueleBezwaren: [
        {referentieId: 30, bezwaarId: 300, bestandsnaam: '005.txt', passage: 'Ongerelateeerd punt',
          scorePercentage: null, toewijzingsmethode: null},
      ],
      antwoord: null,
    },
  ],
};

const MOCK_CONFIG = {
  minClusterSize: 5,
  minSamples: 3,
  clusterSelectionEpsilon: 0.2,
  umapEnabled: true,
  umapNComponents: 5,
  umapNNeighbors: 15,
  umapMinDist: 0.1,
  clusterOpPassages: true,
};

// --- Helper ---

function mountEnLaad(clusteringTaakBody, kernbezwarenBody) {
  cy.intercept('GET', '/api/v1/clustering-config', {
    statusCode: 200, body: MOCK_CONFIG,
  }).as('getConfig');

  if (clusteringTaakBody) {
    cy.intercept('GET', '/api/v1/projects/*/clustering-taken', {
      statusCode: 200, body: clusteringTaakBody,
    }).as('clusteringTaken');
  } else {
    cy.intercept('GET', '/api/v1/projects/*/clustering-taken', {
      statusCode: 204,
    }).as('clusteringTaken');
  }

  if (kernbezwarenBody) {
    cy.intercept('GET', '/api/v1/projects/*/kernbezwaren', {
      statusCode: 200, body: kernbezwarenBody,
    }).as('kernbezwaren');
  } else {
    cy.intercept('GET', '/api/v1/projects/*/kernbezwaren', {
      statusCode: 404,
    }).as('kernbezwaren');
  }

  cy.mount(html`<bezwaarschriften-kernbezwaren></bezwaarschriften-kernbezwaren>`);

  cy.get('bezwaarschriften-kernbezwaren').then(($el) => {
    $el[0].laadClusteringTaken('testproject');
    if (kernbezwarenBody) $el[0].laadKernbezwaren('testproject');
  });

  cy.wait('@clusteringTaken');
}

// === Clustering status ===

describe('clustering status weergave', () => {
  it('toont wachtend-status met loading knop en annuleer-link', () => {
    mountEnLaad(MOCK_CLUSTERING_WACHTEND);

    cy.get('bezwaarschriften-kernbezwaren')
        .find('vl-button[loading][disabled]')
        .should('exist')
        .and('contain.text', 'Wachtend');

    cy.get('bezwaarschriften-kernbezwaren')
        .find('vl-link[button-as-link]')
        .should('contain.text', 'Annuleer');
  });

  it('toont bezig-status met loading knop', () => {
    mountEnLaad(MOCK_CLUSTERING_BEZIG);

    cy.get('bezwaarschriften-kernbezwaren')
        .find('vl-button[loading][disabled]')
        .should('exist')
        .and('contain.text', 'Bezig');
  });

  it('toont fout-status met foutmelding en opnieuw-knop', () => {
    mountEnLaad(MOCK_CLUSTERING_FOUT);

    cy.get('bezwaarschriften-kernbezwaren')
        .find('vl-alert[type="error"]')
        .should('exist')
        .and('have.attr', 'message')
        .and('contain', 'Timeout bij clustering');

    cy.get('bezwaarschriften-kernbezwaren')
        .find('vl-button')
        .should('contain.text', 'Opnieuw clusteren');
  });

  it('update status via werkBijMetClusteringUpdate', () => {
    mountEnLaad(MOCK_CLUSTERING_WACHTEND);

    cy.get('bezwaarschriften-kernbezwaren')
        .find('vl-button[loading]')
        .should('contain.text', 'Wachtend');

    cy.get('bezwaarschriften-kernbezwaren')
        .then(($el) => $el[0].werkBijMetClusteringUpdate({
          ...MOCK_CLUSTERING_BEZIG,
        }));

    cy.get('bezwaarschriften-kernbezwaren')
        .find('vl-button[loading]')
        .should('contain.text', 'Bezig');
  });
});

// === Clustering acties ===

describe('clustering acties', () => {
  it('toont cluster-knop als extractie klaar en geen clustering', () => {
    mountEnLaad(null);

    cy.get('bezwaarschriften-kernbezwaren')
        .then(($el) => {
          $el[0].setExtractieKlaar(true);
          $el[0].setAantalBezwaren(42);
        });

    cy.get('bezwaarschriften-kernbezwaren')
        .find('#groepeer-knop')
        .should('contain.text', 'Cluster bezwaren');
  });

  it('start clustering bij klik op cluster-knop', () => {
    cy.intercept('POST', '/api/v1/projects/testproject/clustering-taken', {
      statusCode: 202,
      body: MOCK_CLUSTERING_WACHTEND,
    }).as('startClustering');

    mountEnLaad(null);

    cy.get('bezwaarschriften-kernbezwaren')
        .then(($el) => {
          $el[0].setExtractieKlaar(true);
          $el[0].setAantalBezwaren(42);
        });

    cy.get('bezwaarschriften-kernbezwaren')
        .find('#groepeer-knop')
        .click();

    cy.wait('@startClustering');
  });

  it('annuleert clustering bij klik op annuleer-link', () => {
    cy.intercept('DELETE', '/api/v1/projects/testproject/clustering-taken', {
      statusCode: 200,
    }).as('annuleerClustering');

    mountEnLaad(MOCK_CLUSTERING_BEZIG);

    cy.get('bezwaarschriften-kernbezwaren')
        .find('vl-link[button-as-link]')
        .contains('Annuleer')
        .click();

    cy.wait('@annuleerClustering');
  });

  it('verwijdert clustering zonder antwoorden', () => {
    cy.intercept('DELETE', '/api/v1/projects/testproject/clustering-taken', {
      statusCode: 200,
    }).as('verwijderClustering');

    mountEnLaad(MOCK_CLUSTERING_KLAAR, MOCK_KERNBEZWAREN);
    cy.wait('@kernbezwaren');

    cy.get('bezwaarschriften-kernbezwaren')
        .find('vl-button[error]')
        .contains('Clustering verwijderen')
        .click();

    cy.wait('@verwijderClustering');
  });

  it('toont bevestigingsmodal bij verwijderen als er antwoorden zijn', () => {
    cy.intercept('DELETE', '/api/v1/projects/testproject/clustering-taken', {
      statusCode: 409,
      body: {aantalAntwoorden: 3},
    }).as('verwijderClustering');

    mountEnLaad(MOCK_CLUSTERING_KLAAR, MOCK_KERNBEZWAREN);
    cy.wait('@kernbezwaren');

    cy.get('bezwaarschriften-kernbezwaren')
        .find('vl-button[error]')
        .contains('Clustering verwijderen')
        .click();

    cy.wait('@verwijderClustering');

    cy.get('bezwaarschriften-kernbezwaren')
        .find('#verwijder-bevestiging-inhoud')
        .should('contain.text', '3 antwoord(en)')
        .and('contain.text', 'verloren gaan');
  });

  it('retry clustering verwijdert en herstart', () => {
    cy.intercept('DELETE', '/api/v1/projects/testproject/clustering-taken', {
      statusCode: 200,
    }).as('verwijderClustering');

    cy.intercept('POST', '/api/v1/projects/testproject/clustering-taken', {
      statusCode: 202,
      body: MOCK_CLUSTERING_WACHTEND,
    }).as('startClustering');

    mountEnLaad(MOCK_CLUSTERING_KLAAR, MOCK_KERNBEZWAREN);
    cy.wait('@kernbezwaren');

    cy.get('bezwaarschriften-kernbezwaren')
        .find('vl-button')
        .contains('Opnieuw clusteren')
        .click();

    cy.wait('@verwijderClustering');
    cy.wait('@startClustering');
  });

  it('retry met antwoorden toont bevestigingsmodal en herstart na bevestiging', () => {
    let deleteCount = 0;
    cy.intercept('DELETE', '/api/v1/projects/testproject/clustering-taken*', (req) => {
      deleteCount++;
      if (!req.url.includes('bevestigd=true')) {
        req.reply({statusCode: 409, body: {aantalAntwoorden: 2}});
      } else {
        req.reply({statusCode: 200});
      }
    }).as('verwijderClustering');

    cy.intercept('POST', '/api/v1/projects/testproject/clustering-taken', {
      statusCode: 202,
      body: MOCK_CLUSTERING_WACHTEND,
    }).as('startClustering');

    mountEnLaad(MOCK_CLUSTERING_KLAAR, MOCK_KERNBEZWAREN);
    cy.wait('@kernbezwaren');

    cy.get('bezwaarschriften-kernbezwaren')
        .find('vl-button')
        .contains('Opnieuw clusteren')
        .click();

    cy.wait('@verwijderClustering');

    // Modal verschijnt
    cy.get('bezwaarschriften-kernbezwaren')
        .find('#verwijder-bevestiging-inhoud')
        .should('contain.text', '2 antwoord(en)');

    cy.get('bezwaarschriften-kernbezwaren')
        .find('#verwijder-bevestiging-bevestig')
        .click();

    cy.wait('@verwijderClustering');
    cy.wait('@startClustering');
  });
});

// === Kernbezwaren flat list ===

describe('kernbezwaren flat list', () => {
  beforeEach(() => {
    mountEnLaad(MOCK_CLUSTERING_KLAAR, MOCK_KERNBEZWAREN);
    cy.wait('@kernbezwaren');
  });

  it('toont kernbezwaren als flat lijst zonder categorieen', () => {
    cy.get('bezwaarschriften-kernbezwaren')
        .find('.kernbezwaar-item')
        .should('have.length', 3);

    // Geen accordions met data-categorie
    cy.get('bezwaarschriften-kernbezwaren')
        .find('vl-accordion[data-categorie]')
        .should('not.exist');
  });

  it('toont samenvatting met vinkje voor kernbezwaar met antwoord', () => {
    cy.get('bezwaarschriften-kernbezwaren')
        .find('.kernbezwaar-samenvatting')
        .contains('\u2714 Gevaarlijk verkeer')
        .should('exist');
  });

  it('toont search-knop met (totaal) formaat', () => {
    // Te veel geluidshinder: 3 bezwaren
    cy.get('bezwaarschriften-kernbezwaren')
        .find('.kernbezwaar-actie vl-button[icon="search"]')
        .first()
        .should('contain.text', '(3)');
  });

  it('toont reductie-samenvatting alert', () => {
    cy.get('bezwaarschriften-kernbezwaren')
        .find('vl-alert[type="success"]')
        .should('have.attr', 'message')
        .and('contain', 'kernbezwaren');
  });

  it('toont noise-waarschuwing als er niet-geclusterde bezwaren zijn', () => {
    cy.get('bezwaarschriften-kernbezwaren')
        .find('vl-alert[type="warning"]')
        .should('have.attr', 'message')
        .and('contain', 'niet');
  });

  it('noise-kernbezwaar staat altijd onderaan', () => {
    cy.get('bezwaarschriften-kernbezwaren')
        .find('.kernbezwaar-item')
        .last()
        .find('.kernbezwaar-samenvatting')
        .should('contain.text', 'Niet-geclusterde bezwaren');
  });
});

// === Side panel ===

describe('side panel passage-weergave', () => {
  beforeEach(() => {
    mountEnLaad(MOCK_CLUSTERING_KLAAR, MOCK_KERNBEZWAREN);
    cy.wait('@kernbezwaren');
  });

  it('opent side panel bij klik op search-knop', () => {
    cy.get('bezwaarschriften-kernbezwaren')
        .find('.kernbezwaar-actie vl-button[icon="search"]')
        .first()
        .click();

    cy.get('bezwaarschriften-kernbezwaren')
        .find('#side-sheet-titel')
        .should('contain.text', 'Te veel geluidshinder');
  });

  it('toont header met totaal en unieke passages', () => {
    cy.get('bezwaarschriften-kernbezwaren')
        .find('.kernbezwaar-actie vl-button[icon="search"]')
        .first()
        .click();

    cy.get('bezwaarschriften-kernbezwaren')
        .find('#side-sheet-inhoud')
        .should('contain.text', '3 individuele bezwaren')
        .and('contain.text', '2 unieke passages');
  });

  it('toont gegroepeerde passages met documenten', () => {
    cy.get('bezwaarschriften-kernbezwaren')
        .find('.kernbezwaar-actie vl-button[icon="search"]')
        .first()
        .click();

    cy.get('bezwaarschriften-kernbezwaren')
        .find('.passage-groep')
        .should('have.length', 2);
  });

  it('toont score badge op passage', () => {
    cy.get('bezwaarschriften-kernbezwaren')
        .find('.kernbezwaar-actie vl-button[icon="search"]')
        .first()
        .click();

    cy.get('bezwaarschriften-kernbezwaren')
        .find('.passage-groep')
        .first()
        .find('.passage-tekst')
        .should('contain.text', '%');
  });

  it('toont toewijzingsmethode badges voor HDBSCAN en CENTROID_FALLBACK', () => {
    cy.get('bezwaarschriften-kernbezwaren')
        .find('.kernbezwaar-actie vl-button[icon="search"]')
        .first()
        .click();

    cy.get('bezwaarschriften-kernbezwaren')
        .find('.toewijzing-badge--hdbscan')
        .should('exist')
        .and('contain.text', 'Clustering');

    cy.get('bezwaarschriften-kernbezwaren')
        .find('.toewijzing-badge--centroid')
        .should('exist')
        .and('contain.text', 'Centroid');
  });
});

// === Side panel paginering ===

describe('side panel paginering', () => {
  it('toont paginering bij meer dan 15 groepen', () => {
    // Passages moeten voldoende verschillen om niet gegroepeerd te worden (Dice < 0.9)
    const uniekeTeksten = [
      'Windmolens zorgen voor slagschaduw op woningen',
      'Fietspaden langs de ringweg ontbreken volledig',
      'Luchtkwaliteit is onvoldoende onderzocht in rapport',
      'Wateroverlast door verharding van het terrein',
      'Beschermde vleermuissoorten worden bedreigd',
      'Parkeergelegenheid is totaal ontoereikend',
      'Grondwaterpeil daalt door bouwactiviteiten',
      'Erfgoedwaarde van het pand wordt aangetast',
      'Stikstofuitstoot overschrijdt de normen',
      'Brandveiligheid van het complex is ondermaats',
      'Geluidshinder door nachtelijke transporten',
      'Zonnepanelen veroorzaken reflectie op aanpalend',
      'Verkeersintensiteit stijgt onaanvaardbaar',
      'Archeologische vindplaats wordt niet beschermd',
      'Riolering kan extra belasting niet verwerken',
      'Trillingen door zwaar verkeer beschadigen fundament',
      'Biodiversiteit in het natuurgebied vermindert',
      'Schoolroutes worden onveilig door vrachtverkeer',
      'Horizonvervuiling door hoogte van constructie',
      'Bodemverontreiniging is niet volledig gesaneerd',
    ];
    const veelBezwaren = uniekeTeksten.map((tekst, i) => ({
      referentieId: i, bezwaarId: i, bestandsnaam: `doc${i}.txt`,
      passage: tekst,
      scorePercentage: 80 + i, toewijzingsmethode: 'HDBSCAN',
    }));
    const mockMetVeel = {
      kernbezwaren: [{
        id: 1, samenvatting: 'Groot kernbezwaar',
        individueleBezwaren: veelBezwaren, antwoord: null,
      }],
    };

    mountEnLaad(MOCK_CLUSTERING_KLAAR, mockMetVeel);
    cy.wait('@kernbezwaren');

    cy.get('bezwaarschriften-kernbezwaren')
        .find('.kernbezwaar-actie vl-button[icon="search"]')
        .first()
        .click();

    cy.get('bezwaarschriften-kernbezwaren')
        .find('.paginering')
        .should('exist')
        .and('contain.text', '1 / 2');

    // Eerste pagina: 15 groepen
    cy.get('bezwaarschriften-kernbezwaren')
        .find('.passage-groep')
        .should('have.length', 15);
  });

  it('navigeert naar volgende pagina', () => {
    const uniekeTeksten = [
      'Windmolens zorgen voor slagschaduw op woningen',
      'Fietspaden langs de ringweg ontbreken volledig',
      'Luchtkwaliteit is onvoldoende onderzocht in rapport',
      'Wateroverlast door verharding van het terrein',
      'Beschermde vleermuissoorten worden bedreigd',
      'Parkeergelegenheid is totaal ontoereikend',
      'Grondwaterpeil daalt door bouwactiviteiten',
      'Erfgoedwaarde van het pand wordt aangetast',
      'Stikstofuitstoot overschrijdt de normen',
      'Brandveiligheid van het complex is ondermaats',
      'Geluidshinder door nachtelijke transporten',
      'Zonnepanelen veroorzaken reflectie op aanpalend',
      'Verkeersintensiteit stijgt onaanvaardbaar',
      'Archeologische vindplaats wordt niet beschermd',
      'Riolering kan extra belasting niet verwerken',
      'Trillingen door zwaar verkeer beschadigen fundament',
      'Biodiversiteit in het natuurgebied vermindert',
      'Schoolroutes worden onveilig door vrachtverkeer',
      'Horizonvervuiling door hoogte van constructie',
      'Bodemverontreiniging is niet volledig gesaneerd',
    ];
    const veelBezwaren = uniekeTeksten.map((tekst, i) => ({
      referentieId: i, bezwaarId: i, bestandsnaam: `doc${i}.txt`,
      passage: tekst,
      scorePercentage: 80, toewijzingsmethode: 'HDBSCAN',
    }));
    const mockMetVeel = {
      kernbezwaren: [{
        id: 1, samenvatting: 'Groot kernbezwaar',
        individueleBezwaren: veelBezwaren, antwoord: null,
      }],
    };

    mountEnLaad(MOCK_CLUSTERING_KLAAR, mockMetVeel);
    cy.wait('@kernbezwaren');

    cy.get('bezwaarschriften-kernbezwaren')
        .find('.kernbezwaar-actie vl-button[icon="search"]')
        .first()
        .click();

    // Klik volgende
    cy.get('bezwaarschriften-kernbezwaren')
        .find('.paginering vl-button')
        .contains('Volgende')
        .click();

    cy.get('bezwaarschriften-kernbezwaren')
        .find('.paginering')
        .should('contain.text', '2 / 2');

    // Pagina 2: 5 groepen
    cy.get('bezwaarschriften-kernbezwaren')
        .find('.passage-groep')
        .should('have.length', 5);
  });
});

// === Passage deduplicatie ===

describe('side panel passage-deduplicatie', () => {
  const MOCK_MET_DUPLICATEN = {
    kernbezwaren: [{
      id: 1, samenvatting: 'Geluidshinder',
      antwoord: null,
      individueleBezwaren: [
        {referentieId: 1, bezwaarId: 1, bestandsnaam: '001.txt', passage: 'Te veel geluid in de buurt',
          scorePercentage: null, toewijzingsmethode: 'HDBSCAN'},
        {referentieId: 2, bezwaarId: 2, bestandsnaam: '002.txt', passage: 'Te veel geluid in de buurt',
          scorePercentage: null, toewijzingsmethode: 'HDBSCAN'},
        {referentieId: 3, bezwaarId: 3, bestandsnaam: '003.txt', passage: 'Te veel geluid in de buurt',
          scorePercentage: null, toewijzingsmethode: 'HDBSCAN'},
        {referentieId: 4, bezwaarId: 4, bestandsnaam: '004.txt', passage: 'Te veel geluid in de buurt',
          scorePercentage: null, toewijzingsmethode: 'HDBSCAN'},
        {referentieId: 5, bezwaarId: 5, bestandsnaam: '005.txt', passage: 'Te veel geluid in de buurt',
          scorePercentage: null, toewijzingsmethode: 'HDBSCAN'},
        {referentieId: 6, bezwaarId: 6, bestandsnaam: '006.txt', passage: 'Te veel geluid in de buurt',
          scorePercentage: null, toewijzingsmethode: 'HDBSCAN'},
        {referentieId: 7, bezwaarId: 7, bestandsnaam: '007.txt', passage: 'Te veel geluid in de buurt',
          scorePercentage: null, toewijzingsmethode: 'HDBSCAN'},
        {referentieId: 8, bezwaarId: 8, bestandsnaam: '008.txt', passage: 'Verkeer is gevaarlijk',
          scorePercentage: null, toewijzingsmethode: 'HDBSCAN'},
      ],
    }],
  };

  beforeEach(() => {
    mountEnLaad(MOCK_CLUSTERING_KLAAR, MOCK_MET_DUPLICATEN);
    cy.wait('@kernbezwaren');

    cy.get('bezwaarschriften-kernbezwaren')
        .find('.kernbezwaar-actie vl-button[icon="search"]')
        .click();
  });

  it('toont gegroepeerde passage slechts 1x met documentenlijst', () => {
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
        .find('.passage-groep')
        .first()
        .find('.passage-toon-alle')
        .click();

    cy.get('bezwaarschriften-kernbezwaren')
        .find('.passage-groep')
        .first()
        .find('.passage-document-link')
        .should('have.length', 7);

    cy.get('bezwaarschriften-kernbezwaren')
        .find('.passage-groep')
        .first()
        .find('.passage-toon-alle')
        .should('not.exist');
  });

  it('toont header met totaal en groepen-aantal', () => {
    cy.get('bezwaarschriften-kernbezwaren')
        .find('#side-sheet-inhoud')
        .should('contain.text', '8 individuele bezwaren')
        .and('contain.text', '2 unieke passages');
  });

  it('toont enkel document als passage uniek is', () => {
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

// === Noise toewijzing ===

describe('handmatige toewijzing voor noise bezwaren', () => {
  const MOCK_MET_NOISE = {
    kernbezwaren: [
      {
        id: 1, samenvatting: 'Geluidshinder',
        individueleBezwaren: [
          {referentieId: 10, bezwaarId: 100, bestandsnaam: '001.txt', passage: 'Geluid',
            scorePercentage: 95, toewijzingsmethode: 'HDBSCAN'},
        ],
        antwoord: null,
      },
      {
        id: 99, samenvatting: 'Niet-geclusterde bezwaren',
        individueleBezwaren: [
          {referentieId: 30, bezwaarId: 300, bestandsnaam: '005.txt', passage: 'Los punt',
            scorePercentage: null, toewijzingsmethode: null},
        ],
        antwoord: null,
      },
    ],
  };

  beforeEach(() => {
    mountEnLaad(MOCK_CLUSTERING_KLAAR, MOCK_MET_NOISE);
    cy.wait('@kernbezwaren');
  });

  it('toont toewijzen-knop bij noise passages', () => {
    // Open side panel voor noise
    cy.get('bezwaarschriften-kernbezwaren')
        .find('.kernbezwaar-item')
        .last()
        .find('.kernbezwaar-actie vl-button[icon="search"]')
        .click();

    cy.get('bezwaarschriften-kernbezwaren')
        .find('.passage-groep vl-button')
        .should('contain.text', 'Toewijzen');
  });

  it('geen toewijzen-knop bij reguliere kernbezwaren', () => {
    // Open side panel voor eerste (niet-noise) kernbezwaar
    cy.get('bezwaarschriften-kernbezwaren')
        .find('.kernbezwaar-item')
        .first()
        .find('.kernbezwaar-actie vl-button[icon="search"]')
        .click();

    cy.get('bezwaarschriften-kernbezwaren')
        .find('.passage-groep vl-button')
        .should('not.exist');
  });

  it('toont suggesties dropdown bij klik op toewijzen', () => {
    cy.intercept('GET', '/api/v1/projects/testproject/noise/300/suggesties', {
      statusCode: 200,
      body: [
        {kernbezwaarId: 1, scorePercentage: 82, samenvatting: 'Geluidshinder'},
        {kernbezwaarId: 2, scorePercentage: 65, samenvatting: 'Fietspaden ontbreken'},
      ],
    }).as('suggesties');

    // Open side panel voor noise
    cy.get('bezwaarschriften-kernbezwaren')
        .find('.kernbezwaar-item')
        .last()
        .find('.kernbezwaar-actie vl-button[icon="search"]')
        .click();

    cy.get('bezwaarschriften-kernbezwaren')
        .find('.passage-groep vl-button')
        .contains('Toewijzen')
        .click();

    cy.wait('@suggesties');

    cy.get('bezwaarschriften-kernbezwaren')
        .find('.toewijzen-dropdown')
        .should('exist');

    cy.get('bezwaarschriften-kernbezwaren')
        .find('.suggestie-item')
        .should('have.length', 2);

    cy.get('bezwaarschriften-kernbezwaren')
        .find('.suggestie-item')
        .first()
        .should('contain.text', 'Geluidshinder')
        .and('contain.text', '82%');
  });

  it('voegt toe knop is disabled tot selectie, voert toewijzing uit na klik', () => {
    cy.intercept('GET', '/api/v1/projects/testproject/noise/300/suggesties', {
      statusCode: 200,
      body: [
        {kernbezwaarId: 1, scorePercentage: 82, samenvatting: 'Geluidshinder'},
      ],
    }).as('suggesties');

    cy.intercept('PUT', '/api/v1/projects/testproject/referenties/30/toewijzing', {
      statusCode: 200,
    }).as('toewijzing');

    // Open side panel voor noise
    cy.get('bezwaarschriften-kernbezwaren')
        .find('.kernbezwaar-item')
        .last()
        .find('.kernbezwaar-actie vl-button[icon="search"]')
        .click();

    cy.get('bezwaarschriften-kernbezwaren')
        .find('.passage-groep vl-button')
        .contains('Toewijzen')
        .click();

    cy.wait('@suggesties');

    // Voeg toe knop is disabled
    cy.get('bezwaarschriften-kernbezwaren')
        .find('.toewijzen-dropdown vl-button')
        .contains('Voeg toe')
        .should('have.attr', 'disabled');

    // Selecteer suggestie
    cy.get('bezwaarschriften-kernbezwaren')
        .find('.suggestie-item')
        .first()
        .click();

    // Suggestie is geselecteerd
    cy.get('bezwaarschriften-kernbezwaren')
        .find('.suggestie-item.suggestie-geselecteerd')
        .should('exist');

    // Voeg toe knop is nu enabled
    cy.get('bezwaarschriften-kernbezwaren')
        .find('.toewijzen-dropdown vl-button')
        .contains('Voeg toe')
        .should('not.have.attr', 'disabled');

    // Klik voeg toe
    cy.get('bezwaarschriften-kernbezwaren')
        .find('.toewijzen-dropdown vl-button')
        .contains('Voeg toe')
        .click();

    cy.wait('@toewijzing').its('request.body')
        .should('deep.equal', {kernbezwaarId: 1});
  });

  it('toont lege melding als geen suggesties beschikbaar', () => {
    cy.intercept('GET', '/api/v1/projects/testproject/noise/300/suggesties', {
      statusCode: 200,
      body: [],
    }).as('suggesties');

    // Open side panel voor noise
    cy.get('bezwaarschriften-kernbezwaren')
        .find('.kernbezwaar-item')
        .last()
        .find('.kernbezwaar-actie vl-button[icon="search"]')
        .click();

    cy.get('bezwaarschriften-kernbezwaren')
        .find('.passage-groep vl-button')
        .contains('Toewijzen')
        .click();

    cy.wait('@suggesties');

    cy.get('bezwaarschriften-kernbezwaren')
        .find('.toewijzen-dropdown')
        .should('contain.text', 'Geen suggesties beschikbaar');
  });
});

// === Clustering parameters ===

describe('clustering parameters', () => {
  beforeEach(() => {
    mountEnLaad(MOCK_CLUSTERING_KLAAR, MOCK_KERNBEZWAREN);
    cy.wait('@kernbezwaren');
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
        .first()
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
        .first()
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

  it('toont cluster-op-passages checkbox als aangevinkt', () => {
    cy.get('bezwaarschriften-kernbezwaren')
        .find('.clustering-params input[type="checkbox"]')
        .last()
        .should('be.checked');
  });

  it('stuurt update bij uitvinken cluster-op-passages', () => {
    cy.intercept('PUT', '/api/v1/clustering-config', (req) => {
      expect(req.body.clusterOpPassages).to.equal(false);
      req.reply({statusCode: 200, body: {...MOCK_CONFIG, clusterOpPassages: false}});
    }).as('updatePassages');

    cy.get('bezwaarschriften-kernbezwaren')
        .find('.clustering-params input[type="checkbox"]')
        .last()
        .uncheck();

    cy.wait('@updatePassages');
  });
});

// === Lege staat ===

describe('lege staat', () => {
  it('toont melding als extractie niet klaar', () => {
    mountEnLaad(null);

    cy.get('bezwaarschriften-kernbezwaren')
        .find('.lege-staat')
        .should('contain.text', 'Nog geen bezwaren');
  });

  it('toont cluster-knop met aantal bezwaren als extractie klaar', () => {
    mountEnLaad(null);

    cy.get('bezwaarschriften-kernbezwaren')
        .then(($el) => {
          $el[0].setExtractieKlaar(true);
          $el[0].setAantalBezwaren(42);
        });

    cy.get('bezwaarschriften-kernbezwaren')
        .find('.lege-staat')
        .should('contain.text', '42 individuele bezwaren');

    cy.get('bezwaarschriften-kernbezwaren')
        .find('#groepeer-knop')
        .should('contain.text', 'Cluster bezwaren');
  });
});
