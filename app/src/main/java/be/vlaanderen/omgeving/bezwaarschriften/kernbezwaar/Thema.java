package be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar;

import java.util.List;

public record Thema(String naam, List<Kernbezwaar> kernbezwaren) {}
