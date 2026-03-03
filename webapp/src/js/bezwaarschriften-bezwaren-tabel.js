import {BaseHTMLElement, defineWebComponent, registerWebComponents} from '@domg-wc/common';
import {VlRichDataTable} from '@domg-wc/components/block/rich-data-table/vl-rich-data-table.component.js';
import {VlRichDataField} from '@domg-wc/components/block/rich-data-table/vl-rich-data-field.component.js';
import {VlPillComponent} from '@domg-wc/components/block/pill/vl-pill.component.js';
import {VlSearchFilterComponent} from '@domg-wc/components/block/search-filter/vl-search-filter.component.js';
import {VlPagerComponent} from '@domg-wc/components/block/pager/vl-pager.component.js';
import {VlInputFieldComponent} from '@domg-wc/components/form/input-field/vl-input-field.component.js';
import {VlFormLabelComponent} from '@domg-wc/components/form/form-label/vl-form-label.component.js';
import {VlSelectComponent} from '@domg-wc/components/form/select/vl-select.component.js';
import {VlSideSheet} from '@domg-wc/components/block/side-sheet/vl-side-sheet.component.js';
import {VlTabsComponent} from '@domg-wc/components/block/tabs/vl-tabs.component.js';
import {VlTabsPaneComponent} from '@domg-wc/components/block/tabs/vl-tabs-pane.component.js';
import {VlTextareaComponent} from '@domg-wc/components/form/textarea/vl-textarea.component.js';
import {VlButtonComponent} from '@domg-wc/components/atom/button/vl-button.component.js';
import {VlModalComponent} from '@domg-wc/components/block/modal/vl-modal.component.js';
import {vlGlobalStyles} from '@domg-wc/styles';

registerWebComponents([
  VlRichDataTable, VlRichDataField, VlPillComponent,
  VlSearchFilterComponent, VlPagerComponent,
  VlInputFieldComponent, VlFormLabelComponent, VlSelectComponent,
  VlSideSheet, VlTabsComponent, VlTabsPaneComponent,
  VlTextareaComponent, VlButtonComponent, VlModalComponent,
]);

const STATUS_LABELS = {
  'todo': 'Te verwerken',
  'wachtend': 'Wachtend',
  'bezig': 'Bezig',
  'extractie-klaar': 'Extractie klaar',
  'fout': 'Fout',
  'niet ondersteund': 'Niet ondersteund',
};

const STATUS_PILL_TYPES = {
  'todo': '',
  'wachtend': 'warning',
  'bezig': 'warning',
  'extractie-klaar': 'success',
  'fout': 'error',
  'niet ondersteund': '',
};

const STATUS_OPTIES = [
  {value: '', label: 'Alle statussen'},
  {value: 'todo', label: 'Te verwerken'},
  {value: 'wachtend', label: 'Wachtend'},
  {value: 'bezig', label: 'Bezig'},
  {value: 'extractie-klaar', label: 'Extractie klaar'},
  {value: 'fout', label: 'Fout'},
  {value: 'niet ondersteund', label: 'Niet ondersteund'},
];

const ITEMS_PER_PAGINA = 50;

export class BezwaarschriftenBezwarenTabel extends BaseHTMLElement {
  static get properties() {
    return {
      bezwaren: {type: Array},
    };
  }

  constructor() {
    super(`
      <style>
        ${vlGlobalStyles}
        :host { display: block; transition: margin-right 0.2s ease; }
        :host(.side-sheet-open) { margin-right: 33.3%; }
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
        .side-sheet-body {
          flex: 1;
          overflow-y: auto;
          padding: 1rem 1.5rem;
        }
        .bezwaar-item {
          margin-bottom: 1.5rem;
          padding-bottom: 1rem;
          border-bottom: 1px solid #e8ebee;
        }
        .bezwaar-item:last-child { border-bottom: none; }
        .bezwaar-samenvatting {
          font-weight: bold;
          margin-bottom: 0.25rem;
        }
        .bezwaar-passage {
          font-style: italic;
          line-height: 1.5;
          color: #687483;
        }
        .bezwaar-waarschuwing {
          color: #a5673f;
          font-weight: bold;
          margin-bottom: 0.5rem;
          padding: 0.4rem 0.6rem;
          background: #fff4e5;
          border-left: 3px solid #a5673f;
          border-radius: 2px;
        }
        .bezwaar-manueel-label {
          margin-bottom: 0.25rem;
        }
        .bezwaar-header {
          display: flex;
          justify-content: space-between;
          align-items: flex-start;
        }
        #manueel-bezwaar-formulier {
          background: #f3f5f6;
          padding: 1rem;
          margin-bottom: 1.5rem;
          border-radius: 4px;
          border: 1px solid #e8ebee;
        }
        #manueel-bezwaar-formulier label {
          display: block;
          font-weight: bold;
          margin-bottom: 0.25rem;
        }
        #manueel-bezwaar-formulier .formulier-veld {
          margin-bottom: 0.75rem;
        }
        .formulier-acties {
          display: flex;
          gap: 0.5rem;
          align-items: center;
        }
        #manueel-foutmelding {
          color: #db3434;
          margin-top: 0.5rem;
        }
      </style>
      <vl-rich-data-table id="tabel" filter-closable filter-closed>
        <vl-search-filter slot="filter" alt>
          <form>
            <section>
              <div>
                <vl-form-label for="filter-bestandsnaam" label="Bestandsnaam" light></vl-form-label>
                <vl-input-field id="filter-bestandsnaam" type="text" name="bestandsnaam" placeholder="Zoek op bestandsnaam..." block></vl-input-field>
              </div>
              <div>
                <vl-form-label for="filter-status" label="Status" light></vl-form-label>
                <vl-select id="filter-status" name="status" placeholder="Alle statussen" block></vl-select>
              </div>
            </section>
          </form>
        </vl-search-filter>
        <vl-rich-data-field name="selectie" label=" "></vl-rich-data-field>
        <vl-rich-data-field name="bestandsnaam" label="Bestandsnaam" sortable></vl-rich-data-field>
        <vl-rich-data-field name="aantalBezwaren" label="Bezwaren" sortable></vl-rich-data-field>
        <vl-rich-data-field name="status" label="Status" sortable></vl-rich-data-field>
        <vl-rich-data-field name="acties" label="Acties"></vl-rich-data-field>
        <vl-pager slot="pager" total-items="0" items-per-page="${ITEMS_PER_PAGINA}" current-page="1"></vl-pager>
      </vl-rich-data-table>
      <vl-side-sheet id="extractie-side-sheet" hide-toggle-button absolute>
        <div class="side-sheet-wrapper">
          <div class="side-sheet-header">
            <div id="extractie-side-sheet-titel" class="side-sheet-titel"></div>
            <vl-button id="extractie-side-sheet-sluit" icon="close" ghost label="Sluiten"></vl-button>
          </div>
          <div id="extractie-side-sheet-inhoud" class="side-sheet-body"></div>
        </div>
      </vl-side-sheet>
      <vl-modal id="verwijder-bezwaar-modal" title="Bezwaar verwijderen" closable>
        <div slot="content">
          <p>Weet je zeker dat je dit bezwaar wil verwijderen? Deze actie kan niet ongedaan gemaakt worden.</p>
        </div>
        <div slot="button">
          <vl-button id="verwijder-bezwaar-bevestig" error="">Verwijderen</vl-button>
        </div>
      </vl-modal>
    `);
    this.__bronBezwaren = [];
    this.__takenData = {};
    this.__timerInterval = null;
    this._projectNaam = null;
    this.__filters = {};
    this.__sorting = [];
    this.__huidigePagina = 1;
    this.__herberekenGepland = false;
    this.__teVerwijderenBezwaar = null;
    this.__tabelKlaar = false;
  }

  set projectNaam(naam) {
    this._projectNaam = naam;
  }

  get projectNaam() {
    return this._projectNaam;
  }

  set bezwaren(waarde) {
    this.__bronBezwaren = waarde || [];
    this.__huidigePagina = 1;
    this._herbereken();
  }

  get bezwaren() {
    return this.__bronBezwaren;
  }

  connectedCallback() {
    super.connectedCallback();

    const tabel = this.shadowRoot.querySelector('#tabel');
    if (tabel) {
      tabel.addEventListener('change', (e) => this._onTabelChange(e));
    }

    const sluitKnop = this.shadowRoot.querySelector('#extractie-side-sheet-sluit');
    const sideSheet = this.shadowRoot.querySelector('#extractie-side-sheet');
    if (sluitKnop && sideSheet) {
      sluitKnop.addEventListener('vl-click', () => {
        sideSheet.close();
        this.classList.remove('side-sheet-open');
      });
    }

    const bevestigKnop = this.shadowRoot.querySelector('#verwijder-bezwaar-bevestig');
    if (bevestigKnop) {
      bevestigKnop.addEventListener('click', () => this._voerVerwijderingUit());
    }

    // vl-rich-data-table gebruikt webComponentPromised: registratie is asynchroon.
    // Wacht tot het component volledig geüpgraded is voordat we renderers, data en
    // statusopties configureren — anders worden property setters niet aangeroepen.
    customElements.whenDefined('vl-rich-data-table').then(() => {
      this.__tabelKlaar = true;
      this._configureerRenderers();
      this._configureerStatusOpties();
      this._herbereken();
    });
  }

  disconnectedCallback() {
    this._stopTimer();
  }

  werkBijMetTaakUpdate(taak) {
    this.__takenData[taak.bestandsnaam] = {
      id: taak.id,
      aangemaaktOp: taak.aangemaaktOp,
      verwerkingGestartOp: taak.verwerkingGestartOp,
    };
    this.__bronBezwaren = this.__bronBezwaren.map((b) =>
      b.bestandsnaam === taak.bestandsnaam ? {
        bestandsnaam: taak.bestandsnaam,
        status: taak.status,
        aantalWoorden: taak.aantalWoorden,
        aantalBezwaren: taak.aantalBezwaren,
        heeftPassagesDieNietInTekstVoorkomen: taak.heeftPassagesDieNietInTekstVoorkomen,
        heeftManueel: taak.heeftManueel,
      } : b,
    );
    this._herbereken();
  }

  geefGeselecteerdeBestandsnamen() {
    const innerTable = this._geefInnerTable();
    if (!innerTable) return [];
    const checkboxes = innerTable.querySelectorAll('.rij-checkbox:checked');
    return Array.from(checkboxes).map((cb) => cb.dataset.bestandsnaam);
  }

  _geefInnerTable() {
    const tabel = this.shadowRoot && this.shadowRoot.querySelector('#tabel');
    if (!tabel) return null;
    const vlTable = tabel.shadowRoot && tabel.shadowRoot.querySelector('vl-table');
    if (!vlTable) return null;
    return vlTable.querySelector('table');
  }

  _configureerStatusOpties() {
    const select = this.shadowRoot.querySelector('#filter-status');
    if (select) {
      select.options = STATUS_OPTIES;
    }
  }

  _configureerRenderers() {
    const velden = this.shadowRoot.querySelectorAll('vl-rich-data-field');
    velden.forEach((veld) => {
      switch (veld.getAttribute('name')) {
        case 'selectie':
          veld.renderer = (td, rij) => {
            td.style.verticalAlign = 'middle';
            const cb = document.createElement('input');
            cb.type = 'checkbox';
            cb.className = 'rij-checkbox';
            cb.dataset.bestandsnaam = rij.bestandsnaam;
            if (this._isDisabled(rij.status)) cb.disabled = true;
            cb.addEventListener('change', () => this._dispatchSelectieGewijzigd());
            td.appendChild(cb);
          };
          break;
        case 'bestandsnaam':
          veld.renderer = (td, rij) => {
            td.style.verticalAlign = 'middle';
            td.style.width = '100%';
            if (rij.heeftPassagesDieNietInTekstVoorkomen) {
              const icon = document.createElement('span');
              icon.textContent = '\u26A0\uFE0F';
              icon.title = 'Niet alle passages konden gevonden worden';
              icon.style.marginRight = '0.4rem';
              td.appendChild(icon);
            }
            if (rij.heeftManueel) {
              const manueelIcon = document.createElement('span');
              manueelIcon.textContent = '\u270D\uFE0F';
              manueelIcon.title = 'Bevat manueel toegevoegde bezwaren';
              manueelIcon.style.marginRight = '0.4rem';
              td.appendChild(manueelIcon);
            }
            if (this._projectNaam) {
              const a = document.createElement('a');
              a.href = `/api/v1/projects/${encodeURIComponent(this._projectNaam)}/bezwaren/${encodeURIComponent(rij.bestandsnaam)}/download`;
              a.download = rij.bestandsnaam;
              a.textContent = rij.bestandsnaam;
              td.appendChild(a);
            } else {
              td.textContent = rij.bestandsnaam;
            }
          };
          break;
        case 'aantalBezwaren':
          veld.renderer = (td, rij) => {
            td.style.verticalAlign = 'middle';
            td.style.textAlign = 'center';
            td.textContent = rij.aantalBezwaren != null ? rij.aantalBezwaren : '';
          };
          break;
        case 'status':
          veld.renderer = (td, rij) => {
            td.className = 'status-cel';
            td.style.verticalAlign = 'middle';
            td.style.textAlign = 'center';
            td.style.minWidth = '220px';
            td.dataset.bestandsnaam = rij.bestandsnaam;
            const pill = document.createElement('vl-pill');
            const type = STATUS_PILL_TYPES[rij.status] || '';
            if (type) pill.setAttribute('type', type);
            if (rij.status === 'niet ondersteund') pill.setAttribute('disabled', '');
            pill.style.fontVariantNumeric = 'tabular-nums';
            pill.style.minWidth = '180px';
            pill.style.display = 'inline-block';

            const isActief = rij.status === 'wachtend' || rij.status === 'bezig';
            if (isActief) {
              const span = document.createElement('span');
              span.className = 'timer-tekst';
              span.textContent = this._formatStatusLabel(rij);
              pill.appendChild(span);
              pill.appendChild(this._maakPillKnop('\u00d7', 'Annuleer verwerking', () => {
                const taakData = this.__takenData[rij.bestandsnaam];
                if (taakData && taakData.id) {
                  this.dispatchEvent(new CustomEvent('annuleer-taak', {
                    detail: {bestandsnaam: rij.bestandsnaam, taakId: taakData.id},
                    bubbles: true, composed: true,
                  }));
                }
              }));
            } else if (rij.status === 'fout') {
              pill.textContent = this._formatStatusLabel(rij);
              pill.appendChild(this._maakPillKnop('\u21bb', 'Opnieuw proberen', () => {
                this.dispatchEvent(new CustomEvent('herstart-taak', {
                  detail: {bestandsnaam: rij.bestandsnaam},
                  bubbles: true, composed: true,
                }));
              }));
            } else if (rij.status === 'todo') {
              pill.textContent = this._formatStatusLabel(rij);
              pill.appendChild(this._maakPillKnop('\u25b6', 'Verwerking starten', () => {
                this.dispatchEvent(new CustomEvent('herstart-taak', {
                  detail: {bestandsnaam: rij.bestandsnaam},
                  bubbles: true, composed: true,
                }));
              }));
            } else {
              pill.textContent = this._formatStatusLabel(rij);
            }

            td.appendChild(pill);
          };
          break;
        case 'acties':
          veld.renderer = (td, rij) => {
            td.style.verticalAlign = 'middle';
            td.style.whiteSpace = 'nowrap';
            if (rij.status === 'extractie-klaar') {
              const zoekBtn = document.createElement('vl-button');
              zoekBtn.setAttribute('icon', 'search');
              zoekBtn.setAttribute('ghost', '');
              zoekBtn.setAttribute('label', 'Extractie-details bekijken');
              zoekBtn.addEventListener('vl-click', (e) => {
                e.stopPropagation();
                this.dispatchEvent(new CustomEvent('toon-extractie-detail', {
                  detail: {bestandsnaam: rij.bestandsnaam},
                  bubbles: true, composed: true,
                }));
              });
              td.appendChild(zoekBtn);
            }
            const btn = document.createElement('vl-button');
            btn.setAttribute('icon', 'bin');
            btn.setAttribute('error', '');
            btn.setAttribute('ghost', '');
            btn.setAttribute('label', 'Bestand verwijderen');
            btn.addEventListener('vl-click', (e) => {
              e.stopPropagation();
              this.dispatchEvent(new CustomEvent('verwijder-bezwaar', {
                detail: {bestandsnaam: rij.bestandsnaam},
                bubbles: true, composed: true,
              }));
            });
            td.appendChild(btn);
          };
          break;
      }
    });
  }

  toonExtractieDetails(projectNaam, bestandsnaam, actieveTab = 'automatisch') {
    const sideSheet = this.shadowRoot.querySelector('#extractie-side-sheet');
    const inhoud = this.shadowRoot.querySelector('#extractie-side-sheet-inhoud');
    const titelEl = this.shadowRoot.querySelector('#extractie-side-sheet-titel');
    if (!sideSheet || !inhoud) return;

    this._huidigeBestandsnaam = bestandsnaam;

    inhoud.innerHTML = '<p>Laden...</p>';
    if (titelEl) titelEl.textContent = bestandsnaam;
    sideSheet.open();
    this.classList.add('side-sheet-open');

    fetch(`/api/v1/projects/${encodeURIComponent(projectNaam)}/extracties/${encodeURIComponent(bestandsnaam)}/details`)
        .then((response) => {
          if (!response.ok) throw new Error('Ophalen details mislukt');
          return response.json();
        })
        .then((data) => {
          inhoud.innerHTML = '';

          const automatisch = data.bezwaren.filter((b) => !b.manueel);
          const manueel = data.bezwaren.filter((b) => b.manueel);

          // Sorteer automatisch: niet-gevonden passages bovenaan
          automatisch.sort((a, b) => {
            if (a.passageGevonden === b.passageGevonden) return 0;
            return a.passageGevonden ? 1 : -1;
          });

          if (titelEl) {
            titelEl.textContent = `${data.bestandsnaam} - ${data.aantalBezwaren} bezwar${data.aantalBezwaren === 1 ? '' : 'en'} gevonden`;
          }

          // Bouw tabs
          const tabs = document.createElement('vl-tabs');
          tabs.setAttribute('disable-links', '');
          tabs.setAttribute('active-tab', actieveTab);

          const autoPane = document.createElement('vl-tabs-pane');
          autoPane.setAttribute('id', 'automatisch');
          autoPane.setAttribute('title', `Automatisch (${automatisch.length})`);

          automatisch.forEach((bezwaar) => {
            autoPane.appendChild(this._maakBezwaarItem(bezwaar, projectNaam, bestandsnaam, false));
          });

          const manueelPane = document.createElement('vl-tabs-pane');
          manueelPane.setAttribute('id', 'manueel');
          manueelPane.setAttribute('title', `Manueel (${manueel.length})`);

          manueel.forEach((bezwaar) => {
            manueelPane.appendChild(this._maakBezwaarItem(bezwaar, projectNaam, bestandsnaam, true));
          });

          // Toevoeg-knop onderaan manueel-tab
          const toevoegKnop = document.createElement('vl-button');
          toevoegKnop.id = 'bezwaar-toevoegen-knop';
          toevoegKnop.setAttribute('icon', 'add');
          toevoegKnop.textContent = 'Toevoegen';
          toevoegKnop.addEventListener('click', () => {
            this._toonManueelBezwaarFormulier();
          });
          manueelPane.appendChild(toevoegKnop);

          tabs.appendChild(autoPane);
          tabs.appendChild(manueelPane);
          inhoud.appendChild(tabs);
        })
        .catch(() => {
          inhoud.innerHTML = '<p>Kon extractie-details niet laden.</p>';
        });
  }

  _maakBezwaarItem(bezwaar, projectNaam, bestandsnaam, isManueelTab) {
    const item = document.createElement('div');
    item.className = 'bezwaar-item';

    if (!bezwaar.passageGevonden) {
      const waarschuwing = document.createElement('div');
      waarschuwing.className = 'bezwaar-waarschuwing';
      waarschuwing.textContent = '\u26A0\uFE0F Passage kon niet gevonden worden';
      item.appendChild(waarschuwing);
    }

    if (bezwaar.manueel && !isManueelTab) {
      const label = document.createElement('vl-pill');
      label.className = 'bezwaar-manueel-label';
      label.textContent = 'Manueel';
      item.appendChild(label);
    }

    const header = document.createElement('div');
    header.className = 'bezwaar-header';

    const samenvatting = document.createElement('div');
    samenvatting.className = 'bezwaar-samenvatting';
    samenvatting.textContent = bezwaar.samenvatting;
    header.appendChild(samenvatting);

    const verwijderKnop = document.createElement('vl-button');
    verwijderKnop.setAttribute('icon', 'bin');
    verwijderKnop.setAttribute('error', '');
    verwijderKnop.setAttribute('ghost', '');
    verwijderKnop.setAttribute('label', 'Bezwaar verwijderen');
    verwijderKnop.addEventListener('vl-click', () => {
      if (!bezwaar.id) return;
      const actieveTab = isManueelTab ? 'manueel' : 'automatisch';
      this._vraagBevestigingVerwijder(projectNaam, bestandsnaam, bezwaar.id, actieveTab);
    });
    header.appendChild(verwijderKnop);

    item.appendChild(header);

    const passage = document.createElement('div');
    passage.className = 'bezwaar-passage';
    passage.textContent = `\u201C${bezwaar.passage}\u201D`;
    item.appendChild(passage);

    return item;
  }

  _vraagBevestigingVerwijder(projectNaam, bestandsnaam, bezwaarId, actieveTab) {
    this.__teVerwijderenBezwaar = {projectNaam, bestandsnaam, bezwaarId, actieveTab};
    const modal = this.shadowRoot.querySelector('#verwijder-bezwaar-modal');
    if (modal) modal.open();
  }

  _voerVerwijderingUit() {
    const data = this.__teVerwijderenBezwaar;
    if (!data) return;

    const modal = this.shadowRoot.querySelector('#verwijder-bezwaar-modal');
    if (modal) modal.close();

    fetch(`/api/v1/projects/${encodeURIComponent(data.projectNaam)}/extracties/${encodeURIComponent(data.bestandsnaam)}/bezwaren/${encodeURIComponent(data.bezwaarId)}`, {
      method: 'DELETE',
    }).then((response) => {
      if (!response.ok) throw new Error('Verwijderen mislukt');
      this.__teVerwijderenBezwaar = null;
      this.toonExtractieDetails(data.projectNaam, data.bestandsnaam, data.actieveTab);
      this.dispatchEvent(new CustomEvent('bezwaar-gewijzigd', {
        bubbles: true, composed: true,
      }));
    }).catch(() => {
      this.__teVerwijderenBezwaar = null;
      const inhoud = this.shadowRoot.querySelector('#extractie-side-sheet-inhoud');
      if (inhoud) {
        const fout = document.createElement('div');
        fout.className = 'bezwaar-waarschuwing';
        fout.textContent = 'Verwijderen mislukt, probeer opnieuw.';
        inhoud.prepend(fout);
      }
    });
  }

  _toonManueelBezwaarFormulier() {
    const inhoud = this.shadowRoot.querySelector('#extractie-side-sheet-inhoud');
    if (!inhoud) return;
    const manueelPane = inhoud.querySelector('vl-tabs-pane#manueel');
    const target = manueelPane || inhoud;
    if (target.querySelector('#manueel-bezwaar-formulier')) return;

    const formulier = document.createElement('div');
    formulier.id = 'manueel-bezwaar-formulier';

    formulier.innerHTML = `
      <div class="formulier-veld">
        <label for="manueel-samenvatting">Samenvatting</label>
        <vl-textarea id="manueel-samenvatting" rows="2" block></vl-textarea>
      </div>
      <div class="formulier-veld">
        <label for="manueel-passage">Passage</label>
        <vl-textarea id="manueel-passage" rows="4" block></vl-textarea>
      </div>
      <div class="formulier-acties">
        <vl-button id="manueel-opslaan" disabled>Opslaan</vl-button>
        <vl-button id="manueel-annuleer" icon="close" ghost label="Annuleren"></vl-button>
      </div>
      <div id="manueel-foutmelding"></div>
    `;

    target.prepend(formulier);

    // Enable/disable opslaan-knop op basis van input
    const samenvatting = formulier.querySelector('#manueel-samenvatting');
    const passage = formulier.querySelector('#manueel-passage');
    const opslaanKnop = formulier.querySelector('#manueel-opslaan');

    const updateOpslaanStatus = () => {
      const samenvattingWaarde = samenvatting.value || '';
      const passageWaarde = passage.value || '';
      if (samenvattingWaarde.trim() && passageWaarde.trim()) {
        opslaanKnop.removeAttribute('disabled');
      } else {
        opslaanKnop.setAttribute('disabled', '');
      }
    };

    samenvatting.addEventListener('input', updateOpslaanStatus);
    passage.addEventListener('input', updateOpslaanStatus);

    // Annuleer
    formulier.querySelector('#manueel-annuleer').addEventListener('click', () => {
      formulier.remove();
    });

    // Opslaan
    opslaanKnop.addEventListener('click', () => {
      this._slaManueelBezwaarOp(samenvatting.value, passage.value);
    });
  }

  _slaManueelBezwaarOp(samenvatting, passage) {
    const projectNaam = this._projectNaam;
    const bestandsnaam = this._huidigeBestandsnaam;

    const foutEl = this.shadowRoot.querySelector('#manueel-foutmelding');
    if (foutEl) foutEl.textContent = '';

    fetch(`/api/v1/projects/${encodeURIComponent(projectNaam)}/extracties/${encodeURIComponent(bestandsnaam)}/bezwaren`, {
      method: 'POST',
      headers: {'Content-Type': 'application/json'},
      body: JSON.stringify({samenvatting, passage}),
    }).then((response) => {
      if (response.ok) {
        // Herlaad side-panel, blijf op manueel-tab
        this.toonExtractieDetails(projectNaam, bestandsnaam, 'manueel');
        this.dispatchEvent(new CustomEvent('bezwaar-gewijzigd', {
          bubbles: true, composed: true,
        }));
      } else {
        return response.json().then((data) => {
          if (foutEl) {
            foutEl.textContent = data.fout || 'Opslaan mislukt, probeer opnieuw.';
          }
        }).catch(() => {
          if (foutEl) foutEl.textContent = 'Opslaan mislukt, probeer opnieuw.';
        });
      }
    }).catch(() => {
      if (foutEl) foutEl.textContent = 'Opslaan mislukt, probeer opnieuw.';
    });
  }

  _onTabelChange(event) {
    const detail = event.detail || {};

    // Bouw filters altijd opnieuw vanuit formData — vl-rich-data retourneert
    // undefined als alle filterwaarden leeg zijn (bv. "Alle statussen").
    const nieuweFilters = {};
    if (detail.formData) {
      for (const [key, value] of detail.formData.entries()) {
        if (value) nieuweFilters[key] = value;
      }
    }
    if (JSON.stringify(nieuweFilters) !== JSON.stringify(this.__filters)) {
      this.__filters = nieuweFilters;
      this.__huidigePagina = 1;
    }

    if (detail.paging && detail.paging.currentPage) {
      this.__huidigePagina = detail.paging.currentPage;
    }

    if (detail.sorting) {
      this.__sorting = detail.sorting;
    }

    this._herbereken();
  }

  _herbereken() {
    if (!this.__tabelKlaar) return;
    if (this.__herberekenGepland) return;
    this.__herberekenGepland = true;
    requestAnimationFrame(() => {
      this.__herberekenGepland = false;
      this._doeHerbereken();
    });
  }

  _doeHerbereken() {
    const tabel = this.shadowRoot && this.shadowRoot.querySelector('#tabel');
    if (!tabel) return;

    let resultaat = this._filterBezwaren(this.__bronBezwaren, this.__filters);
    resultaat = this._sorteerBezwaren(resultaat, this.__sorting);

    const totaal = resultaat.length;
    const pagina = resultaat.slice(
        (this.__huidigePagina - 1) * ITEMS_PER_PAGINA,
        this.__huidigePagina * ITEMS_PER_PAGINA,
    );

    tabel.data = {
      data: pagina,
      paging: {
        currentPage: this.__huidigePagina,
        totalItems: totaal,
      },
    };

    requestAnimationFrame(() => {
      this._configureerSelecteerAlles();
      this._centreerKolomHoofdingen();
    });
    this._dispatchSelectieGewijzigd();
    this._beheerTimer();
  }

  _filterBezwaren(bezwaren, filters) {
    return bezwaren.filter((b) => {
      if (filters.bestandsnaam &&
          !b.bestandsnaam.toLowerCase().includes(filters.bestandsnaam.toLowerCase())) {
        return false;
      }
      if (filters.status && b.status !== filters.status) {
        return false;
      }
      return true;
    });
  }

  _sorteerBezwaren(bezwaren, sorting) {
    if (!sorting || sorting.length === 0) return bezwaren;

    const statusVolgorde = {
      'todo': 0, 'wachtend': 1, 'bezig': 2,
      'extractie-klaar': 3, 'fout': 4, 'niet ondersteund': 5,
    };

    return [...bezwaren].sort((a, b) => {
      for (const sort of sorting) {
        let cmp = 0;
        if (sort.name === 'status') {
          cmp = (statusVolgorde[a.status] ?? 99) - (statusVolgorde[b.status] ?? 99);
        } else if (sort.name === 'aantalBezwaren') {
          cmp = (a.aantalBezwaren ?? 0) - (b.aantalBezwaren ?? 0);
        } else {
          const valA = a[sort.name] ?? '';
          const valB = b[sort.name] ?? '';
          cmp = String(valA).localeCompare(String(valB), 'nl');
        }
        if (cmp !== 0) return sort.direction === 'asc' ? cmp : -cmp;
      }
      return 0;
    });
  }

  _isDisabled(status) {
    return status === 'niet ondersteund' || status === 'wachtend' || status === 'bezig';
  }

  _beheerTimer() {
    const heeftActief = this.__bronBezwaren.some(
        (b) => b.status === 'wachtend' || b.status === 'bezig',
    );
    if (heeftActief && !this.__timerInterval) {
      this.__timerInterval = setInterval(() => this._updateTimers(), 1000);
    } else if (!heeftActief && this.__timerInterval) {
      this._stopTimer();
    }
  }

  _stopTimer() {
    if (this.__timerInterval) {
      clearInterval(this.__timerInterval);
      this.__timerInterval = null;
    }
  }

  _updateTimers() {
    const nu = Date.now();
    const innerTable = this._geefInnerTable();
    if (!innerTable) return;

    this.__bronBezwaren.forEach((b) => {
      if (b.status !== 'wachtend' && b.status !== 'bezig') return;
      const cel = innerTable.querySelector(
          `.status-cel[data-bestandsnaam="${CSS.escape(b.bestandsnaam)}"]`,
      );
      if (!cel) return;
      const timerTekst = cel.querySelector('.timer-tekst');
      if (timerTekst) {
        timerTekst.textContent = this._formatStatusLabel(b, nu);
      }
    });
  }

  _maakPillKnop(symbool, titel, onClick) {
    const btn = document.createElement('button');
    btn.title = titel;
    btn.textContent = symbool;
    Object.assign(btn.style, {
      background: 'none',
      border: 'none',
      cursor: 'pointer',
      fontSize: '14px',
      color: 'inherit',
      padding: '0',
      marginLeft: '6px',
      lineHeight: '1',
      opacity: '0.6',
    });
    btn.addEventListener('mouseenter', () => {
      btn.style.opacity = '1';
    });
    btn.addEventListener('mouseleave', () => {
      btn.style.opacity = '0.6';
    });
    btn.addEventListener('click', (e) => {
      e.stopPropagation();
      onClick();
    });
    return btn;
  }

  _formatStatusLabel(b, nu) {
    nu = nu || Date.now();
    const taakData = this.__takenData[b.bestandsnaam];

    if (b.status === 'wachtend' && taakData && taakData.aangemaaktOp) {
      const wachtMs = nu - new Date(taakData.aangemaaktOp).getTime();
      return `Wachtend (${this._formatTijd(wachtMs)})`;
    }

    if (b.status === 'bezig' && taakData) {
      const wachtMs = taakData.verwerkingGestartOp && taakData.aangemaaktOp ?
        new Date(taakData.verwerkingGestartOp).getTime() -
            new Date(taakData.aangemaaktOp).getTime() :
        0;
      const verwerkMs = taakData.verwerkingGestartOp ?
        nu - new Date(taakData.verwerkingGestartOp).getTime() :
        0;
      return `Bezig (${this._formatTijd(wachtMs)} + ${this._formatTijd(verwerkMs)})`;
    }

    return STATUS_LABELS[b.status] || b.status;
  }

  _formatTijd(ms) {
    const totaalSeconden = Math.floor(ms / 1000);
    const minuten = Math.floor(totaalSeconden / 60);
    const seconden = totaalSeconden % 60;
    return `${minuten}:${String(seconden).padStart(2, '0')}`;
  }

  _configureerSelecteerAlles() {
    const innerTable = this._geefInnerTable();
    if (!innerTable) return;

    const firstTh = innerTable.querySelector('thead th');
    if (!firstTh) return;

    let cb = firstTh.querySelector('#selecteer-alles');
    if (cb) {
      cb.checked = false;
      return;
    }

    cb = document.createElement('input');
    cb.type = 'checkbox';
    cb.id = 'selecteer-alles';
    cb.title = 'Selecteer alles';
    cb.addEventListener('change', (e) => {
      const checked = e.target.checked;
      const table = this._geefInnerTable();
      if (!table) return;
      table.querySelectorAll('.rij-checkbox:not([disabled])').forEach((rijCb) => {
        rijCb.checked = checked;
      });
      this._dispatchSelectieGewijzigd();
    });
    firstTh.textContent = '';
    firstTh.appendChild(cb);
  }

  _centreerKolomHoofdingen() {
    const innerTable = this._geefInnerTable();
    if (!innerTable) return;
    const headers = innerTable.querySelectorAll('thead th');
    // kolommen: selectie(0), bestandsnaam(1), bezwaren(2), status(3), acties(4)
    [2, 3].forEach((i) => {
      if (headers[i]) headers[i].style.textAlign = 'center';
    });
  }

  _dispatchSelectieGewijzigd() {
    const geselecteerd = this.geefGeselecteerdeBestandsnamen();
    this.dispatchEvent(new CustomEvent('selectie-gewijzigd', {
      detail: {geselecteerd},
      bubbles: true,
      composed: true,
    }));
  }
}

defineWebComponent(BezwaarschriftenBezwarenTabel, 'bezwaarschriften-bezwaren-tabel');
