package greycos.solver.greybay.impl.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import greycos.solver.core.api.score.BendableScore;
import greycos.solver.greybay.api.EvaluationStatus;
import greycos.solver.greybay.api.VerificationResult;

import org.junit.jupiter.api.Test;

class EvaluationOutcomeTest {
  @Test
  void arrayBackedScoreCannotMutateCommittedObservationThroughInputOrAccessor() {
    long[] hard = {0};
    long[] soft = {-123};
    var score = BendableScore.of(hard, soft);
    var outcome =
        new EvaluationOutcome<>(
            EvaluationStatus.COMPLETE,
            score,
            0,
            1,
            0,
            2,
            0,
            0,
            VerificationResult.notConfigured(),
            null,
            null,
            List.of());
    var originalEncoding = outcome.toMap();
    hard[0] = -100;
    soft[0] = -900;
    outcome.score().hardScores()[0] = -200;
    outcome.score().softScores()[0] = -800;
    assertThat(outcome.score()).isEqualTo(BendableScore.of(new long[] {0}, new long[] {-123}));
    assertThat(outcome.toMap()).isEqualTo(originalEncoding);
    assertThat(EvaluationOutcome.fromMap(originalEncoding, BendableScore::parseScore))
        .isEqualTo(outcome);
  }
}
