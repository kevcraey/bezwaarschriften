import {html} from 'lit';
import '../src/js/bezwaarschriften-bezwaren-tabel';

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

  beforeEach(() => {
    cy.mount(html`<bezwaarschriften-bezwaren-tabel></bezwaarschriften-bezwaren-tabel>`);
  });

  it('toont niet-gevonden bezwaren bovenaan in side-panel', () => {
    cy.intercept('GET', '/api/v1/projects/*/extracties/*/details', {
      statusCode: 200,
      body: MOCK_DETAILS_MET_OPMERKINGEN,
    }).as('details');

    cy.get('bezwaarschriften-bezwaren-tabel')
        .its(0)
        .invoke('toonExtractieDetails', 'windmolens', 'bezwaar-001.txt');

    cy.wait('@details');

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('.bezwaar-item')
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
        .find('.bezwaar-waarschuwing')
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

  it('toont waarschuwingsicoon bij bestandsnaam in tabel als heeftOpmerkingen true is', () => {
    cy.get('bezwaarschriften-bezwaren-tabel')
        .its(0)
        .then((el) => {
          el.projectNaam = 'windmolens';
          el.bezwaren = [
            {bestandsnaam: 'bezwaar-001.txt', status: 'extractie-klaar', aantalBezwaren: 3, heeftOpmerkingen: true},
            {bestandsnaam: 'bezwaar-002.txt', status: 'extractie-klaar', aantalBezwaren: 2, heeftOpmerkingen: false},
          ];
        });

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('span[title="Niet alle passages konden gevonden worden"]')
        .should('have.length', 1)
        .and('contain.text', '\u26A0\uFE0F');
  });

  it('toont geen waarschuwingsicoon bij bestandsnaam als heeftOpmerkingen false is', () => {
    cy.get('bezwaarschriften-bezwaren-tabel')
        .its(0)
        .then((el) => {
          el.projectNaam = 'windmolens';
          el.bezwaren = [
            {bestandsnaam: 'bezwaar-001.txt', status: 'extractie-klaar', aantalBezwaren: 3, heeftOpmerkingen: false},
            {bestandsnaam: 'bezwaar-002.txt', status: 'extractie-klaar', aantalBezwaren: 2, heeftOpmerkingen: false},
          ];
        });

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('span[title="Niet alle passages konden gevonden worden"]')
        .should('not.exist');
  });

  it('toont waarschuwingsicoon na werkBijMetTaakUpdate met heeftOpmerkingen', () => {
    cy.get('bezwaarschriften-bezwaren-tabel')
        .its(0)
        .then((el) => {
          el.projectNaam = 'windmolens';
          el.bezwaren = [
            {bestandsnaam: 'bezwaar-001.txt', status: 'bezig', aantalBezwaren: null, heeftOpmerkingen: false},
          ];
        });

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('span[title="Niet alle passages konden gevonden worden"]')
        .should('not.exist');

    cy.get('bezwaarschriften-bezwaren-tabel')
        .its(0)
        .invoke('werkBijMetTaakUpdate', {
          bestandsnaam: 'bezwaar-001.txt',
          status: 'extractie-klaar',
          aantalBezwaren: 3,
          heeftOpmerkingen: true,
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

  // --- Task 7: ✍️ emoji in tabel + heeftManueel ---

  it('toont ✍️ emoji bij bestandsnaam als heeftManueel true is', () => {
    cy.get('bezwaarschriften-bezwaren-tabel')
        .its(0)
        .then((el) => {
          el.projectNaam = 'windmolens';
          el.bezwaren = [
            {bestandsnaam: 'bezwaar-001.txt', status: 'extractie-klaar', aantalBezwaren: 3,
              heeftOpmerkingen: false, heeftManueel: true},
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
            {bestandsnaam: 'bezwaar-001.txt', status: 'extractie-klaar', aantalBezwaren: 3,
              heeftOpmerkingen: false, heeftManueel: false},
          ];
        });

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('span[title="Bevat manueel toegevoegde bezwaren"]')
        .should('not.exist');
  });

  it('toont beide iconen als heeftOpmerkingen en heeftManueel true zijn', () => {
    cy.get('bezwaarschriften-bezwaren-tabel')
        .its(0)
        .then((el) => {
          el.projectNaam = 'windmolens';
          el.bezwaren = [
            {bestandsnaam: 'bezwaar-001.txt', status: 'extractie-klaar', aantalBezwaren: 3,
              heeftOpmerkingen: true, heeftManueel: true},
          ];
        });

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('span[title="Niet alle passages konden gevonden worden"]')
        .should('have.length', 1);
    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('span[title="Bevat manueel toegevoegde bezwaren"]')
        .should('have.length', 1);
  });

  // --- Task 8: Side-panel bezwaar rendering met manueel label en verwijder-knop ---

  it('toont "Manueel" label bij manueel bezwaar', () => {
    cy.intercept('GET', '/api/v1/projects/*/extracties/*/details', {
      statusCode: 200,
      body: {
        bestandsnaam: 'bezwaar-001.txt',
        aantalBezwaren: 2,
        bezwaren: [
          {id: 1, samenvatting: 'AI bezwaar', passage: 'Passage 1', passageGevonden: true, manueel: false},
          {id: 2, samenvatting: 'Manueel bezwaar', passage: 'Passage 2', passageGevonden: true, manueel: true},
        ],
      },
    }).as('details');

    cy.get('bezwaarschriften-bezwaren-tabel')
        .its(0)
        .invoke('toonExtractieDetails', 'windmolens', 'bezwaar-001.txt');

    cy.wait('@details');

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('.bezwaar-manueel-label')
        .should('have.length', 1)
        .and('have.text', 'Manueel');
  });

  it('toont verwijder-knop alleen bij manueel bezwaar', () => {
    cy.intercept('GET', '/api/v1/projects/*/extracties/*/details', {
      statusCode: 200,
      body: {
        bestandsnaam: 'bezwaar-001.txt',
        aantalBezwaren: 2,
        bezwaren: [
          {id: 1, samenvatting: 'AI bezwaar', passage: 'Passage 1', passageGevonden: true, manueel: false},
          {id: 2, samenvatting: 'Manueel bezwaar', passage: 'Passage 2', passageGevonden: true, manueel: true},
        ],
      },
    }).as('details');

    cy.get('bezwaarschriften-bezwaren-tabel')
        .its(0)
        .invoke('toonExtractieDetails', 'windmolens', 'bezwaar-001.txt');

    cy.wait('@details');

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('.bezwaar-verwijder-knop')
        .should('have.length', 1);
  });

  it('verwijdert manueel bezwaar na klik op verwijder-knop', () => {
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

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('.bezwaar-verwijder-knop')
        .click();

    cy.wait('@verwijder');
  });

  // --- Task 9: + knop en inline formulier in side-panel ---

  it('toont + knop in side-panel header bij extractie-klaar', () => {
    cy.intercept('GET', '/api/v1/projects/*/extracties/*/details', {
      statusCode: 200,
      body: MOCK_DETAILS_ZONDER_OPMERKINGEN,
    }).as('details');

    cy.get('bezwaarschriften-bezwaren-tabel')
        .its(0)
        .then((el) => {
          el.projectNaam = 'windmolens';
          el.bezwaren = [
            {bestandsnaam: 'bezwaar-002.txt', status: 'extractie-klaar', aantalBezwaren: 2, heeftOpmerkingen: false, heeftManueel: false},
          ];
        });

    cy.get('bezwaarschriften-bezwaren-tabel')
        .its(0)
        .invoke('toonExtractieDetails', 'windmolens', 'bezwaar-002.txt');

    cy.wait('@details');

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('#bezwaar-toevoegen-knop')
        .should('exist');
  });

  it('toont inline formulier na klik op + knop', () => {
    cy.intercept('GET', '/api/v1/projects/*/extracties/*/details', {
      statusCode: 200,
      body: MOCK_DETAILS_ZONDER_OPMERKINGEN,
    }).as('details');

    cy.get('bezwaarschriften-bezwaren-tabel')
        .its(0)
        .then((el) => {
          el.projectNaam = 'windmolens';
          el.bezwaren = [
            {bestandsnaam: 'bezwaar-002.txt', status: 'extractie-klaar', aantalBezwaren: 2, heeftOpmerkingen: false, heeftManueel: false},
          ];
        });

    cy.get('bezwaarschriften-bezwaren-tabel')
        .its(0)
        .invoke('toonExtractieDetails', 'windmolens', 'bezwaar-002.txt');

    cy.wait('@details');

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
          el.bezwaren = [
            {bestandsnaam: 'bezwaar-002.txt', status: 'extractie-klaar', aantalBezwaren: 2, heeftOpmerkingen: false, heeftManueel: false},
          ];
        });

    cy.get('bezwaarschriften-bezwaren-tabel')
        .its(0)
        .invoke('toonExtractieDetails', 'windmolens', 'bezwaar-002.txt');

    cy.wait('@details');

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
          el.bezwaren = [
            {bestandsnaam: 'bezwaar-002.txt', status: 'extractie-klaar', aantalBezwaren: 2, heeftOpmerkingen: false, heeftManueel: false},
          ];
        });

    cy.get('bezwaarschriften-bezwaren-tabel')
        .its(0)
        .invoke('toonExtractieDetails', 'windmolens', 'bezwaar-002.txt');

    cy.wait('@details');

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('#bezwaar-toevoegen-knop')
        .click();

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('#manueel-samenvatting')
        .shadow()
        .find('textarea')
        .type('Nieuw bezwaar');

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('#manueel-passage')
        .shadow()
        .find('textarea')
        .type('Eerste passage.');

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('#manueel-opslaan')
        .click();

    cy.wait('@opslaan');
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
          el.bezwaren = [
            {bestandsnaam: 'bezwaar-002.txt', status: 'extractie-klaar', aantalBezwaren: 2, heeftOpmerkingen: false, heeftManueel: false},
          ];
        });

    cy.get('bezwaarschriften-bezwaren-tabel')
        .its(0)
        .invoke('toonExtractieDetails', 'windmolens', 'bezwaar-002.txt');

    cy.wait('@details');

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('#bezwaar-toevoegen-knop')
        .click();

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('#manueel-samenvatting')
        .shadow()
        .find('textarea')
        .type('Samenvatting');

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('#manueel-passage')
        .shadow()
        .find('textarea')
        .type('Onbekende passage');

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('#manueel-opslaan')
        .click();

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
          el.bezwaren = [
            {bestandsnaam: 'bezwaar-002.txt', status: 'extractie-klaar', aantalBezwaren: 2, heeftOpmerkingen: false, heeftManueel: false},
          ];
        });

    cy.get('bezwaarschriften-bezwaren-tabel')
        .its(0)
        .invoke('toonExtractieDetails', 'windmolens', 'bezwaar-002.txt');

    cy.wait('@details');

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('#bezwaar-toevoegen-knop')
        .click();

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('#manueel-bezwaar-formulier')
        .should('exist');

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('#manueel-annuleer')
        .click();

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('#manueel-bezwaar-formulier')
        .should('not.exist');
  });
});
