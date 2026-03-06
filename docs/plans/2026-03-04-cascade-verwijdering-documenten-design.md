# Design: Cascade verwijdering bij document- en projectdeletie

**Datum:** 2026-03-04

## Probleem

Bij het verwijderen van een document worden de `geextraheerd_bezwaar` records correct opgeruimd via DB cascade (`extractie_taak` → `geextraheerd_bezwaar`), maar `kernbezwaar_referentie` records die naar die bezwaren verwijzen blijven als wezen achter. `kernbezwaar_referentie.bezwaar_id` is geen FK — het is een zwakke referentie.

Gevolg: kernbezwaren tonen referenties naar niet-bestaande bezwaren. Lege kernbezwaren en thema's blijven bestaan.

Daarnaast ruimt `verwijderProject()` geen kernbezwaar-, clustering- of consolidatiedata op.

## Oplossing

Directe service-aanroep: `ProjectService` roept `KernbezwaarService` aan voor cleanup, alles in 1 transactie.

## Cascade volgorde

### verwijderBezwaar (enkel document)

```
1. kernbezwaar_referentie WHERE bestandsnaam = ? AND kernbezwaar.thema.projectNaam = ?
2. kernbezwaar WHERE count(referenties) = 0 AND thema.projectNaam = ?
   (DB cascade verwijdert ook kernbezwaar_antwoord)
3. thema WHERE count(kernbezwaren) = 0 AND projectNaam = ?
4. extractie_taak WHERE bestandsnaam = ? AND projectNaam = ?
   (DB cascade verwijdert extractie_passage + geextraheerd_bezwaar)
5. Bestand van filesystem verwijderen
```

### verwijderProject (heel project)

```
1. thema WHERE projectNaam = ?
   (DB cascade verwijdert kernbezwaar -> referentie -> antwoord)
2. clustering_taak WHERE projectNaam = ?
3. consolidatie_taak WHERE projectNaam = ?
4. extractie_taak WHERE projectNaam = ?  (bestaand)
5. Projectmap van filesystem verwijderen  (bestaand)
```

## API wijzigingen

### Nieuwe methode: `KernbezwaarService.ruimOpNaDocumentVerwijdering()`

Verwijdert referenties voor het bestand, daarna lege kernbezwaren, daarna lege thema's.

### Gewijzigd: `ProjectService.verwijderBezwaar()`

Roept eerst `kernbezwaarService.ruimOpNaDocumentVerwijdering()` aan, daarna bestaande logica.

### Gewijzigd: `ProjectService.verwijderProject()`

Verwijdert eerst thema's, clustering_taken en consolidatie_taken, daarna bestaande logica.

### Nieuwe repository queries

- `KernbezwaarReferentieRepository`: delete by bestandsnaam + projectNaam
- `KernbezwaarRepository`: delete where referenties empty + projectNaam
- `ThemaRepository`: delete where kernbezwaren empty + projectNaam

## Geen automatische re-clustering

Na verwijdering wordt niet automatisch opnieuw geclusterd. De gebruiker kan dit handmatig starten.

## Test-aanpak

### Unit tests

- KernbezwaarService: referenties verwijderd maar kernbezwaar behoudt andere referenties; kernbezwaar zonder referenties verwijderd; thema zonder kernbezwaren verwijderd; thema met andere kernbezwaren blijft; document zonder kernbezwaar-referenties heeft geen effect
- ProjectService: verwijderBezwaar roept cleanup aan; verwijderProject ruimt alle gerelateerde data op

### Integratietests (Testcontainers)

- 2 documenten, 1 verwijderd, gedeeld kernbezwaar: referentie weg, kernbezwaar blijft
- 2 documenten, 1 verwijderd, niet-gedeeld kernbezwaar: kernbezwaar + referentie verwijderd
- Heel project verwijderd: alle data weg, ander project ongewijzigd
