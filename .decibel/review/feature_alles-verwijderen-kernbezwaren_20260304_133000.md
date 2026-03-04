# Code Review: feature/alles-verwijderen-kernbezwaren

**Branch:** `feature/alles-verwijderen-kernbezwaren` → `main`
**Datum:** 2026-03-04
**Reviewer:** Claude Sonnet 4.6

---

## Samenvatting

Voegt een "Alles verwijderen" knop toe naast de "Cluster alle categorieën" knop in de kernbezwaren-tab. De knop verwijdert alle clusteringresultaten (thema's, kernbezwaren, antwoorden) en reset de clustering-taken. Implementatie is TDD-gewijs: 4 service tests, 3 controller tests, 3 Cypress tests toegevoegd.

**Beslissing: 🔄 Request Changes (1 blocking)**

---

## Positief

- Consistente API-structuur: zelfde patroon als per-categorie delete (409 met bevestiging bij antwoorden)
- `_toonVerwijderBevestigingModal` uitgebreid zonder duplicatie (categorie=null handled)
- `verwijderAlleClusteringen_retourneertSuccesZonderThemas` test dekt edge case (geen thema's, wel tasks)
- ClusteringWorker vangt `IllegalArgumentException` al graceful op — geen crash bij verwijderde taken
- Goede zichtbaarheidslogica: knop enkel zichtbaar bij minstens één 'klaar' categorie

---

## Bevindingen

### 🔴 Blocking

#### 1. Actieve workers schrijven data terug na "alles verwijderen"

**Bestanden:** `app/src/main/java/.../ClusteringTaakService.java:265-294`, `webapp/src/js/bezwaarschriften-kernbezwaren.js:327-344`

Als er naast 'klaar' categorieën ook 'bezig' taken zijn (zichtbaar in de UI), en de gebruiker klikt "Alles verwijderen":

1. `verwijderAlleClusteringen` verwijdert alle tasks + thema's
2. De lopende worker controleert `isGeannuleerd(taakId)` → task niet gevonden → `orElse(false)` → gaat door
3. Worker voltooit `clusterEenCategorie` en schrijft **nieuwe** thema- en kernbezwaardata naar de DB
4. De verwijdering is effectief teruggedraaid voor die categorie

`ClusteringWorker.verwerkTaak` (regel 78) vangt `IllegalArgumentException` van `markeerKlaar` correct op, maar de thema-data is al geschreven door `kernbezwaarService.clusterEenCategorie` voor de `markeerKlaar`-aanroep.

Per-categorie-delete doet dit wél correct: annuleert de worker first (`clusteringWorker.annuleerTaak`) zodat die stopt voor de thema-write.

**Aanbeveling (frontend):** Verberg/disable de "Alles verwijderen" knop wanneer er actieve taken zijn:

```javascript
// webapp/src/js/bezwaarschriften-kernbezwaren.js:327
const heeftActieveTaak = this._clusteringTaken.some(
    (ct) => ct.status === 'wachtend' || ct.status === 'bezig');
if (heeftKlareCategorie && !heeftActieveTaak) {
  // toon knop
}
```

Dit is de eenvoudigste fix: de gebruiker ziet de knop niet als er nog een clustering loopt. Alternatief: in de service actieve taken eerst annuleren (meer werk, maar robuuster).

---

### 🟡 Important

#### 2. Ontbrekende test: thema's zonder kernbezwaren

**Bestand:** `app/src/test/java/.../ClusteringTaakServiceTest.java`

De code heeft een `if (!kernIds.isEmpty())` guard op regel 282 (ClusteringTaakService):
```java
if (!kernIds.isEmpty()) {
  long aantalAntwoorden = antwoordRepository.countByKernbezwaarIdIn(kernIds);
```

Er is geen test die verifieert dat de methode correct omgaat met thema's zonder kernbezwaren (bijv. een thema dat aangemaakt is maar leeg is). In dat geval wordt het antwoorden-tellen overgeslagen en direct verwijderd — wat correct is, maar niet gedekt door een test.

**Aanbeveling:**
```java
@Test
void verwijderAlleClusteringen_verwijdertDirectBijThemasZonderKernbezwaren() {
  var thema = maakThema(100L, "windmolens", "Geluid");
  when(themaRepository.findByProjectNaam("windmolens")).thenReturn(List.of(thema));
  when(kernbezwaarRepository.findByThemaIdIn(List.of(100L))).thenReturn(List.of());

  var resultaat = service.verwijderAlleClusteringen("windmolens", false);

  assertThat(resultaat.verwijderd()).isTrue();
  verify(antwoordRepository, never()).countByKernbezwaarIdIn(any());
  verify(themaRepository).deleteByProjectNaam("windmolens");
}
```

---

### 🟢 Nitpick

#### 3. Modal-titel klopt niet bij "alles verwijderen"

**Bestand:** `webapp/src/js/bezwaarschriften-kernbezwaren.js:144`

De modal `#verwijder-bevestiging` heeft de hardcoded titel `"Clustering verwijderen"`. De bevestigingstekst (`_toonVerwijderBevestigingModal`) is netjes aangepast voor het "alles"-geval, maar de modal-titel blijft generiek. Overweeg de titel dynamisch te maken via `modal.setAttribute('title', ...)` vóór `modal.open()`.

---

#### 4. Cypress test: knop niet zichtbaar zonder klare categorieën

**Bestand:** `webapp/test/bezwaarschriften-kernbezwaren.cy.js`

Er is geen test die verifieert dat de "Alles verwijderen" knop **niet** getoond wordt wanneer er geen 'klaar' categorieën zijn (enkel 'todo'/'bezig'/'fout'). Dit is het inverse van de bestaande test op regel 236.

---

## Conclusie

De basis-implementatie is solide en consistent met bestaande patronen. Issue #1 (actieve workers) is de enige blocker: in de praktijk is het een zeldzame race condition (simultanee 'klaar' + 'bezig' taken), maar het gedrag is verrassend voor de gebruiker die verwacht dat "alles" echt weg is.

Fix in frontend (1 regel): knop verbergen bij actieve taken. Daarna kan gereviewd worden met ✅ Approve.
