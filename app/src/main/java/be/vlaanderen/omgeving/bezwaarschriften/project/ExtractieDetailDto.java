package be.vlaanderen.omgeving.bezwaarschriften.project;

import java.util.List;

public record ExtractieDetailDto(
    String bestandsnaam,
    int aantalBezwaren,
    List<BezwaarDetail> bezwaren) {

  public record BezwaarDetail(Long id, String samenvatting, String passage,
      boolean passageGevonden, boolean manueel) { }
}
