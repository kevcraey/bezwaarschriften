import {BaseHTMLElement, defineWebComponent, registerWebComponents} from '@domg-wc/common';
import {VlButtonComponent} from '@domg-wc/components/atom/button/vl-button.component.js';
import {VlAccordionComponent} from '@domg-wc/components/block/accordion/vl-accordion.component.js';
import {VlAlert} from '@domg-wc/components/block/alert';
import {VlModalComponent} from '@domg-wc/components/block/modal/vl-modal.component.js';
import {VlSideSheet} from '@domg-wc/components/block/side-sheet/vl-side-sheet.component.js';
import {VlPillComponent} from '@domg-wc/components/block/pill/vl-pill.component.js';
import {vlGlobalStyles, vlGridStyles, vlGroupStyles} from '@domg-wc/styles';
import '@domg-wc/components/form/textarea-rich';

registerWebComponents([VlButtonComponent, VlAccordionComponent, VlAlert, VlModalComponent, VlSideSheet, VlPillComponent]);

export class BezwaarschriftenKernbezwaren extends BaseHTMLElement {
  constructor() {
    super(`
      <style>
        ${vlGlobalStyles}
        ${vlGridStyles}
        ${vlGroupStyles}
        :host { display: block; padding: 1.5rem; transition: margin-right 0.2s ease; }
        :host(.side-sheet-open) { margin-right: 33.3%; }
        .clustering-header {
          margin-bottom: 1.5rem;
        }
        .categorie-wrapper {
          margin-bottom: 0.5rem;
        }
        .categorie-header {
          display: flex;
          align-items: center;
          padding: 0.75rem 0;
          border-bottom: 1px solid #e8ebee;
          gap: 0.75rem;
        }
        .categorie-label {
          font-weight: bold;
          flex: 1;
        }
        .kernbezwaar-item {
          display: flex;
          flex-wrap: wrap;
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
          text-decoration: none;
          cursor: pointer;
        }
        .passage-bestandsnaam:hover {
          text-decoration: underline;
          color: #0055cc;
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
        .status-pill {
          font-variant-numeric: tabular-nums;
          min-width: 120px;
          display: inline-block;
        }
        .side-sheet-wrapper {
          display: flex;
          flex-direction: column;
          height: calc(100vh - 43px);
          margin: -1.5rem;
          padding: 0;
          overflow: hidden;
        }
        .side-sheet-header {
          display: flex;
          justify-content: space-between;
          align-items: flex-start;
          padding: 1rem 1.5rem;
          border-bottom: 2px solid #e8ebee;
          flex-shrink: 0;
          background: white;
        }
        .side-sheet-titel {
          font-weight: bold;
          flex: 1;
          margin-right: 1rem;
        }
        .side-sheet-sluit-knop {
          flex-shrink: 0;
        }
        .side-sheet-body {
          flex: 1;
          overflow-y: auto;
          padding: 1rem 1.5rem;
        }
        .antwoord-editor-wrapper {
          width: 100%;
          margin-top: 0.75rem;
          padding-top: 0.75rem;
          border-top: 1px solid #e8ebee;
        }
        .antwoord-opslaan-rij {
          margin-top: 0.5rem;
          display: flex;
          gap: 0.5rem;
          align-items: center;
        }
        .antwoord-opslaan-melding {
          font-size: 0.85rem;
          color: #0e7c26;
        }
      </style>
      <div id="inhoud"></div>
      <vl-side-sheet id="side-sheet" hide-toggle-button>
        <div class="side-sheet-wrapper">
          <div class="side-sheet-header">
            <div id="side-sheet-titel" class="side-sheet-titel"></div>
            <vl-button id="side-sheet-sluit-knop" class="side-sheet-sluit-knop"
                ghost="" icon="close" label="Sluiten"></vl-button>
          </div>
          <div id="side-sheet-inhoud" class="side-sheet-body"></div>
        </div>
      </vl-side-sheet>
      <vl-modal id="consolidatie-waarschuwing" title="Antwoordbrieven worden ongeldig" closable not-auto-closable>
        <div slot="content" id="consolidatie-waarschuwing-inhoud"></div>
        <vl-button slot="button" id="consolidatie-waarschuwing-bevestig" error="">Doorgaan en verwijderen</vl-button>
      </vl-modal>
      <vl-modal id="verwijder-bevestiging" title="Clustering verwijderen" closable not-auto-closable>
        <div slot="content" id="verwijder-bevestiging-inhoud"></div>
        <vl-button slot="button" id="verwijder-bevestiging-bevestig" error="">Verwijderen</vl-button>
      </vl-modal>
    `);
    this._projectNaam = null;
    this._aantalBezwaren = 0;
    this._extractieKlaar = false;
    this._themas = null;
    this._bezig = false;
    this._clusteringTaken = [];
    this._timerInterval = null;
  }

  connectedCallback() {
    super.connectedCallback();
    const sluitKnop = this.shadowRoot.querySelector('#side-sheet-sluit-knop');
    const sideSheet = this.shadowRoot.querySelector('#side-sheet');
    if (sluitKnop && sideSheet) {
      sluitKnop.addEventListener('click', () => {
        sideSheet.close();
        this.classList.remove('side-sheet-open');
      });
    }
  }

  disconnectedCallback() {
    this._stopTimer();
  }

  // --- Public methods called by parent ---

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

  laadClusteringTaken(projectNaam) {
    this._projectNaam = projectNaam;
    fetch(`/api/v1/projects/${encodeURIComponent(projectNaam)}/clustering-taken`)
        .then((response) => {
          if (!response.ok) throw new Error('Ophalen clustering-taken mislukt');
          return response.json();
        })
        .then((data) => {
          if (data && data.categorieen) {
            this._clusteringTaken = data.categorieen;
            this._renderInhoud();
            this._beheerTimer();
          }
        })
        .catch(() => {
          this._clusteringTaken = [];
          this._renderInhoud();
        });
  }

  werkBijMetClusteringUpdate(taak) {
    let gevonden = false;
    this._clusteringTaken = this._clusteringTaken.map((ct) => {
      if (ct.categorie === taak.categorie) {
        gevonden = true;
        return {
          ...ct,
          taakId: taak.id,
          status: taak.status,
          aantalBezwaren: taak.aantalBezwaren,
          aantalKernbezwaren: taak.aantalKernbezwaren,
          aangemaaktOp: taak.aangemaaktOp,
          verwerkingGestartOp: taak.verwerkingGestartOp,
          verwerkingVoltooidOp: taak.verwerkingVoltooidOp,
          foutmelding: taak.foutmelding,
        };
      }
      return ct;
    });

    if (!gevonden) {
      this._clusteringTaken.push({
        categorie: taak.categorie,
        status: taak.status,
        taakId: taak.id,
        aantalBezwaren: taak.aantalBezwaren,
        aantalKernbezwaren: taak.aantalKernbezwaren,
        aangemaaktOp: taak.aangemaaktOp,
        verwerkingGestartOp: taak.verwerkingGestartOp,
        verwerkingVoltooidOp: taak.verwerkingVoltooidOp,
        foutmelding: taak.foutmelding,
      });
    }

    // Als een clustering klaar is, herlaad de kernbezwaren
    if (taak.status === 'klaar' && this._projectNaam) {
      this.laadKernbezwaren(this._projectNaam);
    }

    this._renderInhoud();
    this._beheerTimer();
  }

  setAantalBezwaren(aantal) {
    this._aantalBezwaren = aantal;
    if (!this._themas && this._clusteringTaken.length === 0) this._renderInhoud();
  }

  setExtractieKlaar(klaar) {
    this._extractieKlaar = klaar;
    if (!this._themas && this._clusteringTaken.length === 0) this._renderInhoud();
  }

  reset() {
    this._themas = null;
    this._clusteringTaken = [];
    this._stopTimer();
    this._renderInhoud();
  }

  // --- Rendering ---

  _renderInhoud() {
    const inhoud = this.shadowRoot.querySelector('#inhoud');
    if (!inhoud) return;

    // Als er clustering-taken zijn, toon per-categorie layout
    if (this._clusteringTaken.length > 0) {
      this._renderCategorieOverzicht(inhoud);
      return;
    }

    // Legacy: als er themas zijn (van oude clustering)
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
           Voer clustering tot thema's en kernbezwaren uit om verder te gaan.</p>
        <vl-button id="groepeer-knop">Cluster bezwaren</vl-button>
      </div>`;

    const knop = inhoud.querySelector('#groepeer-knop');
    if (knop) {
      knop.addEventListener('click', () => this._groepeer());
    }
  }

  _renderCategorieOverzicht(inhoud) {
    inhoud.innerHTML = '';

    // Globale knop bovenaan rechts
    const header = document.createElement('div');
    header.className = 'clustering-header vl-group vl-group--justify-end';

    const clusterAllesKnop = document.createElement('vl-button');
    clusterAllesKnop.id = 'cluster-alles-knop';
    clusterAllesKnop.textContent = 'Cluster alle categorie\u00EBn';
    clusterAllesKnop.addEventListener('click', () => this._clusterAlles());
    header.appendChild(clusterAllesKnop);

    const heeftKlareCategorie = this._clusteringTaken.some((ct) => ct.status === 'klaar');
    const heeftActieveTaak = this._clusteringTaken.some(
        (ct) => ct.status === 'wachtend' || ct.status === 'bezig');
    if (heeftKlareCategorie && !heeftActieveTaak) {
      const verwijderAllesKnop = document.createElement('vl-button');
      verwijderAllesKnop.id = 'verwijder-alles-knop';
      verwijderAllesKnop.setAttribute('error', '');
      verwijderAllesKnop.textContent = 'Alles verwijderen';
      verwijderAllesKnop.addEventListener('click', () => this._verwijderAlles());
      header.appendChild(verwijderAllesKnop);
    }

    inhoud.appendChild(header);

    // Globale samenvatting als er klare categorien zijn
    if (this._themas) {
      let totaalBezwaren = 0;
      let totaalKernbezwaren = 0;
      this._clusteringTaken.forEach((ct) => {
        if (ct.status === 'klaar') {
          const thema = this._themas.find((t) => t.naam === ct.categorie);
          if (thema) {
            totaalBezwaren += ct.aantalBezwaren;
            totaalKernbezwaren += thema.kernbezwaren.length;
          }
        }
      });
      if (totaalKernbezwaren > 0) {
        const reductie = Math.round(totaalBezwaren / totaalKernbezwaren);
        const reductieTekst = reductie > 1 ? ` (${reductie}x reductie)` : '';
        const totaalAlert = document.createElement('vl-alert');
        totaalAlert.setAttribute('type', 'success');
        totaalAlert.setAttribute('naked', '');
        totaalAlert.setAttribute('message', `Er zijn ${totaalBezwaren} individuele bezwaren ` +
            `herleid naar ${totaalKernbezwaren} kernbezwaren${reductieTekst}.`);
        inhoud.appendChild(totaalAlert);
      }
    }

    // Per categorie
    this._clusteringTaken.forEach((ct) => {
      const wrapper = document.createElement('div');
      wrapper.className = 'categorie-wrapper';
      wrapper.dataset.categorie = ct.categorie;

      const categorieHeader = document.createElement('div');
      categorieHeader.className = 'categorie-header';

      // Label: "Geluid (42 bezwaren)"
      const label = document.createElement('span');
      label.className = 'categorie-label';
      label.textContent = `${ct.categorie} (${ct.aantalBezwaren} bezwaren)`;
      categorieHeader.appendChild(label);

      // Pill met status
      const pill = this._maakStatusPill(ct);
      categorieHeader.appendChild(pill);

      // Actieknop
      const actieKnop = this._maakActieKnop(ct);
      if (actieKnop) {
        categorieHeader.appendChild(actieKnop);
      }

      wrapper.appendChild(categorieHeader);

      // Kernbezwaren tonen voor klare categorien
      if (ct.status === 'klaar' && this._themas) {
        const thema = this._themas.find((t) => t.naam === ct.categorie);
        if (thema && thema.kernbezwaren.length > 0) {
          const aantalKern = thema.kernbezwaren.length;
          const reductie = Math.round(ct.aantalBezwaren / aantalKern);
          const reductieTekst = reductie > 1 ? ` (${reductie}x reductie)` : '';
          const samenvatting = document.createElement('vl-alert');
          samenvatting.setAttribute('type', 'success');
          samenvatting.setAttribute('naked', '');
          samenvatting.setAttribute('size', 'small');
          samenvatting.setAttribute('message', `Er zijn ${ct.aantalBezwaren} individuele bezwaren ` +
              `herleid naar ${aantalKern} kernbezwaren${reductieTekst}.`);
          wrapper.appendChild(samenvatting);

          thema.kernbezwaren.forEach((kern) => {
            wrapper.appendChild(this._maakKernbezwaarItem(kern));
          });
        }
      }

      inhoud.appendChild(wrapper);
    });

    this._dispatchVoortgang();
  }

  _maakKernbezwaarItem(kern) {
    const item = document.createElement('div');
    item.className = 'kernbezwaar-item';

    const samenvatting = document.createElement('div');
    samenvatting.className = 'kernbezwaar-samenvatting';
    samenvatting.textContent = kern.antwoord ? `\u2714 ${kern.samenvatting}` : kern.samenvatting;
    if (kern.antwoord) samenvatting.style.color = '#0e7c3a';

    const penKnop = document.createElement('vl-button');
    penKnop.setAttribute('ghost', '');
    penKnop.setAttribute('icon', 'pencil');
    penKnop.setAttribute('label', kern.antwoord ? 'Antwoord bewerken' : 'Antwoord invoeren');
    if (kern.antwoord) {
      penKnop.classList.add('heeft-antwoord');
    }
    penKnop.addEventListener('click', () => this._toggleEditor(item, kern, penKnop));

    const actie = document.createElement('div');
    actie.className = 'kernbezwaar-actie';
    const knop = document.createElement('vl-button');
    knop.setAttribute('ghost', '');
    knop.setAttribute('icon', 'search');
    knop.textContent = `(${kern.individueleBezwaren.length})`;
    knop.addEventListener('click', () => this._toonPassages(kern));

    actie.appendChild(knop);
    actie.appendChild(penKnop);
    item.appendChild(samenvatting);
    item.appendChild(actie);
    return item;
  }

  _maakStatusPill(ct) {
    const pill = document.createElement('vl-pill');
    pill.dataset.categorie = ct.categorie;
    pill.className = 'status-pill';

    switch (ct.status) {
      case 'todo':
        pill.textContent = 'Te clusteren';
        break;
      case 'wachtend':
      case 'bezig': {
        pill.setAttribute('type', 'warning');
        const span = document.createElement('span');
        span.className = 'timer-tekst';
        span.textContent = this._formatClusteringStatus(ct);
        pill.appendChild(span);
        break;
      }
      case 'klaar': {
        pill.setAttribute('type', 'success');
        pill.textContent = this._formatClusteringStatus(ct);
        break;
      }
      case 'fout':
        pill.setAttribute('type', 'error');
        pill.textContent = 'Fout';
        break;
      default:
        pill.textContent = ct.status;
    }

    return pill;
  }

  _maakActieKnop(ct) {
    let icon;
    let titel;
    let error = false;
    let onClick;

    switch (ct.status) {
      case 'todo':
        icon = 'play-filled';
        titel = 'Clustering starten';
        onClick = () => this._startClustering(ct.categorie);
        break;
      case 'wachtend':
      case 'bezig':
        icon = 'close';
        titel = 'Annuleer clustering';
        onClick = () => this._annuleerClustering(ct.categorie);
        break;
      case 'klaar':
        icon = 'bin';
        titel = 'Verwijder clustering';
        error = true;
        onClick = () => this._toonVerwijderBevestiging(ct.categorie);
        break;
      case 'fout':
        icon = 'synchronize';
        titel = 'Opnieuw clusteren';
        onClick = () => this._startClustering(ct.categorie);
        break;
      default:
        return null;
    }

    const btn = document.createElement('vl-button');
    btn.setAttribute('icon', icon);
    btn.setAttribute('label', titel);
    btn.setAttribute('ghost', '');
    if (error) btn.setAttribute('error', '');
    btn.addEventListener('click', (e) => {
      e.stopPropagation();
      onClick();
    });
    return btn;
  }

  // --- Timer management ---

  _beheerTimer() {
    const heeftActief = this._clusteringTaken.some(
        (ct) => ct.status === 'wachtend' || ct.status === 'bezig',
    );
    if (heeftActief && !this._timerInterval) {
      this._timerInterval = setInterval(() => this._updateTimers(), 1000);
    } else if (!heeftActief && this._timerInterval) {
      this._stopTimer();
    }
  }

  _stopTimer() {
    if (this._timerInterval) {
      clearInterval(this._timerInterval);
      this._timerInterval = null;
    }
  }

  _updateTimers() {
    const inhoud = this.shadowRoot.querySelector('#inhoud');
    if (!inhoud) return;

    this._clusteringTaken.forEach((ct) => {
      if (ct.status !== 'wachtend' && ct.status !== 'bezig') return;
      const pill = inhoud.querySelector(`vl-pill[data-categorie="${CSS.escape(ct.categorie)}"]`);
      if (!pill) return;
      const timerTekst = pill.querySelector('.timer-tekst');
      if (timerTekst) {
        timerTekst.textContent = this._formatClusteringStatus(ct);
      }
    });
  }

  _formatClusteringStatus(ct, nu) {
    nu = nu || Date.now();

    if (ct.status === 'wachtend' && ct.aangemaaktOp) {
      const wachtMs = nu - new Date(ct.aangemaaktOp).getTime();
      return `Wachtend (${this._formatTijd(wachtMs)})`;
    }

    if (ct.status === 'bezig') {
      const wachtMs = ct.verwerkingGestartOp && ct.aangemaaktOp ?
        new Date(ct.verwerkingGestartOp).getTime() -
            new Date(ct.aangemaaktOp).getTime() :
        0;
      const verwerkMs = ct.verwerkingGestartOp ?
        nu - new Date(ct.verwerkingGestartOp).getTime() :
        0;
      return `Bezig (${this._formatTijd(wachtMs)}+${this._formatTijd(verwerkMs)})`;
    }

    if (ct.status === 'klaar') {
      const wachtMs = ct.verwerkingGestartOp && ct.aangemaaktOp ?
        new Date(ct.verwerkingGestartOp).getTime() -
            new Date(ct.aangemaaktOp).getTime() :
        0;
      const verwerkMs = ct.verwerkingVoltooidOp && ct.verwerkingGestartOp ?
        new Date(ct.verwerkingVoltooidOp).getTime() -
            new Date(ct.verwerkingGestartOp).getTime() :
        0;
      return `Klaar (${this._formatTijd(wachtMs)}+${this._formatTijd(verwerkMs)})`;
    }

    return ct.status;
  }

  _formatTijd(ms) {
    const totaalSeconden = Math.floor(ms / 1000);
    const minuten = Math.floor(totaalSeconden / 60);
    const seconden = totaalSeconden % 60;
    return `${minuten}:${String(seconden).padStart(2, '0')}`;
  }

  // --- API methods ---

  _startClustering(categorie) {
    if (!this._projectNaam) return;
    fetch(`/api/v1/projects/${encodeURIComponent(this._projectNaam)}/clustering-taken/${encodeURIComponent(categorie)}`, {
      method: 'POST',
    })
        .then((response) => {
          if (!response.ok) throw new Error('Starten clustering mislukt');
          return response.json();
        })
        .then((taak) => {
          this.werkBijMetClusteringUpdate(taak);
        })
        .catch(() => {
          this.dispatchEvent(new CustomEvent('toon-foutmelding', {
            bubbles: true, composed: true,
            detail: {bericht: 'Starten clustering mislukt'},
          }));
        });
  }

  _annuleerClustering(categorie) {
    if (!this._projectNaam) return;
    fetch(`/api/v1/projects/${encodeURIComponent(this._projectNaam)}/clustering-taken/${encodeURIComponent(categorie)}`, {
      method: 'DELETE',
    })
        .then((response) => {
          if (!response.ok) throw new Error('Annuleren clustering mislukt');
          // Herlaad clustering-taken
          this.laadClusteringTaken(this._projectNaam);
        })
        .catch(() => {
          this.dispatchEvent(new CustomEvent('toon-foutmelding', {
            bubbles: true, composed: true,
            detail: {bericht: 'Annuleren clustering mislukt'},
          }));
        });
  }

  _verwijderClustering(categorie, bevestigd = false) {
    if (!this._projectNaam) return;
    const url = bevestigd ?
      `/api/v1/projects/${encodeURIComponent(this._projectNaam)}/clustering-taken/${encodeURIComponent(categorie)}?bevestigd=true` :
      `/api/v1/projects/${encodeURIComponent(this._projectNaam)}/clustering-taken/${encodeURIComponent(categorie)}`;

    fetch(url, {method: 'DELETE'})
        .then((response) => {
          if (response.status === 409) {
            return response.json().then((data) => {
              this._toonVerwijderBevestigingModal(
                  categorie,
                  data.aantalAntwoorden || 0,
                  () => this._verwijderClustering(categorie, true),
              );
              return null;
            });
          }
          if (!response.ok) throw new Error('Verwijderen clustering mislukt');
          // Herlaad alles
          this.laadClusteringTaken(this._projectNaam);
          this.laadKernbezwaren(this._projectNaam);
          return null;
        })
        .catch(() => {
          this.dispatchEvent(new CustomEvent('toon-foutmelding', {
            bubbles: true, composed: true,
            detail: {bericht: 'Verwijderen clustering mislukt'},
          }));
        });
  }

  _toonVerwijderBevestiging(categorie) {
    this._verwijderClustering(categorie);
  }

  _toonVerwijderBevestigingModal(categorie, aantalAntwoorden, onBevestig) {
    const modal = this.shadowRoot.querySelector('#verwijder-bevestiging');
    const inhoud = this.shadowRoot.querySelector('#verwijder-bevestiging-inhoud');
    const bevestigKnop = this.shadowRoot.querySelector('#verwijder-bevestiging-bevestig');
    if (!modal || !inhoud) return;

    inhoud.innerHTML = '';
    const p = document.createElement('p');
    const context = categorie ? `voor "${categorie}"` : 'voor alle categorie\u00EBn';
    p.textContent = `De clustering ${context} bevat ${aantalAntwoorden} antwoord(en) ` +
        'die verloren gaan als u doorgaat.';
    inhoud.appendChild(p);

    const afhandelen = (bevestigd) => {
      bevestigKnop.removeEventListener('click', bevestigHandler);
      modal.off('close', sluitHandler);
      modal.close();
      if (bevestigd) {
        onBevestig();
      }
    };
    const bevestigHandler = () => afhandelen(true);
    const sluitHandler = () => afhandelen(false);

    bevestigKnop.addEventListener('click', bevestigHandler);
    modal.on('close', sluitHandler);
    modal.open();
  }

  _clusterAlles() {
    if (!this._projectNaam) return;
    fetch(`/api/v1/projects/${encodeURIComponent(this._projectNaam)}/clustering-taken`, {
      method: 'POST',
    })
        .then((response) => {
          if (!response.ok) throw new Error('Cluster alles mislukt');
          return response.json();
        })
        .then((taken) => {
          if (Array.isArray(taken)) {
            taken.forEach((taak) => this.werkBijMetClusteringUpdate(taak));
          }
        })
        .catch(() => {
          this.dispatchEvent(new CustomEvent('toon-foutmelding', {
            bubbles: true, composed: true,
            detail: {bericht: 'Starten clustering voor alle categorie\u00EBn mislukt'},
          }));
        });
  }

  _verwijderAlles(bevestigd = false) {
    if (!this._projectNaam) return;
    const url = bevestigd ?
      `/api/v1/projects/${encodeURIComponent(this._projectNaam)}/clustering-taken?bevestigd=true` :
      `/api/v1/projects/${encodeURIComponent(this._projectNaam)}/clustering-taken`;

    fetch(url, {method: 'DELETE'})
        .then((response) => {
          if (response.status === 409) {
            return response.json().then((data) => {
              this._toonVerwijderBevestigingModal(
                  null,
                  data.aantalAntwoorden || 0,
                  () => this._verwijderAlles(true),
              );
              return null;
            });
          }
          if (!response.ok) throw new Error('Alles verwijderen mislukt');
          this.laadClusteringTaken(this._projectNaam);
          this.laadKernbezwaren(this._projectNaam);
          return null;
        })
        .catch(() => {
          this.dispatchEvent(new CustomEvent('toon-foutmelding', {
            bubbles: true, composed: true,
            detail: {bericht: 'Verwijderen van alle clusteringen mislukt'},
          }));
        });
  }

  // --- Legacy clustering (voor oude flow zonder categorien) ---

  _groepeer() {
    if (this._bezig || !this._projectNaam) return;
    this._bezig = true;

    const knop = this.shadowRoot.querySelector('#groepeer-knop');
    if (knop) {
      knop.setAttribute('disabled', '');
      knop.textContent = 'Bezig met clustering...';
    }

    fetch(`/api/v1/projects/${encodeURIComponent(this._projectNaam)}/kernbezwaren/groepeer`, {
      method: 'POST',
    })
        .then((response) => {
          if (!response.ok) throw new Error('Clustering mislukt');
          return response.json();
        })
        .then((data) => {
          this._themas = data.themas;
          this._renderInhoud();
        })
        .catch(() => {
          if (knop) {
            knop.removeAttribute('disabled');
            knop.textContent = 'Cluster bezwaren';
          }
        })
        .finally(() => {
          this._bezig = false;
        });
  }

  // --- Legacy rendering (zonder categorien) ---

  _renderThemas(inhoud) {
    inhoud.innerHTML = '';

    this._themas.forEach((thema) => {
      const accordion = document.createElement('vl-accordion');
      const aantalKern = thema.kernbezwaren.length;
      const label = aantalKern === 1 ? '1 kernbezwaar' : `${aantalKern} kernbezwaren`;
      accordion.setAttribute('toggle-text', `${thema.naam} (${label})`);
      accordion.setAttribute('default-open', '');

      const wrapper = document.createElement('div');
      thema.kernbezwaren.forEach((kern) => {
        wrapper.appendChild(this._maakKernbezwaarItem(kern));
      });

      accordion.appendChild(wrapper);
      inhoud.appendChild(accordion);
    });

    this._dispatchVoortgang();
  }

  // --- Kernbezwaar item interactions ---

  _toggleEditor(item, kern, penKnop) {
    const bestaandeEditor = item.querySelector('.antwoord-editor-wrapper');
    if (bestaandeEditor) {
      // Sluit editor
      const textarea = bestaandeEditor.querySelector('vl-textarea-rich');
      const huidigeWaarde = textarea ? textarea.value : '';
      const origineleWaarde = kern.antwoord || '';
      if (huidigeWaarde !== origineleWaarde &&
          !confirm('Je hebt onopgeslagen wijzigingen. Wil je afsluiten?')) {
        return;
      }
      bestaandeEditor.remove();
      penKnop.setAttribute('icon', 'pencil');
      return;
    }

    // Open editor
    penKnop.setAttribute('icon', 'close');

    const wrapper = document.createElement('div');
    wrapper.className = 'antwoord-editor-wrapper';

    const textarea = document.createElement('vl-textarea-rich');
    textarea.setAttribute('block', '');
    textarea.setAttribute('rows', '8');
    textarea.setAttribute('label', 'Antwoord op kernbezwaar');
    if (kern.antwoord) {
      textarea.setAttribute('value', kern.antwoord);
    }

    const opslaanRij = document.createElement('div');
    opslaanRij.className = 'antwoord-opslaan-rij';

    const opslaanKnop = document.createElement('vl-button');
    opslaanKnop.textContent = 'Opslaan';
    opslaanKnop.addEventListener('click', () =>
      this._slaAntwoordOp(kern, textarea, opslaanRij));

    opslaanRij.appendChild(opslaanKnop);
    wrapper.appendChild(textarea);
    wrapper.appendChild(opslaanRij);
    item.appendChild(wrapper);
  }

  _slaAntwoordOp(kern, textarea, opslaanRij, bevestigd = false) {
    const inhoud = textarea.value;
    const isLeeg = !inhoud || !inhoud.trim();

    const opslaanKnop = opslaanRij.querySelector('vl-button');
    if (opslaanKnop) opslaanKnop.setAttribute('disabled', '');

    const basisUrl = `/api/v1/projects/${encodeURIComponent(this._projectNaam)}/kernbezwaren/${kern.id}/antwoord`;
    const url = bevestigd ? `${basisUrl}?bevestigd=true` : basisUrl;

    fetch(url, {
      method: 'PUT',
      headers: {'Content-Type': 'application/json'},
      body: JSON.stringify({inhoud: isLeeg ? '' : inhoud}),
    })
        .then((response) => {
          if (response.status === 409) {
            return response.json().then((data) => {
              this._toonConsolidatieWaarschuwing(
                  data.getroffenDocumenten || [],
                  () => this._slaAntwoordOp(kern, textarea, opslaanRij, true),
                  () => {
                    if (opslaanKnop) opslaanKnop.removeAttribute('disabled');
                  });
              return null;
            });
          }
          if (!response.ok) throw new Error('Opslaan mislukt');
          kern.antwoord = isLeeg ? null : inhoud;

          // Update samenvatting: vinkje + groen als antwoord, anders reset
          const editorWrapper = opslaanRij.parentElement;
          const kernItem = editorWrapper.parentElement;
          const samenvattingEl = kernItem &&
              kernItem.querySelector('.kernbezwaar-samenvatting');
          if (samenvattingEl) {
            if (kern.antwoord) {
              samenvattingEl.textContent = `\u2714 ${kern.samenvatting}`;
              samenvattingEl.style.color = '#0e7c3a';
            } else {
              samenvattingEl.textContent = kern.samenvatting;
              samenvattingEl.style.color = '';
            }
          }
          this._dispatchVoortgang();

          // Toon bevestiging
          let melding = opslaanRij.querySelector('.antwoord-opslaan-melding');
          if (!melding) {
            melding = document.createElement('span');
            melding.className = 'antwoord-opslaan-melding';
            opslaanRij.appendChild(melding);
          }
          melding.textContent = 'Opgeslagen';
          setTimeout(() => melding.textContent = '', 3000);
          return null;
        })
        .catch((err) => {
          if (err) alert('Het opslaan van het antwoord is mislukt. Probeer opnieuw.');
        })
        .finally(() => {
          if (opslaanKnop && !bevestigd) opslaanKnop.removeAttribute('disabled');
        });
  }

  _toonConsolidatieWaarschuwing(documenten, onBevestig, onAnnuleer) {
    const modal = this.shadowRoot.querySelector('#consolidatie-waarschuwing');
    const inhoud = this.shadowRoot.querySelector('#consolidatie-waarschuwing-inhoud');
    const bevestigKnop = this.shadowRoot.querySelector('#consolidatie-waarschuwing-bevestig');
    if (!modal || !inhoud) return;

    const MAX_ZICHTBAAR = 10;
    const zichtbaar = documenten.slice(0, MAX_ZICHTBAAR);
    const verborgen = documenten.length - MAX_ZICHTBAAR;

    inhoud.innerHTML = '';
    const p = document.createElement('p');
    p.textContent = 'De volgende documenten hebben al een gegenereerde antwoordbrief. ' +
        'Deze antwoordbrieven worden verwijderd als u doorgaat.';
    inhoud.appendChild(p);

    const ul = document.createElement('ul');
    zichtbaar.forEach((doc) => {
      const li = document.createElement('li');
      li.textContent = doc;
      ul.appendChild(li);
    });
    if (verborgen > 0) {
      const li = document.createElement('li');
      li.textContent = `\u2026 en nog ${verborgen} andere`;
      li.style.fontStyle = 'italic';
      ul.appendChild(li);
    }
    inhoud.appendChild(ul);

    const afhandelen = (bevestigd) => {
      bevestigKnop.removeEventListener('click', bevestigHandler);
      modal.off('close', sluitHandler);
      modal.close();
      if (bevestigd) {
        onBevestig();
      } else {
        onAnnuleer();
      }
    };
    const bevestigHandler = () => afhandelen(true);
    const sluitHandler = () => afhandelen(false);

    bevestigKnop.addEventListener('click', bevestigHandler);
    modal.on('close', sluitHandler);
    modal.open();
  }

  _dispatchVoortgang() {
    if (!this._themas) return;
    let totaal = 0;
    let aantalMetAntwoord = 0;
    this._themas.forEach((thema) => {
      thema.kernbezwaren.forEach((kern) => {
        totaal++;
        if (kern.antwoord) aantalMetAntwoord++;
      });
    });
    this.dispatchEvent(new CustomEvent('antwoord-voortgang', {
      bubbles: true,
      detail: {aantalMetAntwoord, totaal},
    }));
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

      const bestand = document.createElement('a');
      bestand.className = 'passage-bestandsnaam';
      bestand.textContent = ref.bestandsnaam;
      bestand.href = `/api/v1/projects/${encodeURIComponent(this._projectNaam)}/bezwaren/${encodeURIComponent(ref.bestandsnaam)}/download`;
      bestand.download = ref.bestandsnaam;

      const passage = document.createElement('div');
      passage.className = 'passage-tekst';
      passage.textContent = `"${ref.passage}"`;

      item.appendChild(bestand);
      item.appendChild(passage);
      inhoud.appendChild(item);
    });

    sideSheet.open();
    this.classList.add('side-sheet-open');
  }
}

defineWebComponent(BezwaarschriftenKernbezwaren, 'bezwaarschriften-kernbezwaren');
