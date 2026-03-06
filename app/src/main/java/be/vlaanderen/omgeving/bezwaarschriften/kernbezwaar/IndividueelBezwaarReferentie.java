package be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar;

public record IndividueelBezwaarReferentie(
    Long bezwaarId,
    String bestandsnaam,
    String passage,
    Integer scorePercentage) {}
