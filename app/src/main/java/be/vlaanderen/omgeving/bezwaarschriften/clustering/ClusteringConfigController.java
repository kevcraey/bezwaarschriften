package be.vlaanderen.omgeving.bezwaarschriften.clustering;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller voor het live aanpassen van clustering-parameters.
 * Bedoeld voor experimenteren tijdens ontwikkeling.
 */
@RestController
@RequestMapping("/api/v1/clustering-config")
public class ClusteringConfigController {

  private final ClusteringConfig config;

  public ClusteringConfigController(ClusteringConfig config) {
    this.config = config;
  }

  @GetMapping
  public ResponseEntity<ConfigDto> geef() {
    return ResponseEntity.ok(new ConfigDto(
        config.getMinClusterSize(),
        config.getMinSamples(),
        config.getClusterSelectionEpsilon()));
  }

  @PutMapping
  public ResponseEntity<ConfigDto> update(@RequestBody ConfigDto dto) {
    config.setMinClusterSize(dto.minClusterSize());
    config.setMinSamples(dto.minSamples());
    config.setClusterSelectionEpsilon(dto.clusterSelectionEpsilon());
    return ResponseEntity.ok(dto);
  }

  public record ConfigDto(int minClusterSize, int minSamples, double clusterSelectionEpsilon) {}
}
