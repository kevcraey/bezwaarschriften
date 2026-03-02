package be.vlaanderen.omgeving.bezwaarschriften.consolidatie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar.KernbezwaarAntwoordRepository;
import be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar.KernbezwaarReferentieEntiteit;
import be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar.KernbezwaarReferentieRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AntwoordStatusServiceTest {

  @Mock
  private KernbezwaarReferentieRepository referentieRepository;

  @Mock
  private KernbezwaarAntwoordRepository antwoordRepository;

  private AntwoordStatusService service;

  @BeforeEach
  void setUp() {
    service = new AntwoordStatusService(referentieRepository, antwoordRepository);
  }

  @Test
  void berekentAntwoordStatusPerDocument() {
    var ref1 = maakRef("bezwaar-001.txt", 10L);
    var ref2 = maakRef("bezwaar-001.txt", 20L);
    var ref3 = maakRef("bezwaar-002.txt", 10L);
    var ref4 = maakRef("bezwaar-002.txt", 30L);

    when(referentieRepository.findByProjectNaam("windmolens"))
        .thenReturn(List.of(ref1, ref2, ref3, ref4));
    when(antwoordRepository.findKernbezwaarIdsMetAntwoord(anyList()))
        .thenReturn(List.of(10L, 20L));

    var resultaat = service.berekenAntwoordStatus("windmolens");

    assertThat(resultaat).hasSize(2);
    assertThat(resultaat.get("bezwaar-001.txt").aantalMetAntwoord()).isEqualTo(2);
    assertThat(resultaat.get("bezwaar-001.txt").totaal()).isEqualTo(2);
    assertThat(resultaat.get("bezwaar-002.txt").aantalMetAntwoord()).isEqualTo(1);
    assertThat(resultaat.get("bezwaar-002.txt").totaal()).isEqualTo(2);
  }

  @Test
  void retourneertLegeMapAlsGeenReferenties() {
    when(referentieRepository.findByProjectNaam("leeg")).thenReturn(List.of());

    var resultaat = service.berekenAntwoordStatus("leeg");

    assertThat(resultaat).isEmpty();
  }

  @Test
  void documentMetAlleAntwoordenIsVolledig() {
    var ref1 = maakRef("bezwaar-001.txt", 10L);
    when(referentieRepository.findByProjectNaam("p"))
        .thenReturn(List.of(ref1));
    when(antwoordRepository.findKernbezwaarIdsMetAntwoord(anyList()))
        .thenReturn(List.of(10L));

    var resultaat = service.berekenAntwoordStatus("p");

    assertThat(resultaat.get("bezwaar-001.txt").isVolledig()).isTrue();
  }

  @Test
  void documentZonderAntwoordenIsOnvolledig() {
    var ref1 = maakRef("bezwaar-001.txt", 10L);
    when(referentieRepository.findByProjectNaam("p"))
        .thenReturn(List.of(ref1));
    when(antwoordRepository.findKernbezwaarIdsMetAntwoord(anyList()))
        .thenReturn(List.of());

    var resultaat = service.berekenAntwoordStatus("p");

    assertThat(resultaat.get("bezwaar-001.txt").isVolledig()).isFalse();
  }

  private KernbezwaarReferentieEntiteit maakRef(String bestandsnaam, Long kernbezwaarId) {
    var ref = new KernbezwaarReferentieEntiteit();
    ref.setBestandsnaam(bestandsnaam);
    ref.setKernbezwaarId(kernbezwaarId);
    ref.setPassage("test");
    return ref;
  }
}
