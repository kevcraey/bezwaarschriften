# Design: Passage-deduplicatie in Side Panel

**Datum:** 2026-03-05

## Probleem

Bij kernbezwaren met veel individuele bezwaren komen dezelfde (of bijna dezelfde) passages herhaaldelijk voor uit verschillende documenten. Dit maakt de side panel onoverzichtelijk.

## Oplossing

Puur frontend, geen backend-wijzigingen.

### 1. Fuzzy grouping algoritme

Bij het openen van de side panel worden passages gegroepeerd op basis van tekst-gelijkenis:

- Normaliseer passages (lowercase, trim whitespace, interpunctie normaliseren)
- Bereken gelijkenis met Sørensen-Dice coëfficiënt op bigrammen (sneller dan Levenshtein voor langere teksten)
- Drempel: **90% gelijkenis** → zelfde groep
- De langste passage in een groep wordt de "representatieve" passage

### 2. Gegroepeerde weergave in side panel

Per groep:

```
"De passage tekst hier..."
📄 001.txt, 007.txt, 012.txt, 015.txt, 023.txt  ... (12 documenten)
                                                  [Toon alle]
```

- Passage tekst 1x getoond (italic, zoals nu)
- Daaronder: documentnamen als downloadlinks, max 5 zichtbaar
- Bij >5: `... (N documenten)` + "Toon alle" link
- "Toon alle" ontvouwt de volledige lijst met alle downloadlinks
- Passages zonder duplicaten worden gewoon getoond met hun enkele documentlink (zoals nu)

### 3. Teller op search-knop

- Huidig: `(5)`
- Nieuw: `(5|3)` → totaal individuele bezwaren | aantal unieke groepen

### 4. Sortering

Groepen gesorteerd op aantal documenten (meest voorkomend eerst), dan passages zonder duplicaten.

## Scope

- **In scope:** frontend grouping, side panel weergave, teller aanpassing
- **Buiten scope:** backend wijzigingen, embedding-gebaseerde matching
