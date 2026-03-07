package be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar;

public record IndividueelBezwaarReferentie(
    Long referentieId,
    Long bezwaarId,
    String bestandsnaam,
    String passage,
    Integer scorePercentage,
    ToewijzingsMethode toewijzingsmethode) {}
