package be.vlaanderen.omgeving.bezwaarschriften.tekstextractie;

import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Splitst tekst op in chunks die elk binnen de Obscuro-limiet vallen.
 *
 * <p>Splitstrategie in volgorde van voorkeur:
 * <ol>
 *   <li>Dubbele newline (paragraaf-grens)</li>
 *   <li>Enkele newline (regelgrens)</li>
 *   <li>Harde split op de limiet</li>
 * </ol>
 */
@Component
public class PseudonimiseringChunker {

  private final int maxTekens;

  public PseudonimiseringChunker(
      @Value("${bezwaarschriften.pseudonimisering.max-tekens:100000}") int maxTekens) {
    this.maxTekens = maxTekens;
  }

  /**
   * Splitst de tekst in chunks van maximaal {@code maxTekens} tekens.
   *
   * @param tekst de te splitsen tekst
   * @return lijst van chunks (bij splits op \n\n-grenzen geldt: join met "\n\n" = origineel)
   */
  public List<String> chunk(String tekst) {
    if (tekst.length() <= maxTekens) {
      return List.of(tekst);
    }

    var chunks = new ArrayList<String>();
    var rest = tekst;

    while (rest.length() > maxTekens) {
      var zoekGebied = rest.substring(0, maxTekens);

      // Probeer te splitsen op dubbele newline
      int splitPunt = zoekGebied.lastIndexOf("\n\n");

      // Fallback: enkele newline
      if (splitPunt <= 0) {
        splitPunt = zoekGebied.lastIndexOf("\n");
      }

      // Harde split als geen newline gevonden
      if (splitPunt <= 0) {
        splitPunt = maxTekens;
      }

      chunks.add(rest.substring(0, splitPunt));

      // Sla de separator over bij het bepalen van de rest
      if (splitPunt < maxTekens && rest.startsWith("\n\n", splitPunt)) {
        rest = rest.substring(splitPunt + 2); // skip "\n\n"
      } else if (splitPunt < maxTekens && rest.charAt(splitPunt) == '\n') {
        rest = rest.substring(splitPunt + 1); // skip "\n"
      } else {
        rest = rest.substring(splitPunt);
      }
    }

    if (!rest.isEmpty()) {
      chunks.add(rest);
    }

    return chunks;
  }
}
