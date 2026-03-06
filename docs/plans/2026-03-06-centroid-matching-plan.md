# Centroid-matching en sortering Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Per kernbezwaar een match-score berekenen voor elk individueel bezwaar t.o.v. de cluster-centroid, opslaan, en gesorteerd (aflopend) tonen in de UI met percentage.

**Architecture:** Score wordt eenmalig berekend tijdens clustering en opgeslagen in `kernbezwaar_referentie`. Bij ophalen komen referenties gesorteerd op score. Frontend toont score per passage-groep.

**Tech Stack:** Java 21, Spring Boot 3.x, Liquibase, Lit web components (@domg-wc), Cypress

---

### Task 1: Liquibase migratie — score kolom toevoegen

**Files:**
- Create: `app/src/main/resources/config/liquibase/changelog/20260306-referentie-score.xml`
- Modify: `app/src/main/resources/config/liquibase/master.xml`

**Step 1: Maak het Liquibase changelog bestand**

```xml
<?xml version="1.0" encoding="utf-8"?>
<databaseChangeLog
    xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-3.1.xsd">

  <changeSet id="20260306-score" author="kenzo">
    <addColumn tableName="kernbezwaar_referentie">
      <column name="score" type="double precision"/>
    </addColumn>
  </changeSet>

</databaseChangeLog>
```

**Step 2: Voeg de include toe aan master.xml**

Na de regel `<include file="config/liquibase/changelog/20260306-dual-embedding.xml"/>` toevoegen:

```xml
  <include file="config/liquibase/changelog/20260306-referentie-score.xml"/>
```

**Step 3: Commit**

```bash
git add app/src/main/resources/config/liquibase/
git commit -m "feat: liquibase migratie voor score kolom op kernbezwaar_referentie"
```

---

### Task 2: Entiteit en record uitbreiden met score

**Files:**
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/KernbezwaarReferentieEntiteit.java`
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/IndividueelBezwaarReferentie.java`

**Step 1: Voeg score toe aan KernbezwaarReferentieEntiteit**

Na het `passage` veld (rond regel 22), voeg toe:

```java
  @Column(name = "score")
  private Double score;
```

En getter/setter:

```java
  public Double getScore() {
    return score;
  }

  public void setScore(Double score) {
    this.score = score;
  }
```

**Step 2: Breid IndividueelBezwaarReferentie record uit**

Wijzig het record van:

```java
public record IndividueelBezwaarReferentie(Long bezwaarId, String bestandsnaam, String passage) {}
```

Naar:

```java
public record IndividueelBezwaarReferentie(Long bezwaarId, String bestandsnaam, String passage, Integer scorePercentage) {}
```

**Step 3: Compileer om alle aanroepen te vinden die moeten worden aangepast**

Run: `mvn compile -pl app -DskipTests 2>&1 | grep "error"`

Dit zal compilatiefouten tonen in `KernbezwaarService` (bouwReferenties, geefKernbezwaren) die in de volgende task worden opgelost.

**Step 4: Commit**

```bash
git add app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/KernbezwaarReferentieEntiteit.java
git add app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/IndividueelBezwaarReferentie.java
git commit -m "feat: score veld toevoegen aan referentie-entiteit en domain record"
```

---

### Task 3: Score berekenen tijdens clustering en opslaan

**Files:**
- Modify: `app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/KernbezwaarService.java`

**Step 1: Pas `bouwReferenties` aan om scores te accepteren**

Wijzig de signatuur van `bouwReferenties` (regel 408) van:

```java
private List<IndividueelBezwaarReferentie> bouwReferenties(
    List<GeextraheerdBezwaarEntiteit> bezwaren,
    Map<Long, Map<Integer, String>> passageLookup,
    Map<Long, String> bestandsnaamLookup)
```

Naar:

```java
private List<IndividueelBezwaarReferentie> bouwReferenties(
    List<GeextraheerdBezwaarEntiteit> bezwaren,
    Map<Long, Map<Integer, String>> passageLookup,
    Map<Long, String> bestandsnaamLookup,
    Map<Long, Double> scores)
```

En de body:

```java
return bezwaren.stream()
    .map(b -> {
      Double score = scores.get(b.getId());
      Integer scorePercentage = score != null ? (int) Math.round(score * 100) : null;
      return new IndividueelBezwaarReferentie(
          b.getId(),
          bestandsnaamLookup.getOrDefault(b.getTaakId(), "onbekend"),
          geefPassageTekst(b, passageLookup),
          scorePercentage);
    })
    .toList();
```

**Step 2: Pas de cluster-loop in `clusterCategorie` aan (regel ~312-325)**

Vervang het blok binnen `for (var cluster : clusterResultaat.clusters())`:

```java
var clusterBezwaren = cluster.bezwaarIds().stream()
    .map(bezwaarById::get)
    .toList();
var origineleCentroid = berekenOrigineleCentroid(clusterBezwaren);
var representatief = vindDichtstBijCentroid(clusterBezwaren, origineleCentroid);
var samenvatting = representatief.getSamenvatting();

// Bereken score per bezwaar
var scores = new HashMap<Long, Double>();
for (var bezwaar : clusterBezwaren) {
  scores.put(bezwaar.getId(),
      cosinusGelijkenis(bezwaar.getEmbedding(), origineleCentroid));
}

var referenties = bouwReferenties(clusterBezwaren, passageLookup, bestandsnaamLookup, scores);
var kern = slaKernbezwaarOp(opgeslagenThema.getId(), samenvatting, referenties);
kernbezwaren.add(kern);
```

**Step 3: Pas de noise-bezwaren aan (rond regel 330)**

Voor noise-bezwaren, geef een lege scores-map mee:

```java
var referenties = bouwReferenties(noiseBezwaren, passageLookup, bestandsnaamLookup, Map.of());
```

**Step 4: Pas `slaKernbezwaarOp` aan om score op te slaan (regel 386)**

In de for-loop die referenties opslaat, voeg score toe:

```java
for (var ref : referenties) {
  var refEntiteit = new KernbezwaarReferentieEntiteit();
  refEntiteit.setKernbezwaarId(kernEntiteit.getId());
  refEntiteit.setBezwaarId(ref.bezwaarId());
  refEntiteit.setBestandsnaam(ref.bestandsnaam());
  refEntiteit.setPassage(ref.passage());
  refEntiteit.setScore(ref.scorePercentage() != null ? ref.scorePercentage() / 100.0 : null);
  referentieRepository.save(refEntiteit);
  opgeslagenReferenties.add(ref);
}
```

**Step 5: Pas `geefKernbezwaren` aan (regel 171) — scores uitlezen en sorteren**

In de assemblage van referenties, voeg score toe en sorteer:

```java
var refs = refPerKern.getOrDefault(ke.getId(), List.of()).stream()
    .map(re -> {
      Integer scorePercentage = re.getScore() != null
          ? (int) Math.round(re.getScore() * 100) : null;
      return new IndividueelBezwaarReferentie(
          re.getBezwaarId(), re.getBestandsnaam(), re.getPassage(), scorePercentage);
    })
    .sorted(Comparator.comparing(
        IndividueelBezwaarReferentie::scorePercentage,
        Comparator.nullsLast(Comparator.reverseOrder())))
    .toList();
return new Kernbezwaar(ke.getId(), ke.getSamenvatting(), refs, null);
```

**Step 6: Compileer en verifieer**

Run: `mvn compile -pl app -DskipTests`
Expected: BUILD SUCCESS

**Step 7: Commit**

```bash
git add app/src/main/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/KernbezwaarService.java
git commit -m "feat: bereken en sla centroid-scores op tijdens clustering, sorteer bij ophalen"
```

---

### Task 4: Unit tests aanpassen en uitbreiden

**Files:**
- Modify: `app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/KernbezwaarServiceTest.java`
- Modify: `app/src/test/java/be/vlaanderen/omgeving/bezwaarschriften/kernbezwaar/KernbezwaarControllerTest.java`

**Step 1: Fix bestaande tests die IndividueelBezwaarReferentie aanmaken**

Zoek in alle testbestanden naar `new IndividueelBezwaarReferentie(` en voeg het vierde argument `null` toe (of een score-waarde als relevant).

**Step 2: Schrijf test voor score-berekening in KernbezwaarServiceTest**

Voeg een test toe die verifieert dat na clustering de referenties een scorePercentage hebben:

```java
@Test
void clusterEenCategorie_berekenScorePerBezwaar() {
  // Setup: bezwaren met embeddings die dicht bij elkaar liggen
  // Verifieer dat de referenties in het resultaat een scorePercentage > 0 hebben
  // Verifieer dat de referenties gesorteerd zijn op score (aflopend)
}
```

**Step 3: Schrijf test voor geefKernbezwaren sortering**

Voeg een test toe die verifieert dat referenties gesorteerd op score worden geretourneerd.

**Step 4: Draai alle unit tests**

Run: `mvn test -pl app`
Expected: BUILD SUCCESS

**Step 5: Commit**

```bash
git add app/src/test/
git commit -m "test: unit tests voor centroid-score berekening en sortering"
```

---

### Task 5: Frontend — score tonen bij passage-groepen

**Files:**
- Modify: `webapp/src/js/passage-groepering.js`
- Modify: `webapp/src/js/bezwaarschriften-kernbezwaren.js`
- Modify: `webapp/test/passage-groepering.cy.js`

**Step 1: Breid `groepeerPassages` uit om scores mee te nemen**

In `passage-groepering.js`, de groep-objecten krijgen een `maxScore` property. Bij het toevoegen van een bezwaar aan een groep, track de hoogste `scorePercentage`:

In de groep-initialisatie (wanneer een nieuwe groep wordt aangemaakt):

```javascript
groepen.push({
  passage: bezwaar.passage,
  bezwaren: [bezwaar],
  maxScore: bezwaar.scorePercentage ?? null,
});
```

Bij het toevoegen aan een bestaande groep:

```javascript
groep.bezwaren.push(bezwaar);
if (bezwaar.scorePercentage != null) {
  groep.maxScore = groep.maxScore != null
    ? Math.max(groep.maxScore, bezwaar.scorePercentage)
    : bezwaar.scorePercentage;
}
```

Na het groeperen, sorteer op maxScore (aflopend, nulls last):

```javascript
groepen.sort((a, b) => {
  if (a.maxScore == null && b.maxScore == null) return 0;
  if (a.maxScore == null) return 1;
  if (b.maxScore == null) return -1;
  return b.maxScore - a.maxScore;
});
```

**Step 2: Toon score in `_toonPassages` in bezwaarschriften-kernbezwaren.js**

Na het aanmaken van de passage-tekst div (rond de `_toonPassages` methode), voeg een score-label toe:

```javascript
if (groep.maxScore != null) {
  const scoreBadge = document.createElement('span');
  scoreBadge.style.cssText = 'display:inline-block;background:#e8ebee;border-radius:3px;padding:0.1rem 0.4rem;font-size:0.8rem;font-style:normal;color:#333;margin-left:0.5rem;';
  scoreBadge.textContent = `${groep.maxScore}%`;
  passage.appendChild(scoreBadge);
}
```

**Step 3: Schrijf Cypress test voor score in groepering**

Voeg een test toe aan `webapp/test/passage-groepering.cy.js`:

```javascript
it('berekent maxScore per groep en sorteert aflopend', () => {
  const bezwaren = [
    {bestandsnaam: '001.txt', passage: 'Geluidshinder is onaanvaardbaar', scorePercentage: 72},
    {bestandsnaam: '002.txt', passage: 'Geluidshinder is onaanvaardbaar', scorePercentage: 91},
    {bestandsnaam: '003.txt', passage: 'Verkeer neemt toe', scorePercentage: 85},
  ];
  const groepen = groepeerPassages(bezwaren);
  expect(groepen).to.have.length(2);
  // Gesorteerd: Geluidshinder (maxScore 91) eerst, dan Verkeer (85)
  expect(groepen[0].maxScore).to.equal(91);
  expect(groepen[1].maxScore).to.equal(85);
});

it('zet maxScore op null als geen scores beschikbaar', () => {
  const bezwaren = [
    {bestandsnaam: '001.txt', passage: 'Geen score bezwaar'},
  ];
  const groepen = groepeerPassages(bezwaren);
  expect(groepen[0].maxScore).to.be.null;
});
```

**Step 4: Draai de Cypress tests**

Run: `cd webapp && npm test`
Expected: All tests pass

**Step 5: Build frontend**

Run: `cd webapp && npm run build`
Expected: BUILD SUCCESS

**Step 6: Commit**

```bash
git add webapp/src/js/passage-groepering.js webapp/src/js/bezwaarschriften-kernbezwaren.js webapp/test/passage-groepering.cy.js
git commit -m "feat: toon centroid-score per passage-groep in frontend"
```

---

### Task 6: Integratietest en verificatie

**Files:**
- Bestaande integratietests controleren

**Step 1: Draai de volledige backend build inclusief tests**

Run: `mvn clean install -pl app`
Expected: BUILD SUCCESS

**Step 2: Draai integratietests (vereist Docker)**

Run: `mvn verify -pl app`
Expected: BUILD SUCCESS

**Step 3: Final commit als er fixes nodig waren**

---

### Test- en verificatieplan

1. **Unit tests (Task 4):**
   - Score-berekening geeft correcte percentages (cosinus-gelijkenis * 100)
   - Noise-bezwaren krijgen `null` als score
   - Referenties worden gesorteerd op score (aflopend, nulls last)
   - Bestaande tests blijven slagen met nieuwe record-parameter

2. **Frontend tests (Task 5):**
   - `groepeerPassages` berekent `maxScore` correct per groep
   - Groepen worden gesorteerd op `maxScore` (aflopend)
   - Null-scores worden correct afgehandeld

3. **Integratietest (Task 6):**
   - Liquibase migratie draait zonder fouten
   - End-to-end clustering slaat scores op in DB
   - GET kernbezwaren retourneert `scorePercentage` in JSON

4. **Handmatige verificatie:**
   - Start applicatie, cluster bezwaren, controleer dat scores zichtbaar zijn in side-sheet
   - Controleer dat passage-groepen gesorteerd zijn op score
