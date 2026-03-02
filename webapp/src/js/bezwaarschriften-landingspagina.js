import {
  BaseHTMLElement,
  defineWebComponent,
  registerWebComponents,
} from '@domg-wc/common';
import {VlContentHeaderComponent} from '@domg-wc/components/block/content-header/vl-content-header.component.js';
import {VlFunctionalHeaderComponent} from '@domg-wc/components/block/functional-header/vl-functional-header.component.js';
import {VlTemplate} from '@domg-wc/components/block/template/vl-template.component.js';
import {VlTypography} from '@domg-wc/components/block/typography/vl-typography.component.js';
import {vlGlobalStyles} from '@domg-wc/styles';
import './bezwaarschriften-projecten-overzicht.js';
import './bezwaarschriften-project-selectie.js';

registerWebComponents([VlTemplate, VlContentHeaderComponent, VlFunctionalHeaderComponent, VlTypography]);

export class BezwaarschriftenLandingspagina extends BaseHTMLElement {
  constructor() {
    super(`
        <style>
          ${vlGlobalStyles}
        </style>
        <vl-template>
          <div slot="main">
            <vl-content-header id="content-header">
              <img
                is="vl-image"
                slot="image"
                sizes="100vw"
                src="/img/banner.jpg"
              />
              <a slot="context-link" href="/">BEZWAARSCHRIFTEN</a>
            </vl-content-header>
            <div id="functional-header-container"></div>
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
    const contentHeader = this.shadowRoot.querySelector('#content-header');
    const functionalHeaderContainer = this.shadowRoot.querySelector('#functional-header-container');
    const container = this.shadowRoot.querySelector('#pagina-inhoud');
    if (!container) return;

    functionalHeaderContainer.innerHTML = '';
    container.innerHTML = '';

    if (hash.startsWith('project/')) {
      const projectNaam = decodeURIComponent(hash.substring('project/'.length));
      contentHeader.hidden = true;
      this._toonFunctionalHeader(functionalHeaderContainer, projectNaam);
      this._toonProjectDetail(container, projectNaam);
    } else {
      contentHeader.hidden = false;
      this._toonProjectenOverzicht(container);
    }
  }

  _toonFunctionalHeader(container, projectNaam) {
    const header = document.createElement('vl-functional-header');
    header.setAttribute('title', projectNaam);
    header.setAttribute('back', 'Terug naar overzicht');
    header.setAttribute('back-link', '#/');
    container.appendChild(header);
  }

  _toonProjectenOverzicht(container) {
    const overzicht = document.createElement('bezwaarschriften-projecten-overzicht');
    container.appendChild(overzicht);
  }

  _toonProjectDetail(container, projectNaam) {
    const selectie = document.createElement('bezwaarschriften-project-selectie');
    selectie.projectNaam = projectNaam;
    container.appendChild(selectie);
  }
}

defineWebComponent(
    BezwaarschriftenLandingspagina,
    'bezwaarschriften-landingspagina',
);
