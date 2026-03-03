package be.vlaanderen.omgeving.bezwaarschriften.project;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ExtractieController.class)
@WithMockUser
class ExtractieControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockBean
  private ExtractieTaakService extractieTaakService;

  @MockBean
  private ExtractieWorker extractieWorker;

  @Test
  void dientExtractieTakenIn() throws Exception {
    when(extractieTaakService.indienen("windmolens", List.of("a.txt", "b.txt")))
        .thenReturn(List.of(
            new ExtractieTaakDto(1L, "windmolens", "a.txt", "wachtend",
                0, "2026-02-28T10:00:00Z", null, null, null, null, false, false),
            new ExtractieTaakDto(2L, "windmolens", "b.txt", "wachtend",
                0, "2026-02-28T10:00:00Z", null, null, null, null, false, false)
        ));

    mockMvc.perform(post("/api/v1/projects/windmolens/extracties")
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"bestandsnamen\":[\"a.txt\",\"b.txt\"]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.taken[0].bestandsnaam").value("a.txt"))
        .andExpect(jsonPath("$.taken[0].status").value("wachtend"))
        .andExpect(jsonPath("$.taken[1].bestandsnaam").value("b.txt"))
        .andExpect(jsonPath("$.taken[1].status").value("wachtend"));
  }

  @Test
  void geeftExtractieTakenVoorProject() throws Exception {
    when(extractieTaakService.geefTaken("windmolens"))
        .thenReturn(List.of(
            new ExtractieTaakDto(1L, "windmolens", "bezwaar-001.txt", "klaar",
                0, "2026-02-28T10:00:00Z", "2026-02-28T10:01:00Z",
                150, 3, null, false, false)
        ));

    mockMvc.perform(get("/api/v1/projects/windmolens/extracties"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.taken[0].bestandsnaam").value("bezwaar-001.txt"))
        .andExpect(jsonPath("$.taken[0].status").value("klaar"))
        .andExpect(jsonPath("$.taken[0].aantalWoorden").value(150))
        .andExpect(jsonPath("$.taken[0].aantalBezwaren").value(3));
  }

  @Test
  void verwerkenPlantOnafgerondeTakenIn() throws Exception {
    when(extractieTaakService.verwerkOnafgeronde("windmolens")).thenReturn(5);

    mockMvc.perform(post("/api/v1/projects/windmolens/extracties/verwerken")
            .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.aantalIngepland").value(5));
  }

  @Test
  void annuleertExtractieTaak() throws Exception {
    mockMvc.perform(delete("/api/v1/projects/windmolens/extracties/1")
            .with(csrf()))
        .andExpect(status().isNoContent());

    verify(extractieTaakService).verwijderTaak("windmolens", 1L);
    verify(extractieWorker).annuleerTaak(1L);
  }

  @Test
  void geeftExtractieDetailsVoorBestand() throws Exception {
    var detail = new ExtractieDetailDto("bezwaar-001.txt", 2, List.of(
        new ExtractieDetailDto.BezwaarDetail(
            1L, "Geluidshinder door evenementen", "De geluidsoverlast zal...", true, false),
        new ExtractieDetailDto.BezwaarDetail(
            2L, "Parkeertekort", "Er zijn onvoldoende parkeerplaatsen...", true, false)));

    when(extractieTaakService.geefExtractieDetails("windmolens", "bezwaar-001.txt"))
        .thenReturn(detail);

    mockMvc.perform(get("/api/v1/projects/windmolens/extracties/bezwaar-001.txt/details"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.bestandsnaam").value("bezwaar-001.txt"))
        .andExpect(jsonPath("$.aantalBezwaren").value(2))
        .andExpect(jsonPath("$.bezwaren[0].samenvatting").value("Geluidshinder door evenementen"))
        .andExpect(jsonPath("$.bezwaren[0].passage").value("De geluidsoverlast zal..."))
        .andExpect(jsonPath("$.bezwaren[1].samenvatting").value("Parkeertekort"));
  }

  @Test
  void geeftExtractieDetails404AlsGeenResultaat() throws Exception {
    when(extractieTaakService.geefExtractieDetails("windmolens", "onbekend.txt"))
        .thenReturn(null);

    mockMvc.perform(get("/api/v1/projects/windmolens/extracties/onbekend.txt/details"))
        .andExpect(status().isNotFound());
  }

  @Test
  void annulerenGeeft404BijOnbekendeTaak() throws Exception {
    doThrow(new IllegalArgumentException("Taak niet gevonden"))
        .when(extractieTaakService).verwijderTaak("windmolens", 999L);

    mockMvc.perform(delete("/api/v1/projects/windmolens/extracties/999")
            .with(csrf()))
        .andExpect(status().isNotFound());
  }

  @Test
  void geeftExtractieDetailsMetPassageNietGevonden() throws Exception {
    var detail = new ExtractieDetailDto("bezwaar-002.txt", 2, List.of(
        new ExtractieDetailDto.BezwaarDetail(
            1L, "Geluidshinder", "De geluidsoverlast zal...", false, false),
        new ExtractieDetailDto.BezwaarDetail(
            2L, "Parkeertekort", "Er zijn onvoldoende...", true, false)));

    when(extractieTaakService.geefExtractieDetails("windmolens", "bezwaar-002.txt"))
        .thenReturn(detail);

    mockMvc.perform(get("/api/v1/projects/windmolens/extracties/bezwaar-002.txt/details"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.bezwaren[0].passageGevonden").value(false))
        .andExpect(jsonPath("$.bezwaren[1].passageGevonden").value(true));
  }

  @Test
  void voegManueelBezwaarToe() throws Exception {
    var detail = new ExtractieDetailDto.BezwaarDetail(
        10L, "Geluidshinder", "De geluidsoverlast zal...", true, true);
    when(extractieTaakService.voegManueelBezwaarToe(
        "windmolens", "bezwaar-001.txt", "Geluidshinder", "De geluidsoverlast zal..."))
        .thenReturn(detail);

    mockMvc.perform(post("/api/v1/projects/windmolens/extracties/bezwaar-001.txt/bezwaren")
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"samenvatting\":\"Geluidshinder\",\"passage\":\"De geluidsoverlast zal...\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(10))
        .andExpect(jsonPath("$.samenvatting").value("Geluidshinder"))
        .andExpect(jsonPath("$.manueel").value(true));
  }

  @Test
  void voegManueelBezwaarToeGeeft400BijOngeldigePassage() throws Exception {
    when(extractieTaakService.voegManueelBezwaarToe(
        "windmolens", "bezwaar-001.txt", "Samenvatting", "Onbekende passage"))
        .thenThrow(new IllegalArgumentException("Passage komt niet voor in het originele document"));

    mockMvc.perform(post("/api/v1/projects/windmolens/extracties/bezwaar-001.txt/bezwaren")
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"samenvatting\":\"Samenvatting\",\"passage\":\"Onbekende passage\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fout").value("Passage komt niet voor in het originele document"));
  }

  @Test
  void verwijderBezwaarGeeft204() throws Exception {
    mockMvc.perform(delete("/api/v1/projects/windmolens/extracties/bezwaar-001.txt/bezwaren/10")
            .with(csrf()))
        .andExpect(status().isNoContent());

    verify(extractieTaakService).verwijderBezwaar("windmolens", "bezwaar-001.txt", 10L);
  }


  @Test
  void verwijderBezwaarGeeft404BijOnbekendBezwaar() throws Exception {
    doThrow(new IllegalArgumentException("Bezwaar niet gevonden"))
        .when(extractieTaakService).verwijderBezwaar("windmolens", "bezwaar-001.txt", 999L);

    mockMvc.perform(delete("/api/v1/projects/windmolens/extracties/bezwaar-001.txt/bezwaren/999")
            .with(csrf()))
        .andExpect(status().isNotFound());
  }
}
