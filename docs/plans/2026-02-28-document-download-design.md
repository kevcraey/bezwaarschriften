# Design: Document download

## Doel

Gebruikers kunnen bezwaar-documenten downloaden vanuit de documenten-tabel door op de bestandsnaam te klikken.

## Backend

Nieuw endpoint in `ProjectController`:

```
GET /api/v1/projects/{naam}/bezwaren/{bestandsnaam}/download
```

- Resolveert pad: `inputFolder / projectNaam / "bezwaren" / bestandsnaam`
- Path traversal beveiliging: valideer dat bestandsnaam geen `..` of pad-separators bevat, en dat het genormaliseerde pad binnen de input-map valt
- Returnt `ResponseEntity<Resource>` met `Content-Disposition: attachment` header
- MIME-type detectie via `Files.probeContentType()`, fallback `application/octet-stream`
- 404 als bestand niet bestaat

## Frontend

In `bezwaarschriften-bezwaren-tabel.js`: bestandsnaam-cel wordt een `<a>` download-link.

```html
<a href="/api/v1/projects/${project}/bezwaren/${bestandsnaam}/download"
   download>${bestandsnaam}</a>
```

De tabel-component krijgt het projectnaam als property van de parent-component.

## Scope

- Alle bestanden in de bezwaren-map zijn downloadbaar, ongeacht extractie-status of bestandstype.
- Bestaande Spring Security authenticatie geldt automatisch.
