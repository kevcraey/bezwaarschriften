import {BaseHTMLElement, defineWebComponent, registerWebComponents} from '@domg-wc/common';
import {VlTableComponent} from '@domg-wc/components/block/table/vl-table.component.js';
import {VlPillComponent} from '@domg-wc/components/block/pill/vl-pill.component.js';
import {vlGlobalStyles} from '@domg-wc/styles';

registerWebComponents([VlTableComponent, VlPillComponent]);

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
      <style>${vlGlobalStyles}</style>
      <vl-table>
        <table>
          <thead>
            <tr>
              <th><input type="checkbox" id="selecteer-alles" title="Selecteer alles"></th>
              <th>Bestandsnaam</th>
              <th>Aantal bezwaren</th>
              <th>Status</th>
            </tr>
          </thead>
          <tbody id="tabel-body"></tbody>
        </table>
      </vl-table>
    `);
    this.__bezwaren = [];
    this.__takenData = {};
    this.__timerInterval = null;
    this._projectNaam = null;
  }

  set projectNaam(naam) {
    this._projectNaam = naam;
  }

  get projectNaam() {
    return this._projectNaam;
  }

  set bezwaren(waarde) {
    this.__bezwaren = waarde || [];
    this._renderRijen();
  }

  get bezwaren() {
    return this.__bezwaren;
  }

  werkBijMetTaakUpdate(taak) {
    this.__takenData[taak.bestandsnaam] = {
      aangemaaktOp: taak.aangemaaktOp,
      verwerkingGestartOp: taak.verwerkingGestartOp,
    };
    this.__bezwaren = this.__bezwaren.map((b) =>
      b.bestandsnaam === taak.bestandsnaam ? {
        bestandsnaam: taak.bestandsnaam,
        status: taak.status,
        aantalWoorden: taak.aantalWoorden,
        aantalBezwaren: taak.aantalBezwaren,
      } : b,
    );
    this._renderRijen();
  }

  geefGeselecteerdeBestandsnamen() {
    const checkboxes = this.shadowRoot.querySelectorAll('.rij-checkbox:checked');
    return Array.from(checkboxes).map((cb) => cb.dataset.bestandsnaam);
  }

  connectedCallback() {
    super.connectedCallback();
    this._renderRijen();

    const selecteerAlles = this.shadowRoot.querySelector('#selecteer-alles');
    if (selecteerAlles) {
      selecteerAlles.addEventListener('change', (e) => {
        const checked = e.target.checked;
        this.shadowRoot.querySelectorAll('.rij-checkbox:not([disabled])').forEach((cb) => {
          cb.checked = checked;
        });
        this._dispatchSelectieGewijzigd();
      });
    }
  }

  disconnectedCallback() {
    this._stopTimer();
  }

  _renderRijen() {
    const tbody = this.shadowRoot && this.shadowRoot.querySelector('#tabel-body');
    if (!tbody) return;

    const selecteerAlles = this.shadowRoot.querySelector('#selecteer-alles');
    if (selecteerAlles) selecteerAlles.checked = false;

    if (this.__bezwaren.length === 0) {
      tbody.innerHTML = '<tr><td colspan="4">Geen bestanden gevonden</td></tr>';
      this._dispatchSelectieGewijzigd();
      this._stopTimer();
      return;
    }

    tbody.innerHTML = this.__bezwaren
        .map((b) => {
          const disabled = this._isDisabled(b.status) ? 'disabled' : '';
          const aantalBezwaren = b.aantalBezwaren != null ? b.aantalBezwaren : '';
          return `<tr>
            <td><input type="checkbox" class="rij-checkbox" data-bestandsnaam="${this._escapeHtml(b.bestandsnaam)}" ${disabled}></td>
            <td>${this._renderBestandsnaam(b.bestandsnaam)}</td>
            <td>${aantalBezwaren}</td>
            <td class="status-cel" data-bestandsnaam="${this._escapeHtml(b.bestandsnaam)}">${this._formatStatus(b)}</td>
          </tr>`;
        })
        .join('');

    tbody.querySelectorAll('.rij-checkbox').forEach((cb) => {
      cb.addEventListener('change', () => this._dispatchSelectieGewijzigd());
    });

    this._dispatchSelectieGewijzigd();
    this._beheerTimer();
  }

  _isDisabled(status) {
    return status === 'niet ondersteund' || status === 'wachtend' || status === 'bezig';
  }

  _beheerTimer() {
    const heeftActief = this.__bezwaren.some(
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
    this.__bezwaren.forEach((b) => {
      if (b.status !== 'wachtend' && b.status !== 'bezig') return;
      const cel = this.shadowRoot.querySelector(
          `.status-cel[data-bestandsnaam="${CSS.escape(b.bestandsnaam)}"]`,
      );
      if (!cel) return;
      const pill = cel.querySelector('vl-pill');
      if (pill) {
        pill.textContent = this._formatStatusLabel(b, nu);
      }
    });
  }

  _formatStatus(b, nu) {
    nu = nu || Date.now();
    let label = this._formatStatusLabel(b, nu);

    const type = STATUS_PILL_TYPES[b.status] || '';
    const typeAttr = type ? ` data-vl-type="${type}"` : '';
    const disabledAttr = b.status === 'niet ondersteund' ? ' data-vl-disabled' : '';
    return `<vl-pill${typeAttr}${disabledAttr}>${label}</vl-pill>`;
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

  _renderBestandsnaam(bestandsnaam) {
    const escaped = this._escapeHtml(bestandsnaam);
    if (this._projectNaam) {
      const url = `/api/v1/projects/${encodeURIComponent(this._projectNaam)}/bezwaren/${encodeURIComponent(bestandsnaam)}/download`;
      return `<a href="${url}" download="${escaped}">${escaped}</a>`;
    }
    return escaped;
  }

  _escapeHtml(str) {
    const div = document.createElement('div');
    div.appendChild(document.createTextNode(str));
    return div.innerHTML;
  }
}

defineWebComponent(BezwaarschriftenBezwarenTabel, 'bezwaarschriften-bezwaren-tabel');
