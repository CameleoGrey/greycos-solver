package greycos.solver.jpa.api.score;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.Convert;
import jakarta.persistence.Entity;

import greycos.solver.core.api.score.BendableDoubleScore;
import greycos.solver.jpa.impl.AbstractScoreJpaTest;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class BendableDoubleScoreConverterTest extends AbstractScoreJpaTest {

  @Test
  void persistAndMergeFiniteValuesAndStructuralScore() {
    for (double value :
        new double[] {0.0d, -0.0d, 0.1d, Math.nextUp(1.0d), Double.MIN_VALUE, Double.MAX_VALUE}) {
      persistAndMerge(
          new TestScoreEntity(BendableDoubleScore.zero(2, 2)),
          null,
          new BendableDoubleScore(
              -7L, new double[] {value, 0.1d}, new double[] {-value, Double.MIN_VALUE}));
    }
  }

  @Test
  void converterPreservesNullAndRejectsNonFiniteLevels() {
    var converter = new BendableDoubleScoreConverter();
    assertThat(converter.convertToDatabaseColumn(null)).isNull();
    assertThat(converter.convertToEntityAttribute(null)).isNull();
    for (var token : new String[] {"NaN", "Infinity", "-Infinity", "1E99999"}) {
      var invalid = "[" + token + "]hard/[0.0]soft";
      assertThatThrownBy(() -> converter.convertToEntityAttribute(invalid))
          .isInstanceOf(IllegalArgumentException.class)
          .hasStackTraceContaining("finite");
    }
  }

  @Entity(name = "BendableDoubleScoreTestEntity")
  static class TestScoreEntity extends AbstractTestJpaEntity<BendableDoubleScore> {

    @Convert(converter = BendableDoubleScoreConverter.class)
    protected BendableDoubleScore score;

    TestScoreEntity() {}

    TestScoreEntity(BendableDoubleScore score) {
      this.score = score;
    }

    @Override
    public BendableDoubleScore getScore() {
      return score;
    }

    @Override
    public void setScore(BendableDoubleScore score) {
      this.score = score;
    }
  }
}
