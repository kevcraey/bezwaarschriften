package be.vlaanderen.omgeving.bezwaarschriften.project;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.http.MediaType;
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
  void geeftProjectenMetAantalDocumentenTerug() throws Exception {
    when(projectService.geefProjectenMetAantalDocumenten()).thenReturn(List.of(
        new ProjectService.ProjectOverzicht("windmolens", 42),
        new ProjectService.ProjectOverzicht("zonnepanelen", 7)
    ));

    mockMvc.perform(get("/api/v1/projects"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.projecten[0].naam").value("windmolens"))
        .andExpect(jsonPath("$.projecten[0].aantalDocumenten").value(42))
        .andExpect(jsonPath("$.projecten[1].naam").value("zonnepanelen"))
        .andExpect(jsonPath("$.projecten[1].aantalDocumenten").value(7));
  }

  @Test
  void maaktProjectAan() throws Exception {
    mockMvc.perform(post("/api/v1/projects")
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"naam\": \"nieuw-project\"}"))
        .andExpect(status().isCreated());

    verify(projectService).maakProjectAan("nieuw-project");
  }

  @Test
  void maakProjectAan_geeft400AlsProjectAlBestaat() throws Exception {
    doThrow(new IllegalArgumentException("Project bestaat al: bestaand"))
        .when(projectService).maakProjectAan("bestaand");

    mockMvc.perform(post("/api/v1/projects")
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"naam\": \"bestaand\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.messages[0].code").value("invalid.argument"));
  }

  @Test
  void verwijdertProject() throws Exception {
    when(projectService.verwijderProject("oud-project")).thenReturn(true);

    mockMvc.perform(delete("/api/v1/projects/oud-project")
            .with(csrf()))
        .andExpect(status().isNoContent());
  }

  @Test
  void verwijderProject_geeft404AlsProjectNietBestaat() throws Exception {
    when(projectService.verwijderProject("bestaat-niet")).thenReturn(false);

    mockMvc.perform(delete("/api/v1/projects/bestaat-niet")
            .with(csrf()))
        .andExpect(status().isNotFound());
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

  @Test
  void geeftTekstExtractieFoutmeldingMeeInResponse() throws Exception {
    var bezwaar = new BezwaarBestand("bezwaar-001.pdf", BezwaarBestandStatus.TEKST_EXTRACTIE_MISLUKT,
        null, null, false, false, null, null, null, null,
        "Te weinig woorden: 28 (minimum 40)");
    when(projectService.geefBezwaren("windmolens")).thenReturn(List.of(bezwaar));

    mockMvc.perform(get("/api/v1/projects/windmolens/bezwaren"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.bezwaren[0].status").value("tekst-extractie-mislukt"))
        .andExpect(jsonPath("$.bezwaren[0].tekstExtractieFoutmelding")
            .value("Te weinig woorden: 28 (minimum 40)"));
  }

  @Test
  void verwijdertMeerdereBezwaren() throws Exception {
    when(projectService.verwijderBezwaren("windmolens", List.of("doc-a.txt", "doc-b.txt")))
        .thenReturn(2);

    mockMvc.perform(delete("/api/v1/projects/windmolens/bezwaren")
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"bestandsnamen\":[\"doc-a.txt\",\"doc-b.txt\"]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.aantalVerwijderd").value(2));
  }

  @Test
  void verwijderBezwaren_geeft400BijLegeLijst() throws Exception {
    mockMvc.perform(delete("/api/v1/projects/windmolens/bezwaren")
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"bestandsnamen\":[]}"))
        .andExpect(status().isBadRequest());
  }
}
