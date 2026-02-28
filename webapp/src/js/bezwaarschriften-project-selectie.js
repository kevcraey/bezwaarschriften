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
      </div>
      <div id="bezwaren-sectie" hidden>
        <h2>Bezwaarschriften</h2>
        <vl-button id="extraheer-knop" disabled>Extraheer geselecteerde</vl-button>
        <p id="fout-melding" hidden></p>
        <bezwaarschriften-bezwaren-tabel id="bezwaren-tabel"></bezwaarschriften-bezwaren-tabel>
      </div>
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
    const extraheerKnop = this.shadowRoot && this.shadowRoot.querySelector('#extraheer-knop');

    if (selectEl) {
      selectEl.addEventListener('vl-change', (e) => {
        this._verbergFout();
        const naam = e.detail.value;
        this.__geselecteerdProject = naam || null;
        if (naam) {
          this._laadBezwaren(naam);
        } else {
          this.__bezwaren = [];
          this._verbergBezwarenSectie();
        }
      });
    }

    this.shadowRoot.addEventListener('selectie-gewijzigd', (e) => {
      if (extraheerKnop) {
        extraheerKnop.disabled = this.__bezig || e.detail.geselecteerd.length === 0;
      }
    });

    if (extraheerKnop) {
      extraheerKnop.addEventListener('vl-click', () => {
        if (this.__bezig || !this.__geselecteerdProject) return;
        const tabel = this.shadowRoot.querySelector('#bezwaren-tabel');
        if (!tabel) return;
        const geselecteerd = tabel.geefGeselecteerdeBestandsnamen();
        if (geselecteerd.length === 0) return;
        this._extraheerGeselecteerde(this.__geselecteerdProject, geselecteerd);
      });
    }
  }

  _laadBezwaren(projectNaam) {
    this._verbergFout();
    fetch(`/api/v1/projects/${encodeURIComponent(projectNaam)}/bezwaren`)
        .then((response) => {
          if (!response.ok) throw new Error('Ophalen bezwaren mislukt');
          return response.json();
        })
        .then((data) => {
          this.__bezwaren = data.bezwaren;
          this._werkTabelBij();
        })
        .catch(() => {
          this._toonFout('Bezwaren konden niet worden geladen.');
        });
  }

  _extraheerGeselecteerde(projectNaam, bestandsnamen) {
    this._verbergFout();
    this._zetBezig(true);

    const beloftes = bestandsnamen.map((bestandsnaam) => {
      const url = `/api/v1/projects/${encodeURIComponent(projectNaam)}/bezwaren/${encodeURIComponent(bestandsnaam)}/extraheer`;
      return fetch(url, {method: 'POST'})
          .then((response) => {
            if (!response.ok) throw new Error('Extractie mislukt');
            return response.json();
          });
    });

    Promise.allSettled(beloftes)
        .then((resultaten) => {
          let aantalFouten = 0;
          resultaten.forEach((resultaat) => {
            if (resultaat.status === 'fulfilled') {
              const bijgewerkt = resultaat.value;
              this.__bezwaren = this.__bezwaren.map((b) =>
                b.bestandsnaam === bijgewerkt.bestandsnaam ? bijgewerkt : b,
              );
            } else {
              aantalFouten++;
            }
          });
          this._werkTabelBij();
          if (aantalFouten > 0) {
            this._toonFout(`${aantalFouten} bestand(en) konden niet worden geëxtraheerd.`);
          }
        })
        .finally(() => {
          this._zetBezig(false);
        });
  }

  _werkTabelBij() {
    const sectie = this.shadowRoot && this.shadowRoot.querySelector('#bezwaren-sectie');
    const tabel = this.shadowRoot && this.shadowRoot.querySelector('#bezwaren-tabel');
    if (tabel) {
      tabel.bezwaren = this.__bezwaren;
    }
    if (sectie) {
      sectie.hidden = false;
    }
  }

  _verbergBezwarenSectie() {
    const sectie = this.shadowRoot && this.shadowRoot.querySelector('#bezwaren-sectie');
    if (sectie) sectie.hidden = true;
  }

  _zetBezig(bezig) {
    this.__bezig = bezig;
    const extraheerKnop = this.shadowRoot && this.shadowRoot.querySelector('#extraheer-knop');
    if (extraheerKnop) extraheerKnop.disabled = bezig;
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
