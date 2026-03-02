import {BaseHTMLElement, defineWebComponent, registerWebComponents} from '@domg-wc/common';
import {VlRichDataTable} from '@domg-wc/components/block/rich-data-table/vl-rich-data-table.component.js';
import {VlRichDataField} from '@domg-wc/components/block/rich-data-table/vl-rich-data-field.component.js';
import {VlButtonComponent} from '@domg-wc/components/atom/button/vl-button.component.js';
import {VlModalComponent} from '@domg-wc/components/block/modal/vl-modal.component.js';
import {VlInputFieldComponent} from '@domg-wc/components/form/input-field/vl-input-field.component.js';
import {VlFormLabelComponent} from '@domg-wc/components/form/form-label/vl-form-label.component.js';
import {VlToasterComponent} from '@domg-wc/components/block/toaster/vl-toaster.component.js';
import {vlGlobalStyles} from '@domg-wc/styles';

registerWebComponents([
  VlRichDataTable, VlRichDataField, VlButtonComponent,
  VlModalComponent, VlInputFieldComponent, VlFormLabelComponent,
  VlToasterComponent,
]);

export class BezwaarschriftenProjectenOverzicht extends BaseHTMLElement {
  constructor() {
    super(`
      <style>
        ${vlGlobalStyles}
        .header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 2rem; }
        .header h1 { margin: 0; }
        .filter { margin-bottom: 1rem; max-width: 300px; }
        .acties-cel { display: flex; justify-content: flex-end; }
      </style>
      <div class="header">
        <h1 class="vl-title vl-title--h1">Overzicht projecten</h1>
        <vl-button id="toevoegen-knop" icon="add">Project toevoegen</vl-button>
      </div>
      <div class="filter">
        <vl-input-field id="filter-naam" type="text" name="naam" placeholder="Zoek op naam..." block></vl-input-field>
      </div>
      <vl-rich-data-table id="tabel">
        <vl-rich-data-field name="naam" label="Naam" sortable></vl-rich-data-field>
        <vl-rich-data-field name="aantalDocumenten" label="Documenten" sortable></vl-rich-data-field>
        <vl-rich-data-field name="acties" label="Acties"></vl-rich-data-field>
      </vl-rich-data-table>
      <vl-modal id="toevoegen-modal" title="Project toevoegen" closable>
        <div slot="content">
          <vl-form-label for="project-naam-invoer" label="Projectnaam"></vl-form-label>
          <vl-input-field id="project-naam-invoer" type="text" name="naam" placeholder="Projectnaam..." block></vl-input-field>
          <p id="toevoegen-fout" hidden style="color: #db3434; margin-top: 0.5rem;"></p>
        </div>
        <div slot="button">
          <vl-button id="toevoegen-bevestig-knop">Toevoegen</vl-button>
        </div>
      </vl-modal>
      <vl-modal id="verwijder-modal" title="Project verwijderen" closable>
        <div slot="content">
          <p id="verwijder-bevestiging-tekst"></p>
        </div>
        <div slot="button">
          <vl-button id="verwijder-bevestig-knop" error="">Verwijderen</vl-button>
        </div>
      </vl-modal>
      <vl-toaster id="toaster"></vl-toaster>
    `);
    this.__projecten = [];
    this.__tabelKlaar = false;
    this.__filterTekst = '';
    this.__sorting = [];
    this.__teVerwijderenProject = null;
  }

  connectedCallback() {
    super.connectedCallback();
    this._koppelEventListeners();

    customElements.whenDefined('vl-rich-data-table').then(() => {
      this.__tabelKlaar = true;
      this._configureerRenderers();
      this._laadProjecten();
    });
  }

  _configureerRenderers() {
    const velden = this.shadowRoot.querySelectorAll('vl-rich-data-field');
    velden.forEach((veld) => {
      switch (veld.getAttribute('name')) {
        case 'naam':
          veld.renderer = (td, rij) => {
            td.style.width = '100%';
            td.style.verticalAlign = 'middle';
            const a = document.createElement('a');
            a.href = `#/project/${encodeURIComponent(rij.naam)}`;
            a.textContent = rij.naam;
            td.appendChild(a);
          };
          break;
        case 'aantalDocumenten':
          veld.renderer = (td, rij) => {
            td.style.verticalAlign = 'middle';
            td.style.textAlign = 'center';
            td.textContent = rij.aantalDocumenten;
          };
          break;
        case 'acties':
          veld.renderer = (td, rij) => {
            td.style.verticalAlign = 'middle';
            const btn = document.createElement('vl-button');
            btn.setAttribute('icon', 'bin');
            btn.setAttribute('error', '');
            btn.setAttribute('ghost', '');
            btn.setAttribute('label', 'Project verwijderen');
            btn.addEventListener('vl-click', (e) => {
              e.stopPropagation();
              this._toonVerwijderModal(rij.naam, rij.aantalDocumenten);
            });
            td.appendChild(btn);
          };
          break;
      }
    });
  }

  _koppelEventListeners() {
    const tabel = this.shadowRoot.querySelector('#tabel');
    if (tabel) {
      tabel.addEventListener('change', (e) => {
        const detail = e.detail || {};
        if (detail.sorting) {
          this.__sorting = detail.sorting;
          this._toonProjecten();
        }
      });
    }

    const filterInvoer = this.shadowRoot.querySelector('#filter-naam');
    if (filterInvoer) {
      filterInvoer.addEventListener('input', (e) => {
        this.__filterTekst = e.target.value || '';
        this._toonProjecten();
      });
    }

    const toevoegenKnop = this.shadowRoot.querySelector('#toevoegen-knop');
    if (toevoegenKnop) {
      toevoegenKnop.addEventListener('vl-click', () => {
        const invoer = this.shadowRoot.querySelector('#project-naam-invoer');
        if (invoer) invoer.value = '';
        const fout = this.shadowRoot.querySelector('#toevoegen-fout');
        if (fout) fout.hidden = true;
        const modal = this.shadowRoot.querySelector('#toevoegen-modal');
        if (modal) modal.open();
      });
    }

    const toevoegenBevestig = this.shadowRoot.querySelector('#toevoegen-bevestig-knop');
    if (toevoegenBevestig) {
      toevoegenBevestig.addEventListener('vl-click', () => this._voegProjectToe());
    }

    const verwijderBevestig = this.shadowRoot.querySelector('#verwijder-bevestig-knop');
    if (verwijderBevestig) {
      verwijderBevestig.addEventListener('vl-click', () => this._verwijderProject());
    }
  }

  _toonVerwijderModal(naam, aantalDocumenten) {
    this.__teVerwijderenProject = naam;
    const tekst = this.shadowRoot.querySelector('#verwijder-bevestiging-tekst');
    if (tekst) {
      tekst.textContent = `Weet je zeker dat je project '${naam}' wilt verwijderen? ${aantalDocumenten} document(en) en bijhorende extractie-resultaten worden permanent verwijderd.`;
    }
    const modal = this.shadowRoot.querySelector('#verwijder-modal');
    if (modal) modal.open();
  }

  _laadProjecten() {
    fetch('/api/v1/projects')
        .then((response) => {
          if (!response.ok) throw new Error('Ophalen projecten mislukt');
          return response.json();
        })
        .then((data) => {
          this.__projecten = data.projecten;
          this._toonProjecten();
        })
        .catch(() => {
          this._toonToast('error', 'Projecten konden niet worden geladen.');
        });
  }

  _toonProjecten() {
    if (!this.__tabelKlaar) return;
    const tabel = this.shadowRoot.querySelector('#tabel');
    if (!tabel) return;

    let resultaat = this.__projecten;
    if (this.__filterTekst) {
      const zoek = this.__filterTekst.toLowerCase();
      resultaat = resultaat.filter((p) => p.naam.toLowerCase().includes(zoek));
    }
    resultaat = this._sorteerProjecten(resultaat, this.__sorting);

    tabel.data = {data: resultaat};
  }

  _sorteerProjecten(projecten, sorting) {
    if (!sorting || sorting.length === 0) return projecten;
    return [...projecten].sort((a, b) => {
      for (const sort of sorting) {
        let cmp = 0;
        if (sort.name === 'aantalDocumenten') {
          cmp = a.aantalDocumenten - b.aantalDocumenten;
        } else {
          cmp = String(a[sort.name] || '').localeCompare(String(b[sort.name] || ''), 'nl');
        }
        if (cmp !== 0) return sort.direction === 'asc' ? cmp : -cmp;
      }
      return 0;
    });
  }

  _voegProjectToe() {
    const invoer = this.shadowRoot.querySelector('#project-naam-invoer');
    const naam = invoer ? invoer.value.trim() : '';
    if (!naam) {
      const fout = this.shadowRoot.querySelector('#toevoegen-fout');
      if (fout) {
        fout.textContent = 'Projectnaam is verplicht.';
        fout.hidden = false;
      }
      return;
    }

    fetch('/api/v1/projects', {
      method: 'POST',
      headers: {'Content-Type': 'application/json'},
      body: JSON.stringify({naam}),
    })
        .then((response) => {
          if (!response.ok) {
            return response.json().then((data) => {
              throw new Error(data.messages?.[0]?.parameters?.message || 'Aanmaken mislukt');
            });
          }
          const modal = this.shadowRoot.querySelector('#toevoegen-modal');
          if (modal) modal.close();
          this._toonToast('success', `Project '${naam}' aangemaakt.`);
          this._laadProjecten();
        })
        .catch((err) => {
          const fout = this.shadowRoot.querySelector('#toevoegen-fout');
          if (fout) {
            fout.textContent = err.message;
            fout.hidden = false;
          }
        });
  }

  _verwijderProject() {
    if (!this.__teVerwijderenProject) return;
    const naam = this.__teVerwijderenProject;

    fetch(`/api/v1/projects/${encodeURIComponent(naam)}`, {
      method: 'DELETE',
    })
        .then((response) => {
          if (!response.ok) throw new Error('Verwijderen mislukt');
          const modal = this.shadowRoot.querySelector('#verwijder-modal');
          if (modal) modal.close();
          this.__teVerwijderenProject = null;
          this._toonToast('success', `Project '${naam}' verwijderd.`);
          this._laadProjecten();
        })
        .catch(() => {
          this._toonToast('error', 'Verwijderen mislukt.');
        });
  }

  _toonToast(type, bericht) {
    const toaster = this.shadowRoot.querySelector('#toaster');
    if (!toaster) return;
    const alert = document.createElement('vl-alert');
    alert.setAttribute('type', type);
    alert.setAttribute('icon', type === 'success' ? 'check' : 'warning');
    alert.setAttribute('message', bericht);
    alert.setAttribute('closable', '');
    toaster.show(alert);
    if (type === 'success') {
      setTimeout(() => alert.remove(), 5000);
    }
  }
}

defineWebComponent(BezwaarschriftenProjectenOverzicht, 'bezwaarschriften-projecten-overzicht');
