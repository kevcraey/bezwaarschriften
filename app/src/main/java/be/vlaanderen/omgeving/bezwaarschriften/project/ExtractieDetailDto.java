package be.vlaanderen.omgeving.bezwaarschriften.project;

import java.util.List;

public record ExtractieDetailDto(
    String bestandsnaam,
    int aantalBezwaren,
    List<BezwaarDetail> bezwaren) {

  public record BezwaarDetail(String samenvatting, String passage, boolean passageGevonden) { }
}
