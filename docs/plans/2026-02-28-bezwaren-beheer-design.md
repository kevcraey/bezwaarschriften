# Design: Bezwaren Beheer (Upload + Verwijderen)

## Doel

Gebruikers kunnen bezwaarbestanden uploaden naar en verwijderen uit een project, met client-side validatie, server-side beveiliging, en bevestigingsdialoog bij verwijdering.

## Architectuur

Fullstack feature: backend upload/delete endpoints + frontend UI-uitbreiding in de Documenten-tab.

### Backend

Twee nieuwe endpoints op `ProjectController`:

- `POST /api/v1/projects/{naam}/bezwaren/upload` -- multipart file upload
- `DELETE /api/v1/projects/{naam}/bezwaren/{bestandsnaam}` -- verwijdert bestand + extractie-taken

Opslag: bestanden worden geschreven naar `input/{project}/bezwaren/` (bestaande conventie).

### Frontend

Wijzigingen in `bezwaarschriften-project-selectie.js` (Documenten-tab):

- **Verwijderknop** naast "Extraheer geselecteerde" -- werkt op geselecteerde rijen
- **"Bestanden toevoegen" knop** -- toont/verbergt een `vl-upload` dropzone
- **`vl-modal`** bevestigingsdialoog bij verwijdering

## Upload Flow

1. Gebruiker klikt "Bestanden toevoegen" -- upload-zone verschijnt
2. Drag-and-drop of browse, max 100 bestanden, max 50MB per stuk
3. `vl-upload` valideert client-side: bestandsgrootte + extensie (`.txt`)
4. Niet-ondersteunde bestanden krijgen foutmelding in upload-lijst; ondersteunde worden verstuurd
5. `POST /api/v1/projects/{naam}/bezwaren/upload` ontvangt `MultipartFile[]`
6. Backend schrijft elk geldig bestand naar `input/{project}/bezwaren/`
7. Duplicaten (bestand bestaat al) worden overgeslagen met foutmelding per bestand
8. Response:

```json
{
  "geupload": ["a.txt", "b.txt"],
  "fouten": [
    {"bestandsnaam": "c.pdf", "reden": "Niet-ondersteund formaat"},
    {"bestandsnaam": "d.txt", "reden": "Bestand bestaat al"}
  ]
}
```

9. Na upload: bezwaren-tabel wordt herladen

## Verwijder Flow

1. Gebruiker selecteert 1+ bestanden via checkboxes
2. Klikt "Verwijder geselecteerde"
3. `vl-modal` toont: "Weet je zeker dat je X bestand(en) wilt verwijderen? Bestanden en bijhorende extractie-resultaten worden permanent verwijderd."
4. Bij bevestiging: `DELETE /api/v1/projects/{naam}/bezwaren/{bestandsnaam}` per bestand
5. Backend: verwijdert fysiek bestand + alle `extractie_taak` records voor dat bestand
6. Als extractie wachtend/bezig is: taak wordt ook verwijderd (worker vangt `FileNotFoundException` op)
7. Na verwijdering: bezwaren-tabel wordt herladen

## Validatie & Beperkingen

| Regel | Client-side | Server-side |
|---|---|---|
| Alleen `.txt` | `vl-upload` accept filter | Extensie-check in controller |
| Max 50MB per bestand | `vl-upload` max-size | Spring `multipart.max-file-size` |
| Max 100 bestanden per upload | `vl-upload` max-files | Array-lengte check |
| Geen duplicaten | Niet | Bestand-bestaat check |
| Bestandsnaam veilig | Niet | Path traversal preventie |

## Error Handling

- Upload niet-`.txt`: client-side afgewezen, server-side ook gevalideerd
- Upload bestand dat al bestaat: server retourneert fout per bestand, andere bestanden slagen
- Verwijdering tijdens actieve extractie: taak verwijderd, worker vangt fout op
- Netwerk-fouten: foutmelding in bestaande `#fout-melding` element

## Componenten

- `vl-upload` (drag-and-drop + browse, uit `@domg-wc/components`)
- `vl-modal` (bevestigingsdialoog, uit `@domg-wc/components`)
- `vl-button` (toevoegen/verwijderen knoppen)

## Bestanden

### Backend (wijzigen)
- `ProjectController.java` -- upload + delete endpoints
- `ProjectService.java` -- upload + delete logica
- `ProjectPoort.java` -- nieuwe methodes voor schrijven/verwijderen
- `BestandssysteemProjectAdapter.java` -- implementatie bestandsoperaties
- `ExtractieTaakRepository.java` -- delete query voor bestandsnaam

### Frontend (wijzigen)
- `bezwaarschriften-project-selectie.js` -- upload-zone, verwijderknop, modal, event handlers
