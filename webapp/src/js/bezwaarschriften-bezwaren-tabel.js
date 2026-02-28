import {BaseHTMLElement, defineWebComponent, registerWebComponents} from '@domg-wc/common';
import {VlTableComponent} from '@domg-wc/components/block/table/vl-table.component.js';
import {vlGlobalStyles} from '@domg-wc/styles';

registerWebComponents([VlTableComponent]);

const STATUS_LABELS = {
  'todo': 'Te verwerken',
  'extractie-klaar': 'Extractie klaar',
  'fout': 'Fout',
  'niet ondersteund': 'Niet ondersteund',
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
  }

  set bezwaren(waarde) {
    this.__bezwaren = waarde || [];
    this._renderRijen();
  }

  get bezwaren() {
    return this.__bezwaren;
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

  _renderRijen() {
    const tbody = this.shadowRoot && this.shadowRoot.querySelector('#tabel-body');
    if (!tbody) return;

    const selecteerAlles = this.shadowRoot.querySelector('#selecteer-alles');
    if (selecteerAlles) selecteerAlles.checked = false;

    if (this.__bezwaren.length === 0) {
      tbody.innerHTML = '<tr><td colspan="4">Geen bestanden gevonden</td></tr>';
      this._dispatchSelectieGewijzigd();
      return;
    }

    tbody.innerHTML = this.__bezwaren
        .map((b) => {
          const disabled = b.status === 'niet ondersteund' ? 'disabled' : '';
          const aantalBezwaren = b.aantalBezwaren != null ? b.aantalBezwaren : '';
          return `<tr>
            <td><input type="checkbox" class="rij-checkbox" data-bestandsnaam="${this._escapeHtml(b.bestandsnaam)}" ${disabled}></td>
            <td>${this._escapeHtml(b.bestandsnaam)}</td>
            <td>${aantalBezwaren}</td>
            <td>${this._formatStatus(b)}</td>
          </tr>`;
        })
        .join('');

    tbody.querySelectorAll('.rij-checkbox').forEach((cb) => {
      cb.addEventListener('change', () => this._dispatchSelectieGewijzigd());
    });

    this._dispatchSelectieGewijzigd();
  }

  _dispatchSelectieGewijzigd() {
    const geselecteerd = this.geefGeselecteerdeBestandsnamen();
    this.dispatchEvent(new CustomEvent('selectie-gewijzigd', {
      detail: {geselecteerd},
      bubbles: true,
      composed: true,
    }));
  }

  _formatStatus(b) {
    const label = STATUS_LABELS[b.status] || this._escapeHtml(b.status);
    if (b.status === 'extractie-klaar' && b.aantalWoorden != null) {
      return `${label} (${b.aantalWoorden} woorden)`;
    }
    return label;
  }

  _escapeHtml(str) {
    const div = document.createElement('div');
    div.appendChild(document.createTextNode(str));
    return div.innerHTML;
  }
}

defineWebComponent(BezwaarschriftenBezwarenTabel, 'bezwaarschriften-bezwaren-tabel');
