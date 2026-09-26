package greycos.solver.jpa.api.score;

import jakarta.persistence.Convert;
import jakarta.persistence.Entity;

import greycos.solver.core.api.score.BendableScore;
import greycos.solver.jpa.impl.AbstractScoreJpaTest;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class BendableScoreConverterTest extends AbstractScoreJpaTest {

  @Test
  void persistAndMerge() {
    persistAndMerge(
        new BendableScoreConverterTestJpaEntity(BendableScore.zero(3, 2)),
        null,
        BendableScore.of(new long[] {10000L, 2000L, 300L}, new long[] {40L, 5L}),
        new BendableScore(-1L, new long[] {10000L, 2000L, 300L}, new long[] {40L, 5L}));
  }

  @Entity
  static class BendableScoreConverterTestJpaEntity extends AbstractTestJpaEntity<BendableScore> {

    @Convert(converter = BendableScoreConverter.class)
    protected BendableScore score;

    BendableScoreConverterTestJpaEntity() {}

    public BendableScoreConverterTestJpaEntity(BendableScore score) {
      this.score = score;
    }

    @Override
    public BendableScore getScore() {
      return score;
    }

    @Override
    public void setScore(BendableScore score) {
      this.score = score;
    }
  }
}
