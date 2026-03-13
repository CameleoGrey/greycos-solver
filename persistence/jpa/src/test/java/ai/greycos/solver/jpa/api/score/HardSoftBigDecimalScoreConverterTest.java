package ai.greycos.solver.jpa.api.score;

import java.math.BigDecimal;

import jakarta.persistence.Convert;
import jakarta.persistence.Entity;

import ai.greycos.solver.core.api.score.HardSoftBigDecimalScore;
import ai.greycos.solver.jpa.impl.AbstractScoreJpaTest;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class HardSoftBigDecimalScoreConverterTest extends AbstractScoreJpaTest {

  @Test
  void persistAndMerge() {
    persistAndMerge(
        new HardSoftBigDecimalScoreConverterTestJpaEntity(HardSoftBigDecimalScore.ZERO),
        null,
        HardSoftBigDecimalScore.of(new BigDecimal("-10.01000"), new BigDecimal("-2.20000")));
  }

  @Entity
  static class HardSoftBigDecimalScoreConverterTestJpaEntity
      extends AbstractTestJpaEntity<HardSoftBigDecimalScore> {

    @Convert(converter = HardSoftBigDecimalScoreConverter.class)
    protected HardSoftBigDecimalScore score;

    HardSoftBigDecimalScoreConverterTestJpaEntity() {}

    public HardSoftBigDecimalScoreConverterTestJpaEntity(HardSoftBigDecimalScore score) {
      this.score = score;
    }

    @Override
    public HardSoftBigDecimalScore getScore() {
      return score;
    }

    @Override
    public void setScore(HardSoftBigDecimalScore score) {
      this.score = score;
    }
  }
}
