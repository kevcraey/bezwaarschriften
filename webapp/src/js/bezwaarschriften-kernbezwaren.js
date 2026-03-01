import {BaseHTMLElement, defineWebComponent, registerWebComponents} from '@domg-wc/common';
import {VlButtonComponent} from '@domg-wc/components/atom/button/vl-button.component.js';
import {VlAccordionComponent} from '@domg-wc/components/block/accordion/vl-accordion.component.js';
import {VlSideSheet} from '@domg-wc/components/block/side-sheet/vl-side-sheet.component.js';
import {vlGlobalStyles, vlGridStyles} from '@domg-wc/styles';

registerWebComponents([VlButtonComponent, VlAccordionComponent, VlSideSheet]);

export class BezwaarschriftenKernbezwaren extends BaseHTMLElement {
  constructor() {
    super(`
      <style>
        ${vlGlobalStyles}
        ${vlGridStyles}
        :host { display: block; }
        .kernbezwaar-item {
          display: flex;
          justify-content: space-between;
          align-items: flex-start;
          padding: 1rem 0;
          border-bottom: 1px solid #e8ebee;
        }
        .kernbezwaar-item:last-child { border-bottom: none; }
        .kernbezwaar-samenvatting { flex: 1; margin-right: 1rem; }
        .kernbezwaar-actie { white-space: nowrap; }
        .passage-item {
          margin-bottom: 1.5rem;
          padding-bottom: 1rem;
          border-bottom: 1px solid #e8ebee;
        }
        .passage-item:last-child { border-bottom: none; }
        .passage-bestandsnaam {
          font-size: 0.85rem;
          color: #687483;
          margin-bottom: 0.25rem;
        }
        .passage-tekst {
          font-style: italic;
          line-height: 1.5;
        }
        .lege-staat {
          padding: 2rem;
          text-align: center;
          color: #687483;
        }
        .side-sheet-header {
          display: flex;
          justify-content: space-between;
          align-items: flex-start;
          position: sticky;
          top: 0;
          background: white;
          padding: 0.75rem 0;
          z-index: 1;
          border-bottom: 2px solid #e8ebee;
          margin-bottom: 1rem;
        }
        .side-sheet-titel {
          font-weight: bold;
          flex: 1;
          margin-right: 1rem;
        }
        .side-sheet-sluit-knop {
          background: none;
          border: none;
          font-size: 1.5rem;
          cursor: pointer;
          padding: 0;
          color: #333;
          line-height: 1;
          flex-shrink: 0;
        }
        .side-sheet-sluit-knop:hover {
          color: #000;
        }
      </style>
      <div id="inhoud"></div>
      <vl-side-sheet id="side-sheet" hide-toggle-button>
        <div class="side-sheet-header">
          <div id="side-sheet-titel" class="side-sheet-titel"></div>
          <button id="side-sheet-sluit-knop" class="side-sheet-sluit-knop"
              aria-label="Sluiten">&times;</button>
        </div>
        <div id="side-sheet-inhoud"></div>
      </vl-side-sheet>
    `);
    this._projectNaam = null;
    this._aantalBezwaren = 0;
    this._extractieKlaar = false;
    this._themas = null;
    this._bezig = false;
  }

  connectedCallback() {
    super.connectedCallback();
    const sluitKnop = this.shadowRoot.querySelector('#side-sheet-sluit-knop');
    const sideSheet = this.shadowRoot.querySelector('#side-sheet');
    if (sluitKnop && sideSheet) {
      sluitKnop.addEventListener('click', () => sideSheet.close());
    }
  }

  // Public methods called by parent
  laadKernbezwaren(projectNaam) {
    this._projectNaam = projectNaam;
    fetch(`/api/v1/projects/${encodeURIComponent(projectNaam)}/kernbezwaren`)
        .then((response) => {
          if (response.status === 404) {
            this._themas = null;
            this._renderInhoud();
            return null;
          }
          if (!response.ok) throw new Error('Ophalen kernbezwaren mislukt');
          return response.json();
        })
        .then((data) => {
          if (data) {
            this._themas = data.themas;
            this._renderInhoud();
          }
        })
        .catch(() => {
          this._themas = null;
          this._renderInhoud();
        });
  }

  setAantalBezwaren(aantal) {
    this._aantalBezwaren = aantal;
    if (!this._themas) this._renderInhoud();
  }

  setExtractieKlaar(klaar) {
    this._extractieKlaar = klaar;
    if (!this._themas) this._renderInhoud();
  }

  reset() {
    this._themas = null;
    this._renderInhoud();
  }

  // Private rendering
  _renderInhoud() {
    const inhoud = this.shadowRoot.querySelector('#inhoud');
    if (!inhoud) return;

    if (this._themas) {
      this._renderThemas(inhoud);
      return;
    }

    if (!this._extractieKlaar || this._aantalBezwaren === 0) {
      inhoud.innerHTML = `
        <div class="lege-staat">
          <p>Nog geen bezwaren ge\u00EBxtraheerd. Verwerk eerst documenten op de Documenten-tab.</p>
        </div>`;
      return;
    }

    inhoud.innerHTML = `
      <div class="lege-staat">
        <p>${this._aantalBezwaren} individuele bezwaren gevonden.
           Voer groepering tot thema's en kernbezwaren uit om verder te gaan.</p>
        <vl-button id="groepeer-knop">Groepeer bezwaren</vl-button>
      </div>`;

    const knop = inhoud.querySelector('#groepeer-knop');
    if (knop) {
      knop.addEventListener('vl-click', () => this._groepeer());
    }
  }

  _groepeer() {
    if (this._bezig || !this._projectNaam) return;
    this._bezig = true;

    const knop = this.shadowRoot.querySelector('#groepeer-knop');
    if (knop) {
      knop.setAttribute('disabled', '');
      knop.textContent = 'Bezig met groeperen...';
    }

    fetch(`/api/v1/projects/${encodeURIComponent(this._projectNaam)}/kernbezwaren/groepeer`, {
      method: 'POST',
    })
        .then((response) => {
          if (!response.ok) throw new Error('Groepering mislukt');
          return response.json();
        })
        .then((data) => {
          this._themas = data.themas;
          this._renderInhoud();
        })
        .catch(() => {
          if (knop) {
            knop.removeAttribute('disabled');
            knop.textContent = 'Groepeer bezwaren';
          }
        })
        .finally(() => {
          this._bezig = false;
        });
  }

  _renderThemas(inhoud) {
    inhoud.innerHTML = '';
    this._themas.forEach((thema) => {
      const accordion = document.createElement('vl-accordion');
      const aantalKern = thema.kernbezwaren.length;
      const label = aantalKern === 1 ? '1 kernbezwaar' : `${aantalKern} kernbezwaren`;
      accordion.setAttribute('toggle-text', `${thema.naam} (${label})`);

      const wrapper = document.createElement('div');
      thema.kernbezwaren.forEach((kern) => {
        const item = document.createElement('div');
        item.className = 'kernbezwaar-item';

        const samenvatting = document.createElement('div');
        samenvatting.className = 'kernbezwaar-samenvatting';
        samenvatting.textContent = kern.samenvatting;

        const actie = document.createElement('div');
        actie.className = 'kernbezwaar-actie';
        const knop = document.createElement('vl-button');
        knop.setAttribute('tertiary', '');
        knop.setAttribute('icon', 'search');
        knop.textContent = `(${kern.individueleBezwaren.length})`;
        knop.addEventListener('vl-click', () => this._toonPassages(kern));

        actie.appendChild(knop);
        item.appendChild(samenvatting);
        item.appendChild(actie);
        wrapper.appendChild(item);
      });

      accordion.appendChild(wrapper);
      inhoud.appendChild(accordion);
    });
  }

  _toonPassages(kernbezwaar) {
    const sideSheet = this.shadowRoot.querySelector('#side-sheet');
    const inhoud = this.shadowRoot.querySelector('#side-sheet-inhoud');
    const titelEl = this.shadowRoot.querySelector('#side-sheet-titel');
    if (!sideSheet || !inhoud) return;

    inhoud.innerHTML = '';
    if (titelEl) titelEl.textContent = kernbezwaar.samenvatting;

    const aantalLabel = document.createElement('p');
    const n = kernbezwaar.individueleBezwaren.length;
    aantalLabel.textContent = `${n} individuele bezwar${n === 1 ? '' : 'en'}:`;
    inhoud.appendChild(aantalLabel);

    kernbezwaar.individueleBezwaren.forEach((ref) => {
      const item = document.createElement('div');
      item.className = 'passage-item';

      const bestand = document.createElement('div');
      bestand.className = 'passage-bestandsnaam';
      bestand.textContent = ref.bestandsnaam;

      const passage = document.createElement('div');
      passage.className = 'passage-tekst';
      passage.textContent = `"${ref.passage}"`;

      item.appendChild(bestand);
      item.appendChild(passage);
      inhoud.appendChild(item);
    });

    sideSheet.open();
  }
}

defineWebComponent(BezwaarschriftenKernbezwaren, 'bezwaarschriften-kernbezwaren');
