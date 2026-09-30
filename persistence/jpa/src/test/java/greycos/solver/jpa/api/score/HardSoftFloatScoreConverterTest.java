package greycos.solver.jpa.api.score;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.Convert;
import jakarta.persistence.Entity;

import greycos.solver.core.api.score.HardSoftFloatScore;
import greycos.solver.jpa.impl.AbstractScoreJpaTest;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class HardSoftFloatScoreConverterTest extends AbstractScoreJpaTest {

  @Test
  void persistAndMergeFiniteValuesAndStructuralScore() {
    for (float value :
        new float[] {0.0f, -0.0f, 0.1f, Math.nextUp(1.0f), Float.MIN_VALUE, Float.MAX_VALUE}) {
      persistAndMerge(
          new TestScoreEntity(HardSoftFloatScore.ZERO),
          null,
          new HardSoftFloatScore(-7L, value, -value));
    }
  }

  @Test
  void converterPreservesNullAndRejectsNonFiniteLevels() {
    var converter = new HardSoftFloatScoreConverter();
    assertThat(converter.convertToDatabaseColumn(null)).isNull();
    assertThat(converter.convertToEntityAttribute(null)).isNull();
    for (var token : new String[] {"NaN", "Infinity", "-Infinity", "1E99999"}) {
      var invalid = token + "hard/0.0soft";
      assertThatThrownBy(() -> converter.convertToEntityAttribute(invalid))
          .isInstanceOf(IllegalArgumentException.class)
          .hasStackTraceContaining("finite");
    }
  }

  @Entity(name = "HardSoftFloatScoreTestEntity")
  static class TestScoreEntity extends AbstractTestJpaEntity<HardSoftFloatScore> {

    @Convert(converter = HardSoftFloatScoreConverter.class)
    protected HardSoftFloatScore score;

    TestScoreEntity() {}

    TestScoreEntity(HardSoftFloatScore score) {
      this.score = score;
    }

    @Override
    public HardSoftFloatScore getScore() {
      return score;
    }

    @Override
    public void setScore(HardSoftFloatScore score) {
      this.score = score;
    }
  }
}
