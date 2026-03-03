package be.vlaanderen.omgeving.bezwaarschriften.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class VectorTypeTest {

  private final VectorType vectorType = new VectorType();

  @Test
  void returnedClassIsFloatArray() {
    assertThat(vectorType.returnedClass()).isEqualTo(float[].class);
  }

  @Test
  void deepCopyCreatesIndependentArray() {
    var original = new float[]{1.0f, 2.0f, 3.0f};
    var copy = (float[]) vectorType.deepCopy(original);
    assertThat(copy).isEqualTo(original);
    copy[0] = 99.0f;
    assertThat(original[0]).isEqualTo(1.0f);
  }

  @Test
  void deepCopyOfNullReturnsNull() {
    assertThat(vectorType.deepCopy(null)).isNull();
  }

  @Test
  void equalsComparesArrayContents() {
    var vectorA = new float[]{1.0f, 2.0f};
    var vectorB = new float[]{1.0f, 2.0f};
    var vectorC = new float[]{3.0f, 4.0f};
    assertThat(vectorType.equals(vectorA, vectorB)).isTrue();
    assertThat(vectorType.equals(vectorA, vectorC)).isFalse();
    assertThat(vectorType.equals(null, null)).isTrue();
    assertThat(vectorType.equals(vectorA, null)).isFalse();
  }
}
