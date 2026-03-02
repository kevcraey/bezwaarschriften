import {BaseHTMLElement, defineWebComponent, registerWebComponents} from '@domg-wc/common';
import {VlButtonComponent} from '@domg-wc/components/atom/button/vl-button.component.js';
import {VlAccordionComponent} from '@domg-wc/components/block/accordion/vl-accordion.component.js';
import {VlModalComponent} from '@domg-wc/components/block/modal/vl-modal.component.js';
import {VlSideSheet} from '@domg-wc/components/block/side-sheet/vl-side-sheet.component.js';
import {vlGlobalStyles, vlGridStyles} from '@domg-wc/styles';
import '@domg-wc/components/form/textarea-rich';

registerWebComponents([VlButtonComponent, VlAccordionComponent, VlModalComponent, VlSideSheet]);

export class BezwaarschriftenKernbezwaren extends BaseHTMLElement {
  constructor() {
    super(`
      <style>
        ${vlGlobalStyles}
        ${vlGridStyles}
        :host { display: block; padding: 1.5rem; transition: margin-right 0.2s ease; }
        :host(.side-sheet-open) { margin-right: 33.3%; }
        .kernbezwaar-item {
          display: flex;
          flex-wrap: wrap;
          justify-content: space-between;
          align-items: flex-start;
          padding: 1rem 0;
          border-bottom: 1px solid #e8ebee;
        }
        .kernbezwaar-item:last-child { border-bottom: none; }
        .kernbezwaar-samenvatting { flex: 1; margin-right: 1rem; }
        .kernbezwaar-actie { white-space: nowrap; }
        .passage-item {
          margin-bottom: 1.5rem;
          padding-bottom: 1rem;
          border-bottom: 1px solid #e8ebee;
        }
        .passage-item:last-child { border-bottom: none; }
        .passage-bestandsnaam {
          font-size: 0.85rem;
          color: #687483;
          margin-bottom: 0.25rem;
          text-decoration: none;
          cursor: pointer;
        }
        .passage-bestandsnaam:hover {
          text-decoration: underline;
          color: #0055cc;
        }
        .passage-tekst {
          font-style: italic;
          line-height: 1.5;
        }
        .lege-staat {
          padding: 2rem;
          text-align: center;
          color: #687483;
        }
        .side-sheet-wrapper {
          display: flex;
          flex-direction: column;
          height: calc(100vh - 43px);
          margin: -1.5rem;
          padding: 0;
          overflow: hidden;
        }
        .side-sheet-header {
          display: flex;
          justify-content: space-between;
          align-items: flex-start;
          padding: 1rem 1.5rem;
          border-bottom: 2px solid #e8ebee;
          flex-shrink: 0;
          background: white;
        }
        .side-sheet-titel {
          font-weight: bold;
          flex: 1;
          margin-right: 1rem;
        }
        .side-sheet-sluit-knop {
          background: none;
          border: none;
          font-size: 1.5rem;
          cursor: pointer;
          padding: 0;
          color: #333;
          line-height: 1;
          flex-shrink: 0;
        }
        .side-sheet-sluit-knop:hover {
          color: #000;
        }
        .side-sheet-body {
          flex: 1;
          overflow-y: auto;
          padding: 1rem 1.5rem;
        }
        .antwoord-editor-wrapper {
          width: 100%;
          margin-top: 0.75rem;
          padding-top: 0.75rem;
          border-top: 1px solid #e8ebee;
        }
        .antwoord-opslaan-rij {
          margin-top: 0.5rem;
          display: flex;
          gap: 0.5rem;
          align-items: center;
        }
        .antwoord-opslaan-melding {
          font-size: 0.85rem;
          color: #0e7c26;
        }
      </style>
      <div id="inhoud"></div>
      <vl-side-sheet id="side-sheet" hide-toggle-button>
        <div class="side-sheet-wrapper">
          <div class="side-sheet-header">
            <div id="side-sheet-titel" class="side-sheet-titel"></div>
            <button id="side-sheet-sluit-knop" class="side-sheet-sluit-knop"
                aria-label="Sluiten">&times;</button>
          </div>
          <div id="side-sheet-inhoud" class="side-sheet-body"></div>
        </div>
      </vl-side-sheet>
      <vl-modal id="consolidatie-waarschuwing" title="Antwoordbrieven worden ongeldig" closable not-auto-closable>
        <div slot="content" id="consolidatie-waarschuwing-inhoud"></div>
        <vl-button slot="button" id="consolidatie-waarschuwing-bevestig" error="">Doorgaan en verwijderen</vl-button>
      </vl-modal>
    `);
    this._projectNaam = null;
    this._aantalBezwaren = 0;
    this._extractieKlaar = false;
    this._themas = null;
    this._bezig = false;
  }

  connectedCallback() {
    super.connectedCallback();
    const sluitKnop = this.shadowRoot.querySelector('#side-sheet-sluit-knop');
    const sideSheet = this.shadowRoot.querySelector('#side-sheet');
    if (sluitKnop && sideSheet) {
      sluitKnop.addEventListener('click', () => {
        sideSheet.close();
        this.classList.remove('side-sheet-open');
      });
    }
  }

  // Public methods called by parent
  laadKernbezwaren(projectNaam) {
    this._projectNaam = projectNaam;
    fetch(`/api/v1/projects/${encodeURIComponent(projectNaam)}/kernbezwaren`)
        .then((response) => {
          if (response.status === 404) {
            this._themas = null;
            this._renderInhoud();
            return null;
          }
          if (!response.ok) throw new Error('Ophalen kernbezwaren mislukt');
          return response.json();
        })
        .then((data) => {
          if (data) {
            this._themas = data.themas;
            this._renderInhoud();
          }
        })
        .catch(() => {
          this._themas = null;
          this._renderInhoud();
        });
  }

  setAantalBezwaren(aantal) {
    this._aantalBezwaren = aantal;
    if (!this._themas) this._renderInhoud();
  }

  setExtractieKlaar(klaar) {
    this._extractieKlaar = klaar;
    if (!this._themas) this._renderInhoud();
  }

  reset() {
    this._themas = null;
    this._renderInhoud();
  }

  // Private rendering
  _renderInhoud() {
    const inhoud = this.shadowRoot.querySelector('#inhoud');
    if (!inhoud) return;

    if (this._themas) {
      this._renderThemas(inhoud);
      return;
    }

    if (!this._extractieKlaar || this._aantalBezwaren === 0) {
      inhoud.innerHTML = `
        <div class="lege-staat">
          <p>Nog geen bezwaren ge\u00EBxtraheerd. Verwerk eerst documenten op de Documenten-tab.</p>
        </div>`;
      return;
    }

    inhoud.innerHTML = `
      <div class="lege-staat">
        <p>${this._aantalBezwaren} individuele bezwaren gevonden.
           Voer groepering tot thema's en kernbezwaren uit om verder te gaan.</p>
        <vl-button id="groepeer-knop">Groepeer bezwaren</vl-button>
      </div>`;

    const knop = inhoud.querySelector('#groepeer-knop');
    if (knop) {
      knop.addEventListener('vl-click', () => this._groepeer());
    }
  }

  _groepeer() {
    if (this._bezig || !this._projectNaam) return;
    this._bezig = true;

    const knop = this.shadowRoot.querySelector('#groepeer-knop');
    if (knop) {
      knop.setAttribute('disabled', '');
      knop.textContent = 'Bezig met groeperen...';
    }

    fetch(`/api/v1/projects/${encodeURIComponent(this._projectNaam)}/kernbezwaren/groepeer`, {
      method: 'POST',
    })
        .then((response) => {
          if (!response.ok) throw new Error('Groepering mislukt');
          return response.json();
        })
        .then((data) => {
          this._themas = data.themas;
          this._renderInhoud();
        })
        .catch(() => {
          if (knop) {
            knop.removeAttribute('disabled');
            knop.textContent = 'Groepeer bezwaren';
          }
        })
        .finally(() => {
          this._bezig = false;
        });
  }

  _maakOpnieuwKnop() {
    const knop = document.createElement('vl-button');
    knop.setAttribute('secondary', '');
    knop.textContent = 'Opnieuw verwerken';
    knop.addEventListener('vl-click', () => this._groepeer());
    return knop;
  }

  _renderThemas(inhoud) {
    inhoud.innerHTML = '';

    const knopBoven = this._maakOpnieuwKnop();
    knopBoven.style.marginBottom = '1.5rem';
    inhoud.appendChild(knopBoven);

    this._themas.forEach((thema) => {
      const accordion = document.createElement('vl-accordion');
      const aantalKern = thema.kernbezwaren.length;
      const label = aantalKern === 1 ? '1 kernbezwaar' : `${aantalKern} kernbezwaren`;
      accordion.setAttribute('toggle-text', `${thema.naam} (${label})`);
      accordion.setAttribute('default-open', '');

      const wrapper = document.createElement('div');
      thema.kernbezwaren.forEach((kern) => {
        const item = document.createElement('div');
        item.className = 'kernbezwaar-item';

        const samenvatting = document.createElement('div');
        samenvatting.className = 'kernbezwaar-samenvatting';
        samenvatting.textContent = kern.antwoord ? `\u2714 ${kern.samenvatting}` : kern.samenvatting;
        if (kern.antwoord) samenvatting.style.color = '#0e7c3a';

        const penKnop = document.createElement('vl-button');
        penKnop.setAttribute('tertiary', '');
        penKnop.setAttribute('icon', 'pencil');
        if (kern.antwoord) {
          penKnop.classList.add('heeft-antwoord');
        }
        penKnop.addEventListener('vl-click', () => this._toggleEditor(item, kern, penKnop));

        const actie = document.createElement('div');
        actie.className = 'kernbezwaar-actie';
        const knop = document.createElement('vl-button');
        knop.setAttribute('tertiary', '');
        knop.setAttribute('icon', 'search');
        knop.textContent = `(${kern.individueleBezwaren.length})`;
        knop.addEventListener('vl-click', () => this._toonPassages(kern));

        actie.appendChild(knop);
        actie.appendChild(penKnop);
        item.appendChild(samenvatting);
        item.appendChild(actie);
        wrapper.appendChild(item);
      });

      accordion.appendChild(wrapper);
      inhoud.appendChild(accordion);
    });

    const knopOnder = this._maakOpnieuwKnop();
    knopOnder.style.marginTop = '1.5rem';
    inhoud.appendChild(knopOnder);

    this._dispatchVoortgang();
  }

  _toggleEditor(item, kern, penKnop) {
    const bestaandeEditor = item.querySelector('.antwoord-editor-wrapper');
    if (bestaandeEditor) {
      // Sluit editor
      const textarea = bestaandeEditor.querySelector('vl-textarea-rich');
      const huidigeWaarde = textarea ? textarea.value : '';
      const origineleWaarde = kern.antwoord || '';
      if (huidigeWaarde !== origineleWaarde &&
          !confirm('Je hebt onopgeslagen wijzigingen. Wil je afsluiten?')) {
        return;
      }
      bestaandeEditor.remove();
      penKnop.setAttribute('icon', 'pencil');
      return;
    }

    // Open editor
    penKnop.setAttribute('icon', 'close');

    const wrapper = document.createElement('div');
    wrapper.className = 'antwoord-editor-wrapper';

    const textarea = document.createElement('vl-textarea-rich');
    textarea.setAttribute('block', '');
    textarea.setAttribute('rows', '8');
    textarea.setAttribute('label', 'Antwoord op kernbezwaar');
    if (kern.antwoord) {
      textarea.setAttribute('value', kern.antwoord);
    }

    const opslaanRij = document.createElement('div');
    opslaanRij.className = 'antwoord-opslaan-rij';

    const opslaanKnop = document.createElement('vl-button');
    opslaanKnop.textContent = 'Opslaan';
    opslaanKnop.addEventListener('vl-click', () =>
      this._slaAntwoordOp(kern, textarea, opslaanRij));

    opslaanRij.appendChild(opslaanKnop);
    wrapper.appendChild(textarea);
    wrapper.appendChild(opslaanRij);
    item.appendChild(wrapper);
  }

  _slaAntwoordOp(kern, textarea, opslaanRij, bevestigd = false) {
    const inhoud = textarea.value;
    const isLeeg = !inhoud || !inhoud.trim();

    const opslaanKnop = opslaanRij.querySelector('vl-button');
    if (opslaanKnop) opslaanKnop.setAttribute('disabled', '');

    const basisUrl = `/api/v1/projects/${encodeURIComponent(this._projectNaam)}/kernbezwaren/${kern.id}/antwoord`;
    const url = bevestigd ? `${basisUrl}?bevestigd=true` : basisUrl;

    fetch(url, {
      method: 'PUT',
      headers: {'Content-Type': 'application/json'},
      body: JSON.stringify({inhoud: isLeeg ? '' : inhoud}),
    })
        .then((response) => {
          if (response.status === 409) {
            return response.json().then((data) => {
              this._toonConsolidatieWaarschuwing(
                  data.getroffenDocumenten || [],
                  () => this._slaAntwoordOp(kern, textarea, opslaanRij, true),
                  () => {
                    if (opslaanKnop) opslaanKnop.removeAttribute('disabled');
                  });
              return null;
            });
          }
          if (!response.ok) throw new Error('Opslaan mislukt');
          kern.antwoord = isLeeg ? null : inhoud;

          // Update samenvatting: vinkje + groen als antwoord, anders reset
          const editorWrapper = opslaanRij.parentElement;
          const kernItem = editorWrapper.parentElement;
          const samenvattingEl = kernItem &&
              kernItem.querySelector('.kernbezwaar-samenvatting');
          if (samenvattingEl) {
            if (kern.antwoord) {
              samenvattingEl.textContent = `\u2714 ${kern.samenvatting}`;
              samenvattingEl.style.color = '#0e7c3a';
            } else {
              samenvattingEl.textContent = kern.samenvatting;
              samenvattingEl.style.color = '';
            }
          }
          this._dispatchVoortgang();

          // Toon bevestiging
          let melding = opslaanRij.querySelector('.antwoord-opslaan-melding');
          if (!melding) {
            melding = document.createElement('span');
            melding.className = 'antwoord-opslaan-melding';
            opslaanRij.appendChild(melding);
          }
          melding.textContent = 'Opgeslagen';
          setTimeout(() => melding.textContent = '', 3000);
          return null;
        })
        .catch((err) => {
          if (err) alert('Het opslaan van het antwoord is mislukt. Probeer opnieuw.');
        })
        .finally(() => {
          if (opslaanKnop && !bevestigd) opslaanKnop.removeAttribute('disabled');
        });
  }

  _toonConsolidatieWaarschuwing(documenten, onBevestig, onAnnuleer) {
    const modal = this.shadowRoot.querySelector('#consolidatie-waarschuwing');
    const inhoud = this.shadowRoot.querySelector('#consolidatie-waarschuwing-inhoud');
    const bevestigKnop = this.shadowRoot.querySelector('#consolidatie-waarschuwing-bevestig');
    if (!modal || !inhoud) return;

    const MAX_ZICHTBAAR = 10;
    const zichtbaar = documenten.slice(0, MAX_ZICHTBAAR);
    const verborgen = documenten.length - MAX_ZICHTBAAR;

    inhoud.innerHTML = '';
    const p = document.createElement('p');
    p.textContent = 'De volgende documenten hebben al een gegenereerde antwoordbrief. ' +
        'Deze antwoordbrieven worden verwijderd als u doorgaat.';
    inhoud.appendChild(p);

    const ul = document.createElement('ul');
    zichtbaar.forEach((doc) => {
      const li = document.createElement('li');
      li.textContent = doc;
      ul.appendChild(li);
    });
    if (verborgen > 0) {
      const li = document.createElement('li');
      li.textContent = `\u2026 en nog ${verborgen} andere`;
      li.style.fontStyle = 'italic';
      ul.appendChild(li);
    }
    inhoud.appendChild(ul);

    const afhandelen = (bevestigd) => {
      bevestigKnop.removeEventListener('vl-click', bevestigHandler);
      modal.off('close', sluitHandler);
      modal.close();
      if (bevestigd) {
        onBevestig();
      } else {
        onAnnuleer();
      }
    };
    const bevestigHandler = () => afhandelen(true);
    const sluitHandler = () => afhandelen(false);

    bevestigKnop.addEventListener('vl-click', bevestigHandler);
    modal.on('close', sluitHandler);
    modal.open();
  }

  _dispatchVoortgang() {
    if (!this._themas) return;
    let totaal = 0;
    let aantalMetAntwoord = 0;
    this._themas.forEach((thema) => {
      thema.kernbezwaren.forEach((kern) => {
        totaal++;
        if (kern.antwoord) aantalMetAntwoord++;
      });
    });
    this.dispatchEvent(new CustomEvent('antwoord-voortgang', {
      bubbles: true,
      detail: {aantalMetAntwoord, totaal},
    }));
  }

  _toonPassages(kernbezwaar) {
    const sideSheet = this.shadowRoot.querySelector('#side-sheet');
    const inhoud = this.shadowRoot.querySelector('#side-sheet-inhoud');
    const titelEl = this.shadowRoot.querySelector('#side-sheet-titel');
    if (!sideSheet || !inhoud) return;

    inhoud.innerHTML = '';
    if (titelEl) titelEl.textContent = kernbezwaar.samenvatting;

    const aantalLabel = document.createElement('p');
    const n = kernbezwaar.individueleBezwaren.length;
    aantalLabel.textContent = `${n} individuele bezwar${n === 1 ? '' : 'en'}:`;
    inhoud.appendChild(aantalLabel);

    kernbezwaar.individueleBezwaren.forEach((ref) => {
      const item = document.createElement('div');
      item.className = 'passage-item';

      const bestand = document.createElement('a');
      bestand.className = 'passage-bestandsnaam';
      bestand.textContent = ref.bestandsnaam;
      bestand.href = `/api/v1/projects/${encodeURIComponent(this._projectNaam)}/bezwaren/${encodeURIComponent(ref.bestandsnaam)}/download`;
      bestand.download = ref.bestandsnaam;

      const passage = document.createElement('div');
      passage.className = 'passage-tekst';
      passage.textContent = `"${ref.passage}"`;

      item.appendChild(bestand);
      item.appendChild(passage);
      inhoud.appendChild(item);
    });

    sideSheet.open();
    this.classList.add('side-sheet-open');
  }
}

defineWebComponent(BezwaarschriftenKernbezwaren, 'bezwaarschriften-kernbezwaren');
