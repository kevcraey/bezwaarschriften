# Design: Synthetische testdata met fixture-based MockChatModel

**Datum:** 2026-03-02
**Status:** Goedgekeurd

## Doel

Realistische testdata genereren uit de synthetische PDF-bezwaarschriften, en een `FixtureChatModel` bouwen die in tests deterministische, door een echt LLM gegenereerde antwoorden teruggeeft. Hiermee wordt het volledige extractie-pad end-to-end testbaar zonder live LLM-aanroepen.

## Context

- 15 synthetische PDF-bezwaarschriften in `synthetic-dataset/`, allemaal over het project "Herontwikkeling Gaverbeek Stadion"
- Huidige `MockExtractieVerwerker` genereert random counts — niet realistisch
- Spring AI integratie is gepland (story 1.3) maar nog niet geïmplementeerd
- Doel: fixtures die exact het formaat van een echt LLM-antwoord volgen

## Productie-prompt ontwerp

### System prompt

```
Je bent een ervaren ambtenaar bij het Departement Omgeving van de Vlaamse overheid.
Je analyseert bezwaarschriften die zijn ingediend tijdens een openbaar onderzoek.

Je taak is om uit het bezwaarschrift alle individuele bezwaren te identificeren.

## Wat is een individueel bezwaar?

Een individueel bezwaar is één concreet punt van bezwaar dat zelfstandig beantwoord
kan worden door de vergunningverlenende overheid. Voorbeelden:
- "De geluidsoverlast door evenementen zal onze nachtrust verstoren" → één bezwaar (geluidshinder)
- "Het verkeer zal toenemen EN er zijn onvoldoende parkeerplaatsen" → TWEE bezwaren
  (verkeerslast + parkeertekort), ook al staan ze in dezelfde zin

Splits passages die meerdere bezwaren bevatten altijd op in afzonderlijke items.
Eén passage kan dus leiden tot meerdere bezwaren.

## Per bezwaar lever je:

1. **passage**: De letterlijke tekst uit het bezwaarschrift waaruit dit bezwaar blijkt.
   Kopieer de exacte woorden — niet parafraseren. Als het bezwaar over meerdere zinnen
   loopt, neem de volledige relevante passage op.
2. **samenvatting**: Eén zin die het bezwaar kernachtig beschrijft in je eigen woorden.
3. **categorie**: Eén van: milieu, mobiliteit, ruimtelijke_ordening, procedure,
   gezondheid, economisch, sociaal, overig.

Antwoord UITSLUITEND in het volgende JSON-formaat (geen extra tekst):
```

### Verwacht LLM response formaat

```json
{
  "passages": [
    {
      "id": 1,
      "tekst": "De letterlijke passage uit het bezwaarschrift..."
    }
  ],
  "bezwaren": [
    {
      "passageId": 1,
      "samenvatting": "Korte omschrijving van het bezwaar",
      "categorie": "mobiliteit"
    }
  ],
  "metadata": {
    "aantalWoorden": 542,
    "documentSamenvatting": "Korte samenvatting van het volledige bezwaarschrift"
  }
}
```

### User prompt (per document)

```
Context: Openbaar onderzoek voor het project "Herontwikkeling Gaverbeek Stadion"
in Waregem — bouw van een multifunctioneel stadion met parking, commerciële ruimtes
en publieke groenzones langs de Gaverbeek.

Analyseer het volgende bezwaarschrift en extraheer alle individuele bezwaren:

---
{documenttekst}
---
```

## Scheiding tekst vs posities

- **LLM output (fixture):** Passage bevat tekst (want LLMs kunnen geen karakter-offsets tellen)
- **Domeinmodel (na parsing):** Posities worden berekend door de passage-tekst terug te zoeken in het brondocument
- Coverage en deduplicatie worden mogelijk via de posities in het domeinmodel

## Bestandsstructuur

```
testdata/
├── gaverbeek-stadion/
│   ├── projectomschrijving.txt           # samenvatting openbaar onderzoek
│   ├── bezwaren/
│   │   ├── Bezwaar_01.txt                # geëxtraheerde tekst uit PDF
│   │   ├── Bezwaar_01.json               # LLM-response fixture
│   │   ├── Bezwaar_02.txt
│   │   ├── Bezwaar_02.json
│   │   ├── ...
│   │   ├── Bezwaar_14_geheel_gescand.txt
│   │   ├── Bezwaar_14_geheel_gescand.json
│   │   └── Bezwaar_15_mixed.txt
│   │       Bezwaar_15_mixed.json
│   └── manifest.json                     # index: bestandsnaam → fixture mapping
```

### manifest.json

```json
{
  "project": "Herontwikkeling Gaverbeek Stadion",
  "bestanden": [
    {
      "bestandsnaam": "Bezwaar_01",
      "txtBestand": "bezwaren/Bezwaar_01.txt",
      "fixtureBestand": "bezwaren/Bezwaar_01.json",
      "aantalBezwaren": 4,
      "aantalWoorden": 542
    }
  ]
}
```

## Generatie-pipeline

Eenmalige uitvoering via Claude sub-agents:

1. **Per PDF:** Claude sub-agent leest PDF multimodaal → schrijft `.txt` (documenttekst)
2. **Per .txt:** Claude sub-agent krijgt de productie-prompt + documenttekst → schrijft `.json` fixture
3. **manifest.json:** Automatisch gegenereerd met alle bestandsnamen en metadata

## FixtureChatModel

```java
@Profile("test")
@Component
public class FixtureChatModel implements ChatModel {

    private final Map<String, String> fixtures; // documentHash → JSON response

    public FixtureChatModel(@Value("${testdata.pad}") String testdataPad) {
        this.fixtures = laadFixtures(testdataPad);
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        String documentTekst = extractDocumentTekst(prompt);
        String fixtureJson = fixtures.get(hash(documentTekst));
        return new ChatResponse(List.of(new Generation(fixtureJson)));
    }
}
```

### Lookup-strategie

1. Productie-code stuurt: system prompt + user prompt met `{documenttekst}`
2. `FixtureChatModel` extraheert de documenttekst uit de user-prompt
3. Matcht tegen geladen `.txt` bestanden → vindt bijbehorende `.json`
4. Retourneert fixture als LLM-response

## Test flow

```
Test                    FixtureChatModel              testdata/
 │                           │                           │
 ├─ roep extractie aan ─────►│                           │
 │                           ├─ extract documenttekst ──►│
 │                           │◄── match Bezwaar_01.txt ──┤
 │                           │◄── return Bezwaar_01.json ┤
 │◄── ExtractieResultaat ───┤                           │
 │                           │                           │
 ▼ assert bezwaren correct   │                           │
```

## Beslissingen

| Beslissing | Keuze | Reden |
|-----------|-------|-------|
| PDF conversie | Claude multimodaal | Geen extra dependencies, dekt ook gescande docs |
| Fixture formaat | Prompt-aligned JSON | Test het volledige pad: prompt → response → parsing |
| Passage-identificatie | Tekst in LLM output, posities in domeinmodel | LLMs kunnen geen karakter-offsets, tekst-matching is robuuster |
| Mock strategie | FixtureChatModel (Spring AI ChatModel) | Dichter bij productie dan MockExtractieVerwerker |
| Fixture locatie | testdata/ (root level) | Duidelijk gescheiden, niet op classpath maar bewust gekozen |
| Categorieën | milieu, mobiliteit, ruimtelijke_ordening, procedure, gezondheid, economisch, sociaal, overig | Afdekking van typische bezwaarcategorieën bij omgevingsvergunningen |
