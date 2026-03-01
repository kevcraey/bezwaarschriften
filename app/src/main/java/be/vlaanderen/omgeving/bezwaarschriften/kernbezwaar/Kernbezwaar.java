package be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar;

import java.util.List;

public record Kernbezwaar(Long id, String samenvatting,
    List<IndividueelBezwaarReferentie> individueleBezwaren) {}
