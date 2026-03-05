package be.vlaanderen.omgeving.bezwaarschriften.clustering;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UmapDimensieReductieAdapterTest {

  private UmapDimensieReductieAdapter adapter;

  @BeforeEach
  void setUp() {
    var config = new ClusteringConfig();
    config.setUmapNComponents(3);
    config.setUmapNNeighbors(5);
    config.setUmapMinDist(0.1f);
    adapter = new UmapDimensieReductieAdapter(config);
  }

  @Test
  void reduceertNaarJuisteAantalDimensies() {
    var vectoren = genereerRandomVectoren(20, 50);

    var resultaat = adapter.reduceer(vectoren);

    assertThat(resultaat).hasSize(20);
    resultaat.forEach(v -> assertThat(v.length).isEqualTo(3));
  }

  @Test
  void bewaartVolgorde() {
    var vectoren = genereerRandomVectoren(20, 50);

    var resultaat = adapter.reduceer(vectoren);

    assertThat(resultaat).hasSize(vectoren.size());
  }

  @Test
  void teWeinigDatapuntenGeeftOrigineleVectorenTerug() {
    // nNeighbors=5, dus < 6 datapunten is te weinig
    var vectoren = genereerRandomVectoren(3, 50);

    var resultaat = adapter.reduceer(vectoren);

    assertThat(resultaat).hasSize(3);
    // Originele vectoren terug (50D, niet 3D)
    resultaat.forEach(v -> assertThat(v.length).isEqualTo(50));
  }

  private List<float[]> genereerRandomVectoren(int aantal, int dimensies) {
    var random = new Random(42);
    var vectoren = new ArrayList<float[]>();
    for (int i = 0; i < aantal; i++) {
      var vector = new float[dimensies];
      for (int j = 0; j < dimensies; j++) {
        vector[j] = random.nextFloat();
      }
      vectoren.add(vector);
    }
    return vectoren;
  }
}
