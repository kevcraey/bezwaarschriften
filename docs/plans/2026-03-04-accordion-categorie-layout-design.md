# Design: Accordion-layout per categorie

## Context

De kernbezwaren-per-categorie UI gebruikt een flat card-layout met `.categorie-wrapper` / `.categorie-header`. Dit ontwerp vervangt dat met `vl-accordion` componenten voor betere structuur en inklapbare secties.

## Nieuwe layout

Elke categorie wordt een `vl-accordion`, standaard dichtgeklapt.

```
┌─ Global Header ────────────────────────────────────────┐
│  [Cluster alle categorieën]  [Alles verwijderen]       │
├─ Global Alert (behouden) ──────────────────────────────┤
│  120 bezwaren → 18 kernbezwaren (7x reductie)          │
└────────────────────────────────────────────────────────┘

┌─ vl-accordion ─────────────────────────────────────────┐
│  ▶ Geluid              [Te clusteren ▶]   ← menu slot  │
│    42 bezwaren                            ← subtitle    │
└────────────────────────────────────────────────────────┘

┌─ vl-accordion ─────────────────────────────────────────┐
│  ▶ Geur                [Klaar × ↻]       ← menu slot   │
│    28 bezwaren → 5 kernbezwaren           ← subtitle    │
│  ┌─ (content, dichtgeklapt) ────────────────────────┐  │
│  │  ✔ Kernbezwaar 1 samenvatting        (3) ✏       │  │
│  │  Kernbezwaar 2 samenvatting           (5) ✏       │  │
│  └──────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────┘
```

## Slot-mapping

| Slot | Inhoud |
|------|--------|
| `toggle-text` | Categorienaam (bv. "Geluid") |
| `subtitle` slot | Info-tekst |
| `menu` slot | Status pill met actieknoppen |
| default slot | Kernbezwaar-items (alleen bij status `klaar`) |

## Subtitle-tekst per status

| Status | Subtitle |
|--------|----------|
| `todo` | "42 bezwaren" |
| `wachtend` / `bezig` | "42 bezwaren" |
| `klaar` | "42 bezwaren → 5 kernbezwaren" |
| `fout` | "42 bezwaren" |

## Wat verdwijnt

- `.categorie-wrapper`, `.categorie-header`, `.categorie-label` CSS
- Per-categorie `vl-alert` met reductie-info (vervangen door subtitle)
- Aantal in de categorie-label ("Geluid (42 bezwaren)" → "Geluid")

## Wat blijft

- Globale samenvatting `vl-alert` bovenaan
- Status pill (identieke logica, nu in `menu` slot)
- Kernbezwaar-items in default slot (identieke rendering)
- Alle actie-logica (start, annuleer, verwijder, retry)

## Alle accordions standaard dichtgeklapt

Geen `default-open` attribuut. Gebruiker klapt zelf open wat relevant is.
