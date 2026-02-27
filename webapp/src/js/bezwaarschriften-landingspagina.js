import {
  BaseHTMLElement,
  defineWebComponent,
  registerWebComponents,
} from '@domg-wc/common';
import {VlContentHeaderComponent} from '@domg-wc/components/block/content-header/vl-content-header.component.js';
import {VlTemplate} from '@domg-wc/components/block/template/vl-template.component.js';
import {VlTypography} from '@domg-wc/components/block/typography/vl-typography.component.js';
import {vlGlobalStyles, vlGridStyles} from '@domg-wc/styles';
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
                  <div class="vl-column vl-column--8 vl-column--10--s vl-column--12--xs">
                   <vl-typography>
                    <h1 class="vl-title vl-title--h1" style="margin-bottom: 3rem">Bezwaarschriften</h1>
                    <p class="vl-introduction">
                        Welkom op de toepassing Bezwaarschriften. Hier kan u bezwaarschriften automatisch laten verwerken.
                    </p>
                   </vl-typography>
                  </div>
                  <div class="vl-column vl-column--2 vl-column--1--s"></div>
                </div>
              </div>  
            </section>
          </div>
        </vl-template>
    `);
    this.__addVlElementStyleSheetsToDocument();
  }

  __addVlElementStyleSheetsToDocument() {
    document.adoptedStyleSheets = [
      ...vlGlobalStyles.map((style) => style.styleSheet),
      ...vlGridStyles.map((style) => style.styleSheet),
    ];
  }
}

defineWebComponent(
    BezwaarschriftenLandingspagina,
    'bezwaarschriften-landingspagina',
);
