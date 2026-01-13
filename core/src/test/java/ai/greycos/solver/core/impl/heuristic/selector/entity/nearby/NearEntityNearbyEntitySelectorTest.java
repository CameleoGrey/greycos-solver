package ai.greycos.solver.core.impl.heuristic.selector.entity.nearby;

import static ai.greycos.solver.core.testutil.PlannerAssert.assertAllCodesOfEntitySelector;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ai.greycos.solver.core.impl.cotwin.entity.descriptor.EntityDescriptor;
import ai.greycos.solver.core.impl.heuristic.selector.SelectorTestUtils;
import ai.greycos.solver.core.impl.heuristic.selector.common.nearby.NearbyDistanceMeter;
import ai.greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import ai.greycos.solver.core.impl.phase.scope.AbstractStepScope;
import ai.greycos.solver.core.impl.score.director.InnerScoreDirector;
import ai.greycos.solver.core.impl.solver.scope.SolverScope;
import ai.greycos.solver.core.testcotwin.TestdataEntity;
import ai.greycos.solver.core.testcotwin.TestdataSolution;
import ai.greycos.solver.core.testutil.PlannerTestUtils;
import ai.greycos.solver.core.testutil.TestNearbyRandom;
import ai.greycos.solver.core.testutil.TestRandom;

import org.junit.jupiter.api.Test;

/** Tests for {@link NearEntityNearbyEntitySelector}. */
class NearEntityNearbyEntitySelectorTest {

  @Test
  void randomSelection() {
    final TestdataEntity morocco = new TestdataEntity("Morocco");
    final TestdataEntity spain = new TestdataEntity("Spain");
    final TestdataEntity australia = new TestdataEntity("Australia");
    final TestdataEntity brazil = new TestdataEntity("Brazil");

    EntityDescriptor<TestdataSolution> entityDescriptor =
        SelectorTestUtils.mockEntityDescriptor(TestdataEntity.class);
    var childEntitySelector =
        SelectorTestUtils.mockEntitySelector(entityDescriptor, morocco, spain, australia, brazil);
    when(childEntitySelector.isNeverEnding()).thenReturn(true);

    NearbyDistanceMeter<TestdataEntity, TestdataEntity> meter =
        (origin, destination) -> {
          if (origin == morocco) {
            if (destination == morocco) return 0.0;
            else if (destination == spain) return 1.0;
            else if (destination == australia) return 100.0;
            else if (destination == brazil) return 50.0;
          } else if (origin == spain) {
            if (destination == morocco) return 1.0;
            else if (destination == spain) return 0.0;
            else if (destination == australia) return 101.0;
            else if (destination == brazil) return 51.0;
          } else if (origin == australia) {
            if (destination == morocco) return 100.0;
            else if (destination == spain) return 101.0;
            else if (destination == australia) return 0.0;
            else if (destination == brazil) return 60.0;
          } else if (origin == brazil) {
            if (destination == morocco) return 50.0;
            else if (destination == spain) return 51.0;
            else if (destination == australia) return 60.0;
            else if (destination == brazil) return 0.0;
          }
          return Double.MAX_VALUE;
        };

    var mimicReplayingEntitySelector =
        SelectorTestUtils.mockReplayingEntitySelector(
            entityDescriptor, morocco, morocco, morocco, morocco);

    NearEntityNearbyEntitySelector<TestdataSolution> entitySelector =
        new NearEntityNearbyEntitySelector<>(
            childEntitySelector, mimicReplayingEntitySelector, meter, new TestNearbyRandom(), true);

    TestRandom workingRandom = new TestRandom(0, 1, 2, 0);

    InnerScoreDirector<TestdataSolution, ?> scoreDirector = mock(InnerScoreDirector.class);
    SolverScope<TestdataSolution> solverScope =
        SelectorTestUtils.solvingStarted(entitySelector, scoreDirector, workingRandom);
    AbstractPhaseScope<TestdataSolution> phaseScopeA =
        PlannerTestUtils.delegatingPhaseScope(solverScope);
    entitySelector.phaseStarted(phaseScopeA);
    AbstractStepScope<TestdataSolution> stepScopeA1 =
        PlannerTestUtils.delegatingStepScope(phaseScopeA);
    entitySelector.stepStarted(stepScopeA1);
    var iterator = entitySelector.iterator();
    assertThat(((TestdataEntity) iterator.next()).getCode()).isEqualTo("Morocco");
    assertThat(((TestdataEntity) iterator.next()).getCode()).isEqualTo("Spain");
    assertThat(((TestdataEntity) iterator.next()).getCode()).isEqualTo("Australia");
    assertThat(((TestdataEntity) iterator.next()).getCode()).isEqualTo("Morocco");
    assertThat(entitySelector.isNeverEnding()).isTrue();
    entitySelector.stepEnded(stepScopeA1);
    entitySelector.phaseEnded(phaseScopeA);
    entitySelector.solvingEnded(solverScope);
  }

  @Test
  void originalSelection() {
    final TestdataEntity morocco = new TestdataEntity("Morocco");
    final TestdataEntity spain = new TestdataEntity("Spain");
    final TestdataEntity australia = new TestdataEntity("Australia");
    final TestdataEntity brazil = new TestdataEntity("Brazil");

    EntityDescriptor<TestdataSolution> entityDescriptor =
        SelectorTestUtils.mockEntityDescriptor(TestdataEntity.class);
    var childEntitySelector =
        SelectorTestUtils.mockEntitySelector(entityDescriptor, morocco, spain, australia, brazil);
    when(childEntitySelector.isNeverEnding()).thenReturn(false);

    NearbyDistanceMeter<TestdataEntity, TestdataEntity> meter =
        (origin, destination) -> {
          if (origin == morocco) {
            if (destination == morocco) return 0.0;
            else if (destination == spain) return 1.0;
            else if (destination == australia) return 100.0;
            else if (destination == brazil) return 50.0;
          }
          return Double.MAX_VALUE;
        };

    var mimicReplayingEntitySelector =
        SelectorTestUtils.mockReplayingEntitySelector(
            entityDescriptor, morocco, spain, australia, brazil);

    NearEntityNearbyEntitySelector<TestdataSolution> entitySelector =
        new NearEntityNearbyEntitySelector<>(
            childEntitySelector,
            mimicReplayingEntitySelector,
            meter,
            new TestNearbyRandom(),
            false);

    TestRandom workingRandom = new TestRandom(0);

    InnerScoreDirector<TestdataSolution, ?> scoreDirector = mock(InnerScoreDirector.class);
    SolverScope<TestdataSolution> solverScope =
        SelectorTestUtils.solvingStarted(entitySelector, scoreDirector, workingRandom);
    AbstractPhaseScope<TestdataSolution> phaseScopeA =
        PlannerTestUtils.delegatingPhaseScope(solverScope);
    entitySelector.phaseStarted(phaseScopeA);
    AbstractStepScope<TestdataSolution> stepScopeA1 =
        PlannerTestUtils.delegatingStepScope(phaseScopeA);
    entitySelector.stepStarted(stepScopeA1);
    assertAllCodesOfEntitySelector(entitySelector, "Morocco", "Spain", "Australia", "Brazil");
    entitySelector.stepEnded(stepScopeA1);
    entitySelector.phaseEnded(phaseScopeA);
    entitySelector.solvingEnded(solverScope);
  }
}
