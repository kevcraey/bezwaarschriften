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
              <th>Bestandsnaam</th>
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

  connectedCallback() {
    super.connectedCallback();
    this._renderRijen();
  }

  _renderRijen() {
    const tbody = this.shadowRoot && this.shadowRoot.querySelector('#tabel-body');
    if (!tbody) return;

    if (this.__bezwaren.length === 0) {
      tbody.innerHTML = '<tr><td colspan="2">Geen bestanden gevonden</td></tr>';
      return;
    }

    tbody.innerHTML = this.__bezwaren
        .map((b) => `<tr>
          <td>${this._escapeHtml(b.bestandsnaam)}</td>
          <td>${this._formatStatus(b)}</td>
        </tr>`)
        .join('');
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
