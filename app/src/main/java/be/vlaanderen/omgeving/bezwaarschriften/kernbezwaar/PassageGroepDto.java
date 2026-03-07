package be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar;

import java.util.List;

public record PassageGroepDto(Long id, String passage, List<PassageGroepDocument> documenten) {}
