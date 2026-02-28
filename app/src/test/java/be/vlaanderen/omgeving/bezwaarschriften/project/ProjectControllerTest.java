package be.vlaanderen.omgeving.bezwaarschriften.project;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProjectController.class)
@WithMockUser
class ProjectControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockBean
  private ProjectService projectService;

  @Test
  void geeftProjectenTerug() throws Exception {
    when(projectService.geefProjecten()).thenReturn(List.of("windmolens", "zonnepanelen"));

    mockMvc.perform(get("/api/v1/projects"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.projecten[0]").value("windmolens"))
        .andExpect(jsonPath("$.projecten[1]").value("zonnepanelen"));
  }

  @Test
  void geeftLegeProjectenLijstTerug() throws Exception {
    when(projectService.geefProjecten()).thenReturn(List.of());

    mockMvc.perform(get("/api/v1/projects"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.projecten").isEmpty());
  }

  @Test
  void geeftBezwarenTerugVoorProject() throws Exception {
    when(projectService.geefBezwaren("windmolens")).thenReturn(List.of(
        new BezwaarBestand("bezwaar-001.txt", BezwaarBestandStatus.TODO),
        new BezwaarBestand("bijlage.pdf", BezwaarBestandStatus.NIET_ONDERSTEUND)
    ));

    mockMvc.perform(get("/api/v1/projects/windmolens/bezwaren"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.bezwaren[0].bestandsnaam").value("bezwaar-001.txt"))
        .andExpect(jsonPath("$.bezwaren[0].status").value("todo"))
        .andExpect(jsonPath("$.bezwaren[1].bestandsnaam").value("bijlage.pdf"))
        .andExpect(jsonPath("$.bezwaren[1].status").value("niet ondersteund"));
  }

  @Test
  void geeft404VoorOnbekendProject() throws Exception {
    when(projectService.geefBezwaren("bestaat-niet"))
        .thenThrow(new ProjectNietGevondenException("bestaat-niet"));

    mockMvc.perform(get("/api/v1/projects/bestaat-niet/bezwaren"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.messages[0].code").value("project.not-found"))
        .andExpect(jsonPath("$.messages[0].parameters.naam").value("bestaat-niet"));
  }

  @Test
  void downloadBestand_stuurtBestandAlsBijlage() throws Exception {
    Path tempFile = Files.createTempFile("test", ".txt");
    Files.writeString(tempFile, "testinhoud");
    when(projectService.geefBestandsPad("windmolens", "bezwaar1.txt")).thenReturn(tempFile);

    mockMvc.perform(get("/api/v1/projects/windmolens/bezwaren/bezwaar1.txt/download"))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Disposition", "attachment; filename=\"bezwaar1.txt\""))
        .andExpect(content().string("testinhoud"));

    Files.deleteIfExists(tempFile);
  }

  @Test
  void downloadBestand_geeft404VoorOnbekendBestand() throws Exception {
    when(projectService.geefBestandsPad("windmolens", "onbekend.txt"))
        .thenThrow(new BestandNietGevondenException("onbekend.txt"));

    mockMvc.perform(get("/api/v1/projects/windmolens/bezwaren/onbekend.txt/download"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.messages[0].code").value("bestand.not-found"));
  }

  @Test
  void downloadBestand_geeft400BijOngeldigeBestandsnaam() throws Exception {
    when(projectService.geefBestandsPad(eq("windmolens"), eq("kwaadaardig.txt")))
        .thenThrow(new IllegalArgumentException("Ongeldige bestandsnaam: kwaadaardig.txt"));

    mockMvc.perform(get("/api/v1/projects/windmolens/bezwaren/kwaadaardig.txt/download"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.messages[0].code").value("invalid.argument"));
  }
}
