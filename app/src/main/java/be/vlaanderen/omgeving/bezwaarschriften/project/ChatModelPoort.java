package be.vlaanderen.omgeving.bezwaarschriften.project;

/**
 * Port voor communicatie met een taalmodel (LLM).
 *
 * <p>Hexagonale abstractie die onafhankelijk is van de concrete LLM-provider.
 * Kan geimplementeerd worden door Spring AI, een HTTP-client, of een fixture-mock.
 */
public interface ChatModelPoort {

  /**
   * Stuurt een prompt naar het taalmodel en retourneert het antwoord.
   *
   * @param systemPrompt De systeem-instructie voor het model
   * @param userPrompt De gebruikers-prompt met documentinhoud
   * @return Het ruwe antwoord van het model (verwacht: JSON)
   */
  String chat(String systemPrompt, String userPrompt);
}
