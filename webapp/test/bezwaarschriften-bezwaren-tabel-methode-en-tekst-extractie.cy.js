import {html} from 'lit';
import '../src/js/bezwaarschriften-bezwaren-tabel';

describe('bezwaarschriften-bezwaren-tabel methode-kolom en tekst-extractie statussen', () => {
  beforeEach(() => {
    cy.mount(html`<bezwaarschriften-bezwaren-tabel></bezwaarschriften-bezwaren-tabel>`);
  });

  // --- Methode kolom ---

  it('toont "Digitaal" in methode-kolom als extractieMethode DIGITAAL is', () => {
    cy.get('bezwaarschriften-bezwaren-tabel')
        .its(0)
        .then((el) => {
          el.projectNaam = 'testproject';
          el.bezwaren = [
            {bestandsnaam: 'doc-001.pdf', status: 'bezwaar-extractie-klaar', aantalBezwaren: 2, extractieMethode: 'DIGITAAL'},
          ];
        });

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('vl-rich-data-field[name="extractieMethode"]')
        .should('exist');

    cy.get('bezwaarschriften-bezwaren-tabel')
        .contains('span', 'Digitaal')
        .should('exist');
  });

  it('toont "OCR" in methode-kolom als extractieMethode OCR is', () => {
    cy.get('bezwaarschriften-bezwaren-tabel')
        .its(0)
        .then((el) => {
          el.projectNaam = 'testproject';
          el.bezwaren = [
            {bestandsnaam: 'scan-001.pdf', status: 'bezwaar-extractie-klaar', aantalBezwaren: 1, extractieMethode: 'OCR'},
          ];
        });

    cy.get('bezwaarschriften-bezwaren-tabel')
        .contains('span', 'OCR')
        .should('exist');
  });

  it('toont "-" in methode-kolom als extractieMethode null is', () => {
    cy.get('bezwaarschriften-bezwaren-tabel')
        .its(0)
        .then((el) => {
          el.projectNaam = 'testproject';
          el.bezwaren = [
            {bestandsnaam: 'doc-002.pdf', status: 'todo', aantalBezwaren: null, extractieMethode: null},
          ];
        });

    cy.get('bezwaarschriften-bezwaren-tabel')
        .contains('span', '-')
        .should('exist');
  });

  it('toont "-" in methode-kolom als extractieMethode undefined is', () => {
    cy.get('bezwaarschriften-bezwaren-tabel')
        .its(0)
        .then((el) => {
          el.projectNaam = 'testproject';
          el.bezwaren = [
            {bestandsnaam: 'doc-003.pdf', status: 'todo', aantalBezwaren: null},
          ];
        });

    cy.get('bezwaarschriften-bezwaren-tabel')
        .contains('span', '-')
        .should('exist');
  });

  // --- Status pills voor tekst-extractie ---

  it('toont warning pill met label "Tekst extractie wachtend"', () => {
    cy.get('bezwaarschriften-bezwaren-tabel')
        .its(0)
        .then((el) => {
          el.projectNaam = 'testproject';
          el.bezwaren = [
            {bestandsnaam: 'doc-004.pdf', status: 'tekst-extractie-wachtend', aantalBezwaren: null},
          ];
        });

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('vl-pill[type="warning"]')
        .should('exist')
        .and('contain.text', 'Tekst extractie wachtend');
  });

  it('toont error pill met label "Tekst extractie mislukt"', () => {
    cy.get('bezwaarschriften-bezwaren-tabel')
        .its(0)
        .then((el) => {
          el.projectNaam = 'testproject';
          el.bezwaren = [
            {bestandsnaam: 'doc-005.pdf', status: 'tekst-extractie-mislukt', aantalBezwaren: null},
          ];
        });

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('vl-pill[type="error"]')
        .should('exist')
        .and('contain.text', 'Tekst extractie mislukt');
  });

  it('toont foutmelding tekst bij tekst-extractie-mislukt', () => {
    cy.get('bezwaarschriften-bezwaren-tabel')
        .its(0)
        .then((el) => {
          el.projectNaam = 'testproject';
          el.bezwaren = [
            {
              bestandsnaam: 'doc-fout.pdf',
              status: 'tekst-extractie-mislukt',
              aantalBezwaren: null,
              tekstExtractieFoutmelding: 'Te weinig woorden: 28 (minimum 40)',
            },
          ];
        });

    cy.get('bezwaarschriften-bezwaren-tabel')
        .contains('Te weinig woorden: 28 (minimum 40)')
        .should('exist');
  });

  // --- Checkboxen altijd enabled ---

  it('checkbox enabled voor tekst-extractie-wachtend status', () => {
    cy.get('bezwaarschriften-bezwaren-tabel')
        .its(0)
        .then((el) => {
          el.projectNaam = 'testproject';
          el.bezwaren = [
            {bestandsnaam: 'doc-006.pdf', status: 'tekst-extractie-wachtend', aantalBezwaren: null},
          ];
        });

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('input.rij-checkbox[data-bestandsnaam="doc-006.pdf"]')
        .should('not.be.disabled');
  });

  it('checkbox enabled voor tekst-extractie-bezig status', () => {
    cy.get('bezwaarschriften-bezwaren-tabel')
        .its(0)
        .then((el) => {
          el.projectNaam = 'testproject';
          el.bezwaren = [
            {bestandsnaam: 'doc-007.pdf', status: 'tekst-extractie-bezig', aantalBezwaren: null},
          ];
        });

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('input.rij-checkbox[data-bestandsnaam="doc-007.pdf"]')
        .should('not.be.disabled');
  });

  it('checkbox enabled voor tekst-extractie-klaar status', () => {
    cy.get('bezwaarschriften-bezwaren-tabel')
        .its(0)
        .then((el) => {
          el.projectNaam = 'testproject';
          el.bezwaren = [
            {bestandsnaam: 'doc-008.pdf', status: 'tekst-extractie-klaar', aantalBezwaren: null},
          ];
        });

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('input.rij-checkbox[data-bestandsnaam="doc-008.pdf"]')
        .should('not.be.disabled');
  });

  it('checkbox enabled voor todo status', () => {
    cy.get('bezwaarschriften-bezwaren-tabel')
        .its(0)
        .then((el) => {
          el.projectNaam = 'testproject';
          el.bezwaren = [
            {bestandsnaam: 'doc-008b.pdf', status: 'todo', aantalBezwaren: null},
          ];
        });

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('input.rij-checkbox[data-bestandsnaam="doc-008b.pdf"]')
        .should('not.be.disabled');
  });

  it('checkbox enabled voor tekst-extractie-mislukt status', () => {
    cy.get('bezwaarschriften-bezwaren-tabel')
        .its(0)
        .then((el) => {
          el.projectNaam = 'testproject';
          el.bezwaren = [
            {bestandsnaam: 'doc-009.pdf', status: 'tekst-extractie-mislukt', aantalBezwaren: null},
          ];
        });

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('input.rij-checkbox[data-bestandsnaam="doc-009.pdf"]')
        .should('not.be.disabled');
  });

  it('checkbox enabled voor extractie-klaar status', () => {
    cy.get('bezwaarschriften-bezwaren-tabel')
        .its(0)
        .then((el) => {
          el.projectNaam = 'testproject';
          el.bezwaren = [
            {bestandsnaam: 'doc-010.pdf', status: 'bezwaar-extractie-klaar', aantalBezwaren: 5},
          ];
        });

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('input.rij-checkbox[data-bestandsnaam="doc-010.pdf"]')
        .should('not.be.disabled');
  });

  // --- Tekst-extractie-klaar toont play button ---

  it('tekst-extractie-klaar toont pill met play button om AI-extractie te starten', () => {
    cy.get('bezwaarschriften-bezwaren-tabel')
        .its(0)
        .then((el) => {
          el.projectNaam = 'testproject';
          el.bezwaren = [
            {bestandsnaam: 'doc-011.pdf', status: 'tekst-extractie-klaar', aantalBezwaren: null},
          ];
        });

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('vl-pill')
        .should('contain.text', 'Klaar voor extractie')
        .find('button[title="Verwerking starten"]')
        .should('exist')
        .and('contain.text', '\u25b6');
  });

  it('tekst-extractie-klaar play button dispatcht herstart-taak event', () => {
    cy.get('bezwaarschriften-bezwaren-tabel')
        .its(0)
        .then((el) => {
          el.projectNaam = 'testproject';
          el.bezwaren = [
            {bestandsnaam: 'doc-012.pdf', status: 'tekst-extractie-klaar', aantalBezwaren: null},
          ];
          el.addEventListener('herstart-taak', cy.stub().as('herstartTaak'));
        });

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('vl-pill button[title="Verwerking starten"]')
        .click();

    cy.get('@herstartTaak').should('have.been.calledOnce');
    cy.get('@herstartTaak').its('firstCall.args.0.detail')
        .should('deep.equal', {bestandsnaam: 'doc-012.pdf'});
  });

  // --- Gecombineerd: methode en status samen ---

  it('toont methode en status correct samen in een rij', () => {
    cy.get('bezwaarschriften-bezwaren-tabel')
        .its(0)
        .then((el) => {
          el.projectNaam = 'testproject';
          el.bezwaren = [
            {bestandsnaam: 'digitaal.pdf', status: 'bezwaar-extractie-klaar', aantalBezwaren: 3, extractieMethode: 'DIGITAAL'},
            {bestandsnaam: 'scan.pdf', status: 'tekst-extractie-klaar', aantalBezwaren: null, extractieMethode: 'OCR'},
            {bestandsnaam: 'nieuw.pdf', status: 'tekst-extractie-wachtend', aantalBezwaren: null, extractieMethode: null},
          ];
        });

    // Digitaal rij
    cy.get('bezwaarschriften-bezwaren-tabel')
        .contains('span', 'Digitaal')
        .should('exist');

    // OCR rij
    cy.get('bezwaarschriften-bezwaren-tabel')
        .contains('span', 'OCR')
        .should('exist');

    // Status pills
    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('vl-pill[type="success"]')
        .should('contain.text', 'Bezwaar-extractie klaar');

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('vl-pill[type="warning"]')
        .should('contain.text', 'Tekst extractie wachtend');
  });

  // --- OCR niet beschikbaar ---

  it('toont error pill met label "OCR niet beschikbaar"', () => {
    cy.get('bezwaarschriften-bezwaren-tabel')
        .its(0)
        .then((el) => {
          el.projectNaam = 'testproject';
          el.bezwaren = [
            {bestandsnaam: 'doc-013.pdf', status: 'tekst-extractie-ocr-niet-beschikbaar', aantalBezwaren: null},
          ];
        });

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('vl-pill[type="error"]')
        .should('exist')
        .and('contain.text', 'OCR niet beschikbaar');
  });

  // --- Annuleer-knop voor tekst-extractie ---

  it('tekst-extractie-bezig annuleer-knop dispatcht annuleer-taak event met type tekst-extractie', () => {
    cy.get('bezwaarschriften-bezwaren-tabel')
        .its(0)
        .then((el) => {
          el.projectNaam = 'testproject';
          el.bezwaren = [
            {
              bestandsnaam: 'doc-014.pdf',
              status: 'tekst-extractie-bezig',
              aantalBezwaren: null,
              tekstExtractieAangemaaktOp: '2026-03-09T10:00:00Z',
              tekstExtractieGestartOp: '2026-03-09T10:00:05Z',
              tekstExtractieTaakId: 42,
            },
          ];
          el.addEventListener('annuleer-taak', cy.stub().as('annuleerTaak'));
        });

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('vl-pill button[title="Annuleer verwerking"]')
        .click();

    cy.get('@annuleerTaak').should('have.been.calledOnce');
    cy.get('@annuleerTaak').its('firstCall.args.0.detail')
        .should('deep.equal', {
          bestandsnaam: 'doc-014.pdf',
          taakId: 42,
          type: 'tekst-extractie',
        });
  });

  it('tekst-extractie-wachtend annuleer-knop dispatcht annuleer-taak event met type tekst-extractie', () => {
    cy.get('bezwaarschriften-bezwaren-tabel')
        .its(0)
        .then((el) => {
          el.projectNaam = 'testproject';
          el.bezwaren = [
            {
              bestandsnaam: 'doc-015.pdf',
              status: 'tekst-extractie-wachtend',
              aantalBezwaren: null,
              tekstExtractieAangemaaktOp: '2026-03-09T11:00:00Z',
              tekstExtractieTaakId: 43,
            },
          ];
          el.addEventListener('annuleer-taak', cy.stub().as('annuleerTaak'));
        });

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('vl-pill button[title="Annuleer verwerking"]')
        .click();

    cy.get('@annuleerTaak').should('have.been.calledOnce');
    cy.get('@annuleerTaak').its('firstCall.args.0.detail')
        .should('deep.equal', {
          bestandsnaam: 'doc-015.pdf',
          taakId: 43,
          type: 'tekst-extractie',
        });
  });

  // --- Stale takenData na verwijder + herlaad ---

  it('toont geen oude timestamp na verwijder + herlaad van hetzelfde bestand', () => {
    const oudeTimestamp = new Date(Date.now() - 5 * 60 * 1000).toISOString(); // 5 minuten geleden

    cy.get('bezwaarschriften-bezwaren-tabel')
        .its(0)
        .then((el) => {
          el.projectNaam = 'testproject';
          // Stap 1: bestand met oude timestamp (simuleert eerste upload)
          el.bezwaren = [
            {
              bestandsnaam: 'herlaad.pdf',
              status: 'tekst-extractie-wachtend',
              aantalBezwaren: null,
              tekstExtractieAangemaaktOp: oudeTimestamp,
              tekstExtractieTaakId: 99,
            },
          ];
          // Stap 2: bestand verwijderd, lijst ververst zonder het bestand
          el.bezwaren = [];
          // Stap 3: bestand opnieuw geüpload zonder timing-data (zoals _voegGeuploadeBezwarenToe doet)
          el.bezwaren = [
            {
              bestandsnaam: 'herlaad.pdf',
              status: 'tekst-extractie-wachtend',
              aantalBezwaren: null,
            },
          ];
        });

    // De pill mag GEEN timer tonen met minuten (stale data van vorige upload)
    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('vl-pill[type="warning"]')
        .find('.timer-tekst')
        .invoke('text')
        .should('not.match', /\([1-9]\d*:\d{2}\)/);
  });
});
