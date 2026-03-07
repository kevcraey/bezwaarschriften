package be.vlaanderen.omgeving.bezwaarschriften.consolidatie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar.KernbezwaarAntwoordRepository;
import be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar.KernbezwaarEntiteit;
import be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar.KernbezwaarReferentieEntiteit;
import be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar.KernbezwaarReferentieRepository;
import be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar.KernbezwaarRepository;
import be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar.PassageGroepLidEntiteit;
import be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar.PassageGroepLidRepository;
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

  @Mock
  private KernbezwaarRepository kernbezwaarRepository;

  @Mock
  private PassageGroepLidRepository passageGroepLidRepository;

  private AntwoordStatusService service;

  @BeforeEach
  void setUp() {
    service = new AntwoordStatusService(referentieRepository, antwoordRepository,
        kernbezwaarRepository, passageGroepLidRepository);
  }

  @Test
  void berekentAntwoordStatusPerDocument() {
    // Referenties met passage-groep IDs
    var ref1 = maakRef(1L, 10L); // groep 1 → bezwaar-001.txt, kern 10
    var ref2 = maakRef(2L, 20L); // groep 2 → bezwaar-001.txt, kern 20
    var ref3 = maakRef(3L, 10L); // groep 3 → bezwaar-002.txt, kern 10
    var ref4 = maakRef(4L, 30L); // groep 4 → bezwaar-002.txt, kern 30

    when(referentieRepository.findByProjectNaam("windmolens"))
        .thenReturn(List.of(ref1, ref2, ref3, ref4));

    // Passage groep leden: koppelen groep-ID aan bestandsnaam
    when(passageGroepLidRepository.findByPassageGroepIdIn(anyList()))
        .thenReturn(List.of(
            maakLid(1L, "bezwaar-001.txt"),
            maakLid(2L, "bezwaar-001.txt"),
            maakLid(3L, "bezwaar-002.txt"),
            maakLid(4L, "bezwaar-002.txt")));

    when(antwoordRepository.findKernbezwaarIdsMetAntwoord(anyList()))
        .thenReturn(List.of(10L, 20L));
    when(kernbezwaarRepository.findAllById(anyList()))
        .thenReturn(List.of(
            maakKernbezwaar(10L, "Geluidshinder"),
            maakKernbezwaar(20L, "Motivering"),
            maakKernbezwaar(30L, "Advies milieuraad")));

    var resultaat = service.berekenAntwoordStatus("windmolens");

    assertThat(resultaat).hasSize(2);
    assertThat(resultaat.get("bezwaar-001.txt").aantalMetAntwoord()).isEqualTo(2);
    assertThat(resultaat.get("bezwaar-001.txt").totaal()).isEqualTo(2);
    assertThat(resultaat.get("bezwaar-001.txt").kernbezwaren()).hasSize(2);
    assertThat(resultaat.get("bezwaar-001.txt").kernbezwaren())
        .allMatch(AntwoordStatus.KernbezwaarInfo::beantwoord);
    assertThat(resultaat.get("bezwaar-002.txt").aantalMetAntwoord()).isEqualTo(1);
    assertThat(resultaat.get("bezwaar-002.txt").totaal()).isEqualTo(2);
    assertThat(resultaat.get("bezwaar-002.txt").kernbezwaren()).hasSize(2);
    assertThat(resultaat.get("bezwaar-002.txt").kernbezwaren())
        .extracting(AntwoordStatus.KernbezwaarInfo::samenvatting)
        .containsExactlyInAnyOrder("Geluidshinder", "Advies milieuraad");
  }

  @Test
  void retourneertLegeMapAlsGeenReferenties() {
    when(referentieRepository.findByProjectNaam("leeg")).thenReturn(List.of());

    var resultaat = service.berekenAntwoordStatus("leeg");

    assertThat(resultaat).isEmpty();
  }

  @Test
  void documentMetAlleAntwoordenIsVolledig() {
    var ref1 = maakRef(1L, 10L);
    when(referentieRepository.findByProjectNaam("p"))
        .thenReturn(List.of(ref1));
    when(passageGroepLidRepository.findByPassageGroepIdIn(anyList()))
        .thenReturn(List.of(maakLid(1L, "bezwaar-001.txt")));
    when(antwoordRepository.findKernbezwaarIdsMetAntwoord(anyList()))
        .thenReturn(List.of(10L));
    when(kernbezwaarRepository.findAllById(anyList()))
        .thenReturn(List.of(maakKernbezwaar(10L, "Geluidshinder")));

    var resultaat = service.berekenAntwoordStatus("p");

    assertThat(resultaat.get("bezwaar-001.txt").isVolledig()).isTrue();
  }

  @Test
  void documentZonderAntwoordenIsOnvolledig() {
    var ref1 = maakRef(1L, 10L);
    when(referentieRepository.findByProjectNaam("p"))
        .thenReturn(List.of(ref1));
    when(passageGroepLidRepository.findByPassageGroepIdIn(anyList()))
        .thenReturn(List.of(maakLid(1L, "bezwaar-001.txt")));
    when(antwoordRepository.findKernbezwaarIdsMetAntwoord(anyList()))
        .thenReturn(List.of());
    when(kernbezwaarRepository.findAllById(anyList()))
        .thenReturn(List.of(maakKernbezwaar(10L, "Geluidshinder")));

    var resultaat = service.berekenAntwoordStatus("p");

    assertThat(resultaat.get("bezwaar-001.txt").isVolledig()).isFalse();
  }

  private KernbezwaarReferentieEntiteit maakRef(Long passageGroepId, Long kernbezwaarId) {
    var ref = new KernbezwaarReferentieEntiteit();
    ref.setKernbezwaarId(kernbezwaarId);
    ref.setPassageGroepId(passageGroepId);
    return ref;
  }

  private PassageGroepLidEntiteit maakLid(Long passageGroepId, String bestandsnaam) {
    var lid = new PassageGroepLidEntiteit();
    lid.setPassageGroepId(passageGroepId);
    lid.setBezwaarId(passageGroepId * 100); // unique bezwaarId
    lid.setBestandsnaam(bestandsnaam);
    return lid;
  }

  private KernbezwaarEntiteit maakKernbezwaar(Long id, String samenvatting) {
    var kb = new KernbezwaarEntiteit();
    kb.setId(id);
    kb.setSamenvatting(samenvatting);
    return kb;
  }
}
