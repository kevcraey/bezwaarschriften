package be.vlaanderen.omgeving.bezwaarschriften.project;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
  void starktBatchverwerkingEnGeeftStatusTerug() throws Exception {
    when(projectService.verwerk("windmolens")).thenReturn(List.of(
        new BezwaarBestand("bezwaar-001.txt", BezwaarBestandStatus.EXTRACTIE_KLAAR)
    ));

    mockMvc.perform(post("/api/v1/projects/windmolens/verwerk").with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.bezwaren[0].bestandsnaam").value("bezwaar-001.txt"))
        .andExpect(jsonPath("$.bezwaren[0].status").value("extractie-klaar"));
  }

  @Test
  void geeft404VoorOnbekendProjectBijVerwerk() throws Exception {
    when(projectService.verwerk("bestaat-niet"))
        .thenThrow(new ProjectNietGevondenException("bestaat-niet"));

    mockMvc.perform(post("/api/v1/projects/bestaat-niet/verwerk").with(csrf()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.messages[0].code").value("project.not-found"));
  }
}
