package greycos.solver.jpa.api.score;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.Convert;
import jakarta.persistence.Entity;

import greycos.solver.core.api.score.HardSoftDoubleScore;
import greycos.solver.jpa.impl.AbstractScoreJpaTest;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class HardSoftDoubleScoreConverterTest extends AbstractScoreJpaTest {

  @Test
  void persistAndMergeFiniteValuesAndStructuralScore() {
    for (double value :
        new double[] {0.0d, -0.0d, 0.1d, Math.nextUp(1.0d), Double.MIN_VALUE, Double.MAX_VALUE}) {
      persistAndMerge(
          new TestScoreEntity(HardSoftDoubleScore.ZERO),
          null,
          new HardSoftDoubleScore(-7L, value, -value));
    }
  }

  @Test
  void converterPreservesNullAndRejectsNonFiniteLevels() {
    var converter = new HardSoftDoubleScoreConverter();
    assertThat(converter.convertToDatabaseColumn(null)).isNull();
    assertThat(converter.convertToEntityAttribute(null)).isNull();
    for (var token : new String[] {"NaN", "Infinity", "-Infinity", "1E99999"}) {
      var invalid = token + "hard/0.0soft";
      assertThatThrownBy(() -> converter.convertToEntityAttribute(invalid))
          .isInstanceOf(IllegalArgumentException.class)
          .hasStackTraceContaining("finite");
    }
  }

  @Entity(name = "HardSoftDoubleScoreTestEntity")
  static class TestScoreEntity extends AbstractTestJpaEntity<HardSoftDoubleScore> {

    @Convert(converter = HardSoftDoubleScoreConverter.class)
    protected HardSoftDoubleScore score;

    TestScoreEntity() {}

    TestScoreEntity(HardSoftDoubleScore score) {
      this.score = score;
    }

    @Override
    public HardSoftDoubleScore getScore() {
      return score;
    }

    @Override
    public void setScore(HardSoftDoubleScore score) {
      this.score = score;
    }
  }
}
