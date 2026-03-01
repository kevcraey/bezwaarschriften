import {
  BaseHTMLElement,
  defineWebComponent,
  registerWebComponents,
} from '@domg-wc/common';
import {VlContentHeaderComponent} from '@domg-wc/components/block/content-header/vl-content-header.component.js';
import {VlTemplate} from '@domg-wc/components/block/template/vl-template.component.js';
import {VlTypography} from '@domg-wc/components/block/typography/vl-typography.component.js';
import {vlGlobalStyles, vlGridStyles} from '@domg-wc/styles';
import './bezwaarschriften-projecten-overzicht.js';
import './bezwaarschriften-project-selectie.js';

registerWebComponents([VlTemplate, VlContentHeaderComponent, VlTypography]);

export class BezwaarschriftenLandingspagina extends BaseHTMLElement {
  constructor() {
    super(`
        <style>
          ${vlGlobalStyles}
          ${vlGridStyles}
        </style>
        <vl-template>
          <div slot="main">
            <vl-content-header>
              <img
                is="vl-image"
                slot="image"
                sizes="100vw"
                src="/img/banner.jpg"
              />
              <a slot="context-link" href="/">BEZWAARSCHRIFTEN</a>
            </vl-content-header>
            <section class="vl-region">
              <div class="vl-layout">
                <div class="vl-grid">
                  <div class="vl-column vl-column--2 vl-column--1--s"></div>
                  <div id="pagina-inhoud" class="vl-column vl-column--8 vl-column--10--s vl-column--12--xs">
                  </div>
                  <div class="vl-column vl-column--2 vl-column--1--s"></div>
                </div>
              </div>
            </section>
          </div>
        </vl-template>
    `);
    this.__addVlElementStyleSheetsToDocument();
    this._onHashChange = this._onHashChange.bind(this);
  }

  __addVlElementStyleSheetsToDocument() {
    document.adoptedStyleSheets = [
      ...vlGlobalStyles.map((style) => style.styleSheet),
      ...vlGridStyles.map((style) => style.styleSheet),
    ];
  }

  connectedCallback() {
    super.connectedCallback();
    window.addEventListener('hashchange', this._onHashChange);
    this._routeer();
  }

  disconnectedCallback() {
    window.removeEventListener('hashchange', this._onHashChange);
  }

  _onHashChange() {
    this._routeer();
  }

  _routeer() {
    const hash = window.location.hash.replace(/^#\/?/, '');
    const container = this.shadowRoot.querySelector('#pagina-inhoud');
    if (!container) return;

    container.innerHTML = '';

    if (hash.startsWith('project/')) {
      const projectNaam = decodeURIComponent(hash.substring('project/'.length));
      this._toonProjectDetail(container, projectNaam);
    } else {
      this._toonProjectenOverzicht(container);
    }
  }

  _toonProjectenOverzicht(container) {
    const typography = document.createElement('vl-typography');
    const h1 = document.createElement('h1');
    h1.className = 'vl-title vl-title--h1';
    h1.style.marginBottom = '3rem';
    h1.textContent = 'Bezwaarschriften';
    typography.appendChild(h1);
    const p = document.createElement('p');
    p.className = 'vl-introduction';
    p.textContent = 'Welkom op de toepassing Bezwaarschriften. Hier kan u bezwaarschriften automatisch laten verwerken.';
    typography.appendChild(p);
    container.appendChild(typography);

    const overzicht = document.createElement('bezwaarschriften-projecten-overzicht');
    container.appendChild(overzicht);
  }

  _toonProjectDetail(container, projectNaam) {
    const typography = document.createElement('vl-typography');
    const h1 = document.createElement('h1');
    h1.className = 'vl-title vl-title--h1';
    h1.style.marginBottom = '3rem';
    h1.textContent = projectNaam;
    typography.appendChild(h1);
    container.appendChild(typography);

    const selectie = document.createElement('bezwaarschriften-project-selectie');
    selectie.projectNaam = projectNaam;
    container.appendChild(selectie);
  }
}

defineWebComponent(
    BezwaarschriftenLandingspagina,
    'bezwaarschriften-landingspagina',
);
