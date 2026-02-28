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
      <style>
        ${vlGlobalStyles}
        .extractie-knop {
          border: none;
          background: none;
          cursor: pointer;
          font-size: 1.2em;
          padding: 4px 8px;
        }
        .extractie-knop:disabled {
          opacity: 0.3;
          cursor: not-allowed;
        }
      </style>
      <vl-table>
        <table>
          <thead>
            <tr>
              <th>Bestandsnaam</th>
              <th>Aantal bezwaren</th>
              <th>Status</th>
              <th>Acties</th>
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

  connectedCallback() {
    super.connectedCallback();
    this._renderRijen();
  }

  _renderRijen() {
    const tbody = this.shadowRoot && this.shadowRoot.querySelector('#tabel-body');
    if (!tbody) return;

    if (this.__bezwaren.length === 0) {
      tbody.innerHTML = '<tr><td colspan="4">Geen bestanden gevonden</td></tr>';
      return;
    }

    tbody.innerHTML = this.__bezwaren
        .map((b) => {
          const disabled = b.status === 'niet ondersteund' ? 'disabled' : '';
          const aantalBezwaren = b.aantalBezwaren != null ? b.aantalBezwaren : '';
          return `<tr>
            <td>${this._escapeHtml(b.bestandsnaam)}</td>
            <td>${aantalBezwaren}</td>
            <td>${this._formatStatus(b)}</td>
            <td>
              <button class="extractie-knop" data-bestandsnaam="${this._escapeHtml(b.bestandsnaam)}" ${disabled}
                title="Extraheer bezwaren">&#128269;</button>
            </td>
          </tr>`;
        })
        .join('');

    tbody.querySelectorAll('.extractie-knop:not([disabled])').forEach((knop) => {
      knop.addEventListener('click', (e) => {
        const bestandsnaam = e.target.dataset.bestandsnaam;
        this.dispatchEvent(new CustomEvent('extraheer-bezwaar', {
          detail: {bestandsnaam},
          bubbles: true,
          composed: true,
        }));
      });
    });
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
