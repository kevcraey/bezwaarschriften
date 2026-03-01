package be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/**
 * Mock-implementatie van {@link KernbezwaarPoort} die hardcoded thema's retourneert.
 *
 * <p>Genereert realistische mock data met meerdere thema's, kernbezwaren en
 * honderden individuele bezwaarreferenties om de UI te testen op schaal.
 */
@Component
public class MockKernbezwaarAdapter implements KernbezwaarPoort {

  private static final String[][] THEMAS = {
      {"Geluid", "Geluidsoverlast door nachtelijk vrachtverkeer",
          "Trillingen en laagfrequent geluid door zware machines",
          "Piekgeluiden bij laden en lossen van materiaal",
          "Constante achtergrondherrie door ventilatiesystemen"},
      {"Mobiliteit", "Verkeerscongestie op de N-weg door projectverkeer",
          "Onveilige verkeerssituaties voor fietsers en voetgangers",
          "Schade aan lokale weginfrastructuur door zwaar transport",
          "Parkeerproblemen in de omliggende woonwijken"},
      {"Geurhinder", "Aanhoudende geuroverlast bij zuidwestenwind",
          "Chemische geur bij opstarten van installaties",
          "Stankhinder vanuit afvalopslagplaats"},
      {"Gezondheid", "Fijnstofuitstoot boven de wettelijke normen",
          "Toename van luchtwegklachten bij omwonenden",
          "Slaapstoornissen door nachtelijke activiteiten"},
      {"Natuur en landschap",
          "Verstoring van beschermde broedgebieden",
          "Lichtvervuiling door permanente nachtverlichting",
          "Aantasting van het open landschapskarakter"},
      {"Waterhuishouding",
          "Verhoogd overstromingsrisico door verharding",
          "Vervuiling van de aangrenzende waterloop"},
      {"Waardevermindering",
          "Daling van vastgoedwaarden in de directe omgeving",
          "Verminderde leefbaarheid van de woonwijk",
          "Economische schade voor lokale horecazaken"},
  };

  private static final String[] PASSAGES = {
      "De geluidsoverlast is ondraaglijk, vooral 's nachts"
          + " wanneer het vrachtverkeer op volle toeren draait.",
      "Wij ondervinden ernstige hinder van nachtelijk"
          + " transport langs onze woning.",
      "De trillingen van de machines zijn voelbaar"
          + " tot in onze woonkamer.",
      "Het laagfrequent bromgeluid is dag en nacht aanwezig"
          + " en veroorzaakt hoofdpijn.",
      "Bij elke lading die gelost wordt, schrikt"
          + " het hele gezin wakker.",
      "De N-weg staat elke ochtend vast door de"
          + " vrachtwagens van het project.",
      "Het verkeer is sinds de start van het project"
          + " onhoudbaar geworden.",
      "Mijn kinderen kunnen niet meer veilig naar"
          + " school fietsen door het zware verkeer.",
      "De weg naar het centrum is volledig kapotgereden"
          + " door de zware transporten.",
      "Er is nergens meer plek om te parkeren"
          + " sinds de werknemers hier parkeren.",
      "Bij zuidwestenwind is de stank ondraaglijk en"
          + " moeten we ramen en deuren sluiten.",
      "De geurhinder maakt het onmogelijk om buiten"
          + " te zitten in de tuin.",
      "Wij klagen al jaren over de geuroverlast maar"
          + " er wordt niets aan gedaan.",
      "De chemische geur 's ochtends vroeg is"
          + " alarmerend en doet ons vrezen voor onze gezondheid.",
      "Onze was kunnen we niet meer buiten hangen"
          + " vanwege de stank.",
      "De luchtkwaliteit in onze straat is aantoonbaar"
          + " verslechterd sinds het project.",
      "Meerdere buren hebben luchtwegproblemen ontwikkeld"
          + " die er voorheen niet waren.",
      "Door de nachtelijke herrie slapen wij al maanden"
          + " slecht, met alle gevolgen van dien.",
      "De huisarts bevestigt dat onze klachten gerelateerd"
          + " zijn aan de omgevingsfactoren.",
      "Het fijnstof bedekt dagelijks onze auto's"
          + " en terrasmeubilair.",
      "De broedkolonie in het aangrenzende natuurgebied"
          + " is dit jaar niet teruggekeerd.",
      "De permanente nachtverlichting verstoort het"
          + " natuurlijke dag-nachtritme in de omgeving.",
      "Het open polderlandschap is volledig aangetast"
          + " door de industriele constructies.",
      "Vogels en vleermuizen zijn grotendeels verdwenen"
          + " uit onze omgeving.",
      "De beek achter ons huis is troebel geworden"
          + " en er drijft regelmatig schuim op het water.",
      "Bij hevige regen staat onze tuin onder water"
          + " sinds de verharding op het terrein.",
      "Onze woning is volgens de makelaar 15 tot 20"
          + " procent in waarde gedaald.",
      "Het restaurant aan het plein heeft 30 procent"
          + " minder klanten sinds de overlast.",
      "Niemand wil hier nog komen wonen, huizen staan"
          + " maanden te koop.",
      "De combinatie van geur, geluid en verkeer maakt"
          + " deze buurt onleefbaar.",
      "Wij eisen een onafhankelijk onderzoek naar de"
          + " gezondheidseffecten op lange termijn.",
      "Het MER-rapport bevat naar ons oordeel ernstige"
          + " lacunes op het vlak van cumulatieve effecten.",
      "De vergunde geluidsnormen worden structureel"
          + " overschreden, zoals onze eigen metingen aantonen.",
      "De beloofde compensatiemaatregelen zijn nooit"
          + " uitgevoerd.",
      "Wij verzoeken de vergunning te weigeren zolang"
          + " er geen afdoende oplossing is voor de waterafvoer.",
  };

  @Override
  public List<Thema> groepeer(List<BezwaarInvoer> bezwaarTeksten) {
    if (bezwaarTeksten.isEmpty()) {
      return List.of();
    }

    var bestandsnamen = bezwaarTeksten.stream()
        .map(BezwaarInvoer::bestandsnaam)
        .toList();

    var idTeller = new AtomicLong(1);
    var refIdTeller = new AtomicLong(1);
    var themas = new ArrayList<Thema>();

    for (String[] themaData : THEMAS) {
      var themaNaam = themaData[0];
      var kernbezwaren = new ArrayList<Kernbezwaar>();

      for (int k = 1; k < themaData.length; k++) {
        var samenvatting = themaData[k];
        int aantalRefs = 5 + (int) (idTeller.get() % 25);
        var refs = new ArrayList<IndividueelBezwaarReferentie>();

        for (int r = 0; r < aantalRefs; r++) {
          int passageIdx = (int) (refIdTeller.get() % PASSAGES.length);
          int bestandIdx = (int) (refIdTeller.get() % bestandsnamen.size());
          refs.add(new IndividueelBezwaarReferentie(
              refIdTeller.getAndIncrement(),
              bestandsnamen.get(bestandIdx),
              PASSAGES[passageIdx]));
        }

        kernbezwaren.add(new Kernbezwaar(
            idTeller.getAndIncrement(), samenvatting, refs));
      }

      themas.add(new Thema(themaNaam, kernbezwaren));
    }

    return themas;
  }
}
