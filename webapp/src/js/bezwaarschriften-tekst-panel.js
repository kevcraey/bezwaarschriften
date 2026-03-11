import {LitElement, html, css} from 'lit';
import {registerWebComponents} from '@domg-wc/common';
import {VlButtonComponent} from '@domg-wc/components/atom/button/vl-button.component.js';
import {VlAlertComponent} from '@domg-wc/components/block/alert/vl-alert.component.js';
import {VlLoaderComponent} from '@domg-wc/components/block/loader/vl-loader.component.js';

registerWebComponents([VlButtonComponent, VlAlertComponent, VlLoaderComponent]);

/**
 * Linkse side-sheet die de geextraheerde tekst van een bezwaarschrift toont.
 *
 * Bewuste afwijking van het design system: vl-side-sheet ondersteunt geen
 * left-positionering, daarom gebruiken we custom CSS voor het linkse panel.
 */
class BezwaarschriftenTekstPanel extends LitElement {
  static get properties() {
    return {
      __open: {state: true},
      __bestandsnaam: {state: true},
      __tekst: {state: true},
      __laden: {state: true},
      __fout: {state: true},
    };
  }

  static get styles() {
    return css`
      :host {
        display: block;
      }

      .tekst-panel {
        position: fixed;
        top: 0;
        left: 0;
        width: 33.3%;
        height: 100vh;
        background: white;
        box-shadow: 2px 0 8px rgba(0, 0, 0, 0.15);
        z-index: 100;
        display: flex;
        flex-direction: column;
        transform: translateX(-100%);
        transition: transform 0.3s ease;
      }

      .tekst-panel.open {
        transform: translateX(0);
      }

      .panel-header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        padding: var(--vl-spacing--xsmall);
        border-bottom: 1px solid #e8ebee;
        min-height: 3.5rem;
      }

      .panel-header h3 {
        margin: 0;
        font-size: 1.1rem;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
        flex: 1;
        margin-right: var(--vl-spacing--xsmall);
      }

      .panel-body {
        flex: 1;
        overflow-y: auto;
        padding: var(--vl-spacing--xsmall);
        white-space: pre-wrap;
        font-family: inherit;
        line-height: 1.6;
      }
    `;
  }

  constructor() {
    super();
    this.__open = false;
    this.__bestandsnaam = '';
    this.__tekst = '';
    this.__laden = false;
    this.__fout = null;
  }

  open(projectNaam, bestandsnaam) {
    this.__bestandsnaam = bestandsnaam;
    this.__tekst = '';
    this.__fout = null;
    this.__laden = true;
    this.__open = true;

    this.dispatchEvent(new CustomEvent('tekst-panel-geopend', {
      bubbles: true, composed: true,
    }));

    fetch(`/api/v1/projects/${encodeURIComponent(projectNaam)}/tekst-extracties/${encodeURIComponent(bestandsnaam)}/tekst`)
        .then((response) => {
          if (!response.ok) throw new Error('Tekst ophalen mislukt');
          return response.json();
        })
        .then((data) => {
          this.__tekst = data.tekst;
          this.__laden = false;
        })
        .catch((error) => {
          this.__fout = error.message;
          this.__laden = false;
        });
  }

  sluit() {
    this.__open = false;
    this.dispatchEvent(new CustomEvent('tekst-panel-gesloten', {
      bubbles: true, composed: true,
    }));
  }

  get isOpen() {
    return this.__open;
  }

  render() {
    return html`
      <div class="tekst-panel ${this.__open ? 'open' : ''}">
        <div class="panel-header">
          <h3 title="${this.__bestandsnaam}">${this.__bestandsnaam}</h3>
          <vl-button
            icon="close"
            ghost=""
            label="Sluiten"
            @click="${this.sluit}">
          </vl-button>
        </div>
        <div class="panel-body">
          ${this.__laden ? html`<vl-loader></vl-loader>` : ''}
          ${this.__fout ? html`<vl-alert type="error" title="Fout">${this.__fout}</vl-alert>` : ''}
          ${!this.__laden && !this.__fout ? this.__tekst : ''}
        </div>
      </div>
    `;
  }
}

customElements.define('bezwaarschriften-tekst-panel', BezwaarschriftenTekstPanel);
export {BezwaarschriftenTekstPanel};
