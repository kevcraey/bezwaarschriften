import {BaseHTMLElement, defineWebComponent, registerWebComponents} from '@domg-wc/common';
import {VlRichDataTable} from '@domg-wc/components/block/rich-data-table/vl-rich-data-table.component.js';
import {VlRichDataField} from '@domg-wc/components/block/rich-data-table/vl-rich-data-field.component.js';
import {VlPillComponent} from '@domg-wc/components/block/pill/vl-pill.component.js';
import {VlSearchFilterComponent} from '@domg-wc/components/block/search-filter/vl-search-filter.component.js';
import {VlPagerComponent} from '@domg-wc/components/block/pager/vl-pager.component.js';
import {vlGlobalStyles} from '@domg-wc/styles';

registerWebComponents([VlRichDataTable, VlRichDataField, VlPillComponent, VlSearchFilterComponent, VlPagerComponent]);

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
        .status-cel { min-width: 220px; }
      </style>
      <vl-rich-data-table id="tabel">
        <vl-search-filter slot="filter" filter-title="Filters">
          <form id="filter-form">
            <label>
              Bestandsnaam
              <input type="text" name="bestandsnaam" placeholder="Zoek op bestandsnaam...">
            </label>
            <label>
              Status
              <select name="status">
                <option value="">Alle statussen</option>
                <option value="todo">Te verwerken</option>
                <option value="wachtend">Wachtend</option>
                <option value="bezig">Bezig</option>
                <option value="extractie-klaar">Extractie klaar</option>
                <option value="fout">Fout</option>
                <option value="niet ondersteund">Niet ondersteund</option>
              </select>
            </label>
          </form>
        </vl-search-filter>
        <vl-rich-data-field name="selectie" label=" "></vl-rich-data-field>
        <vl-rich-data-field name="bestandsnaam" label="Bestandsnaam" sortable></vl-rich-data-field>
        <vl-rich-data-field name="aantalBezwaren" label="Aantal bezwaren" sortable></vl-rich-data-field>
        <vl-rich-data-field name="status" label="Status" sortable></vl-rich-data-field>
        <div slot="pager" id="pager-wrapper">
          <div style="display: flex; align-items: center; gap: 16px; flex-wrap: wrap;">
            <vl-pager id="pager" items-per-page="50" current-page="1" total-items="0"></vl-pager>
            <label>
              Per pagina:
              <select id="pagina-grootte">
                <option value="50" selected>50</option>
                <option value="100">100</option>
                <option value="alle">Alle</option>
              </select>
            </label>
          </div>
        </div>
      </vl-rich-data-table>
    `);
    this.__bronBezwaren = [];
    this.__takenData = {};
    this.__timerInterval = null;
    this._projectNaam = null;
    this.__filters = {};
    this.__sorting = [];
    this.__paginaGrootte = 50;
    this.__huidigePagina = 1;
    this.__herberekenGepland = false;
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
    this._configureerRenderers();

    // Luister naar change-events van vl-rich-data (getriggerd door vl-input events)
    const tabel = this.shadowRoot.querySelector('#tabel');
    if (tabel) {
      tabel.addEventListener('change', (e) => this._onTabelChange(e));
    }

    // Native <input> en <select> vuren 'input'/'change' events, niet 'vl-input'.
    // vl-rich-data luistert op 'vl-input', dus native elementen triggeren het
    // change-event van de tabel niet. Voeg daarom directe listeners toe.
    this._koppelFilterListeners();

    const paginaGrootteSelect = this.shadowRoot.querySelector('#pagina-grootte');
    if (paginaGrootteSelect) {
      paginaGrootteSelect.addEventListener('change', (e) => {
        const waarde = e.target.value;
        this.__paginaGrootte = waarde === 'alle' ? Infinity : parseInt(waarde, 10);
        this.__huidigePagina = 1;
        this._herbereken();
      });
    }

    this._herbereken();
  }

  disconnectedCallback() {
    this._stopTimer();
  }

  werkBijMetTaakUpdate(taak) {
    this.__takenData[taak.bestandsnaam] = {
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
    return vlTable.querySelector('table'); // light DOM, NOT shadowRoot
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
            td.dataset.bestandsnaam = rij.bestandsnaam;
            const pill = document.createElement('vl-pill');
            const type = STATUS_PILL_TYPES[rij.status] || '';
            if (type) pill.setAttribute('type', type);
            if (rij.status === 'niet ondersteund') pill.setAttribute('disabled', '');
            pill.textContent = this._formatStatusLabel(rij);
            td.appendChild(pill);
          };
          break;
      }
    });
  }

  _onTabelChange(event) {
    const detail = event.detail || {};

    // Filter state uit formData
    if (detail.formData) {
      this.__filters = {};
      for (const [key, value] of detail.formData.entries()) {
        if (value) this.__filters[key] = value;
      }
      // Bij filter-wijziging: reset naar pagina 1
      this.__huidigePagina = 1;
    }

    // Sorting state
    if (detail.sorting) {
      this.__sorting = detail.sorting;
    }

    // Paging state
    if (detail.paging && detail.paging.currentPage) {
      this.__huidigePagina = detail.paging.currentPage;
    }

    this._herbereken();
  }

  _koppelFilterListeners() {
    const form = this.shadowRoot.querySelector('#filter-form');
    if (!form) return;

    const _verzamelFilters = () => {
      const formData = new FormData(form);
      this.__filters = {};
      for (const [key, value] of formData.entries()) {
        if (value) this.__filters[key] = value;
      }
      this.__huidigePagina = 1;
      this._herbereken();
    };

    const input = form.querySelector('input[name="bestandsnaam"]');
    if (input) {
      input.addEventListener('input', _verzamelFilters);
    }

    const select = form.querySelector('select[name="status"]');
    if (select) {
      select.addEventListener('change', _verzamelFilters);
    }
  }

  _herbereken() {
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
    const paginaGrootte = this.__paginaGrootte === Infinity ? totaal : this.__paginaGrootte;
    const effectievePaginaGrootte = paginaGrootte > 0 ? paginaGrootte : totaal;
    const totalePaginas = effectievePaginaGrootte > 0 ? Math.ceil(totaal / effectievePaginaGrootte) : 1;
    this.__huidigePagina = Math.max(1, Math.min(this.__huidigePagina, totalePaginas));

    const start = (this.__huidigePagina - 1) * effectievePaginaGrootte;
    const pagina = this.__paginaGrootte === Infinity ? resultaat : resultaat.slice(start, start + effectievePaginaGrootte);

    // Werk pager bij
    const pager = this.shadowRoot.querySelector('#pager');
    if (pager) {
      pager.setAttribute('total-items', String(totaal));
      pager.setAttribute('items-per-page', String(effectievePaginaGrootte > totaal ? totaal || 1 : effectievePaginaGrootte));
      pager.setAttribute('current-page', String(this.__huidigePagina));
      if (this.__paginaGrootte === Infinity) {
        pager.setAttribute('pagination-disabled', '');
      } else {
        pager.removeAttribute('pagination-disabled');
      }
    }

    tabel.data = {data: pagina};
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
      const pill = cel.querySelector('vl-pill');
      if (pill) {
        pill.textContent = this._formatStatusLabel(b, nu);
      }
    });
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
