import {BaseHTMLElement, defineWebComponent, registerWebComponents} from '@domg-wc/common';
import {VlButtonComponent} from '@domg-wc/components/atom/button/vl-button.component.js';
import {VlTabsComponent} from '@domg-wc/components/block/tabs/vl-tabs.component.js';
import {VlTabsPaneComponent} from '@domg-wc/components/block/tabs/vl-tabs-pane.component.js';
import {VlUploadComponent} from '@domg-wc/components/form/upload/vl-upload.component.js';
import {VlModalComponent} from '@domg-wc/components/block/modal/vl-modal.component.js';
import {VlToasterComponent} from '@domg-wc/components/block/toaster/vl-toaster.component.js';
import {vlGlobalStyles, vlGridStyles, vlGroupStyles} from '@domg-wc/styles';
import './bezwaarschriften-bezwaren-tabel.js';
import './bezwaarschriften-kernbezwaren.js';
import './bezwaarschriften-resultaten-tabel.js';

registerWebComponents([VlButtonComponent, VlTabsComponent, VlTabsPaneComponent, VlUploadComponent, VlModalComponent, VlToasterComponent]);

export class BezwaarschriftenProjectSelectie extends BaseHTMLElement {
  static get properties() {
    return {
      __geselecteerdProject: {state: true},
      __bezwaren: {state: true},
      __bezig: {state: true},
      __fout: {state: true},
    };
  }

  set projectNaam(naam) {
    this.__geselecteerdProject = naam;
    if (naam && this.isConnected) {
      this._laadBezwaren(naam);
    }
  }

  get projectNaam() {
    return this.__geselecteerdProject;
  }

  constructor() {
    super(`
      <style>
        ${vlGlobalStyles}
        ${vlGridStyles}
        ${vlGroupStyles}
      </style>
      <div id="tabs-sectie">
        <vl-tabs observe-title active-tab="documenten" disable-links>
          <vl-tabs-pane id="documenten" title="Documenten">
            <div class="vl-group">
              <vl-button id="verwerken-knop" hidden>Verwerken</vl-button>
              <vl-button id="verwijder-knop" error="" hidden>Verwijder geselecteerde</vl-button>
              <vl-button id="toevoegen-knop">Bestanden toevoegen</vl-button>
            </div>
            <p id="fout-melding" hidden></p>
            <bezwaarschriften-bezwaren-tabel id="bezwaren-tabel"></bezwaarschriften-bezwaren-tabel>
          </vl-tabs-pane>
          <vl-tabs-pane id="kernbezwaren" title="Kernbezwaren">
            <bezwaarschriften-kernbezwaren id="kernbezwaren-component"></bezwaarschriften-kernbezwaren>
          </vl-tabs-pane>
          <vl-tabs-pane id="resultaten" title="Resultaten">
            <vl-button id="consolideren-knop" hidden>Genereer antwoordbrieven</vl-button>
            <bezwaarschriften-resultaten-tabel id="resultaten-tabel"></bezwaarschriften-resultaten-tabel>
          </vl-tabs-pane>
        </vl-tabs>
      </div>
      <vl-modal id="verwijder-modal" title="Bestanden verwijderen" closable>
        <div slot="content">
          <p id="verwijder-bevestiging-tekst"></p>
        </div>
        <div slot="button">
          <vl-button id="verwijder-bevestig-knop" error="">Verwijderen</vl-button>
        </div>
      </vl-modal>
      <vl-modal id="annuleer-modal" title="Verwerking annuleren" closable>
        <div slot="content">
          <p id="annuleer-bevestiging-tekst"></p>
        </div>
        <div slot="button">
          <vl-button id="annuleer-bevestig-knop" error="">Verwerking stoppen</vl-button>
        </div>
      </vl-modal>
      <vl-modal id="upload-modal" title="Bestanden toevoegen" closable>
        <div slot="content">
          <vl-upload id="bestand-upload"
            accepted-files=".txt"
            max-files="1000"
            max-size="50"
            main-title="Bezwaarbestanden toevoegen"
            sub-title="Sleep .txt bestanden hierheen of klik om te bladeren">
          </vl-upload>
        </div>
        <div slot="button">
          <vl-button id="upload-verzend-knop">Uploaden</vl-button>
        </div>
      </vl-modal>
      <vl-toaster id="toaster"></vl-toaster>
    `);
    this.__bezwaren = [];
    this.__bezig = false;
    this.__fout = null;
    this._ws = null;
    this._wsReconnectDelay = 1000;
    this._teVerwijderenBestanden = [];
    this._teAnnulerenTaak = null;
    this.__consolidatieDocumenten = [];
  }

  connectedCallback() {
    super.connectedCallback();
    if (this.__geselecteerdProject) {
      this._laadBezwaren(this.__geselecteerdProject);
    }
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
    const url = `${protocol}//${window.location.host}/ws/taken`;
    this._ws = new WebSocket(url);

    this._ws.onmessage = (event) => {
      const data = JSON.parse(event.data);
      if (data.type === 'taak-update') {
        this._verwerkTaakUpdate(data.taak);
      }
      if (data.type === 'consolidatie-update') {
        this._verwerkConsolidatieUpdate(data.taak);
      }
      if (data.type === 'clustering-update') {
        this._verwerkClusteringUpdate(data.taak);
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
        heeftPassagesDieNietInTekstVoorkomen: taak.heeftPassagesDieNietInTekstVoorkomen,
      } : b,
    );
    const tabel = this.shadowRoot.querySelector('#bezwaren-tabel');
    if (tabel) {
      tabel.werkBijMetTaakUpdate(taak);
    }
    this._werkDocumentenTabTitelBij();
    this._werkVerwerkenKnopBij();
    if (taak.status === 'extractie-klaar') {
      const kernComp = this.shadowRoot.querySelector('#kernbezwaren-component');
      if (kernComp) {
        const totaalBezwaren = this.__bezwaren
            .filter((b) => b.status === 'extractie-klaar')
            .reduce((sum, b) => sum + (b.aantalBezwaren || 0), 0);
        kernComp.setAantalBezwaren(totaalBezwaren);
        kernComp.setExtractieKlaar(true);
      }
    }
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
                  heeftPassagesDieNietInTekstVoorkomen: taak.heeftPassagesDieNietInTekstVoorkomen,
                } : b,
              );
              tabel.werkBijMetTaakUpdate(taak);
            });
            this._werkDocumentenTabTitelBij();
            this._werkVerwerkenKnopBij();
          }
        })
        .catch(() => {/* stille fout bij sync */});
  }

  _koppelEventListeners() {
    const verwerkenKnop = this.shadowRoot && this.shadowRoot.querySelector('#verwerken-knop');
    const verwijderKnop = this.shadowRoot && this.shadowRoot.querySelector('#verwijder-knop');
    const toevoegenKnop = this.shadowRoot && this.shadowRoot.querySelector('#toevoegen-knop');
    const uploadVerzendKnop = this.shadowRoot && this.shadowRoot.querySelector('#upload-verzend-knop');
    const verwijderBevestigKnop = this.shadowRoot && this.shadowRoot.querySelector('#verwijder-bevestig-knop');

    this.shadowRoot.addEventListener('annuleer-taak', (e) => {
      const {bestandsnaam, taakId} = e.detail;
      this._teAnnulerenTaak = {bestandsnaam, taakId};
      const tekst = this.shadowRoot.querySelector('#annuleer-bevestiging-tekst');
      if (tekst) {
        tekst.textContent = `Weet je zeker dat je de verwerking van "${bestandsnaam}" wilt annuleren?`;
      }
      const modal = this.shadowRoot.querySelector('#annuleer-modal');
      if (modal) modal.open();
    });

    const annuleerBevestigKnop = this.shadowRoot && this.shadowRoot.querySelector('#annuleer-bevestig-knop');
    if (annuleerBevestigKnop) {
      annuleerBevestigKnop.addEventListener('vl-click', () => {
        if (this._teAnnulerenTaak) {
          this._annuleerTaak(this._teAnnulerenTaak.taakId);
        }
      });
    }

    this.shadowRoot.addEventListener('verwijder-bezwaar', (e) => {
      const {bestandsnaam} = e.detail;
      this._teVerwijderenBestanden = [bestandsnaam];
      const tekst = this.shadowRoot.querySelector('#verwijder-bevestiging-tekst');
      if (tekst) {
        tekst.textContent = `Weet je zeker dat je "${bestandsnaam}" wilt verwijderen? Het bestand en bijhorende extractie-resultaten worden permanent verwijderd.`;
      }
      const modal = this.shadowRoot.querySelector('#verwijder-modal');
      if (modal) modal.open();
    });

    this.shadowRoot.addEventListener('herstart-taak', (e) => {
      const {bestandsnaam} = e.detail;
      if (this.__geselecteerdProject) {
        this._dienExtractiesIn(this.__geselecteerdProject, [bestandsnaam]);
      }
    });

    this.shadowRoot.addEventListener('toon-extractie-detail', (e) => {
      const {bestandsnaam} = e.detail;
      const tabel = this.shadowRoot.querySelector('#bezwaren-tabel');
      if (tabel && this.__geselecteerdProject) {
        tabel.toonExtractieDetails(this.__geselecteerdProject, bestandsnaam);
      }
    });

    this.shadowRoot.addEventListener('antwoord-voortgang', (e) => {
      this._werkKernbezwarenTabTitelBij(e.detail.aantalMetAntwoord, e.detail.totaal);
      if (this.__geselecteerdProject) {
        this._laadConsolidaties(this.__geselecteerdProject);
      }
    });

    this.shadowRoot.addEventListener('selectie-gewijzigd', (e) => {
      if (e.target.tagName === 'BEZWAARSCHRIFTEN-BEZWAREN-TABEL') {
        const aantal = e.detail.geselecteerd.length;
        const heeftSelectie = aantal > 0;
        if (verwijderKnop) {
          verwijderKnop.hidden = !heeftSelectie;
        }
        if (toevoegenKnop) {
          toevoegenKnop.hidden = heeftSelectie;
        }
        this._werkVerwerkenKnopBij();
      } else if (e.target.tagName === 'BEZWAARSCHRIFTEN-RESULTATEN-TABEL') {
        this._werkConsoliderenKnopBij();
      }
    });

    // Consolidatie-events
    this.shadowRoot.addEventListener('start-consolidatie', (e) => {
      const {bestandsnaam} = e.detail;
      if (this.__geselecteerdProject) {
        this._dienConsolidatiesIn(this.__geselecteerdProject, [bestandsnaam]);
      }
    });

    this.shadowRoot.addEventListener('herstart-consolidatie', (e) => {
      const {bestandsnaam} = e.detail;
      if (this.__geselecteerdProject) {
        this._dienConsolidatiesIn(this.__geselecteerdProject, [bestandsnaam]);
      }
    });

    this.shadowRoot.addEventListener('annuleer-consolidatie', (e) => {
      const {taakId} = e.detail;
      this._annuleerConsolidatieTaak(taakId);
    });

    const consoliderenKnop = this.shadowRoot && this.shadowRoot.querySelector('#consolideren-knop');
    if (consoliderenKnop) {
      consoliderenKnop.addEventListener('vl-click', () => {
        if (!this.__geselecteerdProject || !this.__consolidatieDocumenten) return;
        const opStartbaar = this.__consolidatieDocumenten
            .filter((d) => d.status === 'volledig' || d.status === 'fout')
            .map((d) => d.bestandsnaam);
        if (opStartbaar.length > 0) {
          this._dienConsolidatiesIn(this.__geselecteerdProject, opStartbaar);
        }
      });
    }

    if (verwerkenKnop) {
      verwerkenKnop.addEventListener('vl-click', () => {
        if (this.__bezig || !this.__geselecteerdProject) return;
        const tabel = this.shadowRoot.querySelector('#bezwaren-tabel');
        const geselecteerd = tabel ? tabel.geefGeselecteerdeBestandsnamen() : [];
        if (geselecteerd.length > 0) {
          this._dienExtractiesIn(this.__geselecteerdProject, geselecteerd);
        } else {
          this._verwerkOnafgeronde(this.__geselecteerdProject);
        }
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
        const modal = this.shadowRoot.querySelector('#upload-modal');
        if (modal) modal.open();
      });
    }

    if (uploadVerzendKnop) {
      uploadVerzendKnop.addEventListener('vl-click', () => {
        this._verzendUpload();
      });
    }

    const tabs = this.shadowRoot && this.shadowRoot.querySelector('vl-tabs');
    if (tabs) {
      tabs.addEventListener('change', (e) => {
        tabs.scrollIntoView({behavior: 'smooth', block: 'start'});
        const activeTab = e.detail && e.detail.activeTab;
        if (activeTab === 'kernbezwaren' && this.__geselecteerdProject) {
          const kernComp = this.shadowRoot.querySelector('#kernbezwaren-component');
          if (kernComp) {
            kernComp.laadClusteringTaken(this.__geselecteerdProject);
            kernComp.laadKernbezwaren(this.__geselecteerdProject);
          }
        }
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
          this._werkVerwerkenKnopBij();
          this._syncExtracties(projectNaam);
          this._werkKernbezwarenBij(projectNaam, data.bezwaren);
          this._laadConsolidaties(projectNaam);
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
                  heeftPassagesDieNietInTekstVoorkomen: taak.heeftPassagesDieNietInTekstVoorkomen,
                } : b,
              );
              tabel.werkBijMetTaakUpdate(taak);
            });
            this._werkDocumentenTabTitelBij();
            this._werkVerwerkenKnopBij();
          }
        })
        .catch(() => {
          this._toonFout('Extracties konden niet worden ingediend.');
        })
        .finally(() => {
          this._zetBezig(false);
        });
  }

  _verwerkOnafgeronde(projectNaam) {
    this._verbergFout();
    this._zetBezig(true);

    fetch(`/api/v1/projects/${encodeURIComponent(projectNaam)}/extracties/verwerken`, {
      method: 'POST',
    })
        .then((response) => {
          if (!response.ok) throw new Error('Verwerken mislukt');
        })
        .catch(() => {
          this._toonFout('Verwerken mislukt.');
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

  _werkKernbezwarenTabTitelBij(aantalMetAntwoord, totaal) {
    const tabs = this.shadowRoot.querySelector('vl-tabs');
    const slot = tabs && tabs.shadowRoot &&
        tabs.shadowRoot.querySelector(`slot[name="kernbezwaren-title-slot"]`);
    if (!slot) return;

    if (totaal === 0) {
      slot.innerHTML = 'Kernbezwaren';
      slot.style.color = '';
      return;
    }

    const allesKlaar = aantalMetAntwoord === totaal;

    let titel = `Kernbezwaren (${aantalMetAntwoord}/${totaal})`;
    if (allesKlaar) titel = `\u2714\uFE0F Kernbezwaren (${totaal}/${totaal})`;

    slot.innerHTML = titel;
    slot.style.color = allesKlaar ? '#0e7c3a' : '';
  }

  _werkVerwerkenKnopBij() {
    const verwerkenKnop = this.shadowRoot && this.shadowRoot.querySelector('#verwerken-knop');
    if (!verwerkenKnop) return;
    const tabel = this.shadowRoot.querySelector('#bezwaren-tabel');
    const geselecteerd = tabel ? tabel.geefGeselecteerdeBestandsnamen() : [];
    if (geselecteerd.length > 0) {
      verwerkenKnop.hidden = false;
      verwerkenKnop.textContent = `Verwerken (${geselecteerd.length})`;
    } else {
      const aantalTeVerwerken = this.__bezwaren.filter(
          (b) => b.status === 'fout' || b.status === 'todo').length;
      verwerkenKnop.hidden = aantalTeVerwerken === 0;
      if (aantalTeVerwerken > 0) {
        verwerkenKnop.textContent = `Verwerken (${aantalTeVerwerken})`;
      }
    }
  }

  _werkKernbezwarenBij(projectNaam, bezwaren) {
    const kernComp = this.shadowRoot.querySelector('#kernbezwaren-component');
    if (!kernComp) return;
    const aantalKlaar = bezwaren.filter((b) => b.status === 'extractie-klaar').length;
    const totaalBezwaren = bezwaren
        .filter((b) => b.status === 'extractie-klaar')
        .reduce((sum, b) => sum + (b.aantalBezwaren || 0), 0);
    kernComp.setAantalBezwaren(totaalBezwaren);
    kernComp.setExtractieKlaar(aantalKlaar > 0);
    kernComp.laadKernbezwaren(projectNaam);
    kernComp.laadClusteringTaken(projectNaam);
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
          const modal = this.shadowRoot.querySelector('#upload-modal');
          if (modal) modal.close();

          if (data.geupload && data.geupload.length > 0) {
            this._toonToast('success',
                `${data.geupload.length} bestand(en) succesvol opgeladen.`);
          }

          if (data.fouten && data.fouten.length > 0) {
            const alert = document.createElement('vl-alert');
            alert.setAttribute('type', 'error');
            alert.setAttribute('icon', 'warning');
            alert.setAttribute('message',
                `${data.fouten.length} bestand(en) niet opgeladen: bestand met dezelfde naam bestaat al.`);
            alert.setAttribute('closable', '');
            const ul = document.createElement('ul');
            data.fouten.forEach((f) => {
              const li = document.createElement('li');
              li.textContent = f.bestandsnaam;
              ul.appendChild(li);
            });
            alert.appendChild(ul);
            const toaster = this.shadowRoot.querySelector('#toaster');
            if (toaster) toaster.show(alert);
          }

          this._laadBezwaren(this.__geselecteerdProject);
        })
        .catch(() => {
          this._toonToast('error', 'Upload mislukt.');
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

  _annuleerTaak(taakId) {
    if (!this.__geselecteerdProject) return;

    this._zetBezig(true);
    this._verbergFout();

    fetch(`/api/v1/projects/${encodeURIComponent(this.__geselecteerdProject)}/extracties/${taakId}`, {
      method: 'DELETE',
    })
        .then((response) => {
          if (!response.ok) throw new Error('Annuleren mislukt');
          this._toonToast('success', 'Verwerking geannuleerd.');
          this._laadBezwaren(this.__geselecteerdProject);
        })
        .catch(() => {
          this._toonFout('Annuleren van verwerking mislukt.');
        })
        .finally(() => {
          this._zetBezig(false);
          this._teAnnulerenTaak = null;
        });
  }

  _laadConsolidaties(projectNaam) {
    fetch(`/api/v1/projects/${encodeURIComponent(projectNaam)}/consolidaties`)
        .then((response) => {
          if (!response.ok) return null;
          return response.json();
        })
        .then((data) => {
          if (data && data.documenten) {
            this.__consolidatieDocumenten = data.documenten;
            this._werkResultatenTabelBij();
            this._werkResultatenTabTitelBij();
            this._werkConsoliderenKnopBij();
          }
        })
        .catch(() => {/* stille fout bij laden consolidaties */});
  }

  _dienConsolidatiesIn(projectNaam, bestandsnamen) {
    fetch(`/api/v1/projects/${encodeURIComponent(projectNaam)}/consolidaties`, {
      method: 'POST',
      headers: {'Content-Type': 'application/json'},
      body: JSON.stringify({bestandsnamen}),
    })
        .then((response) => {
          if (!response.ok) throw new Error('Indienen consolidaties mislukt');
          return response.json();
        })
        .then((data) => {
          if (data && data.taken) {
            const tabel = this.shadowRoot.querySelector('#resultaten-tabel');
            if (tabel) {
              data.taken.forEach((taak) => tabel.werkBijMetTaakUpdate(taak));
            }
            this._werkResultatenTabTitelBij();
            this._werkConsoliderenKnopBij();
          }
        })
        .catch(() => {
          this._toonToast('error', 'Consolidatie kon niet worden ingediend.');
        });
  }

  _annuleerConsolidatieTaak(taakId) {
    if (!this.__geselecteerdProject) return;
    fetch(`/api/v1/projects/${encodeURIComponent(this.__geselecteerdProject)}/consolidaties/${taakId}`, {
      method: 'DELETE',
    })
        .then((response) => {
          if (!response.ok) throw new Error('Annuleren mislukt');
          this._toonToast('success', 'Consolidatie geannuleerd.');
          this._laadConsolidaties(this.__geselecteerdProject);
        })
        .catch(() => {
          this._toonToast('error', 'Annuleren van consolidatie mislukt.');
        });
  }

  _verwerkConsolidatieUpdate(taak) {
    if (!this.__geselecteerdProject || taak.projectNaam !== this.__geselecteerdProject) {
      return;
    }
    const tabel = this.shadowRoot.querySelector('#resultaten-tabel');
    if (tabel) {
      tabel.werkBijMetTaakUpdate(taak);
    }
    // Update local state
    this.__consolidatieDocumenten = this.__consolidatieDocumenten.map((d) =>
      d.bestandsnaam === taak.bestandsnaam ? {...d, status: taak.status} : d,
    );
    this._werkResultatenTabTitelBij();
    this._werkConsoliderenKnopBij();
  }

  _verwerkClusteringUpdate(taak) {
    if (!this.__geselecteerdProject || taak.projectNaam !== this.__geselecteerdProject) {
      return;
    }
    const kernComp = this.shadowRoot.querySelector('#kernbezwaren-component');
    if (kernComp) {
      kernComp.werkBijMetClusteringUpdate(taak);
    }
  }

  _werkResultatenTabelBij() {
    const tabel = this.shadowRoot && this.shadowRoot.querySelector('#resultaten-tabel');
    if (tabel && this.__consolidatieDocumenten) {
      tabel.projectNaam = this.__geselecteerdProject;
      tabel.documenten = this.__consolidatieDocumenten;
    }
  }

  _werkResultatenTabTitelBij() {
    if (!this.__consolidatieDocumenten || this.__consolidatieDocumenten.length === 0) return;
    const totaal = this.__consolidatieDocumenten.length;
    const aantalKlaar = this.__consolidatieDocumenten.filter(
        (d) => d.status === 'klaar').length;
    const isBezig = this.__consolidatieDocumenten.some(
        (d) => d.status === 'wachtend' || d.status === 'bezig');
    const allesKlaar = aantalKlaar === totaal;

    let titel = `Resultaten (${aantalKlaar}/${totaal})`;
    if (allesKlaar) titel = `\u2714\uFE0F Resultaten (${totaal}/${totaal})`;
    if (isBezig) titel += ' \u23F3';

    const tabs = this.shadowRoot.querySelector('vl-tabs');
    const slot = tabs && tabs.shadowRoot &&
        tabs.shadowRoot.querySelector(`slot[name="resultaten-title-slot"]`);
    if (slot) {
      slot.innerHTML = titel;
      slot.style.color = allesKlaar ? '#0e7c3a' : '';
    }
  }

  _werkConsoliderenKnopBij() {
    const consoliderenKnop = this.shadowRoot && this.shadowRoot.querySelector('#consolideren-knop');
    if (!consoliderenKnop || !this.__consolidatieDocumenten) return;
    const aantalOpStartbaar = this.__consolidatieDocumenten.filter(
        (d) => d.status === 'volledig' || d.status === 'fout').length;
    consoliderenKnop.hidden = aantalOpStartbaar === 0;
    if (aantalOpStartbaar > 0) {
      consoliderenKnop.textContent = `Genereer antwoordbrieven (${aantalOpStartbaar})`;
    }
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
