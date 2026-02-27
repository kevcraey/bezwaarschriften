import {define, BaseElementOfType, registerWebComponents} from '@domg-wc/common-utilities';
import {VlContentHeaderComponent, VlTemplate, VlTypography} from '@domg-wc/components';
import {vlElementsStyle, VlGridElement, VlIntroductionElement} from '@domg-wc/elements';
registerWebComponents([VlTemplate, VlContentHeaderComponent, VlTypography, VlGridElement, VlIntroductionElement]);

export class JurisLandingspagina extends BaseElementOfType(HTMLElement) {
  constructor() {
    super(`
        <style>
          ${vlElementsStyle}
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
              <a slot="context-link" href="/">JURIS</a>
            </vl-content-header>
            <section is="vl-region">
              <div is="vl-layout">
                <div is="vl-grid">
                  <div is="vl-column">
                   <vl-typography>
                    <h1 is="vl-h1" style="margin-bottom: 3rem">Hello world from JURIS!</h1>
                    </vl-typography>
                  </div>
                  <div is="vl-column">
                    <p is="vl-introduction">
                        This page was constructed using a library of web components that we offer. 
                        It is build upon the design system of Digitaal Vlaanderen.
                        You can find the documentation <a href="https://milieuinfo.github.io/uig-pages/" target="_blank">here</a>.
                    </p>
                  </div>
                </div>
              </div>  
            </section>
          </div>
        </vl-template>
    `);
    this.__addVlElementStyleSheetsToDocument();
  }

  __addVlElementStyleSheetsToDocument() {
    document.adoptedStyleSheets = [...vlElementsStyle.map((style) => style.styleSheet)];
  }
}

define('juris-landingspagina', JurisLandingspagina);
