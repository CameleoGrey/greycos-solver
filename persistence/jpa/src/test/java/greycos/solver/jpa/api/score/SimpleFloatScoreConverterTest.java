package greycos.solver.jpa.api.score;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.Convert;
import jakarta.persistence.Entity;

import greycos.solver.core.api.score.SimpleFloatScore;
import greycos.solver.jpa.impl.AbstractScoreJpaTest;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class SimpleFloatScoreConverterTest extends AbstractScoreJpaTest {

  @Test
  void persistAndMergeFiniteValuesAndStructuralScore() {
    for (float value :
        new float[] {0.0f, -0.0f, 0.1f, Math.nextUp(1.0f), Float.MIN_VALUE, Float.MAX_VALUE}) {
      persistAndMerge(
          new TestScoreEntity(SimpleFloatScore.ZERO), null, new SimpleFloatScore(-7L, value));
    }
  }

  @Test
  void converterPreservesNullAndRejectsNonFiniteLevels() {
    var converter = new SimpleFloatScoreConverter();
    assertThat(converter.convertToDatabaseColumn(null)).isNull();
    assertThat(converter.convertToEntityAttribute(null)).isNull();
    for (var token : new String[] {"NaN", "Infinity", "-Infinity", "1E99999"}) {
      var invalid = token;
      assertThatThrownBy(() -> converter.convertToEntityAttribute(invalid))
          .isInstanceOf(IllegalArgumentException.class)
          .hasStackTraceContaining("finite");
    }
  }

  @Entity(name = "SimpleFloatScoreTestEntity")
  static class TestScoreEntity extends AbstractTestJpaEntity<SimpleFloatScore> {

    @Convert(converter = SimpleFloatScoreConverter.class)
    protected SimpleFloatScore score;

    TestScoreEntity() {}

    TestScoreEntity(SimpleFloatScore score) {
      this.score = score;
    }

    @Override
    public SimpleFloatScore getScore() {
      return score;
    }

    @Override
    public void setScore(SimpleFloatScore score) {
      this.score = score;
    }
  }
}
