# Tekst-extractie

## Doel

PDF's en TXT-bestanden omzetten naar platte tekst voor verdere verwerking in de bezwaarschriften-pipeline.

---

## Verwerkingsflow

```
Upload → opslag in bezwaren-orig/ → TekstExtractieTaak (WACHTEND) → async verwerking → bezwaren-text/
```

### Per bestandstype

- **PDF:** digitale extractie (PDFTextStripper) → kwaliteitscontrole → OCR-fallback indien kwaliteit onvoldoende
- **TXT:** directe kwaliteitscontrole (geen extractie nodig)

### Resultaat

Geëxtraheerde tekst wordt opgeslagen als `.txt` bestand in `bezwaren-text/`.

---

## Kwaliteitscriteria

| Criterium | Drempelwaarde |
|---|---|
| Minimaal aantal woorden | 100 |
| Alfanumerieke ratio (excl. spaties) | >= 70% |
| Klinker/letter ratio | 20% - 60% |

Tekst die niet aan alle criteria voldoet wordt als onvoldoende beschouwd. Bij PDF's triggert dit de OCR-fallback.

---

## Extractiemethodes

| Methode | Toepassing |
|---|---|
| **DIGITAAL** | PDFTextStripper (PDF) of directe lezing (TXT) |
| **OCR** | Tesseract met taalmodellen `nld` + `eng`, rendering op 300 DPI |

---

## Statussen (TekstExtractieTaak)

| Status | Betekenis |
|---|---|
| `WACHTEND` | Taak aangemaakt, wacht op verwerking |
| `BEZIG` | Extractie is gestart |
| `KLAAR` | Tekst succesvol geëxtraheerd en opgeslagen |
| `MISLUKT` | Extractie gefaald (zowel digitaal als OCR) |
| `OCR_NIET_BESCHIKBAAR` | OCR nodig maar Tesseract niet geïnstalleerd |

---

## Folderstructuur

```
{project}/
├── bezwaren-orig/   → originele bestanden (PDF, TXT)
└── bezwaren-text/   → geëxtraheerde platte tekst (.txt)
```

---

## Gate-mechanisme

AI-bezwaarextractie kan pas starten nadat tekst-extractie succesvol is afgerond (status `KLAAR`). Dit voorkomt dat de LLM wordt aangeroepen met ontbrekende of corrupte invoer.

---

## Traceability

De gebruikte extractiemethode (Digitaal of OCR) wordt per document bijgehouden in de `TekstExtractieTaak` en getoond in de documententabel in de frontend.

---

## Vereisten

- **PDFBox** (Apache PDFBox) — voor digitale PDF-tekstextractie
- **Tesseract OCR** (optioneel) — voor OCR-fallback bij gescande PDF's. Als Tesseract niet beschikbaar is, krijgt de taak status `OCR_NIET_BESCHIKBAAR`
