import {html} from 'lit';
import '../src/js/bezwaarschriften-bezwaren-tabel';

function switchNaarManueelTab() {
  cy.get('bezwaarschriften-bezwaren-tabel')
      .find('vl-tabs')
      .then(($tabs) => {
        const sr = $tabs[0].shadowRoot;
        const tabLink = sr && sr.querySelector('vl-tab#manueel [tab]');
        if (tabLink) tabLink.click();
      });
  // Wacht tot de manueel-sectie zichtbaar is
  cy.get('bezwaarschriften-bezwaren-tabel')
      .find('vl-tabs')
      .shadow()
      .find('vl-tab-section#manueel-pane[show="true"]')
      .should('exist');
}

describe('bezwaarschriften-bezwaren-tabel extractie-details', () => {
  const MOCK_DETAILS_MET_OPMERKINGEN = {
    bestandsnaam: 'bezwaar-001.txt',
    aantalBezwaren: 3,
    bezwaren: [
      {samenvatting: 'Gevonden bezwaar', passage: 'Dit is een gevonden passage.', passageGevonden: true},
      {samenvatting: 'Niet gevonden bezwaar', passage: 'Deze passage bestaat niet.', passageGevonden: false},
      {samenvatting: 'Nog een gevonden bezwaar', passage: 'Nog een gevonden passage.', passageGevonden: true},
    ],
  };

  const MOCK_DETAILS_ZONDER_OPMERKINGEN = {
    bestandsnaam: 'bezwaar-002.txt',
    aantalBezwaren: 2,
    bezwaren: [
      {samenvatting: 'Eerste bezwaar', passage: 'Eerste passage.', passageGevonden: true},
      {samenvatting: 'Tweede bezwaar', passage: 'Tweede passage.', passageGevonden: true},
    ],
  };

  const MOCK_DETAILS_GEMENGD = {
    bestandsnaam: 'bezwaar-003.txt',
    aantalBezwaren: 4,
    bezwaren: [
      {id: 1, samenvatting: 'AI bezwaar 1', passage: 'AI passage 1.', passageGevonden: true, manueel: false},
      {id: 2, samenvatting: 'AI bezwaar 2', passage: 'AI passage 2.', passageGevonden: false, manueel: false},
      {id: 3, samenvatting: 'Manueel bezwaar 1', passage: 'Manueel passage 1.', passageGevonden: true, manueel: true},
      {id: 4, samenvatting: 'Manueel bezwaar 2', passage: 'Manueel passage 2.', passageGevonden: true, manueel: true},
    ],
  };

  beforeEach(() => {
    cy.mount(html`<bezwaarschriften-bezwaren-tabel></bezwaarschriften-bezwaren-tabel>`);
  });

  // --- Tabs structuur ---

  it('toont twee tabs met correcte aantallen', () => {
    cy.intercept('GET', '/api/v1/projects/*/extracties/*/details', {
      statusCode: 200,
      body: MOCK_DETAILS_GEMENGD,
    }).as('details');

    cy.get('bezwaarschriften-bezwaren-tabel')
        .its(0)
        .invoke('toonExtractieDetails', 'windmolens', 'bezwaar-003.txt');

    cy.wait('@details');

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('vl-tabs')
        .should('exist');

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('vl-tabs-pane#automatisch')
        .should('have.attr', 'title', 'Automatisch (2)');

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('vl-tabs-pane#manueel')
        .should('have.attr', 'title', 'Manueel (2)');
  });

  it('automatisch-tab toont alleen niet-manuele bezwaren', () => {
    cy.intercept('GET', '/api/v1/projects/*/extracties/*/details', {
      statusCode: 200,
      body: MOCK_DETAILS_GEMENGD,
    }).as('details');

    cy.get('bezwaarschriften-bezwaren-tabel')
        .its(0)
        .invoke('toonExtractieDetails', 'windmolens', 'bezwaar-003.txt');

    cy.wait('@details');

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('vl-tabs-pane#automatisch .bezwaar-item')
        .should('have.length', 2);

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('vl-tabs-pane#automatisch .bezwaar-samenvatting')
        .first()
        .should('contain.text', 'AI bezwaar');
  });

  it('manueel-tab toont alleen manuele bezwaren', () => {
    cy.intercept('GET', '/api/v1/projects/*/extracties/*/details', {
      statusCode: 200,
      body: MOCK_DETAILS_GEMENGD,
    }).as('details');

    cy.get('bezwaarschriften-bezwaren-tabel')
        .its(0)
        .invoke('toonExtractieDetails', 'windmolens', 'bezwaar-003.txt');

    cy.wait('@details');

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('vl-tabs-pane#manueel .bezwaar-item')
        .should('have.length', 2);

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('vl-tabs-pane#manueel .bezwaar-samenvatting')
        .first()
        .should('contain.text', 'Manueel bezwaar');
  });

  it('manueel-tab toont "Bezwaar toevoegen" knop', () => {
    cy.intercept('GET', '/api/v1/projects/*/extracties/*/details', {
      statusCode: 200,
      body: MOCK_DETAILS_GEMENGD,
    }).as('details');

    cy.get('bezwaarschriften-bezwaren-tabel')
        .its(0)
        .then((el) => {
          el.projectNaam = 'windmolens';
        });

    cy.get('bezwaarschriften-bezwaren-tabel')
        .its(0)
        .invoke('toonExtractieDetails', 'windmolens', 'bezwaar-003.txt');

    cy.wait('@details');

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('vl-tabs-pane#manueel #bezwaar-toevoegen-knop')
        .should('exist');
  });

  it('klik op "Bezwaar toevoegen" toont formulier in manueel-tab', () => {
    cy.intercept('GET', '/api/v1/projects/*/extracties/*/details', {
      statusCode: 200,
      body: MOCK_DETAILS_GEMENGD,
    }).as('details');

    cy.get('bezwaarschriften-bezwaren-tabel')
        .its(0)
        .then((el) => {
          el.projectNaam = 'windmolens';
        });

    cy.get('bezwaarschriften-bezwaren-tabel')
        .its(0)
        .invoke('toonExtractieDetails', 'windmolens', 'bezwaar-003.txt');

    cy.wait('@details');

    switchNaarManueelTab();

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('vl-tabs-pane#manueel #bezwaar-toevoegen-knop')
        .click();

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('vl-tabs-pane#manueel #manueel-bezwaar-formulier')
        .should('exist');
  });

  it('sluit-knop is een vl-button met icon="close" en ghost', () => {
    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('vl-button#extractie-side-sheet-sluit')
        .should('exist')
        .and('have.attr', 'icon', 'close')
        .and('have.attr', 'ghost');
  });

  // --- Automatisch-tab: sortering en waarschuwingen ---

  it('toont niet-gevonden bezwaren bovenaan in automatisch-tab', () => {
    cy.intercept('GET', '/api/v1/projects/*/extracties/*/details', {
      statusCode: 200,
      body: MOCK_DETAILS_MET_OPMERKINGEN,
    }).as('details');

    cy.get('bezwaarschriften-bezwaren-tabel')
        .its(0)
        .invoke('toonExtractieDetails', 'windmolens', 'bezwaar-001.txt');

    cy.wait('@details');

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('vl-tabs-pane#automatisch .bezwaar-item')
        .first()
        .find('.bezwaar-samenvatting')
        .should('have.text', 'Niet gevonden bezwaar');
  });

  it('toont waarschuwingstekst bij niet-gevonden passage', () => {
    cy.intercept('GET', '/api/v1/projects/*/extracties/*/details', {
      statusCode: 200,
      body: MOCK_DETAILS_MET_OPMERKINGEN,
    }).as('details');

    cy.get('bezwaarschriften-bezwaren-tabel')
        .its(0)
        .invoke('toonExtractieDetails', 'windmolens', 'bezwaar-001.txt');

    cy.wait('@details');

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('vl-tabs-pane#automatisch .bezwaar-waarschuwing')
        .should('have.length', 1)
        .and('contain.text', 'Passage kon niet gevonden worden');
  });

  it('toont geen waarschuwing bij gevonden passages', () => {
    cy.intercept('GET', '/api/v1/projects/*/extracties/*/details', {
      statusCode: 200,
      body: MOCK_DETAILS_ZONDER_OPMERKINGEN,
    }).as('details');

    cy.get('bezwaarschriften-bezwaren-tabel')
        .its(0)
        .invoke('toonExtractieDetails', 'windmolens', 'bezwaar-002.txt');

    cy.wait('@details');

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('.bezwaar-waarschuwing')
        .should('not.exist');
  });

  // --- Tabel iconen ---

  it('toont waarschuwingsicoon bij bestandsnaam in tabel als heeftPassagesDieNietInTekstVoorkomen true is', () => {
    cy.get('bezwaarschriften-bezwaren-tabel')
        .its(0)
        .then((el) => {
          el.projectNaam = 'windmolens';
          el.bezwaren = [
            {bestandsnaam: 'bezwaar-001.txt', tekstExtractieStatus: 'KLAAR', bezwaarExtractieStatus: 'KLAAR', aantalBezwaren: 3, heeftPassagesDieNietInTekstVoorkomen: true},
            {bestandsnaam: 'bezwaar-002.txt', tekstExtractieStatus: 'KLAAR', bezwaarExtractieStatus: 'KLAAR', aantalBezwaren: 2, heeftPassagesDieNietInTekstVoorkomen: false},
          ];
        });

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('span[title="Niet alle passages konden gevonden worden"]')
        .should('have.length', 1)
        .and('contain.text', '\u26A0\uFE0F');
  });

  it('toont geen waarschuwingsicoon bij bestandsnaam als heeftPassagesDieNietInTekstVoorkomen false is', () => {
    cy.get('bezwaarschriften-bezwaren-tabel')
        .its(0)
        .then((el) => {
          el.projectNaam = 'windmolens';
          el.bezwaren = [
            {bestandsnaam: 'bezwaar-001.txt', tekstExtractieStatus: 'KLAAR', bezwaarExtractieStatus: 'KLAAR', aantalBezwaren: 3, heeftPassagesDieNietInTekstVoorkomen: false},
            {bestandsnaam: 'bezwaar-002.txt', tekstExtractieStatus: 'KLAAR', bezwaarExtractieStatus: 'KLAAR', aantalBezwaren: 2, heeftPassagesDieNietInTekstVoorkomen: false},
          ];
        });

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('span[title="Niet alle passages konden gevonden worden"]')
        .should('not.exist');
  });

  it('toont waarschuwingsicoon na werkBijMetTaakUpdate met heeftPassagesDieNietInTekstVoorkomen', () => {
    cy.get('bezwaarschriften-bezwaren-tabel')
        .its(0)
        .then((el) => {
          el.projectNaam = 'windmolens';
          el.bezwaren = [
            {bestandsnaam: 'bezwaar-001.txt', tekstExtractieStatus: 'KLAAR', bezwaarExtractieStatus: 'BEZIG', aantalBezwaren: null, heeftPassagesDieNietInTekstVoorkomen: false},
          ];
        });

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('span[title="Niet alle passages konden gevonden worden"]')
        .should('not.exist');

    cy.get('bezwaarschriften-bezwaren-tabel')
        .its(0)
        .invoke('werkBijMetTaakUpdate', {
          bestandsnaam: 'bezwaar-001.txt',
          tekstExtractieStatus: 'KLAAR', bezwaarExtractieStatus: 'KLAAR',
          aantalBezwaren: 3,
          heeftPassagesDieNietInTekstVoorkomen: true,
        });

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('span[title="Niet alle passages konden gevonden worden"]')
        .should('have.length', 1);
  });

  it('side-panel titel toont bestandsnaam en aantal bezwaren', () => {
    cy.intercept('GET', '/api/v1/projects/*/extracties/*/details', {
      statusCode: 200,
      body: MOCK_DETAILS_ZONDER_OPMERKINGEN,
    }).as('details');

    cy.get('bezwaarschriften-bezwaren-tabel')
        .its(0)
        .invoke('toonExtractieDetails', 'windmolens', 'bezwaar-002.txt');

    cy.wait('@details');

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('#extractie-side-sheet-titel')
        .should('contain.text', '2 bezwaren gevonden');
  });

  // --- ✍️ emoji in tabel + heeftManueel ---

  it('toont ✍️ emoji bij bestandsnaam als heeftManueel true is', () => {
    cy.get('bezwaarschriften-bezwaren-tabel')
        .its(0)
        .then((el) => {
          el.projectNaam = 'windmolens';
          el.bezwaren = [
            {bestandsnaam: 'bezwaar-001.txt', tekstExtractieStatus: 'KLAAR', bezwaarExtractieStatus: 'KLAAR', aantalBezwaren: 3,
              heeftPassagesDieNietInTekstVoorkomen: false, heeftManueel: true},
          ];
        });

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('span[title="Bevat manueel toegevoegde bezwaren"]')
        .should('have.length', 1)
        .and('contain.text', '\u270D\uFE0F');
  });

  it('toont geen ✍️ emoji als heeftManueel false is', () => {
    cy.get('bezwaarschriften-bezwaren-tabel')
        .its(0)
        .then((el) => {
          el.projectNaam = 'windmolens';
          el.bezwaren = [
            {bestandsnaam: 'bezwaar-001.txt', tekstExtractieStatus: 'KLAAR', bezwaarExtractieStatus: 'KLAAR', aantalBezwaren: 3,
              heeftPassagesDieNietInTekstVoorkomen: false, heeftManueel: false},
          ];
        });

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('span[title="Bevat manueel toegevoegde bezwaren"]')
        .should('not.exist');
  });

  it('toont beide iconen als heeftPassagesDieNietInTekstVoorkomen en heeftManueel true zijn', () => {
    cy.get('bezwaarschriften-bezwaren-tabel')
        .its(0)
        .then((el) => {
          el.projectNaam = 'windmolens';
          el.bezwaren = [
            {bestandsnaam: 'bezwaar-001.txt', tekstExtractieStatus: 'KLAAR', bezwaarExtractieStatus: 'KLAAR', aantalBezwaren: 3,
              heeftPassagesDieNietInTekstVoorkomen: true, heeftManueel: true},
          ];
        });

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('span[title="Niet alle passages konden gevonden worden"]')
        .should('have.length', 1);
    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('span[title="Bevat manueel toegevoegde bezwaren"]')
        .should('have.length', 1);
  });

  // --- Manueel-tab: verwijder-knop ---

  it('toont verwijder-knop bij manueel bezwaar in manueel-tab', () => {
    cy.intercept('GET', '/api/v1/projects/*/extracties/*/details', {
      statusCode: 200,
      body: MOCK_DETAILS_GEMENGD,
    }).as('details');

    cy.get('bezwaarschriften-bezwaren-tabel')
        .its(0)
        .invoke('toonExtractieDetails', 'windmolens', 'bezwaar-003.txt');

    cy.wait('@details');

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('vl-tabs-pane#manueel .bezwaar-header vl-button[icon="bin"]')
        .should('have.length', 2);

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('vl-tabs-pane#automatisch .bezwaar-header vl-button[icon="bin"]')
        .should('have.length', 2);
  });

  it('verwijdert manueel bezwaar na bevestiging in modal', () => {
    cy.intercept('GET', '/api/v1/projects/*/extracties/*/details', {
      statusCode: 200,
      body: {
        bestandsnaam: 'bezwaar-001.txt',
        aantalBezwaren: 1,
        bezwaren: [
          {id: 10, samenvatting: 'Manueel bezwaar', passage: 'Passage.', passageGevonden: true, manueel: true},
        ],
      },
    }).as('details');

    cy.intercept('DELETE', '/api/v1/projects/windmolens/extracties/bezwaar-001.txt/bezwaren/10', {
      statusCode: 204,
    }).as('verwijder');

    cy.get('bezwaarschriften-bezwaren-tabel')
        .its(0)
        .then((el) => {
          el.projectNaam = 'windmolens';
        });

    cy.get('bezwaarschriften-bezwaren-tabel')
        .its(0)
        .invoke('toonExtractieDetails', 'windmolens', 'bezwaar-001.txt');

    cy.wait('@details');

    switchNaarManueelTab();

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('vl-tabs-pane#manueel .bezwaar-header vl-button[icon="bin"]')
        .click();

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('#verwijder-bezwaar-modal')
        .should('exist');

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('#verwijder-bezwaar-bevestig')
        .click({force: true});

    cy.wait('@verwijder');
  });


  it('toont verwijder-knop ook bij AI-bezwaar in automatisch-tab', () => {
    cy.intercept('GET', '/api/v1/projects/*/extracties/*/details', {
      statusCode: 200,
      body: MOCK_DETAILS_GEMENGD,
    }).as('details');

    cy.get('bezwaarschriften-bezwaren-tabel')
        .its(0)
        .invoke('toonExtractieDetails', 'windmolens', 'bezwaar-003.txt');

    cy.wait('@details');

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('vl-tabs-pane#automatisch .bezwaar-header vl-button[icon="bin"]')
        .should('have.length', 2);
  });

  it('verwijdert AI-bezwaar en herlaadt op automatisch-tab', () => {
    cy.intercept('GET', '/api/v1/projects/*/extracties/*/details', {
      statusCode: 200,
      body: {
        bestandsnaam: 'bezwaar-003.txt',
        aantalBezwaren: 1,
        bezwaren: [
          {id: 1, samenvatting: 'AI bezwaar', passage: 'Passage.', passageGevonden: true, manueel: false},
        ],
      },
    }).as('details');

    cy.intercept('DELETE', '/api/v1/projects/windmolens/extracties/bezwaar-003.txt/bezwaren/1', {
      statusCode: 204,
    }).as('verwijder');

    cy.get('bezwaarschriften-bezwaren-tabel')
        .its(0)
        .then((el) => {
          el.projectNaam = 'windmolens';
        });

    cy.get('bezwaarschriften-bezwaren-tabel')
        .its(0)
        .invoke('toonExtractieDetails', 'windmolens', 'bezwaar-003.txt');

    cy.wait('@details');

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('vl-tabs-pane#automatisch .bezwaar-header vl-button[icon="bin"]')
        .first()
        .click();

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('#verwijder-bezwaar-bevestig')
        .click({force: true});

    cy.wait('@verwijder');

    // Side-panel herlaadt op automatisch-tab
    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('vl-tabs')
        .should('have.attr', 'active-tab', 'automatisch');
  });

  // --- Formulier tests (in manueel-tab context) ---

  it('toont inline formulier na klik op toevoegen-knop in manueel-tab', () => {
    cy.intercept('GET', '/api/v1/projects/*/extracties/*/details', {
      statusCode: 200,
      body: MOCK_DETAILS_ZONDER_OPMERKINGEN,
    }).as('details');

    cy.get('bezwaarschriften-bezwaren-tabel')
        .its(0)
        .then((el) => {
          el.projectNaam = 'windmolens';
        });

    cy.get('bezwaarschriften-bezwaren-tabel')
        .its(0)
        .invoke('toonExtractieDetails', 'windmolens', 'bezwaar-002.txt');

    cy.wait('@details');

    switchNaarManueelTab();

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('#bezwaar-toevoegen-knop')
        .click();

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('#manueel-bezwaar-formulier')
        .should('exist');

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('#manueel-samenvatting')
        .should('exist');

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('#manueel-passage')
        .should('exist');
  });

  it('opslaan-knop disabled bij lege velden', () => {
    cy.intercept('GET', '/api/v1/projects/*/extracties/*/details', {
      statusCode: 200,
      body: MOCK_DETAILS_ZONDER_OPMERKINGEN,
    }).as('details');

    cy.get('bezwaarschriften-bezwaren-tabel')
        .its(0)
        .then((el) => {
          el.projectNaam = 'windmolens';
        });

    cy.get('bezwaarschriften-bezwaren-tabel')
        .its(0)
        .invoke('toonExtractieDetails', 'windmolens', 'bezwaar-002.txt');

    cy.wait('@details');

    switchNaarManueelTab();

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('#bezwaar-toevoegen-knop')
        .click();

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('#manueel-opslaan')
        .should('have.attr', 'disabled');
  });

  it('slaat manueel bezwaar op en herlaadt side-panel', () => {
    cy.intercept('GET', '/api/v1/projects/*/extracties/*/details', {
      statusCode: 200,
      body: MOCK_DETAILS_ZONDER_OPMERKINGEN,
    }).as('details');

    cy.intercept('POST', '/api/v1/projects/windmolens/extracties/bezwaar-002.txt/bezwaren', {
      statusCode: 201,
      body: {id: 10, samenvatting: 'Nieuw bezwaar', passage: 'Eerste passage.', passageGevonden: true, manueel: true},
    }).as('opslaan');

    cy.get('bezwaarschriften-bezwaren-tabel')
        .its(0)
        .then((el) => {
          el.projectNaam = 'windmolens';
        });

    cy.get('bezwaarschriften-bezwaren-tabel')
        .its(0)
        .invoke('toonExtractieDetails', 'windmolens', 'bezwaar-002.txt');

    cy.wait('@details');

    switchNaarManueelTab();

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('#bezwaar-toevoegen-knop')
        .click();

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('#manueel-samenvatting')
        .shadow()
        .find('textarea')
        .type('Nieuw bezwaar', {force: true});

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('#manueel-passage')
        .shadow()
        .find('textarea')
        .type('Eerste passage.', {force: true});

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('#manueel-opslaan')
        .click({force: true});

    cy.wait('@opslaan');
  });

  it('blijft op manueel-tab na opslaan van manueel bezwaar', () => {
    const detailsNaOpslaan = {
      bestandsnaam: 'bezwaar-002.txt',
      aantalBezwaren: 3,
      bezwaren: [
        {id: 1, samenvatting: 'Eerste bezwaar', passage: 'Eerste passage.', passageGevonden: true, manueel: false},
        {id: 2, samenvatting: 'Tweede bezwaar', passage: 'Tweede passage.', passageGevonden: true, manueel: false},
        {id: 10, samenvatting: 'Nieuw bezwaar', passage: 'Nieuwe passage.', passageGevonden: true, manueel: true},
      ],
    };

    cy.intercept('GET', '/api/v1/projects/*/extracties/*/details', {
      statusCode: 200,
      body: MOCK_DETAILS_ZONDER_OPMERKINGEN,
    }).as('details');

    cy.intercept('POST', '/api/v1/projects/windmolens/extracties/bezwaar-002.txt/bezwaren', {
      statusCode: 201,
      body: {id: 10, samenvatting: 'Nieuw bezwaar', passage: 'Nieuwe passage.', passageGevonden: true, manueel: true},
    }).as('opslaan');

    cy.get('bezwaarschriften-bezwaren-tabel')
        .its(0)
        .then((el) => {
          el.projectNaam = 'windmolens';
        });

    cy.get('bezwaarschriften-bezwaren-tabel')
        .its(0)
        .invoke('toonExtractieDetails', 'windmolens', 'bezwaar-002.txt');

    cy.wait('@details');

    switchNaarManueelTab();

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('#bezwaar-toevoegen-knop')
        .click();

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('#manueel-samenvatting')
        .shadow()
        .find('textarea')
        .type('Nieuw bezwaar', {force: true});

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('#manueel-passage')
        .shadow()
        .find('textarea')
        .type('Nieuwe passage.', {force: true});

    // Na opslaan: herlaad geeft nu detailsNaOpslaan terug
    cy.intercept('GET', '/api/v1/projects/*/extracties/*/details', {
      statusCode: 200,
      body: detailsNaOpslaan,
    }).as('detailsHerladen');

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('#manueel-opslaan')
        .click({force: true});

    cy.wait('@opslaan');
    cy.wait('@detailsHerladen');

    // Na herlaad moet de manueel-tab actief zijn
    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('vl-tabs')
        .should('have.attr', 'active-tab', 'manueel');

    // En het manueel bezwaar moet zichtbaar zijn in de manueel-tab
    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('vl-tabs-pane#manueel .bezwaar-samenvatting')
        .should('contain.text', 'Nieuw bezwaar');
  });

  it('toont foutmelding bij ongeldige passage', () => {
    cy.intercept('GET', '/api/v1/projects/*/extracties/*/details', {
      statusCode: 200,
      body: MOCK_DETAILS_ZONDER_OPMERKINGEN,
    }).as('details');

    cy.intercept('POST', '/api/v1/projects/windmolens/extracties/bezwaar-002.txt/bezwaren', {
      statusCode: 400,
      body: {fout: 'Passage komt niet voor in het originele document'},
    }).as('opslaan');

    cy.get('bezwaarschriften-bezwaren-tabel')
        .its(0)
        .then((el) => {
          el.projectNaam = 'windmolens';
        });

    cy.get('bezwaarschriften-bezwaren-tabel')
        .its(0)
        .invoke('toonExtractieDetails', 'windmolens', 'bezwaar-002.txt');

    cy.wait('@details');

    switchNaarManueelTab();

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('#bezwaar-toevoegen-knop')
        .click();

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('#manueel-samenvatting')
        .shadow()
        .find('textarea')
        .type('Samenvatting', {force: true});

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('#manueel-passage')
        .shadow()
        .find('textarea')
        .type('Onbekende passage', {force: true});

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('#manueel-opslaan')
        .click({force: true});

    cy.wait('@opslaan');

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('#manueel-foutmelding')
        .should('contain.text', 'Passage komt niet voor in het originele document');
  });

  it('annuleert formulier bij klik op annuleer-knop', () => {
    cy.intercept('GET', '/api/v1/projects/*/extracties/*/details', {
      statusCode: 200,
      body: MOCK_DETAILS_ZONDER_OPMERKINGEN,
    }).as('details');

    cy.get('bezwaarschriften-bezwaren-tabel')
        .its(0)
        .then((el) => {
          el.projectNaam = 'windmolens';
        });

    cy.get('bezwaarschriften-bezwaren-tabel')
        .its(0)
        .invoke('toonExtractieDetails', 'windmolens', 'bezwaar-002.txt');

    cy.wait('@details');

    switchNaarManueelTab();

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('#bezwaar-toevoegen-knop')
        .click();

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('#manueel-bezwaar-formulier')
        .should('exist');

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('#manueel-annuleer')
        .click({force: true});

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('#manueel-bezwaar-formulier')
        .should('not.exist');
  });

  it('werkBijMetTaakUpdate met aantalBezwaren null overschrijft bestaande waarde niet', () => {
    cy.get('bezwaarschriften-bezwaren-tabel')
        .its(0)
        .then((el) => {
          el.projectNaam = 'windmolens';
          el.bezwaren = [
            {bestandsnaam: 'bezwaar-001.txt', tekstExtractieStatus: 'KLAAR', bezwaarExtractieStatus: 'KLAAR', aantalBezwaren: 7, heeftPassagesDieNietInTekstVoorkomen: false},
          ];
        });

    // Simuleer een _syncExtracties update met aantalBezwaren: null (zoals de /extracties endpoint retourneert)
    cy.get('bezwaarschriften-bezwaren-tabel')
        .its(0)
        .then((el) => {
          el.werkBijMetTaakUpdate({
            bestandsnaam: 'bezwaar-001.txt',
            bezwaarExtractieStatus: 'KLAAR',
            aantalWoorden: 100,
            aantalBezwaren: null,
            heeftPassagesDieNietInTekstVoorkomen: false,
          });
          // Verifieer het datamodel direct: aantalBezwaren mag niet overschreven zijn met null
          const bezwaar = el.__bronBezwaren.find((b) => b.bestandsnaam === 'bezwaar-001.txt');
          expect(bezwaar.aantalBezwaren).to.equal(7);
        });
  });
});
