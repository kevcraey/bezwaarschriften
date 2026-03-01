import {BaseHTMLElement, defineWebComponent, registerWebComponents} from '@domg-wc/common';
import {VlRichDataTable} from '@domg-wc/components/block/rich-data-table/vl-rich-data-table.component.js';
import {VlRichDataField} from '@domg-wc/components/block/rich-data-table/vl-rich-data-field.component.js';
import {VlPillComponent} from '@domg-wc/components/block/pill/vl-pill.component.js';
import {VlSearchFilterComponent} from '@domg-wc/components/block/search-filter/vl-search-filter.component.js';
import {VlPagerComponent} from '@domg-wc/components/block/pager/vl-pager.component.js';
import {VlInputFieldComponent} from '@domg-wc/components/form/input-field/vl-input-field.component.js';
import {VlFormLabelComponent} from '@domg-wc/components/form/form-label/vl-form-label.component.js';
import {VlSelectComponent} from '@domg-wc/components/form/select/vl-select.component.js';
import {vlGlobalStyles} from '@domg-wc/styles';

registerWebComponents([
  VlRichDataTable, VlRichDataField, VlPillComponent,
  VlSearchFilterComponent, VlPagerComponent,
  VlInputFieldComponent, VlFormLabelComponent, VlSelectComponent,
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
        <vl-rich-data-field name="aantalBezwaren" label="Aantal bezwaren" sortable></vl-rich-data-field>
        <vl-rich-data-field name="status" label="Status" sortable></vl-rich-data-field>
        <vl-pager slot="pager" total-items="0" items-per-page="${ITEMS_PER_PAGINA}" current-page="1"></vl-pager>
      </vl-rich-data-table>
    `);
    this.__bronBezwaren = [];
    this.__takenData = {};
    this.__timerInterval = null;
    this._projectNaam = null;
    this.__filters = {};
    this.__sorting = [];
    this.__huidigePagina = 1;
    this.__herberekenGepland = false;
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
            td.textContent = rij.aantalBezwaren != null ? rij.aantalBezwaren : '';
          };
          break;
        case 'status':
          veld.renderer = (td, rij) => {
            td.className = 'status-cel';
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
      }
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

    requestAnimationFrame(() => this._configureerSelecteerAlles());
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
