package greycos.solver.core.impl.solver.termination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.score.definition.HardSoftScoreDefinition;
import greycos.solver.core.impl.score.definition.SimpleScoreDefinition;
import greycos.solver.core.testcotwin.TestdataSolution;

import org.junit.jupiter.api.Test;

class TerminationGraphAccessTest {

  @Test
  void auditedLeavesUseOnlyStoredProgress() {
    var definitions =
        List.<Termination<TestdataSolution>>of(
            new BasicPlumbingTermination<>(false),
            new ChildThreadPlumbingTermination<>(),
            new BestScoreTermination<>(
                new SimpleScoreDefinition(), SimpleScore.ZERO, new double[0]),
            new BestScoreFeasibleTermination<>(new HardSoftScoreDefinition(), new double[0]),
            new DiminishedReturnsTermination<TestdataSolution, SimpleScore>(1000, 0.01),
            new MoveCountTermination<>(20),
            new ScoreCalculationCountTermination<>(20),
            new StepCountTermination<>(20),
            new TimeMillisSpentTermination<>(1000),
            new UnimprovedStepCountTermination<>(20),
            new UnimprovedTimeMillisSpentTermination<>(1000),
            new UnimprovedTimeMillisSpentScoreDifferenceThresholdTermination<>(
                1000, SimpleScore.ONE));

    assertThat(definitions)
        .allSatisfy(
            definition ->
                assertThat(TerminationGraphAccess.isWorkingSolutionIndependent(definition))
                    .as(definition.getClass().getSimpleName())
                    .isTrue());
  }

  @Test
  void nestedCompositesAndBothBridgeKindsRetainTheCapability() {
    var time = new TimeMillisSpentTermination<TestdataSolution>(1000);
    var definition =
        new AndCompositeTermination<>(
            new BasicPlumbingTermination<>(false),
            new OrCompositeTermination<>(
                new StepCountTermination<>(20),
                PhaseTermination.bridge(
                    new OrCompositeTermination<>(
                        new MoveCountTermination<>(20),
                        new PhaseToSolverTerminationBridge<>(time)))));

    assertThat(TerminationGraphAccess.isWorkingSolutionIndependent(definition)).isTrue();
  }

  @Test
  @SuppressWarnings("unchecked")
  void genericUnknownChildrenCannotDisappearBehindPhaseViewsOrBridges() {
    var unknown = (Termination<TestdataSolution>) mock(Termination.class);
    var inner = new OrCompositeTermination<>(new TimeMillisSpentTermination<>(1000), unknown);
    var outer =
        new AndCompositeTermination<>(
            new StepCountTermination<>(20), PhaseTermination.bridge(inner));

    assertThat(inner.getPhaseTerminationList()).hasSize(1);
    assertThat(TerminationGraphAccess.isWorkingSolutionIndependent(unknown)).isFalse();
    assertThat(TerminationGraphAccess.isWorkingSolutionIndependent(inner)).isFalse();
    assertThat(TerminationGraphAccess.isWorkingSolutionIndependent(outer)).isFalse();
    assertThat(
            TerminationGraphAccess.isWorkingSolutionIndependent(
                new PhaseToSolverTerminationBridge<>(outer)))
        .isFalse();
    verifyNoInteractions(unknown);
  }

  @Test
  void bridgeSubclassCannotInheritTrustFromItsDelegate() {
    var definition =
        new PhaseToSolverTerminationBridge<TestdataSolution>(
            new TimeMillisSpentTermination<>(1000)) {
          @Override
          public boolean isPhaseTerminated(AbstractPhaseScope<TestdataSolution> scope) {
            throw new AssertionError("Capability inspection must not invoke a predicate.");
          }
        };

    assertThat(TerminationGraphAccess.isWorkingSolutionIndependent(definition)).isFalse();
    assertThat(
            TerminationGraphAccess.isWorkingSolutionIndependent(
                new OrCompositeTermination<>(definition)))
        .isFalse();
  }

  @Test
  void supplierBackedProgressIsConservativelyExcluded() {
    var definition =
        new SharedScoreTermination<TestdataSolution>(
            new BestScoreTermination<>(
                new SimpleScoreDefinition(), SimpleScore.ZERO, new double[0]),
            () -> {
              throw new AssertionError("Capability inspection must not invoke progress suppliers.");
            },
            true);

    assertThat(TerminationGraphAccess.isWorkingSolutionIndependent(definition)).isFalse();
  }
}
