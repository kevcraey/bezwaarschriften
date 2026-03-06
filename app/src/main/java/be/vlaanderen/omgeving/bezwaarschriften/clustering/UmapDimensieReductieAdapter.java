package be.vlaanderen.omgeving.bezwaarschriften.clustering;

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tagbio.umap.Umap;

/**
 * Dimensiereductie-adapter die UMAP toepast via tag.bio/umap.
 *
 * <p>Reduceert hoog-dimensionale embedding-vectoren naar een compactere ruimte
 * zodat HDBSCAN beter kan clusteren.
 */
@Component
public class UmapDimensieReductieAdapter implements DimensieReductiePoort {

  private static final Logger LOG =
      LoggerFactory.getLogger(UmapDimensieReductieAdapter.class);

  private final ClusteringConfig config;

  public UmapDimensieReductieAdapter(ClusteringConfig config) {
    this.config = config;
  }

  @Override
  public List<float[]> reduceer(List<float[]> vectoren) {
    int minimaalAantal = config.getUmapNNeighbors() + 1;
    if (vectoren.size() < minimaalAantal) {
      LOG.warn("Te weinig datapunten ({}) voor UMAP (minimaal {}), "
          + "gebruik originele vectoren", vectoren.size(), minimaalAantal);
      return vectoren;
    }

    var umap = new Umap();
    umap.setNumberComponents(config.getUmapNComponents());
    umap.setNumberNearestNeighbours(config.getUmapNNeighbors());
    umap.setMinDist(config.getUmapMinDist());
    umap.setMetric("cosine");
    umap.setVerbose(false);

    double[][] invoer = naarDoubleMatrix(vectoren);
    double[][] resultaat = metOnderdrukteSysteemUitvoer(() -> umap.fitTransform(invoer));
    return naarFloatLijst(resultaat);
  }

  private double[][] naarDoubleMatrix(List<float[]> vectoren) {
    var matrix = new double[vectoren.size()][];
    for (int i = 0; i < vectoren.size(); i++) {
      var floats = vectoren.get(i);
      var doubles = new double[floats.length];
      for (int j = 0; j < floats.length; j++) {
        doubles[j] = floats[j];
      }
      matrix[i] = doubles;
    }
    return matrix;
  }

  /**
   * Voert een operatie uit terwijl System.out tijdelijk wordt onderdrukt.
   * De UMAP-library (tag.bio) logt rechtstreeks naar System.out via Utils.message()
   * en produceert onterecht "Update counter exceeded total" meldingen door een bug
   * in de interne progress-tracking.
   */
  private <T> T metOnderdrukteSysteemUitvoer(java.util.function.Supplier<T> operatie) {
    var origineel = System.out;
    try {
      System.setOut(new PrintStream(OutputStream.nullOutputStream()));
      return operatie.get();
    } finally {
      System.setOut(origineel);
    }
  }

  private List<float[]> naarFloatLijst(double[][] matrix) {
    var lijst = new ArrayList<float[]>(matrix.length);
    for (var rij : matrix) {
      var floats = new float[rij.length];
      for (int j = 0; j < rij.length; j++) {
        floats[j] = (float) rij[j];
      }
      lijst.add(floats);
    }
    return lijst;
  }
}
