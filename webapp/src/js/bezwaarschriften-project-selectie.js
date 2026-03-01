import {BaseHTMLElement, defineWebComponent, registerWebComponents} from '@domg-wc/common';
import {VlSelectComponent} from '@domg-wc/components/form/select/vl-select.component.js';
import {VlButtonComponent} from '@domg-wc/components/atom/button/vl-button.component.js';
import {VlTabsComponent} from '@domg-wc/components/block/tabs/vl-tabs.component.js';
import {VlTabsPaneComponent} from '@domg-wc/components/block/tabs/vl-tabs-pane.component.js';
import {VlUploadComponent} from '@domg-wc/components/form/upload/vl-upload.component.js';
import {VlModalComponent} from '@domg-wc/components/block/modal/vl-modal.component.js';
import {VlToasterComponent} from '@domg-wc/components/block/toaster/vl-toaster.component.js';
import {vlGlobalStyles, vlGridStyles} from '@domg-wc/styles';
import './bezwaarschriften-bezwaren-tabel.js';

registerWebComponents([VlSelectComponent, VlButtonComponent, VlTabsComponent, VlTabsPaneComponent, VlUploadComponent, VlModalComponent, VlToasterComponent]);

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
      <div id="tabs-sectie" hidden>
        <vl-tabs observe-title active-tab="documenten">
          <vl-tabs-pane id="documenten" title="Documenten">
            <vl-button id="extraheer-knop" hidden>Extraheer geselecteerde</vl-button>
            <vl-button id="verwijder-knop" hidden>Verwijder geselecteerde</vl-button>
            <vl-button id="retry-knop" hidden>Opnieuw proberen</vl-button>
            <vl-button id="toevoegen-knop">Bestanden toevoegen</vl-button>
            <div id="upload-zone" hidden>
              <vl-upload id="bestand-upload"
                accepted-files=".txt"
                max-files="100"
                max-size="50"
                main-title="Bezwaarbestanden toevoegen"
                sub-title="Sleep .txt bestanden hierheen of klik om te bladeren"
                disallow-duplicates>
              </vl-upload>
              <vl-button id="upload-verzend-knop">Uploaden</vl-button>
            </div>
            <p id="fout-melding" hidden></p>
            <bezwaarschriften-bezwaren-tabel id="bezwaren-tabel"></bezwaarschriften-bezwaren-tabel>
          </vl-tabs-pane>
          <vl-tabs-pane id="kernbezwaren" title="Kernbezwaren">
            <p>Kernbezwaren worden hier getoond na verwerking.</p>
          </vl-tabs-pane>
        </vl-tabs>
      </div>
      <vl-modal id="verwijder-modal" title="Bestanden verwijderen" closable>
        <div slot="content">
          <p id="verwijder-bevestiging-tekst"></p>
        </div>
        <div slot="button">
          <vl-button id="verwijder-bevestig-knop">Verwijderen</vl-button>
        </div>
      </vl-modal>
      <vl-toaster id="toaster"></vl-toaster>
    `);
    this.__projecten = [];
    this.__geselecteerdProject = null;
    this.__bezwaren = [];
    this.__bezig = false;
    this.__fout = null;
    this._ws = null;
    this._wsReconnectDelay = 1000;
    this._teVerwijderenBestanden = [];
  }

  connectedCallback() {
    super.connectedCallback();
    this._laadProjecten();
    this._koppelEventListeners();
    this._verbindWebSocket();
  }

  disconnectedCallback() {
    if (this._ws) {
      this._ws.close();
      this._ws = null;
    }
  }

  _verbindWebSocket() {
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const url = `${protocol}//${window.location.host}/ws/extracties`;
    this._ws = new WebSocket(url);

    this._ws.onmessage = (event) => {
      const data = JSON.parse(event.data);
      if (data.type === 'taak-update') {
        this._verwerkTaakUpdate(data.taak);
      }
    };

    this._ws.onclose = () => {
      setTimeout(() => {
        this._wsReconnectDelay = Math.min(this._wsReconnectDelay * 2, 30000);
        this._verbindWebSocket();
        if (this.__geselecteerdProject) {
          this._syncExtracties(this.__geselecteerdProject);
        }
      }, this._wsReconnectDelay);
    };

    this._ws.onopen = () => {
      this._wsReconnectDelay = 1000;
    };
  }

  _verwerkTaakUpdate(taak) {
    if (!this.__geselecteerdProject || taak.projectNaam !== this.__geselecteerdProject) {
      return;
    }
    this.__bezwaren = this.__bezwaren.map((b) =>
      b.bestandsnaam === taak.bestandsnaam ? {
        ...b,
        status: taak.status,
        aantalWoorden: taak.aantalWoorden,
        aantalBezwaren: taak.aantalBezwaren,
      } : b,
    );
    const tabel = this.shadowRoot.querySelector('#bezwaren-tabel');
    if (tabel) {
      tabel.werkBijMetTaakUpdate(taak);
    }
    this._werkDocumentenTabTitelBij();
    this._werkRetryKnopBij();
  }

  _syncExtracties(projectNaam) {
    fetch(`/api/v1/projects/${encodeURIComponent(projectNaam)}/extracties`)
        .then((response) => response.json())
        .then((data) => {
          const tabel = this.shadowRoot.querySelector('#bezwaren-tabel');
          if (tabel && data.taken) {
            data.taken.forEach((taak) => {
              this.__bezwaren = this.__bezwaren.map((b) =>
                b.bestandsnaam === taak.bestandsnaam ? {
                  ...b,
                  status: taak.status,
                  aantalWoorden: taak.aantalWoorden,
                  aantalBezwaren: taak.aantalBezwaren,
                } : b,
              );
              tabel.werkBijMetTaakUpdate(taak);
            });
            this._werkDocumentenTabTitelBij();
            this._werkRetryKnopBij();
          }
        })
        .catch(() => {/* stille fout bij sync */});
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
    const verwijderKnop = this.shadowRoot && this.shadowRoot.querySelector('#verwijder-knop');
    const retryKnop = this.shadowRoot && this.shadowRoot.querySelector('#retry-knop');
    const toevoegenKnop = this.shadowRoot && this.shadowRoot.querySelector('#toevoegen-knop');
    const uploadVerzendKnop = this.shadowRoot && this.shadowRoot.querySelector('#upload-verzend-knop');
    const verwijderBevestigKnop = this.shadowRoot && this.shadowRoot.querySelector('#verwijder-bevestig-knop');

    if (selectEl) {
      selectEl.addEventListener('vl-change', (e) => {
        this._verbergFout();
        const naam = e.detail.value;
        this.__geselecteerdProject = naam || null;
        if (naam) {
          this._laadBezwaren(naam);
        } else {
          this.__bezwaren = [];
          this._verbergTabsSectie();
        }
      });
    }

    this.shadowRoot.addEventListener('selectie-gewijzigd', (e) => {
      const aantal = e.detail.geselecteerd.length;
      const heeftSelectie = aantal > 0;
      if (extraheerKnop) {
        extraheerKnop.hidden = !heeftSelectie;
        extraheerKnop.textContent = `Extraheer geselecteerde (${aantal})`;
      }
      if (verwijderKnop) {
        verwijderKnop.hidden = !heeftSelectie;
      }
      if (toevoegenKnop) {
        toevoegenKnop.hidden = heeftSelectie;
      }
      if (retryKnop) {
        retryKnop.hidden = heeftSelectie || this.__bezwaren.filter((b) => b.status === 'fout').length === 0;
      }
    });

    if (extraheerKnop) {
      extraheerKnop.addEventListener('vl-click', () => {
        if (this.__bezig || !this.__geselecteerdProject) return;
        const tabel = this.shadowRoot.querySelector('#bezwaren-tabel');
        if (!tabel) return;
        const geselecteerd = tabel.geefGeselecteerdeBestandsnamen();
        if (geselecteerd.length === 0) return;
        this._dienExtractiesIn(this.__geselecteerdProject, geselecteerd);
      });
    }

    if (retryKnop) {
      retryKnop.addEventListener('vl-click', () => {
        if (this.__bezig || !this.__geselecteerdProject) return;
        this._retryGefaaldeExtracties(this.__geselecteerdProject);
      });
    }

    if (verwijderKnop) {
      verwijderKnop.addEventListener('vl-click', () => {
        const tabel = this.shadowRoot.querySelector('#bezwaren-tabel');
        if (!tabel) return;
        const geselecteerd = tabel.geefGeselecteerdeBestandsnamen();
        if (geselecteerd.length === 0) return;
        this._teVerwijderenBestanden = geselecteerd;
        const tekst = this.shadowRoot.querySelector('#verwijder-bevestiging-tekst');
        if (tekst) {
          tekst.textContent = `Weet je zeker dat je ${geselecteerd.length} bestand(en) wilt verwijderen? Bestanden en bijhorende extractie-resultaten worden permanent verwijderd.`;
        }
        const modal = this.shadowRoot.querySelector('#verwijder-modal');
        if (modal) modal.open();
      });
    }

    if (verwijderBevestigKnop) {
      verwijderBevestigKnop.addEventListener('vl-click', () => {
        this._verwijderBestanden(this._teVerwijderenBestanden);
      });
    }

    if (toevoegenKnop) {
      toevoegenKnop.addEventListener('vl-click', () => {
        const uploadZone = this.shadowRoot.querySelector('#upload-zone');
        if (uploadZone) uploadZone.hidden = !uploadZone.hidden;
      });
    }

    if (uploadVerzendKnop) {
      uploadVerzendKnop.addEventListener('vl-click', () => {
        this._verzendUpload();
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
          this._werkDocumentenTabTitelBij();
          this._werkRetryKnopBij();
          this._syncExtracties(projectNaam);
        })
        .catch(() => {
          this._toonFout('Bezwaren konden niet worden geladen.');
        });
  }

  _dienExtractiesIn(projectNaam, bestandsnamen) {
    this._verbergFout();
    this._zetBezig(true);

    fetch(`/api/v1/projects/${encodeURIComponent(projectNaam)}/extracties`, {
      method: 'POST',
      headers: {'Content-Type': 'application/json'},
      body: JSON.stringify({bestandsnamen}),
    })
        .then((response) => {
          if (!response.ok) throw new Error('Indienen extracties mislukt');
          return response.json();
        })
        .then((data) => {
          const tabel = this.shadowRoot.querySelector('#bezwaren-tabel');
          if (tabel && data.taken) {
            data.taken.forEach((taak) => {
              this.__bezwaren = this.__bezwaren.map((b) =>
                b.bestandsnaam === taak.bestandsnaam ? {
                  ...b,
                  status: taak.status,
                  aantalWoorden: taak.aantalWoorden,
                  aantalBezwaren: taak.aantalBezwaren,
                } : b,
              );
              tabel.werkBijMetTaakUpdate(taak);
            });
            this._werkDocumentenTabTitelBij();
            this._werkRetryKnopBij();
          }
        })
        .catch(() => {
          this._toonFout('Extracties konden niet worden ingediend.');
        })
        .finally(() => {
          this._zetBezig(false);
        });
  }

  _retryGefaaldeExtracties(projectNaam) {
    this._verbergFout();
    this._zetBezig(true);

    fetch(`/api/v1/projects/${encodeURIComponent(projectNaam)}/extracties/retry`, {
      method: 'POST',
    })
        .then((response) => {
          if (!response.ok) throw new Error('Retry mislukt');
          return response.json();
        })
        .then((data) => {
          if (data.aantalOpnieuwIngepland > 0) {
            this._toonToast('success',
                `${data.aantalOpnieuwIngepland} extractie(s) opnieuw ingepland.`);
          }
        })
        .catch(() => {
          this._toonFout('Opnieuw proberen mislukt.');
        })
        .finally(() => {
          this._zetBezig(false);
        });
  }

  _werkTabelBij() {
    const sectie = this.shadowRoot && this.shadowRoot.querySelector('#tabs-sectie');
    const tabel = this.shadowRoot && this.shadowRoot.querySelector('#bezwaren-tabel');
    if (tabel) {
      tabel.projectNaam = this.__geselecteerdProject;
      tabel.bezwaren = this.__bezwaren;
    }
    if (sectie) {
      sectie.hidden = false;
    }
  }

  _werkDocumentenTabTitelBij() {
    const pane = this.shadowRoot && this.shadowRoot.querySelector('#documenten');
    if (!pane || this.__bezwaren.length === 0) return;

    const totaal = this.__bezwaren.length;
    const aantalKlaar = this.__bezwaren.filter((b) => b.status === 'extractie-klaar').length;
    const aantalFout = this.__bezwaren.filter((b) => b.status === 'fout').length;
    const isBezig = this.__bezwaren.some(
        (b) => b.status === 'wachtend' || b.status === 'bezig',
    );
    const allesKlaar = aantalKlaar === totaal;

    let titel = `Documenten (${aantalKlaar}/${totaal})`;
    if (allesKlaar) titel = `\u2714\uFE0F Documenten (${totaal}/${totaal})`;
    if (isBezig) titel += ' \u23F3';
    if (aantalFout > 0) titel += ` \u26A0\uFE0F${aantalFout}`;

    const tabs = this.shadowRoot.querySelector('vl-tabs');
    const slot = tabs && tabs.shadowRoot &&
        tabs.shadowRoot.querySelector(`slot[name="documenten-title-slot"]`);
    if (slot) {
      slot.innerHTML = titel;
      slot.style.color = allesKlaar ? '#0e7c3a' : '';
    }
  }

  _werkRetryKnopBij() {
    const retryKnop = this.shadowRoot && this.shadowRoot.querySelector('#retry-knop');
    if (!retryKnop) return;
    const aantalFout = this.__bezwaren.filter((b) => b.status === 'fout').length;
    retryKnop.hidden = aantalFout === 0;
    if (aantalFout > 0) {
      retryKnop.textContent = `Opnieuw proberen (${aantalFout})`;
    }
  }

  _verbergTabsSectie() {
    const sectie = this.shadowRoot && this.shadowRoot.querySelector('#tabs-sectie');
    if (sectie) sectie.hidden = true;
  }

  _verzendUpload() {
    const uploadEl = this.shadowRoot.querySelector('#bestand-upload');
    if (!uploadEl || !this.__geselecteerdProject) return;

    const bestanden = uploadEl.getFiles();
    if (!bestanden || bestanden.length === 0) return;

    const formData = new FormData();
    bestanden.forEach((f) => formData.append('bestanden', f));

    this._zetBezig(true);
    this._verbergFout();

    fetch(`/api/v1/projects/${encodeURIComponent(this.__geselecteerdProject)}/bezwaren/upload`, {
      method: 'POST',
      body: formData,
    })
        .then((response) => {
          if (!response.ok) throw new Error('Upload mislukt');
          return response.json();
        })
        .then((data) => {
          uploadEl.removeAllFiles();
          const uploadZone = this.shadowRoot.querySelector('#upload-zone');
          if (uploadZone) uploadZone.hidden = true;

          if (data.fouten && data.fouten.length > 0) {
            const foutTekst = data.fouten.map((f) => `${f.bestandsnaam}: ${f.reden}`).join(', ');
            this._toonFout(`Sommige bestanden konden niet worden geupload: ${foutTekst}`);
          }

          this._laadBezwaren(this.__geselecteerdProject);
        })
        .catch(() => {
          this._toonFout('Upload mislukt.');
        })
        .finally(() => {
          this._zetBezig(false);
        });
  }

  _verwijderBestanden(bestandsnamen) {
    if (!bestandsnamen || bestandsnamen.length === 0 || !this.__geselecteerdProject) return;

    this._zetBezig(true);
    this._verbergFout();

    const verwijderPromises = bestandsnamen.map((naam) =>
      fetch(`/api/v1/projects/${encodeURIComponent(this.__geselecteerdProject)}/bezwaren/${encodeURIComponent(naam)}`, {
        method: 'DELETE',
      }),
    );

    Promise.all(verwijderPromises)
        .then(() => {
          this._laadBezwaren(this.__geselecteerdProject);
        })
        .catch(() => {
          this._toonFout('Verwijdering mislukt.');
        })
        .finally(() => {
          this._zetBezig(false);
        });
  }

  _zetBezig(bezig) {
    this.__bezig = bezig;
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

  _toonToast(type, bericht) {
    const toaster = this.shadowRoot.querySelector('#toaster');
    if (!toaster) return;

    const alert = document.createElement('vl-alert');
    alert.setAttribute('type', type);
    alert.setAttribute('icon', type === 'success' ? 'check' : 'warning');
    alert.setAttribute('message', bericht);
    alert.setAttribute('closable', '');
    toaster.show(alert);

    if (type === 'success') {
      setTimeout(() => alert.remove(), 5000);
    }
  }
}

defineWebComponent(BezwaarschriftenProjectSelectie, 'bezwaarschriften-project-selectie');
