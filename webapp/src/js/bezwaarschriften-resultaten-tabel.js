import {BaseHTMLElement, defineWebComponent, registerWebComponents} from '@domg-wc/common';
import {VlRichDataTable} from '@domg-wc/components/block/rich-data-table/vl-rich-data-table.component.js';
import {VlRichDataField} from '@domg-wc/components/block/rich-data-table/vl-rich-data-field.component.js';
import {VlPillComponent} from '@domg-wc/components/block/pill/vl-pill.component.js';
import {VlSearchFilterComponent} from '@domg-wc/components/block/search-filter/vl-search-filter.component.js';
import {VlPagerComponent} from '@domg-wc/components/block/pager/vl-pager.component.js';
import {VlInputFieldComponent} from '@domg-wc/components/form/input-field/vl-input-field.component.js';
import {VlFormLabelComponent} from '@domg-wc/components/form/form-label/vl-form-label.component.js';
import {VlSelectComponent} from '@domg-wc/components/form/select/vl-select.component.js';
import {VlPopoverComponent} from '@domg-wc/components/block/popover/vl-popover.component.js';
import {vlGlobalStyles} from '@domg-wc/styles';

registerWebComponents([
  VlRichDataTable, VlRichDataField, VlPillComponent,
  VlSearchFilterComponent, VlPagerComponent,
  VlInputFieldComponent, VlFormLabelComponent, VlSelectComponent,
  VlPopoverComponent,
]);

const STATUS_LABELS = {
  'onvolledig': 'Onvolledig',
  'volledig': 'Volledig',
  'wachtend': 'Wachtend',
  'bezig': 'Bezig',
  'klaar': 'Klaar',
  'fout': 'Fout',
};

const STATUS_PILL_TYPES = {
  'onvolledig': '',
  'volledig': '',
  'wachtend': 'warning',
  'bezig': 'warning',
  'klaar': 'success',
  'fout': 'error',
};

const STATUS_ORDENING = {
  'onvolledig': 0,
  'volledig': 1,
  'wachtend': 2,
  'bezig': 3,
  'klaar': 4,
  'fout': 5,
};

const STATUS_OPTIES = [
  {value: '', label: 'Alle statussen'},
  {value: 'onvolledig', label: 'Onvolledig'},
  {value: 'volledig', label: 'Volledig'},
  {value: 'wachtend', label: 'Wachtend'},
  {value: 'bezig', label: 'Bezig'},
  {value: 'klaar', label: 'Klaar'},
  {value: 'fout', label: 'Fout'},
];

const ITEMS_PER_PAGINA = 50;

export class BezwaarschriftenResultatenTabel extends BaseHTMLElement {
  static get properties() {
    return {
      documenten: {type: Array},
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
        <vl-rich-data-field name="antwoorden" label="Antwoorden" sortable></vl-rich-data-field>
        <vl-rich-data-field name="status" label="Status" sortable></vl-rich-data-field>
        <vl-pager slot="pager" total-items="0" items-per-page="${ITEMS_PER_PAGINA}" current-page="1"></vl-pager>
      </vl-rich-data-table>
    `);
    this.__bronDocumenten = [];
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

  set documenten(waarde) {
    this.__bronDocumenten = waarde || [];
    this.__huidigePagina = 1;
    this._herbereken();
  }

  get documenten() {
    return this.__bronDocumenten;
  }

  connectedCallback() {
    super.connectedCallback();

    const tabel = this.shadowRoot.querySelector('#tabel');
    if (tabel) {
      tabel.addEventListener('change', (e) => this._onTabelChange(e));
    }

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
    this.__bronDocumenten = this.__bronDocumenten.map((d) =>
      d.bestandsnaam === taak.bestandsnaam ? {
        ...d,
        status: taak.status,
      } : d,
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
      const aanwezigeStatussen = new Set(this.__bronDocumenten.map((d) => d.status));
      const opties = STATUS_OPTIES.filter((o) => o.value === '' || aanwezigeStatussen.has(o.value));
      select.options = opties;
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
            cb.addEventListener('change', () => this._dispatchSelectieGewijzigd());
            td.appendChild(cb);
          };
          break;
        case 'bestandsnaam':
          veld.renderer = (td, rij) => {
            td.style.verticalAlign = 'middle';
            td.style.width = '100%';
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
        case 'antwoorden':
          veld.renderer = (td, rij) => {
            td.style.verticalAlign = 'middle';
            td.style.textAlign = 'center';
            if (rij.antwoordenAantal != null && rij.antwoordenTotaal != null) {
              const anchorId = `antw-${rij.bestandsnaam.replace(/[^a-zA-Z0-9]/g, '-')}`;
              const span = document.createElement('span');
              span.id = anchorId;
              span.textContent = `${rij.antwoordenAantal}/${rij.antwoordenTotaal}`;
              span.style.cursor = 'default';
              td.appendChild(span);

              if (rij.kernbezwaren && rij.kernbezwaren.length > 0) {
                const popover = document.createElement('vl-popover');
                popover.setAttribute('for', anchorId);
                popover.setAttribute('trigger', 'hover focus');
                popover.setAttribute('placement', 'top');
                popover.setAttribute('content-padding', 'small');
                popover.setAttribute('hide-arrow', '');

                const lijst = document.createElement('div');
                lijst.style.fontSize = '13px';
                lijst.style.lineHeight = '1.6';
                lijst.style.whiteSpace = 'nowrap';
                lijst.style.textAlign = 'left';
                rij.kernbezwaren.forEach((kb) => {
                  const regel = document.createElement('div');
                  regel.textContent = `${kb.beantwoord ? '\u2713' : '\u2717'} ${kb.samenvatting}`;
                  regel.style.color = kb.beantwoord ? 'var(--vl-theme-fg-color, #333)' : 'var(--vl-theme-error-color, #db3434)';
                  lijst.appendChild(regel);
                });
                popover.appendChild(lijst);
                td.appendChild(popover);
              }
            } else {
              td.textContent = '';
            }
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
            pill.style.fontVariantNumeric = 'tabular-nums';
            pill.style.minWidth = '180px';
            pill.style.display = 'inline-block';

            const isActief = rij.status === 'wachtend' || rij.status === 'bezig';
            if (isActief) {
              const span = document.createElement('span');
              span.className = 'timer-tekst';
              span.textContent = this._formatStatusLabel(rij);
              pill.appendChild(span);
              pill.appendChild(this._maakPillKnop('\u00d7', 'Annuleer consolidatie', () => {
                const taakData = this.__takenData[rij.bestandsnaam];
                if (taakData && taakData.id) {
                  this.dispatchEvent(new CustomEvent('annuleer-consolidatie', {
                    detail: {bestandsnaam: rij.bestandsnaam, taakId: taakData.id},
                    bubbles: true, composed: true,
                  }));
                }
              }));
            } else if (rij.status === 'fout') {
              pill.textContent = this._formatStatusLabel(rij);
              pill.appendChild(this._maakPillKnop('\u21bb', 'Opnieuw proberen', () => {
                this.dispatchEvent(new CustomEvent('herstart-consolidatie', {
                  detail: {bestandsnaam: rij.bestandsnaam},
                  bubbles: true, composed: true,
                }));
              }));
            } else if (rij.status === 'volledig') {
              pill.textContent = this._formatStatusLabel(rij);
              pill.appendChild(this._maakPillKnop('\u25b6', 'Consolidatie starten', () => {
                this.dispatchEvent(new CustomEvent('start-consolidatie', {
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

    this._configureerStatusOpties();

    let resultaat = this._filterDocumenten(this.__bronDocumenten, this.__filters);
    resultaat = this._sorteerDocumenten(resultaat, this.__sorting);

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

  _filterDocumenten(documenten, filters) {
    return documenten.filter((d) => {
      if (filters.bestandsnaam &&
          !d.bestandsnaam.toLowerCase().includes(filters.bestandsnaam.toLowerCase())) {
        return false;
      }
      if (filters.status && d.status !== filters.status) {
        return false;
      }
      return true;
    });
  }

  _sorteerDocumenten(documenten, sorting) {
    if (!sorting || sorting.length === 0) return documenten;

    return [...documenten].sort((a, b) => {
      for (const sort of sorting) {
        let cmp = 0;
        if (sort.name === 'status') {
          cmp = (STATUS_ORDENING[a.status] ?? 99) - (STATUS_ORDENING[b.status] ?? 99);
        } else if (sort.name === 'antwoorden') {
          const fracA = (a.antwoordenTotaal ?? 0) === 0 ? 0 : (a.antwoordenAantal ?? 0) / a.antwoordenTotaal;
          const fracB = (b.antwoordenTotaal ?? 0) === 0 ? 0 : (b.antwoordenAantal ?? 0) / b.antwoordenTotaal;
          cmp = fracA - fracB;
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

  _beheerTimer() {
    const heeftActief = this.__bronDocumenten.some(
        (d) => d.status === 'wachtend' || d.status === 'bezig',
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

    this.__bronDocumenten.forEach((d) => {
      if (d.status !== 'wachtend' && d.status !== 'bezig') return;
      const cel = innerTable.querySelector(
          `.status-cel[data-bestandsnaam="${CSS.escape(d.bestandsnaam)}"]`,
      );
      if (!cel) return;
      const timerTekst = cel.querySelector('.timer-tekst');
      if (timerTekst) {
        timerTekst.textContent = this._formatStatusLabel(d, nu);
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

  _formatStatusLabel(d, nu) {
    nu = nu || Date.now();
    const taakData = this.__takenData[d.bestandsnaam];

    if (d.status === 'wachtend' && taakData && taakData.aangemaaktOp) {
      const wachtMs = nu - new Date(taakData.aangemaaktOp).getTime();
      return `Wachtend (${this._formatTijd(wachtMs)})`;
    }

    if (d.status === 'bezig' && taakData) {
      const wachtMs = taakData.verwerkingGestartOp && taakData.aangemaaktOp ?
        new Date(taakData.verwerkingGestartOp).getTime() -
            new Date(taakData.aangemaaktOp).getTime() :
        0;
      const verwerkMs = taakData.verwerkingGestartOp ?
        nu - new Date(taakData.verwerkingGestartOp).getTime() :
        0;
      return `Bezig (${this._formatTijd(wachtMs)} + ${this._formatTijd(verwerkMs)})`;
    }

    return STATUS_LABELS[d.status] || d.status;
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
      table.querySelectorAll('.rij-checkbox').forEach((rijCb) => {
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
    // kolommen: selectie(0), bestandsnaam(1), antwoorden(2), status(3)
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

defineWebComponent(BezwaarschriftenResultatenTabel, 'bezwaarschriften-resultaten-tabel');
