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
            {bestandsnaam: 'doc-001.pdf', tekstExtractieStatus: 'KLAAR', bezwaarExtractieStatus: 'KLAAR', aantalBezwaren: 2, extractieMethode: 'DIGITAAL'},
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
            {bestandsnaam: 'scan-001.pdf', tekstExtractieStatus: 'KLAAR', bezwaarExtractieStatus: 'KLAAR', aantalBezwaren: 1, extractieMethode: 'OCR'},
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
            {bestandsnaam: 'doc-002.pdf', tekstExtractieStatus: 'GEEN', bezwaarExtractieStatus: 'GEEN', aantalBezwaren: null, extractieMethode: null},
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
            {bestandsnaam: 'doc-003.pdf', tekstExtractieStatus: 'GEEN', bezwaarExtractieStatus: 'GEEN', aantalBezwaren: null},
          ];
        });

    cy.get('bezwaarschriften-bezwaren-tabel')
        .contains('span', '-')
        .should('exist');
  });

  // --- Status pills voor tekst-extractie ---

  it('toont warning pill met label "Tekst extractie bezig"', () => {
    cy.get('bezwaarschriften-bezwaren-tabel')
        .its(0)
        .then((el) => {
          el.projectNaam = 'testproject';
          el.bezwaren = [
            {bestandsnaam: 'doc-004.pdf', tekstExtractieStatus: 'BEZIG', bezwaarExtractieStatus: 'GEEN', aantalBezwaren: null},
          ];
        });

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('vl-pill[type="warning"]')
        .should('exist')
        .and('contain.text', 'Tekst extractie bezig');
  });

  it('toont error pill met label "Tekst extractie mislukt"', () => {
    cy.get('bezwaarschriften-bezwaren-tabel')
        .its(0)
        .then((el) => {
          el.projectNaam = 'testproject';
          el.bezwaren = [
            {bestandsnaam: 'doc-005.pdf', tekstExtractieStatus: 'FOUT', bezwaarExtractieStatus: 'GEEN', aantalBezwaren: null},
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
              tekstExtractieStatus: 'FOUT', bezwaarExtractieStatus: 'GEEN',
              aantalBezwaren: null,
              foutmelding: 'Te weinig woorden: 28 (minimum 40)',
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
            {bestandsnaam: 'doc-006.pdf', tekstExtractieStatus: 'BEZIG', bezwaarExtractieStatus: 'GEEN', aantalBezwaren: null},
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
            {bestandsnaam: 'doc-007.pdf', tekstExtractieStatus: 'BEZIG', bezwaarExtractieStatus: 'GEEN', aantalBezwaren: null},
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
            {bestandsnaam: 'doc-008.pdf', tekstExtractieStatus: 'KLAAR', bezwaarExtractieStatus: 'GEEN', aantalBezwaren: null},
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
            {bestandsnaam: 'doc-008b.pdf', tekstExtractieStatus: 'GEEN', bezwaarExtractieStatus: 'GEEN', aantalBezwaren: null},
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
            {bestandsnaam: 'doc-009.pdf', tekstExtractieStatus: 'FOUT', bezwaarExtractieStatus: 'GEEN', aantalBezwaren: null},
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
            {bestandsnaam: 'doc-010.pdf', tekstExtractieStatus: 'KLAAR', bezwaarExtractieStatus: 'KLAAR', aantalBezwaren: 5},
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
            {bestandsnaam: 'doc-011.pdf', tekstExtractieStatus: 'KLAAR', bezwaarExtractieStatus: 'GEEN', aantalBezwaren: null},
          ];
        });

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('vl-pill')
        .should('contain.text', 'Te verwerken')
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
            {bestandsnaam: 'doc-012.pdf', tekstExtractieStatus: 'KLAAR', bezwaarExtractieStatus: 'GEEN', aantalBezwaren: null},
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
            {bestandsnaam: 'digitaal.pdf', tekstExtractieStatus: 'KLAAR', bezwaarExtractieStatus: 'KLAAR', aantalBezwaren: 3, extractieMethode: 'DIGITAAL'},
            {bestandsnaam: 'scan.pdf', tekstExtractieStatus: 'KLAAR', bezwaarExtractieStatus: 'GEEN', aantalBezwaren: null, extractieMethode: 'OCR'},
            {bestandsnaam: 'nieuw.pdf', tekstExtractieStatus: 'BEZIG', bezwaarExtractieStatus: 'GEEN', aantalBezwaren: null, extractieMethode: null},
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
        .should('contain.text', 'Tekst extractie bezig');
  });

  // --- OCR niet beschikbaar ---

  it('toont error pill met label "OCR niet beschikbaar"', () => {
    cy.get('bezwaarschriften-bezwaren-tabel')
        .its(0)
        .then((el) => {
          el.projectNaam = 'testproject';
          el.bezwaren = [
            {bestandsnaam: 'doc-013.pdf', tekstExtractieStatus: 'NIET_ONDERSTEUND', bezwaarExtractieStatus: 'GEEN', aantalBezwaren: null},
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
              tekstExtractieStatus: 'BEZIG', bezwaarExtractieStatus: 'GEEN',
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
              tekstExtractieStatus: 'BEZIG', bezwaarExtractieStatus: 'GEEN',
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
              tekstExtractieStatus: 'BEZIG', bezwaarExtractieStatus: 'GEEN',
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
              tekstExtractieStatus: 'BEZIG', bezwaarExtractieStatus: 'GEEN',
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

  // --- Oog-knop (tekst-preview) ---

  it('toont oog-knop bij tekst-extractie-klaar status', () => {
    cy.get('bezwaarschriften-bezwaren-tabel')
        .its(0)
        .then((el) => {
          el.projectNaam = 'testproject';
          el.bezwaren = [
            {bestandsnaam: 'doc-001.pdf', tekstExtractieStatus: 'KLAAR', bezwaarExtractieStatus: 'GEEN'},
          ];
        });

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('vl-button[icon="view-add"]')
        .should('exist');
  });

  it('toont oog-knop bij bezwaar-extractie-klaar status', () => {
    cy.get('bezwaarschriften-bezwaren-tabel')
        .its(0)
        .then((el) => {
          el.projectNaam = 'testproject';
          el.bezwaren = [
            {bestandsnaam: 'doc-001.pdf', tekstExtractieStatus: 'KLAAR', bezwaarExtractieStatus: 'KLAAR', aantalBezwaren: 3},
          ];
        });

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('vl-button[icon="view-add"]')
        .should('exist');
  });

  it('toont oog-knop bij bezwaar-extractie-wachtend status', () => {
    cy.get('bezwaarschriften-bezwaren-tabel')
        .its(0)
        .then((el) => {
          el.projectNaam = 'testproject';
          el.bezwaren = [
            {bestandsnaam: 'doc-001.pdf', tekstExtractieStatus: 'KLAAR', bezwaarExtractieStatus: 'BEZIG'},
          ];
        });

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('vl-button[icon="view-add"]')
        .should('exist');
  });

  it('verbergt oog-knop bij todo status', () => {
    cy.get('bezwaarschriften-bezwaren-tabel')
        .its(0)
        .then((el) => {
          el.projectNaam = 'testproject';
          el.bezwaren = [
            {bestandsnaam: 'doc-001.pdf', tekstExtractieStatus: 'GEEN', bezwaarExtractieStatus: 'GEEN'},
          ];
        });

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('vl-button[icon="view-add"]')
        .should('not.exist');
  });

  it('verbergt oog-knop bij tekst-extractie-bezig status', () => {
    cy.get('bezwaarschriften-bezwaren-tabel')
        .its(0)
        .then((el) => {
          el.projectNaam = 'testproject';
          el.bezwaren = [
            {bestandsnaam: 'doc-001.pdf', tekstExtractieStatus: 'BEZIG', bezwaarExtractieStatus: 'GEEN',
             tekstExtractieAangemaaktOp: '2026-03-01T10:00:00Z',
             tekstExtractieVerwerkingGestartOp: '2026-03-01T10:01:00Z',
             tekstExtractieTaakId: 1},
          ];
        });

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('vl-button[icon="view-add"]')
        .should('not.exist');
  });

  it('oog-knop dispatcht toon-geextraheerde-tekst event', () => {
    cy.get('bezwaarschriften-bezwaren-tabel')
        .its(0)
        .then((el) => {
          el.projectNaam = 'testproject';
          el.bezwaren = [
            {bestandsnaam: 'bezwaar-001.pdf', tekstExtractieStatus: 'KLAAR', bezwaarExtractieStatus: 'GEEN'},
          ];
          el.addEventListener('toon-geextraheerde-tekst', cy.stub().as('toonTekst'));
        });

    cy.get('bezwaarschriften-bezwaren-tabel')
        .find('vl-button[icon="view-add"]')
        .click();

    cy.get('@toonTekst').should('have.been.calledOnce');
    cy.get('@toonTekst').its('firstCall.args.0.detail')
        .should('deep.include', {projectNaam: 'testproject', bestandsnaam: 'bezwaar-001.pdf'});
  });
});
