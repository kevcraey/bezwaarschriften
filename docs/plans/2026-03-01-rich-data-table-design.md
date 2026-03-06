# Design: Migratie naar vl-rich-data-table met filtering, sorting en paginatie

## Probleem

De documententabel (`bezwaarschriften-bezwaren-tabel`) gebruikt een handmatige `<vl-table>` zonder filtering, sorting of paginatie. Bij projecten met tot 5000 bezwaren is dit onwerkbaar.

## Oplossing

Migreer naar `<vl-rich-data-table>` uit @domg-wc/components 2.7.0 met:
- Client-side filtering (bestandsnaam tekst + status dropdown)
- Sorting op alle datakolommen (bestandsnaam, aantal bezwaren, status)
- Paginatie met keuze 50 / 100 / alle per pagina
- Checkbox-selectie via custom renderer

## Componentstructuur

```html
<vl-rich-data-table>
  <vl-search-filter slot="filter" filter-title="Filters">
    <form>
      <input type="text" name="bestandsnaam" placeholder="Zoek op bestandsnaam...">
      <select name="status">
        <option value="">Alle statussen</option>
        <option value="todo">Te verwerken</option>
        <option value="wachtend">Wachtend</option>
        <option value="bezig">Bezig</option>
        <option value="extractie-klaar">Extractie klaar</option>
        <option value="fout">Fout</option>
        <option value="niet ondersteund">Niet ondersteund</option>
      </select>
    </form>
  </vl-search-filter>

  <vl-rich-data-field name="selectie" label="">
    <!-- custom checkbox renderer -->
  </vl-rich-data-field>
  <vl-rich-data-field name="bestandsnaam" selector="bestandsnaam"
    label="Bestandsnaam" sortable>
  </vl-rich-data-field>
  <vl-rich-data-field name="aantalBezwaren" selector="aantalBezwaren"
    label="Aantal bezwaren" sortable>
  </vl-rich-data-field>
  <vl-rich-data-field name="status" selector="status"
    label="Status" sortable>
    <!-- custom status pill renderer -->
  </vl-rich-data-field>

  <vl-pager slot="pager"></vl-pager>
</vl-rich-data-table>
```

## Dataflow

```
__bronBezwaren (volledige dataset, bijgewerkt door API + WebSocket)
    ↓ filter (bestandsnaam contains + status exact match)
    ↓ sort (kolom + richting, multi-sort ondersteund)
    → __gefilterdeEnGesorteerde
    ↓ pagineer (pagina + pageSize: 50/100/alle)
    → table.data = { data: [...], paging: {...} }
```

### Filtering

- **Bestandsnaam**: case-insensitive `includes()` (= `*zoekterm*`)
- **Status**: exacte match uit dropdown
- Alle 6 statussen beschikbaar als filteroptie (todo, wachtend, bezig, extractie-klaar, fout, niet ondersteund)

### Sorting

- Alle datakolommen sorteerbaar (bestandsnaam, aantalBezwaren, status)
- Bestandsnaam: `localeCompare('nl')` voor correcte Nederlandse sortering
- AantalBezwaren: numeriek
- Status: op label-tekst of vaste volgorde

### Paginatie

- Standaard 50 per pagina
- Gebruiker kan kiezen: 50 / 100 / alle
- Selecteer-alles checkbox werkt op de zichtbare gefilterde rijen op de huidige pagina

### WebSocket-updates

- Wijzigen `__bronBezwaren` (source of truth)
- Na wijziging: `_herbereken()` past filters/sorting/paginatie opnieuw toe
- Huidige filter/sort/pagina-staat blijft behouden
- Timer-logica (wachtend/bezig) blijft werken via status-renderer

## Custom renderers

### Checkbox renderer
- Rendert `<input type="checkbox">` per rij
- Disabled voor statussen: wachtend, bezig, niet ondersteund
- "Selecteer alles" checkbox in header
- Dispatcht `selectie-gewijzigd` event bij wijziging

### Status renderer
- Rendert `<vl-pill>` met type (success/warning/error) en label
- Timer-weergave voor wachtend ("Wachtend (1:23)") en bezig ("Bezig (0:45 + 2:10)")
- Interval-timer update elke seconde voor actieve statussen

## Publieke API (ongewijzigd)

De component behoudt dezelfde interface naar de parent:

| Member | Type | Beschrijving |
|--------|------|-------------|
| `bezwaren` | setter | Zet volledige dataset |
| `projectNaam` | property | Projectnaam voor download-links |
| `werkBijMetTaakUpdate(taak)` | method | WebSocket taak-update verwerken |
| `geefGeselecteerdeBestandsnamen()` | method | Array van geselecteerde bestandsnamen |
| `selectie-gewijzigd` | event | Dispatcht bij selectie-wijziging |

## Impactanalyse

### Herschreven
- `webapp/src/js/bezwaarschriften-bezwaren-tabel.js` — volledig herschreven

### Minimale wijzigingen
- `webapp/src/js/bezwaarschriften-project-selectie.js` — eventueel kleine aanpassingen, publieke API blijft gelijk

### Niet geraakt
- Backend API's
- WebSocket-communicatie
- Andere frontend-componenten