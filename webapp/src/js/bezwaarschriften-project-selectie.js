import {BaseHTMLElement, defineWebComponent, registerWebComponents} from '@domg-wc/common';
import {VlSelectComponent} from '@domg-wc/components/form/select/vl-select.component.js';
import {VlButtonComponent} from '@domg-wc/components/atom/button/vl-button.component.js';
import {vlGlobalStyles, vlGridStyles} from '@domg-wc/styles';
import './bezwaarschriften-bezwaren-tabel.js';

registerWebComponents([VlSelectComponent, VlButtonComponent]);

export class BezwaarschriftenProjectSelectie extends BaseHTMLElement {
  static get properties() {
    return {
      __projecten: {state: true},
      __geselecteerdProject: {state: true},
      __bezwaren: {state: true},
      __bezig: {state: true},
      __fout: {state: true},
    };
  }

  constructor() {
    super(`
      <style>
        ${vlGlobalStyles}
        ${vlGridStyles}
      </style>
      <div id="selectie-wrapper">
        <vl-select id="project-select" placeholder="Kies een project..."></vl-select>
        <vl-button id="verwerk-knop">Verwerk alles</vl-button>
        <p id="fout-melding" hidden></p>
      </div>
      <bezwaarschriften-bezwaren-tabel id="bezwaren-tabel" hidden></bezwaarschriften-bezwaren-tabel>
    `);
    this.__projecten = [];
    this.__geselecteerdProject = null;
    this.__bezwaren = [];
    this.__bezig = false;
    this.__fout = null;
  }

  connectedCallback() {
    super.connectedCallback();
    this._laadProjecten();
    this._koppelEventListeners();
  }

  _laadProjecten() {
    fetch('/api/v1/projects')
        .then((response) => {
          if (!response.ok) throw new Error('Ophalen projecten mislukt');
          return response.json();
        })
        .then((data) => {
          this.__projecten = data.projecten;
          const selectEl = this.shadowRoot && this.shadowRoot.querySelector('#project-select');
          if (selectEl) {
            selectEl.options = this.__projecten.map((naam) => ({value: naam, label: naam}));
          }
        })
        .catch(() => {
          this._toonFout('Projecten konden niet worden geladen.');
        });
  }

  _koppelEventListeners() {
    const selectEl = this.shadowRoot && this.shadowRoot.querySelector('#project-select');
    const verwerkKnop = this.shadowRoot && this.shadowRoot.querySelector('#verwerk-knop');

    if (selectEl) {
      selectEl.addEventListener('vl-change', (e) => {
        this._verbergFout();
        const naam = e.detail.value;
        this.__geselecteerdProject = naam || null;
        if (!naam) {
          this.__bezwaren = [];
          this._verbergTabel();
        }
      });
    }

    if (verwerkKnop) {
      verwerkKnop.addEventListener('vl-click', () => {
        if (this.__bezig) return;
        if (!this.__geselecteerdProject) {
          this._toonFout('Selecteer eerst een project.');
          return;
        }
        this._verwerkBezwaren(this.__geselecteerdProject);
      });
    }
  }

  _verwerkBezwaren(projectNaam) {
    this._verbergFout();
    this._zetBezig(true);
    fetch(`/api/v1/projects/${encodeURIComponent(projectNaam)}/verwerk`, {method: 'POST'})
        .then((response) => {
          if (!response.ok) throw new Error('Verwerking mislukt');
          return response.json();
        })
        .then((data) => {
          this.__bezwaren = data.bezwaren;
          this._werkTabelBij();
        })
        .catch(() => {
          this._toonFout('Verwerking kon niet worden gestart.');
        })
        .finally(() => {
          this.__bezig = false;
        });
  }

  _werkTabelBij() {
    const tabel = this.shadowRoot && this.shadowRoot.querySelector('#bezwaren-tabel');
    if (tabel) {
      tabel.bezwaren = this.__bezwaren;
      tabel.hidden = false;
    }
  }

  _verbergTabel() {
    const tabel = this.shadowRoot && this.shadowRoot.querySelector('#bezwaren-tabel');
    if (tabel) tabel.hidden = true;
  }

  _zetBezig(bezig) {
    this.__bezig = bezig;
    const knop = this.shadowRoot && this.shadowRoot.querySelector('#verwerk-knop');
    if (knop) knop.disabled = bezig;
  }

  _toonFout(bericht) {
    const foutEl = this.shadowRoot && this.shadowRoot.querySelector('#fout-melding');
    if (foutEl) {
      foutEl.textContent = bericht;
      foutEl.hidden = false;
    }
  }

  _verbergFout() {
    const foutEl = this.shadowRoot && this.shadowRoot.querySelector('#fout-melding');
    if (foutEl) foutEl.hidden = true;
  }
}

defineWebComponent(BezwaarschriftenProjectSelectie, 'bezwaarschriften-project-selectie');
