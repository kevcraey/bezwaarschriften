package be.vlaanderen.omgeving.bezwaarschriften.consolidatie;

public record ConsolidatieTaakDto(
    Long id, String projectNaam, String bestandsnaam, String status,
    int aantalPogingen, String aangemaaktOp, String verwerkingGestartOp,
    String foutmelding) {

  static ConsolidatieTaakDto van(ConsolidatieTaak taak) {
    return new ConsolidatieTaakDto(
        taak.getId(), taak.getProjectNaam(), taak.getBestandsnaam(),
        statusNaarString(taak.getStatus()), taak.getAantalPogingen(),
        taak.getAangemaaktOp().toString(),
        taak.getVerwerkingGestartOp() != null ? taak.getVerwerkingGestartOp().toString() : null,
        taak.getFoutmelding());
  }

  private static String statusNaarString(ConsolidatieTaakStatus status) {
    return switch (status) {
      case WACHTEND -> "wachtend";
      case BEZIG -> "bezig";
      case KLAAR -> "klaar";
      case FOUT -> "fout";
    };
  }
}
