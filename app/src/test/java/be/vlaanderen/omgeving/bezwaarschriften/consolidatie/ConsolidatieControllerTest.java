package be.vlaanderen.omgeving.bezwaarschriften.consolidatie;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ConsolidatieController.class)
@WithMockUser
class ConsolidatieControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockBean
  private ConsolidatieTaakService consolidatieTaakService;

  @MockBean
  private ConsolidatieWorker consolidatieWorker;

  @MockBean
  private AntwoordStatusService antwoordStatusService;

  @Test
  void geeftConsolidatieStatusPerDocument() throws Exception {
    when(antwoordStatusService.berekenAntwoordStatus("windmolens"))
        .thenReturn(Map.of(
            "bezwaar-001.txt", new AntwoordStatus(2, 3),
            "bezwaar-002.txt", new AntwoordStatus(2, 2)));
    when(consolidatieTaakService.geefTaken("windmolens"))
        .thenReturn(List.of(
            new ConsolidatieTaakDto(1L, "windmolens", "bezwaar-002.txt", "klaar",
                0, "2026-03-02T10:00:00Z", "2026-03-02T10:01:00Z", null)));

    mockMvc.perform(get("/api/v1/projects/windmolens/consolidaties"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.documenten[?(@.bestandsnaam=='bezwaar-001.txt')].antwoordenAantal").value(2))
        .andExpect(jsonPath("$.documenten[?(@.bestandsnaam=='bezwaar-001.txt')].antwoordenTotaal").value(3))
        .andExpect(jsonPath("$.documenten[?(@.bestandsnaam=='bezwaar-001.txt')].status").value("onvolledig"))
        .andExpect(jsonPath("$.documenten[?(@.bestandsnaam=='bezwaar-002.txt')].status").value("klaar"));
  }

  @Test
  void dientConsolidatieTakenIn() throws Exception {
    when(consolidatieTaakService.indienen("windmolens", List.of("bezwaar-001.txt")))
        .thenReturn(List.of(
            new ConsolidatieTaakDto(1L, "windmolens", "bezwaar-001.txt", "wachtend",
                0, "2026-03-02T10:00:00Z", null, null)));

    mockMvc.perform(post("/api/v1/projects/windmolens/consolidaties")
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"bestandsnamen\":[\"bezwaar-001.txt\"]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.taken[0].bestandsnaam").value("bezwaar-001.txt"))
        .andExpect(jsonPath("$.taken[0].status").value("wachtend"));
  }

  @Test
  void annuleertConsolidatieTaak() throws Exception {
    mockMvc.perform(delete("/api/v1/projects/windmolens/consolidaties/1")
            .with(csrf()))
        .andExpect(status().isNoContent());

    verify(consolidatieTaakService).verwijderTaak("windmolens", 1L);
    verify(consolidatieWorker).annuleerTaak(1L);
  }
}
