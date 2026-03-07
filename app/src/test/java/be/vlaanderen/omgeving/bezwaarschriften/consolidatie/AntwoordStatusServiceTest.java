package be.vlaanderen.omgeving.bezwaarschriften.consolidatie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar.KernbezwaarAntwoordRepository;
import be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar.KernbezwaarEntiteit;
import be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar.KernbezwaarReferentieEntiteit;
import be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar.KernbezwaarReferentieRepository;
import be.vlaanderen.omgeving.bezwaarschriften.kernbezwaar.KernbezwaarRepository;
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

  private AntwoordStatusService service;

  @BeforeEach
  void setUp() {
    service = new AntwoordStatusService(referentieRepository, antwoordRepository,
        kernbezwaarRepository);
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
    var ref1 = maakRef("bezwaar-001.txt", 10L);
    when(referentieRepository.findByProjectNaam("p"))
        .thenReturn(List.of(ref1));
    when(antwoordRepository.findKernbezwaarIdsMetAntwoord(anyList()))
        .thenReturn(List.of(10L));
    when(kernbezwaarRepository.findAllById(anyList()))
        .thenReturn(List.of(maakKernbezwaar(10L, "Geluidshinder")));

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
    when(kernbezwaarRepository.findAllById(anyList()))
        .thenReturn(List.of(maakKernbezwaar(10L, "Geluidshinder")));

    var resultaat = service.berekenAntwoordStatus("p");

    assertThat(resultaat.get("bezwaar-001.txt").isVolledig()).isFalse();
  }

  // TODO: task 8 - bestandsnaam ophalen via passage_groep_lid
  private KernbezwaarReferentieEntiteit maakRef(String bestandsnaam, Long kernbezwaarId) {
    var ref = new KernbezwaarReferentieEntiteit();
    ref.setKernbezwaarId(kernbezwaarId);
    ref.setPassageGroepId(0L);
    return ref;
  }

  private KernbezwaarEntiteit maakKernbezwaar(Long id, String samenvatting) {
    var kb = new KernbezwaarEntiteit();
    kb.setId(id);
    kb.setSamenvatting(samenvatting);
    return kb;
  }
}
