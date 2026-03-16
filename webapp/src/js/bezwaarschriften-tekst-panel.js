import {BaseHTMLElement, registerWebComponents} from '@domg-wc/common';
import {VlButtonComponent} from '@domg-wc/components/atom/button/vl-button.component.js';
import {VlAlert} from '@domg-wc/components/block/alert/vl-alert.component.js';
import {VlLoaderComponent} from '@domg-wc/components/block/loader/vl-loader.component.js';
import {VlSideSheet} from '@domg-wc/components/block/side-sheet/vl-side-sheet.component.js';
import {vlGlobalStyles} from '@domg-wc/styles';

registerWebComponents([VlButtonComponent, VlAlert, VlLoaderComponent, VlSideSheet]);

/**
 * Linkse side-sheet die de geëxtraheerde tekst van een bezwaarschrift toont.
 * Gebruikt vl-side-sheet met left-attribuut, zelfde opzet als de rechter side-sheets.
 */
class BezwaarschriftenTekstPanel extends BaseHTMLElement {
  constructor() {
    super(`
      <style>
        ${vlGlobalStyles}
        :host { display: block; }
        #tekst-side-sheet {
          --vl-spacing--medium: 0;
          --vl-spacing--large: 0;
          --vl-spacing--small: 0;
          --vl-spacing--normal: 0;
          --vl-page--max-width: 0;
          --vl-page--min-width: 0;
          --vl-page--max-width-wide: 100%;
        }
        .side-sheet-wrapper {
          display: flex;
          flex-direction: column;
          height: calc(100vh - 43px);
          padding: 0;
          overflow: hidden;
        }
        .side-sheet-header {
          display: flex;
          justify-content: space-between;
          align-items: center;
          padding: var(--vl-spacing--xsmall);
          border-bottom: 1px solid #e8ebee;
          min-height: 3.5rem;
          flex-shrink: 0;
          background: white;
        }
        .side-sheet-header h2 {
          margin: 0;
          font-size: 1.1rem;
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
          flex: 1;
          margin-right: var(--vl-spacing--xsmall);
        }
        .side-sheet-body {
          flex: 1;
          overflow-y: auto;
          padding: 1rem 1.5rem;
          white-space: pre-wrap;
          font-family: inherit;
          line-height: 1.6;
        }
      </style>
      <vl-side-sheet id="tekst-side-sheet" left hide-toggle-button>
        <div class="side-sheet-wrapper">
          <div class="side-sheet-header">
            <h2 id="tekst-titel"></h2>
            <vl-button id="tekst-sluit" icon="close" ghost label="Sluiten"></vl-button>
          </div>
          <div id="tekst-inhoud" class="side-sheet-body"></div>
        </div>
      </vl-side-sheet>
    `);
  }

  connectedCallback() {
    super.connectedCallback();

    const sluitKnop = this.shadowRoot.querySelector('#tekst-sluit');
    const sideSheet = this.shadowRoot.querySelector('#tekst-side-sheet');
    if (sluitKnop && sideSheet) {
      sluitKnop.addEventListener('vl-click', () => {
        sideSheet.close();
        this.dispatchEvent(new CustomEvent('tekst-panel-gesloten', {
          bubbles: true, composed: true,
        }));
      });
    }
  }

  open(projectNaam, bestandsnaam) {
    const sideSheet = this.shadowRoot.querySelector('#tekst-side-sheet');
    const titelEl = this.shadowRoot.querySelector('#tekst-titel');
    const inhoudEl = this.shadowRoot.querySelector('#tekst-inhoud');
    if (!sideSheet || !inhoudEl) return;

    if (titelEl) {
      titelEl.textContent = bestandsnaam;
      titelEl.title = bestandsnaam;
    }
    inhoudEl.innerHTML = '<vl-loader></vl-loader>';
    sideSheet.open();

    this.dispatchEvent(new CustomEvent('tekst-panel-geopend', {
      bubbles: true, composed: true,
    }));

    fetch(`/api/v1/projects/${encodeURIComponent(projectNaam)}/tekst-extracties/${encodeURIComponent(bestandsnaam)}/tekst`)
        .then((response) => {
          if (!response.ok) throw new Error('Tekst ophalen mislukt');
          return response.json();
        })
        .then((data) => {
          inhoudEl.textContent = data.tekst;
        })
        .catch((error) => {
          inhoudEl.innerHTML = `<vl-alert type="error" title="Fout">${error.message}</vl-alert>`;
        });
  }

  sluit() {
    const sideSheet = this.shadowRoot.querySelector('#tekst-side-sheet');
    if (sideSheet) sideSheet.close();
    this.dispatchEvent(new CustomEvent('tekst-panel-gesloten', {
      bubbles: true, composed: true,
    }));
  }

  get isOpen() {
    const sideSheet = this.shadowRoot.querySelector('#tekst-side-sheet');
    return sideSheet ? sideSheet.hasAttribute('open') : false;
  }
}

customElements.define('bezwaarschriften-tekst-panel', BezwaarschriftenTekstPanel);
export {BezwaarschriftenTekstPanel};
