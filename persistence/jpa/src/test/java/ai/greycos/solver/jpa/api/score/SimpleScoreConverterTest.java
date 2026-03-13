package ai.greycos.solver.jpa.api.score;

import jakarta.persistence.Convert;
import jakarta.persistence.Entity;

import ai.greycos.solver.core.api.score.SimpleScore;
import ai.greycos.solver.jpa.impl.AbstractScoreJpaTest;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class SimpleScoreConverterTest extends AbstractScoreJpaTest {

  @Test
  void persistAndMerge() {
    persistAndMerge(
        new SimpleScoreConverterTestJpaEntity(SimpleScore.ZERO), null, SimpleScore.of(-10));
  }

  @Entity
  static class SimpleScoreConverterTestJpaEntity extends AbstractTestJpaEntity<SimpleScore> {

    @Convert(converter = SimpleScoreConverter.class)
    protected SimpleScore score;

    SimpleScoreConverterTestJpaEntity() {}

    public SimpleScoreConverterTestJpaEntity(SimpleScore score) {
      this.score = score;
    }

    @Override
    public SimpleScore getScore() {
      return score;
    }

    @Override
    public void setScore(SimpleScore score) {
      this.score = score;
    }
  }
}
