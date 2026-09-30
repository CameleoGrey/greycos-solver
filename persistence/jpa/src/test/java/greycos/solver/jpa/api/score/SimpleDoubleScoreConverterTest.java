package greycos.solver.jpa.api.score;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.Convert;
import jakarta.persistence.Entity;

import greycos.solver.core.api.score.SimpleDoubleScore;
import greycos.solver.jpa.impl.AbstractScoreJpaTest;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class SimpleDoubleScoreConverterTest extends AbstractScoreJpaTest {

  @Test
  void persistAndMergeFiniteValuesAndStructuralScore() {
    for (double value :
        new double[] {0.0d, -0.0d, 0.1d, Math.nextUp(1.0d), Double.MIN_VALUE, Double.MAX_VALUE}) {
      persistAndMerge(
          new TestScoreEntity(SimpleDoubleScore.ZERO), null, new SimpleDoubleScore(-7L, value));
    }
  }

  @Test
  void converterPreservesNullAndRejectsNonFiniteLevels() {
    var converter = new SimpleDoubleScoreConverter();
    assertThat(converter.convertToDatabaseColumn(null)).isNull();
    assertThat(converter.convertToEntityAttribute(null)).isNull();
    for (var token : new String[] {"NaN", "Infinity", "-Infinity", "1E99999"}) {
      var invalid = token;
      assertThatThrownBy(() -> converter.convertToEntityAttribute(invalid))
          .isInstanceOf(IllegalArgumentException.class)
          .hasStackTraceContaining("finite");
    }
  }

  @Entity(name = "SimpleDoubleScoreTestEntity")
  static class TestScoreEntity extends AbstractTestJpaEntity<SimpleDoubleScore> {

    @Convert(converter = SimpleDoubleScoreConverter.class)
    protected SimpleDoubleScore score;

    TestScoreEntity() {}

    TestScoreEntity(SimpleDoubleScore score) {
      this.score = score;
    }

    @Override
    public SimpleDoubleScore getScore() {
      return score;
    }

    @Override
    public void setScore(SimpleDoubleScore score) {
      this.score = score;
    }
  }
}
