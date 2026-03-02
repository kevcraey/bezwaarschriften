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
});
