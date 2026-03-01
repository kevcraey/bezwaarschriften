package be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar;

import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Mock-implementatie van {@link KernbezwaarPoort} die hardcoded thema's retourneert.
 */
@Component
public class MockKernbezwaarAdapter implements KernbezwaarPoort {

  @Override
  public List<Thema> groepeer(List<BezwaarInvoer> bezwaarTeksten) {
    if (bezwaarTeksten.isEmpty()) {
      return List.of();
    }

    return List.of(
        new Thema("Geluid", List.of(
            new Kernbezwaar(1L,
                "Geluidshinder tijdens nachtelijke uren door vrachtverkeer",
                List.of(
                    new IndividueelBezwaarReferentie(1L,
                        "bezwaar1.txt",
                        "De geluidsoverlast is ondraaglijk, vooral"
                            + " 's nachts wanneer het vrachtverkeer"
                            + " op volle toeren draait."),
                    new IndividueelBezwaarReferentie(2L, "bezwaar3.txt",
                        "Wij ondervinden ernstige hinder van nachtelijk transport langs onze woning."))),
            new Kernbezwaar(2L,
                "Trillingen en laagfrequent geluid door zware machines",
                List.of(
                    new IndividueelBezwaarReferentie(3L, "bezwaar2.txt",
                        "De trillingen van de machines zijn voelbaar tot in onze woonkamer."),
                    new IndividueelBezwaarReferentie(4L, "bezwaar4.txt",
                        "Het laagfrequent bromgeluid is dag en nacht aanwezig."))))),
        new Thema("Mobiliteit", List.of(
            new Kernbezwaar(3L,
                "Verkeerscongestie op de N-weg door projectgerelateerd verkeer",
                List.of(
                    new IndividueelBezwaarReferentie(5L, "bezwaar1.txt",
                        "De N-weg staat elke ochtend vast door de vrachtwagens van het project."),
                    new IndividueelBezwaarReferentie(6L, "bezwaar5.txt",
                        "Het verkeer is sinds de start van het project onhoudbaar geworden."))),
            new Kernbezwaar(4L,
                "Onveilige verkeerssituaties voor fietsers en voetgangers",
                List.of(
                    new IndividueelBezwaarReferentie(7L, "bezwaar2.txt",
                        "Mijn kinderen kunnen niet meer veilig naar school fietsen."))))),
        new Thema("Geurhinder", List.of(
            new Kernbezwaar(5L,
                "Aanhoudende geuroverlast vanuit de fabriek bij bepaalde windrichtingen",
                List.of(
                    new IndividueelBezwaarReferentie(8L, "bezwaar3.txt",
                        "Bij zuidwestenwind is de stank ondraaglijk en moeten we ramen en deuren sluiten."),
                    new IndividueelBezwaarReferentie(9L, "bezwaar4.txt",
                        "De geurhinder maakt het onmogelijk om buiten te zitten in de tuin."),
                    new IndividueelBezwaarReferentie(10L, "bezwaar5.txt",
                        "Wij klagen al jaren over de geuroverlast maar er wordt niets aan gedaan."))))));
  }
}
