package be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar;

public record IndividueelBezwaarReferentie(
    Long referentieId,
    String samenvatting,
    String passage,
    Integer scorePercentage,
    ToewijzingsMethode toewijzingsmethode,
    PassageGroepDto passageGroep) {}
