import {BaseHTMLElement, defineWebComponent, registerWebComponents} from '@domg-wc/common';
import {VlButtonComponent} from '@domg-wc/components/atom/button/vl-button.component.js';
import {VlAccordionComponent} from '@domg-wc/components/block/accordion/vl-accordion.component.js';
import {VlAlert} from '@domg-wc/components/block/alert';
import {VlModalComponent} from '@domg-wc/components/block/modal/vl-modal.component.js';
import {VlSideSheet} from '@domg-wc/components/block/side-sheet/vl-side-sheet.component.js';
import {VlPillComponent} from '@domg-wc/components/block/pill/vl-pill.component.js';
import {VlLinkComponent} from '@domg-wc/components/atom/link/vl-link.component.js';
import {VlCheckboxComponent} from '@domg-wc/components/form/checkbox/vl-checkbox.component.js';
import {VlInputFieldComponent} from '@domg-wc/components/form/input-field/vl-input-field.component.js';
import {vlGlobalStyles, vlGridStyles, vlGroupStyles} from '@domg-wc/styles';
import '@domg-wc/components/form/textarea-rich';

registerWebComponents([VlButtonComponent, VlAccordionComponent, VlAlert, VlModalComponent, VlSideSheet, VlPillComponent, VlLinkComponent, VlCheckboxComponent, VlInputFieldComponent]);

const PAGE_SIZE = 15;

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
        .clustering-params-accordion {
          margin-bottom: var(--vl-spacing--xsmall);
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
        .passage-groep {
          margin-bottom: 1.5rem;
          padding-bottom: 1rem;
          border-bottom: 1px solid #e8ebee;
        }
        .passage-groep:last-child {
          border-bottom: none;
        }
        .passage-tekst {
          font-style: italic;
          line-height: 1.5;
        }
        .passage-documenten {
          margin-top: 0.5rem;
          font-size: 0.85rem;
          line-height: 1.8;
        }
        .passage-document-link {
          color: #687483;
          text-decoration: none;
          cursor: pointer;
          margin-right: 0.5rem;
        }
        .passage-document-link:hover {
          text-decoration: underline;
          color: #0055cc;
        }
        .passage-toon-alle {
          color: #0055cc;
          cursor: pointer;
          font-size: 0.85rem;
          text-decoration: none;
        }
        .passage-toon-alle:hover {
          text-decoration: underline;
        }
        .lege-staat {
          padding: 2rem;
          text-align: center;
          color: #687483;
        }
        .status-pill {
          font-variant-numeric: tabular-nums;
          min-width: 8rem;
          display: inline-flex;
          white-space: nowrap;
          align-self: center;
          justify-content: center;
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
        .toewijzing-badge {
          display: inline-block;
          font-size: 0.75rem;
          padding: 0.15rem 0.5rem;
          border-radius: 3px;
          margin-left: 0.5rem;
        }
        .toewijzing-badge--hdbscan {
          background: #e8f5e9;
          color: #2e7d32;
        }
        .toewijzing-badge--centroid {
          background: #fff3e0;
          color: #e65100;
        }
        .toewijzing-badge--manueel {
          background: #e3f2fd;
          color: #1565c0;
        }
        .paginering {
          display: flex;
          justify-content: center;
          align-items: center;
          gap: 1rem;
          padding: 1rem 0;
          border-top: 1px solid #e8ebee;
        }
        .toewijzen-dropdown {
          margin-top: 0.5rem;
          padding: 0.75rem;
          background: #f5f6f7;
          border: 1px solid #e8ebee;
          border-radius: 4px;
        }
        .suggestie-item {
          display: flex;
          justify-content: space-between;
          align-items: center;
          padding: 0.5rem;
          cursor: pointer;
          border-radius: 3px;
        }
        .suggestie-item:hover {
          background: #e8ebee;
        }
        .suggestie-item.suggestie-geselecteerd {
          background: #d4e5f7;
          outline: 2px solid #0055cc;
        }
        .suggestie-score {
          font-weight: bold;
          color: #687483;
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
    this._kernbezwaren = [];
    this._clusteringTaak = null;
    this._huidigKernbezwaar = null;
    this._huidigGroepen = null;
    this._huidigePagina = 1;
    this._deduplicatieVoorClustering = true;
    this._timerInterval = null;
  }

  _telDocumenten(kern) {
    return kern.individueleBezwaren.reduce(
        (sum, ref) => sum + (ref.passageGroep ? ref.passageGroep.documenten.length : 1), 0);
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
            this._kernbezwaren = [];
            this._renderInhoud();
            return null;
          }
          if (!response.ok) throw new Error('Ophalen kernbezwaren mislukt');
          return response.json();
        })
        .then((data) => {
          if (data) {
            this._kernbezwaren = data.kernbezwaren || [];
            this._renderInhoud();
          }
        })
        .catch(() => {
          this._kernbezwaren = [];
          this._renderInhoud();
        });
  }

  laadClusteringTaken(projectNaam) {
    this._projectNaam = projectNaam;
    fetch(`/api/v1/projects/${encodeURIComponent(projectNaam)}/clustering-taken`)
        .then((response) => {
          if (response.ok && response.status !== 204) {
            return response.json();
          }
          this._clusteringTaak = null;
          this._renderInhoud();
          return null;
        })
        .then((data) => {
          if (data) {
            this._clusteringTaak = data;
            this._renderInhoud();
            this._beheerTimer();
          }
        })
        .catch(() => {
          this._clusteringTaak = null;
          this._renderInhoud();
        });
  }

  werkBijMetClusteringUpdate(taakDto) {
    this._clusteringTaak = taakDto;
    if (taakDto.status === 'klaar') {
      this.laadKernbezwaren(this._projectNaam);
    } else {
      this._renderInhoud();
    }
    this._beheerTimer();
  }

  setAantalBezwaren(aantal) {
    this._aantalBezwaren = aantal;
    if (this._kernbezwaren.length === 0 && !this._clusteringTaak) this._renderInhoud();
  }

  setExtractieKlaar(klaar) {
    this._extractieKlaar = klaar;
    if (this._kernbezwaren.length === 0 && !this._clusteringTaak) this._renderInhoud();
  }

  reset() {
    this._kernbezwaren = [];
    this._clusteringTaak = null;
    this._stopTimer();
    this._renderInhoud();
  }

  // --- Rendering ---

  _renderInhoud() {
    const inhoud = this.shadowRoot.querySelector('#inhoud');
    if (!inhoud) return;

    // Clustering actief: toon status
    if (this._clusteringTaak &&
        (this._clusteringTaak.status === 'wachtend' || this._clusteringTaak.status === 'bezig')) {
      this._renderClusteringStatus(inhoud);
      return;
    }

    // Kernbezwaren beschikbaar: toon flat list
    if (this._kernbezwaren.length > 0) {
      this._renderKernbezwaren(inhoud);
      return;
    }

    // Clustering klaar maar geen kernbezwaren (onverwacht): toon clustering-info
    if (this._clusteringTaak && this._clusteringTaak.status === 'klaar') {
      this._renderKernbezwaren(inhoud);
      return;
    }

    // Fout bij clustering
    if (this._clusteringTaak && this._clusteringTaak.status === 'fout') {
      this._renderClusteringFout(inhoud);
      return;
    }

    // Geen bezwaren of extractie niet klaar
    if (!this._extractieKlaar || this._aantalBezwaren === 0) {
      inhoud.innerHTML = `
        <div class="lege-staat">
          <p>Nog geen bezwaren ge\u00EBxtraheerd. Verwerk eerst documenten op de Documenten-tab.</p>
        </div>`;
      return;
    }

    // Bezwaren beschikbaar maar nog niet geclusterd
    inhoud.innerHTML = '';
    this._renderClusteringParams(inhoud);

    const legeStaat = document.createElement('div');
    legeStaat.className = 'lege-staat';
    const p = document.createElement('p');
    p.textContent = `${this._aantalBezwaren} individuele bezwaren gevonden. ` +
        'Voer clustering uit om kernbezwaren te identificeren.';
    legeStaat.appendChild(p);

    const knop = document.createElement('vl-button');
    knop.id = 'groepeer-knop';
    knop.textContent = 'Cluster bezwaren';
    knop.addEventListener('click', () => this._startClustering());
    legeStaat.appendChild(knop);

    inhoud.appendChild(legeStaat);
  }

  _renderClusteringStatus(inhoud) {
    inhoud.innerHTML = '';
    this._renderClusteringParams(inhoud);

    const statusDiv = document.createElement('div');
    statusDiv.className = 'lege-staat';

    const actieRij = document.createElement('div');
    actieRij.style.cssText = 'display:flex;align-items:center;gap:1rem;justify-content:center;';

    const loadingKnop = document.createElement('vl-button');
    loadingKnop.setAttribute('loading', '');
    loadingKnop.setAttribute('disabled', '');
    loadingKnop.className = 'clustering-loading-knop';
    const loadingTekst = document.createElement('span');
    loadingTekst.className = 'timer-tekst';
    loadingTekst.textContent = this._formatClusteringStatus(this._clusteringTaak);
    loadingKnop.appendChild(loadingTekst);
    actieRij.appendChild(loadingKnop);

    const annuleerLink = document.createElement('vl-link');
    annuleerLink.setAttribute('button-as-link', '');
    annuleerLink.setAttribute('icon', 'cross');
    annuleerLink.setAttribute('icon-placement', 'before');
    annuleerLink.textContent = 'Annuleer';
    annuleerLink.style.cursor = 'pointer';
    annuleerLink.addEventListener('click', () => this._annuleerClustering());
    actieRij.appendChild(annuleerLink);

    statusDiv.appendChild(actieRij);
    inhoud.appendChild(statusDiv);
  }

  _renderClusteringFout(inhoud) {
    inhoud.innerHTML = '';
    this._renderClusteringParams(inhoud);

    const foutAlert = document.createElement('vl-alert');
    foutAlert.setAttribute('type', 'error');
    foutAlert.setAttribute('size', 'small');
    foutAlert.setAttribute('message',
        `Clustering mislukt: ${this._clusteringTaak.foutmelding || 'Onbekende fout'}`);
    inhoud.appendChild(foutAlert);

    const acties = document.createElement('div');
    acties.className = 'clustering-header vl-group vl-group--justify-end';
    acties.style.marginTop = 'var(--vl-spacing--xsmall)';

    const opnieuwKnop = document.createElement('vl-button');
    opnieuwKnop.textContent = 'Opnieuw clusteren';
    opnieuwKnop.addEventListener('click', () => this._retryClustering());
    acties.appendChild(opnieuwKnop);

    inhoud.appendChild(acties);
  }

  _renderKernbezwaren(inhoud) {
    inhoud.innerHTML = '';
    this._renderClusteringParams(inhoud);

    // Actiebalk
    const header = document.createElement('div');
    header.className = 'clustering-header vl-group vl-group--justify-end';

    const verwijderKnop = document.createElement('vl-button');
    verwijderKnop.setAttribute('error', '');
    verwijderKnop.textContent = 'Clustering verwijderen';
    verwijderKnop.addEventListener('click', () => this._verwijderClustering());
    header.appendChild(verwijderKnop);

    const opnieuwKnop = document.createElement('vl-button');
    opnieuwKnop.textContent = 'Opnieuw clusteren';
    opnieuwKnop.addEventListener('click', () => this._retryClustering());
    header.appendChild(opnieuwKnop);

    inhoud.appendChild(header);

    // Samenvatting
    const echteKernbezwaren = this._kernbezwaren.filter(
        (k) => k.samenvatting !== 'Niet-geclusterde bezwaren');

    let totaalBezwaren = 0;
    let aantalHdbscan = 0;
    let aantalCentroid = 0;
    let aantalManueel = 0;
    let nietGeclusterd = 0;
    this._kernbezwaren.forEach((k) => {
      const docCount = this._telDocumenten(k);
      if (k.samenvatting === 'Niet-geclusterde bezwaren') {
        nietGeclusterd += docCount;
      } else {
        k.individueleBezwaren.forEach((b) => {
          const bDocs = b.passageGroep ? b.passageGroep.documenten.length : 1;
          if (b.toewijzingsmethode === 'HDBSCAN') aantalHdbscan += bDocs;
          else if (b.toewijzingsmethode === 'CENTROID_FALLBACK') aantalCentroid += bDocs;
          else if (b.toewijzingsmethode === 'MANUEEL') aantalManueel += bDocs;
        });
      }
      totaalBezwaren += docCount;
    });

    const geclusterdeBezwaren = totaalBezwaren - nietGeclusterd;
    if (echteKernbezwaren.length > 0) {
      const reductie = Math.round(geclusterdeBezwaren / echteKernbezwaren.length);
      const reductieTekst = reductie > 1 ? ` (${reductie}x reductie)` : '';
      const totaalAlert = document.createElement('vl-alert');
      totaalAlert.setAttribute('type', 'success');
      totaalAlert.setAttribute('naked', '');
      totaalAlert.setAttribute('size', 'small');
      const delen = [];
      if (aantalHdbscan > 0) delen.push(`${aantalHdbscan} clustering`);
      if (aantalCentroid > 0) delen.push(`${aantalCentroid} centroid`);
      if (aantalManueel > 0) delen.push(`${aantalManueel} handmatig`);
      const verdelingTekst = delen.length > 0 ? ` (${delen.join(', ')})` : '';
      totaalAlert.setAttribute('message',
          `${geclusterdeBezwaren} individuele bezwaren${verdelingTekst} herleid naar ` +
          `${echteKernbezwaren.length} kernbezwaren${reductieTekst}.`);
      inhoud.appendChild(totaalAlert);
    }

    if (nietGeclusterd > 0 && totaalBezwaren > 0) {
      const pctNoise = Math.round((nietGeclusterd / totaalBezwaren) * 100);
      const waarschuwing = document.createElement('vl-alert');
      waarschuwing.setAttribute('type', 'warning');
      waarschuwing.setAttribute('naked', '');
      waarschuwing.setAttribute('size', 'small');
      waarschuwing.style.marginTop = 'var(--vl-spacing--xsmall)';
      waarschuwing.setAttribute('message',
          `${nietGeclusterd} individuele bezwaren (${pctNoise}%) niet toegewezen.`);
      inhoud.appendChild(waarschuwing);
    }

    // Sorteer kernbezwaren: meeste bezwaren eerst (op basis van totaal documenten)
    const gesorteerd = [...this._kernbezwaren].sort(
        (a, b) => this._telDocumenten(b) - this._telDocumenten(a),
    );

    // Render kernbezwaar items
    gesorteerd.forEach((kern) => {
      inhoud.appendChild(this._maakKernbezwaarItem(kern));
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
    knop.textContent = `(${this._telDocumenten(kern)})`;
    knop.addEventListener('click', () => this._toonPassages(kern));

    actie.appendChild(knop);
    actie.appendChild(penKnop);
    item.appendChild(samenvatting);
    item.appendChild(actie);
    return item;
  }

  // --- Timer management ---

  _beheerTimer() {
    const heeftActief = this._clusteringTaak &&
        (this._clusteringTaak.status === 'wachtend' || this._clusteringTaak.status === 'bezig');
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
    if (!inhoud || !this._clusteringTaak) return;

    if (this._clusteringTaak.status !== 'wachtend' && this._clusteringTaak.status !== 'bezig') {
      return;
    }
    const timerTekst = inhoud.querySelector('.timer-tekst');
    if (timerTekst) {
      timerTekst.textContent = this._formatClusteringStatus(this._clusteringTaak);
    }
  }

  _formatClusteringStatus(ct, nu) {
    nu = nu || Date.now();

    if (ct.status === 'wachtend') {
      return 'Wachtend...';
    }

    if (ct.status === 'bezig') {
      const verwerkMs = ct.verwerkingGestartOp ?
        nu - new Date(ct.verwerkingGestartOp).getTime() :
        0;
      return `Bezig (${this._formatTijd(verwerkMs)})`;
    }

    if (ct.status === 'klaar') {
      const verwerkMs = ct.verwerkingVoltooidOp && ct.verwerkingGestartOp ?
        new Date(ct.verwerkingVoltooidOp).getTime() -
            new Date(ct.verwerkingGestartOp).getTime() :
        0;
      return `Klaar (${this._formatTijd(verwerkMs)})`;
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

  _startClustering() {
    if (!this._projectNaam) return;
    const dedup = this._deduplicatieVoorClustering ? 'true' : 'false';
    fetch(`/api/v1/projects/${encodeURIComponent(this._projectNaam)}/clustering-taken?deduplicatieVoorClustering=${dedup}`, {
      method: 'POST',
    })
        .then((response) => {
          if (!response.ok) throw new Error('Starten clustering mislukt');
          return response.json();
        })
        .then((taakDto) => {
          this._clusteringTaak = taakDto;
          this._renderInhoud();
          this._beheerTimer();
        })
        .catch(() => {
          this.dispatchEvent(new CustomEvent('toon-foutmelding', {
            bubbles: true, composed: true,
            detail: {bericht: 'Starten clustering mislukt'},
          }));
        });
  }

  _annuleerClustering() {
    if (!this._projectNaam) return;
    fetch(`/api/v1/projects/${encodeURIComponent(this._projectNaam)}/clustering-taken`, {
      method: 'DELETE',
    })
        .then((response) => {
          if (!response.ok) throw new Error('Annuleren clustering mislukt');
          this._clusteringTaak = null;
          this._kernbezwaren = [];
          this._renderInhoud();
          this._beheerTimer();
        })
        .catch(() => {
          this.dispatchEvent(new CustomEvent('toon-foutmelding', {
            bubbles: true, composed: true,
            detail: {bericht: 'Annuleren clustering mislukt'},
          }));
        });
  }

  _verwijderClustering(bevestigd = false) {
    if (!this._projectNaam) return;
    const url = bevestigd ?
      `/api/v1/projects/${encodeURIComponent(this._projectNaam)}/clustering-taken?bevestigd=true` :
      `/api/v1/projects/${encodeURIComponent(this._projectNaam)}/clustering-taken`;

    fetch(url, {method: 'DELETE'})
        .then((response) => {
          if (response.status === 409) {
            return response.json().then((data) => {
              this._toonVerwijderBevestigingModal(
                  data.aantalAntwoorden || 0,
                  () => this._verwijderClustering(true),
              );
              return null;
            });
          }
          if (!response.ok) throw new Error('Verwijderen clustering mislukt');
          this._kernbezwaren = [];
          this._clusteringTaak = null;
          this._huidigKernbezwaar = null;
          this._huidigGroepen = null;
          const sideSheet = this.shadowRoot.querySelector('#side-sheet');
          if (sideSheet) {
            sideSheet.close();
            this.classList.remove('side-sheet-open');
          }
          this._renderInhoud();
          return null;
        })
        .catch(() => {
          this.dispatchEvent(new CustomEvent('toon-foutmelding', {
            bubbles: true, composed: true,
            detail: {bericht: 'Verwijderen clustering mislukt'},
          }));
        });
  }

  _retryClustering() {
    if (!this._projectNaam) return;
    const url = `/api/v1/projects/${encodeURIComponent(this._projectNaam)}/clustering-taken`;

    fetch(url, {method: 'DELETE'})
        .then((response) => {
          if (response.status === 409) {
            return response.json().then((data) => {
              this._toonVerwijderBevestigingModal(
                  data.aantalAntwoorden || 0,
                  () => {
                    fetch(`${url}?bevestigd=true`, {method: 'DELETE'})
                        .then((resp) => {
                          if (!resp.ok) throw new Error('Verwijderen mislukt');
                          this._startClustering();
                        })
                        .catch(() => {
                          this.dispatchEvent(new CustomEvent('toon-foutmelding', {
                            bubbles: true, composed: true,
                            detail: {bericht: 'Opnieuw clusteren mislukt'},
                          }));
                        });
                  },
              );
              return null;
            });
          }
          if (!response.ok) throw new Error('Opnieuw clusteren mislukt');
          this._startClustering();
          return null;
        })
        .catch(() => {
          this.dispatchEvent(new CustomEvent('toon-foutmelding', {
            bubbles: true, composed: true,
            detail: {bericht: 'Opnieuw clusteren mislukt'},
          }));
        });
  }

  _toonVerwijderBevestigingModal(aantalAntwoorden, onBevestig) {
    const modal = this.shadowRoot.querySelector('#verwijder-bevestiging');
    const inhoud = this.shadowRoot.querySelector('#verwijder-bevestiging-inhoud');
    const bevestigKnop = this.shadowRoot.querySelector('#verwijder-bevestiging-bevestig');
    if (!modal || !inhoud) return;

    inhoud.innerHTML = '';
    const p = document.createElement('p');
    p.textContent = `De clustering bevat ${aantalAntwoorden} antwoord(en) ` +
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

  _renderClusteringParams(inhoud) {
    const accordion = document.createElement('vl-accordion');
    accordion.className = 'clustering-params-accordion';
    accordion.setAttribute('toggle-text', 'Clustering parameters');
    accordion.setAttribute('open-toggle-text', 'Clustering parameters');

    const wrapper = document.createElement('div');
    accordion.appendChild(wrapper);

    fetch('/api/v1/clustering-config')
        .then((r) => r.json())
        .then((config) => {
          // --- Opties sectie ---
          const optiesTitel = document.createElement('h6');
          optiesTitel.textContent = 'Opties';
          wrapper.appendChild(optiesTitel);

          const optiesGrid = document.createElement('div');
          optiesGrid.className = 'vl-grid';
          wrapper.appendChild(optiesGrid);

          // UMAP toggle
          const umapCol = document.createElement('div');
          umapCol.className = 'vl-col--4-12';
          const umapToggle = document.createElement('vl-checkbox');
          umapToggle.setAttribute('switch', '');
          umapToggle.id = 'umap-toggle';
          umapToggle.checked = config.umapEnabled;
          umapToggle.textContent = 'UMAP dimensiereductie';
          umapCol.appendChild(umapToggle);
          optiesGrid.appendChild(umapCol);

          // Cluster op passages toggle
          const passageCol = document.createElement('div');
          passageCol.className = 'vl-col--4-12';
          const passageToggle = document.createElement('vl-checkbox');
          passageToggle.setAttribute('switch', '');
          passageToggle.id = 'passage-toggle';
          passageToggle.checked = config.clusterOpPassages;
          passageToggle.textContent = 'Cluster op passages';
          passageCol.appendChild(passageToggle);
          optiesGrid.appendChild(passageCol);

          // Passage-deduplicatie toggle
          const deduplicatieCol = document.createElement('div');
          deduplicatieCol.className = 'vl-col--4-12';
          const deduplicatieToggle = document.createElement('vl-checkbox');
          deduplicatieToggle.setAttribute('switch', '');
          deduplicatieToggle.id = 'deduplicatie-toggle';
          deduplicatieToggle.checked = this._deduplicatieVoorClustering;
          deduplicatieToggle.textContent = 'Passage-deduplicatie';
          deduplicatieCol.appendChild(deduplicatieToggle);
          optiesGrid.appendChild(deduplicatieCol);

          // Event listeners voor toggles (vl-input vuurt alleen bij user interactie)
          umapToggle.addEventListener('vl-input', (e) => {
            umapSectie.style.display = e.detail.checked ? '' : 'none';
            this._updateClusteringConfig('umapEnabled', e.detail.checked);
          });
          passageToggle.addEventListener('vl-input', (e) => {
            this._updateClusteringConfig('clusterOpPassages', e.detail.checked);
          });
          deduplicatieToggle.addEventListener('vl-input', (e) => {
            this._deduplicatieVoorClustering = e.detail.checked;
          });

          // --- UMAP parameters sectie ---
          const umapSectie = document.createElement('div');
          umapSectie.style.display = config.umapEnabled ? '' : 'none';

          const umapTitel = document.createElement('h6');
          umapTitel.textContent = 'UMAP parameters';
          umapSectie.appendChild(umapTitel);

          const umapGrid = document.createElement('div');
          umapGrid.className = 'vl-grid';
          umapSectie.appendChild(umapGrid);

          const umapParams = [
            {key: 'umapNComponents', label: 'Dimensies', min: 2, step: 1, decimals: 0},
            {key: 'umapNNeighbors', label: 'Buren', min: 2, step: 1, decimals: 0},
            {key: 'umapMinDist', label: 'Min. afstand', min: 0, step: 0.05, decimals: 2},
          ];

          umapParams.forEach(({key, label, min, step, decimals}) => {
            const col = document.createElement('div');
            col.className = 'vl-col--4-12';
            const formLabel = document.createElement('label');
            formLabel.textContent = label;
            col.appendChild(formLabel);
            const input = document.createElement('vl-input-field');
            input.setAttribute('type', 'number');
            input.setAttribute('min', min);
            input.setAttribute('block', '');
            input.value = decimals === 0 ?
                String(config[key]) : Number(config[key]).toFixed(decimals);
            input.addEventListener('vl-input', (e) => {
              const val = decimals === 0 ?
                  parseInt(e.detail.value, 10) : parseFloat(e.detail.value);
              if (!isNaN(val)) this._updateClusteringConfig(key, val);
            });
            col.appendChild(input);
            umapGrid.appendChild(col);
          });

          wrapper.appendChild(umapSectie);

          // --- HDBSCAN parameters sectie ---
          const hdbscanTitel = document.createElement('h6');
          hdbscanTitel.textContent = 'HDBSCAN parameters';
          wrapper.appendChild(hdbscanTitel);

          const hdbscanGrid = document.createElement('div');
          hdbscanGrid.className = 'vl-grid';
          wrapper.appendChild(hdbscanGrid);

          const hdbscanParams = [
            {key: 'minClusterSize', label: 'Min. clustergrootte', min: 2, step: 1, decimals: 0},
            {key: 'minSamples', label: 'Min. samples', min: 1, step: 1, decimals: 0},
            {key: 'clusterSelectionEpsilon', label: 'Epsilon', min: 0, step: 0.05, decimals: 2},
          ];

          hdbscanParams.forEach(({key, label, min, step, decimals}) => {
            const col = document.createElement('div');
            col.className = 'vl-col--4-12';
            const formLabel = document.createElement('label');
            formLabel.textContent = label;
            col.appendChild(formLabel);
            const input = document.createElement('vl-input-field');
            input.setAttribute('type', 'number');
            input.setAttribute('min', min);
            input.setAttribute('block', '');
            input.value = decimals === 0 ?
                String(config[key]) : Number(config[key]).toFixed(decimals);
            input.addEventListener('vl-input', (e) => {
              const val = decimals === 0 ?
                  parseInt(e.detail.value, 10) : parseFloat(e.detail.value);
              if (!isNaN(val)) this._updateClusteringConfig(key, val);
            });
            col.appendChild(input);
            hdbscanGrid.appendChild(col);
          });
        })
        .catch(() => {/* stil falen als config niet beschikbaar is */});

    inhoud.appendChild(accordion);
  }

  _updateClusteringConfig(key, waarde) {
    fetch('/api/v1/clustering-config')
        .then((r) => r.json())
        .then((huidig) => {
          const nieuw = {...huidig, [key]: waarde};
          return fetch('/api/v1/clustering-config', {
            method: 'PUT',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify(nieuw),
          });
        });
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
    if (!this._kernbezwaren || this._kernbezwaren.length === 0) return;
    let totaal = 0;
    let aantalMetAntwoord = 0;
    this._kernbezwaren.forEach((kern) => {
      totaal++;
      if (kern.antwoord) aantalMetAntwoord++;
    });
    this.dispatchEvent(new CustomEvent('antwoord-voortgang', {
      bubbles: true,
      detail: {aantalMetAntwoord, totaal},
    }));
  }

  // --- Side panel met paginering ---

  _toonPassages(kernbezwaar) {
    this._huidigKernbezwaar = kernbezwaar;
    this._huidigGroepen = kernbezwaar.individueleBezwaren;
    this._huidigePagina = 1;
    this._renderPassagePagina();
  }

  _renderPassagePagina() {
    const sideSheet = this.shadowRoot.querySelector('#side-sheet');
    const inhoud = this.shadowRoot.querySelector('#side-sheet-inhoud');
    const titelEl = this.shadowRoot.querySelector('#side-sheet-titel');
    if (!sideSheet || !inhoud) return;

    const kernbezwaar = this._huidigKernbezwaar;
    const groepen = this._huidigGroepen;
    if (!kernbezwaar || !groepen) return;

    inhoud.innerHTML = '';
    if (titelEl) titelEl.textContent = kernbezwaar.samenvatting;

    const totaal = this._telDocumenten(kernbezwaar);
    const totaalPaginas = Math.max(1, Math.ceil(groepen.length / PAGE_SIZE));
    if (this._huidigePagina > totaalPaginas) this._huidigePagina = totaalPaginas;

    const aantalLabel = document.createElement('p');
    aantalLabel.textContent = `${totaal} individuele bezwar${totaal === 1 ? '' : 'en'}, ` +
        `${groepen.length} unieke passage${groepen.length === 1 ? '' : 's'}:`;
    inhoud.appendChild(aantalLabel);

    const isNoise = kernbezwaar.samenvatting === 'Niet-geclusterde bezwaren';
    const MAX_ZICHTBAAR = 5;
    const startIdx = (this._huidigePagina - 1) * PAGE_SIZE;
    const paginaGroepen = groepen.slice(startIdx, startIdx + PAGE_SIZE);

    paginaGroepen.forEach((groep) => {
      const groepEl = document.createElement('div');
      groepEl.className = 'passage-groep';

      const passage = document.createElement('div');
      passage.className = 'passage-tekst';
      passage.appendChild(document.createTextNode(`"${groep.passage}"`));

      if (groep.scorePercentage != null) {
        const scoreBadge = document.createElement('span');
        scoreBadge.style.cssText = 'display:inline-block;background:#e8ebee;border-radius:3px;' +
            'padding:0.1rem 0.4rem;font-size:0.8rem;font-style:normal;color:#333;margin-left:0.5rem;';
        scoreBadge.textContent = `${groep.scorePercentage}%`;
        passage.appendChild(scoreBadge);
      }

      // Toewijzingsmethode badge
      if (groep.toewijzingsmethode) {
        const methode = groep.toewijzingsmethode;
        const badge = document.createElement('span');
        if (methode === 'HDBSCAN') {
          badge.className = 'toewijzing-badge toewijzing-badge--hdbscan';
          badge.textContent = 'Clustering';
        } else if (methode === 'CENTROID_FALLBACK') {
          badge.className = 'toewijzing-badge toewijzing-badge--centroid';
          badge.textContent = 'Centroid';
        } else if (methode === 'MANUEEL') {
          badge.className = 'toewijzing-badge toewijzing-badge--manueel';
          badge.textContent = 'Handmatig';
        }
        if (badge.className) passage.appendChild(badge);
      }

      groepEl.appendChild(passage);

      const docContainer = document.createElement('div');
      docContainer.className = 'passage-documenten';

      const documenten = groep.passageGroep ? groep.passageGroep.documenten : [];
      const zichtbaar = documenten.slice(0, MAX_ZICHTBAAR);
      const verborgen = documenten.slice(MAX_ZICHTBAAR);

      zichtbaar.forEach((ref) => {
        docContainer.appendChild(this._maakDocumentLink(ref));
      });

      if (verborgen.length > 0) {
        const toonAlleLink = document.createElement('a');
        toonAlleLink.className = 'passage-toon-alle';
        toonAlleLink.href = '#';
        toonAlleLink.textContent = `... (${documenten.length} documenten)`;
        toonAlleLink.addEventListener('click', (e) => {
          e.preventDefault();
          verborgen.forEach((ref) => {
            docContainer.insertBefore(this._maakDocumentLink(ref), toonAlleLink);
          });
          toonAlleLink.remove();
        });
        docContainer.appendChild(toonAlleLink);
      }

      groepEl.appendChild(docContainer);

      // Toewijzen-knop voor noise passages
      if (isNoise && groep) {
        const toewijzenKnop = document.createElement('vl-button');
        toewijzenKnop.setAttribute('ghost', '');
        toewijzenKnop.textContent = 'Toewijzen';
        toewijzenKnop.style.marginTop = '0.5rem';
        toewijzenKnop.addEventListener('click', () =>
          this._toonSuggesties(groep, groepEl));
        groepEl.appendChild(toewijzenKnop);
      }

      inhoud.appendChild(groepEl);
    });

    // Paginering
    if (totaalPaginas > 1) {
      const paginering = document.createElement('div');
      paginering.className = 'paginering';

      const vorigeKnop = document.createElement('vl-button');
      vorigeKnop.setAttribute('ghost', '');
      vorigeKnop.textContent = 'Vorige';
      if (this._huidigePagina <= 1) vorigeKnop.setAttribute('disabled', '');
      vorigeKnop.addEventListener('click', () => {
        if (this._huidigePagina > 1) {
          this._huidigePagina--;
          this._renderPassagePagina();
        }
      });

      const indicator = document.createElement('span');
      indicator.textContent = `${this._huidigePagina} / ${totaalPaginas}`;

      const volgendeKnop = document.createElement('vl-button');
      volgendeKnop.setAttribute('ghost', '');
      volgendeKnop.textContent = 'Volgende';
      if (this._huidigePagina >= totaalPaginas) volgendeKnop.setAttribute('disabled', '');
      volgendeKnop.addEventListener('click', () => {
        if (this._huidigePagina < totaalPaginas) {
          this._huidigePagina++;
          this._renderPassagePagina();
        }
      });

      paginering.appendChild(vorigeKnop);
      paginering.appendChild(indicator);
      paginering.appendChild(volgendeKnop);
      inhoud.appendChild(paginering);
    }

    sideSheet.open();
    this.classList.add('side-sheet-open');
  }

  _maakDocumentLink(ref) {
    const link = document.createElement('a');
    link.className = 'passage-document-link';
    link.textContent = ref.bestandsnaam;
    link.href = `/api/v1/projects/${encodeURIComponent(this._projectNaam)}/bezwaren/${encodeURIComponent(ref.bestandsnaam)}/download`;
    link.download = ref.bestandsnaam;
    return link;
  }

  // --- Handmatige toewijzing ---

  async _toonSuggesties(bezwaar, groepEl) {
    const bestaand = groepEl.querySelector('.toewijzen-dropdown');
    if (bestaand) {
      bestaand.remove();
      return;
    }

    const eersteBezwaarId = bezwaar.passageGroep && bezwaar.passageGroep.documenten.length > 0 ?
      bezwaar.passageGroep.documenten[0].bezwaarId : null;
    if (!eersteBezwaarId) return;
    const response = await fetch(
        `/api/v1/projects/${encodeURIComponent(this._projectNaam)}/noise/${eersteBezwaarId}/suggesties`);
    if (!response.ok) return;
    const suggesties = await response.json();

    const dropdown = document.createElement('div');
    dropdown.className = 'toewijzen-dropdown';

    if (suggesties.length === 0) {
      const leeg = document.createElement('p');
      leeg.textContent = 'Geen suggesties beschikbaar.';
      leeg.style.color = '#687483';
      dropdown.appendChild(leeg);
    } else {
      let geselecteerdId = null;

      suggesties.forEach((s) => {
        const item = document.createElement('div');
        item.className = 'suggestie-item';

        const tekst = document.createElement('span');
        tekst.textContent = s.samenvatting;
        tekst.style.flex = '1';
        tekst.style.marginRight = '1rem';

        const score = document.createElement('span');
        score.className = 'suggestie-score';
        score.textContent = `${s.scorePercentage}%`;

        item.appendChild(tekst);
        item.appendChild(score);
        item.addEventListener('click', () => {
          dropdown.querySelectorAll('.suggestie-item').forEach(
              (el) => el.classList.remove('suggestie-geselecteerd'));
          item.classList.add('suggestie-geselecteerd');
          geselecteerdId = s.kernbezwaarId;
          voegToeKnop.removeAttribute('disabled');
        });
        dropdown.appendChild(item);
      });

      const voegToeKnop = document.createElement('vl-button');
      voegToeKnop.textContent = 'Voeg toe';
      voegToeKnop.setAttribute('disabled', '');
      voegToeKnop.style.marginTop = '0.5rem';
      voegToeKnop.addEventListener('click', () => {
        if (geselecteerdId) {
          this._voerToewijzingUit(bezwaar, geselecteerdId);
        }
      });
      dropdown.appendChild(voegToeKnop);
    }

    groepEl.appendChild(dropdown);
  }

  async _voerToewijzingUit(bezwaar, kernbezwaarId) {
    const response = await fetch(
        `/api/v1/projects/${encodeURIComponent(this._projectNaam)}/referenties/${bezwaar.referentieId}/toewijzing`,
        {
          method: 'PUT',
          headers: {'Content-Type': 'application/json'},
          body: JSON.stringify({kernbezwaarId}),
        });
    if (response.ok) {
      // Verwijder het bezwaar lokaal uit de noise lijst
      const noiseKernbezwaar = this._kernbezwaren.find(
          (k) => k.samenvatting === 'Niet-geclusterde bezwaren');
      if (noiseKernbezwaar) {
        noiseKernbezwaar.individueleBezwaren = noiseKernbezwaar.individueleBezwaren.filter(
            (b) => b.referentieId !== bezwaar.referentieId);
        // Update cache en herrender enkel side panel
        this._huidigGroepen = this._huidigGroepen.filter(
            (g) => g.referentieId !== bezwaar.referentieId);
        if (noiseKernbezwaar.individueleBezwaren.length === 0) {
          const sideSheet = this.shadowRoot.querySelector('#side-sheet');
          if (sideSheet) {
            sideSheet.close();
            this.classList.remove('side-sheet-open');
          }
        } else {
          this._renderPassagePagina();
        }
      }
      // Achtergrond: herlaad alles voor consistente state (buiten kritisch pad)
      this.laadKernbezwaren(this._projectNaam);
    }
  }
}

defineWebComponent(BezwaarschriftenKernbezwaren, 'bezwaarschriften-kernbezwaren');
