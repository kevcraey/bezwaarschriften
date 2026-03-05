package be.vlaanderen.omgeving.bezwaarschriften.clustering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import be.vlaanderen.omgeving.bezwaarschriften.clustering.ClusteringConfigController.ConfigDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class ClusteringConfigControllerTest {

  @Spy
  private ClusteringConfig config;

  @InjectMocks
  private ClusteringConfigController controller;

  @Test
  void geefBevatUmapVelden() {
    var response = controller.geef();

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    var dto = response.getBody();
    assertThat(dto.umapEnabled()).isTrue();
    assertThat(dto.umapNComponents()).isEqualTo(5);
    assertThat(dto.umapNNeighbors()).isEqualTo(15);
    assertThat(dto.umapMinDist()).isEqualTo(0.1f);
  }

  @Test
  void updatePastUmapVeldenAan() {
    var dto = new ConfigDto(3, 2, 0.5, false, 10, 30, 0.5f);

    var response = controller.update(dto);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    verify(config).setUmapEnabled(false);
    verify(config).setUmapNComponents(10);
    verify(config).setUmapNNeighbors(30);
    verify(config).setUmapMinDist(0.5f);
  }
}
