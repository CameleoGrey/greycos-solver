package greycos.solver.jpa.api.score;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.Convert;
import jakarta.persistence.Entity;

import greycos.solver.core.api.score.HardMediumSoftFloatScore;
import greycos.solver.jpa.impl.AbstractScoreJpaTest;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class HardMediumSoftFloatScoreConverterTest extends AbstractScoreJpaTest {

  @Test
  void persistAndMergeFiniteValuesAndStructuralScore() {
    for (float value :
        new float[] {0.0f, -0.0f, 0.1f, Math.nextUp(1.0f), Float.MIN_VALUE, Float.MAX_VALUE}) {
      persistAndMerge(
          new TestScoreEntity(HardMediumSoftFloatScore.ZERO),
          null,
          new HardMediumSoftFloatScore(-7L, value, 0.1f, -value));
    }
  }

  @Test
  void converterPreservesNullAndRejectsNonFiniteLevels() {
    var converter = new HardMediumSoftFloatScoreConverter();
    assertThat(converter.convertToDatabaseColumn(null)).isNull();
    assertThat(converter.convertToEntityAttribute(null)).isNull();
    for (var token : new String[] {"NaN", "Infinity", "-Infinity", "1E99999"}) {
      var invalid = token + "hard/0.0medium/0.0soft";
      assertThatThrownBy(() -> converter.convertToEntityAttribute(invalid))
          .isInstanceOf(IllegalArgumentException.class)
          .hasStackTraceContaining("finite");
    }
  }

  @Entity(name = "HardMediumSoftFloatScoreTestEntity")
  static class TestScoreEntity extends AbstractTestJpaEntity<HardMediumSoftFloatScore> {

    @Convert(converter = HardMediumSoftFloatScoreConverter.class)
    protected HardMediumSoftFloatScore score;

    TestScoreEntity() {}

    TestScoreEntity(HardMediumSoftFloatScore score) {
      this.score = score;
    }

    @Override
    public HardMediumSoftFloatScore getScore() {
      return score;
    }

    @Override
    public void setScore(HardMediumSoftFloatScore score) {
      this.score = score;
    }
  }
}
