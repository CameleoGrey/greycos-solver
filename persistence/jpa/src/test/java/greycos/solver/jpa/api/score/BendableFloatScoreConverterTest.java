package greycos.solver.jpa.api.score;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.Convert;
import jakarta.persistence.Entity;

import greycos.solver.core.api.score.BendableFloatScore;
import greycos.solver.jpa.impl.AbstractScoreJpaTest;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class BendableFloatScoreConverterTest extends AbstractScoreJpaTest {

  @Test
  void persistAndMergeFiniteValuesAndStructuralScore() {
    for (float value :
        new float[] {0.0f, -0.0f, 0.1f, Math.nextUp(1.0f), Float.MIN_VALUE, Float.MAX_VALUE}) {
      persistAndMerge(
          new TestScoreEntity(BendableFloatScore.zero(2, 2)),
          null,
          new BendableFloatScore(
              -7L, new float[] {value, 0.1f}, new float[] {-value, Float.MIN_VALUE}));
    }
  }

  @Test
  void converterPreservesNullAndRejectsNonFiniteLevels() {
    var converter = new BendableFloatScoreConverter();
    assertThat(converter.convertToDatabaseColumn(null)).isNull();
    assertThat(converter.convertToEntityAttribute(null)).isNull();
    for (var token : new String[] {"NaN", "Infinity", "-Infinity", "1E99999"}) {
      var invalid = "[" + token + "]hard/[0.0]soft";
      assertThatThrownBy(() -> converter.convertToEntityAttribute(invalid))
          .isInstanceOf(IllegalArgumentException.class)
          .hasStackTraceContaining("finite");
    }
  }

  @Entity(name = "BendableFloatScoreTestEntity")
  static class TestScoreEntity extends AbstractTestJpaEntity<BendableFloatScore> {

    @Convert(converter = BendableFloatScoreConverter.class)
    protected BendableFloatScore score;

    TestScoreEntity() {}

    TestScoreEntity(BendableFloatScore score) {
      this.score = score;
    }

    @Override
    public BendableFloatScore getScore() {
      return score;
    }

    @Override
    public void setScore(BendableFloatScore score) {
      this.score = score;
    }
  }
}
