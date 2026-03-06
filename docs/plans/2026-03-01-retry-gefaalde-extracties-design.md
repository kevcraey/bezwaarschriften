# Design: Retry gefaalde extracties + status-pills

## Doel

Gebruikers moeten alle gefaalde extracties binnen een project in 1 klik opnieuw kunnen proberen. Tegelijk wordt de statusweergave in de documenttabel visueel verbeterd met `vl-pill` componenten.

## Scope

Twee samenhangende wijzigingen:

1. **Retry-knop** voor gefaalde extracties per project
2. **Status als vl-pill** in de documenttabel (vervangt platte tekst)

---

## 1. Retry gefaalde extracties

### Backend

**Nieuw endpoint:**

```
POST /api/v1/projects/{naam}/extracties/retry
```

Response: `200 OK`
```json
{ "aantalOpnieuwIngepland": 3 }
```

**Nieuwe repository-query (`ExtractieTaakRepository`):**

```java
List<ExtractieTaak> findByProjectNaamAndStatus(String projectNaam, ExtractieTaakStatus status);
```

**Nieuwe service-methode (`ExtractieTaakService.herplanGefaaldeTaken`):**

- Haalt alle taken met status `FOUT` op voor het project
- Per taak: `maxPogingen += 1`, status → `WACHTEND`, `foutmelding` → null, timestamps resetten
- Stuurt WebSocket-notificatie per taak
- Retourneert het aantal herplande taken

**Poging-teller:** Telt door (niet reset). Elke handmatige retry geeft precies 1 extra poging.

**Bestaande infrastructuur hergebruikt:** De `ExtractieWorker` (polling elke seconde) pakt de herplande taken automatisch op. Geen nieuwe achtergrondprocessen nodig.

### Frontend

**Retry-knop (`bezwaarschriften-project-selectie.js`):**

- Positie: naast "Extraheer geselecteerde" knop
- Begint met `hidden` attribuut
- Verschijnt alleen als er minstens 1 bestand met status `fout` is
- Tekst: `"Opnieuw proberen (N)"` met N = aantal gefaalde
- Na klik: `POST .../extracties/retry` aanroepen
- Na succesvolle retry: taken springen naar "Wachtend" via WebSocket, knop verdwijnt automatisch (0 fouten)

**Nieuwe methode `_werkRetryKnopBij()`:**

- Telt bezwaren met status `fout`
- Zet `hidden` op basis van aantal fouten
- Wordt aangeroepen vanuit dezelfde plekken als `_werkDocumentenTabTitelBij()`

---

## 2. Status als vl-pill

### Mapping

| Status | Label | Pill type |
|--------|-------|-----------|
| `todo` | Te verwerken | *(default/neutraal)* |
| `wachtend` | Wachtend (MM:SS) | `warning` |
| `bezig` | Bezig (MM:SS + MM:SS) | `warning` |
| `extractie-klaar` | Extractie klaar | `success` |
| `fout` | Fout | `error` |
| `niet ondersteund` | Niet ondersteund | default + `disabled` |

### Wijzigingen (`bezwaarschriften-bezwaren-tabel.js`)

- Import en registreer `VlPillComponent`
- `_formatStatus()` retourneert `<vl-pill data-vl-type="...">Label</vl-pill>` HTML
- `_updateTimers()` update de pill-tekstinhoud voor wachtend/bezig-statussen
- **Aantal woorden wordt niet meer getoond** (was testdata)

---

## Niet in scope

- Retry per individueel bestand
- Globale retry over alle projecten
- Exponential backoff bij automatische retries
- Retry-limiet (gebruiker kan onbeperkt handmatig retrien)
