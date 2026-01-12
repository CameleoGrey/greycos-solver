package ai.greycos.solver.core.impl.heuristic.selector.value.nearby;

import static ai.greycos.solver.core.testutil.PlannerAssert.assertAllCodesOfValueSelectorForEntity;
import static ai.greycos.solver.core.testutil.PlannerAssert.assertCodesOfNeverEndingValueSelectorForEntity;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ai.greycos.solver.core.impl.domain.entity.descriptor.EntityDescriptor;
import ai.greycos.solver.core.impl.domain.variable.descriptor.GenuineVariableDescriptor;
import ai.greycos.solver.core.impl.heuristic.selector.SelectorTestUtils;
import ai.greycos.solver.core.impl.heuristic.selector.common.nearby.NearbyDistanceMeter;
import ai.greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import ai.greycos.solver.core.impl.phase.scope.AbstractStepScope;
import ai.greycos.solver.core.impl.score.director.InnerScoreDirector;
import ai.greycos.solver.core.impl.solver.scope.SolverScope;
import ai.greycos.solver.core.testdomain.TestdataEntity;
import ai.greycos.solver.core.testdomain.TestdataSolution;
import ai.greycos.solver.core.testdomain.TestdataValue;
import ai.greycos.solver.core.testutil.PlannerTestUtils;
import ai.greycos.solver.core.testutil.TestNearbyRandom;
import ai.greycos.solver.core.testutil.TestRandom;

import org.junit.jupiter.api.Test;

/** Tests for {@link NearValueNearbyValueSelector}. */
class NearValueNearbyValueSelectorTest {

  @Test
  void originalSelection() {
    TestdataEntity entity = new TestdataEntity("entity");
    TestdataValue v1 = new TestdataValue("10");
    TestdataValue v2 = new TestdataValue("45");
    TestdataValue v3 = new TestdataValue("50");
    TestdataValue v4 = new TestdataValue("60");
    TestdataValue v5 = new TestdataValue("75");

    EntityDescriptor<TestdataSolution> entityDescriptor =
        SelectorTestUtils.mockEntityDescriptor(TestdataEntity.class);
    GenuineVariableDescriptor<TestdataSolution> variableDescriptor =
        SelectorTestUtils.mockVariableDescriptor(entityDescriptor, "value");

    NearbyDistanceMeter<TestdataValue, TestdataValue> meter =
        (origin, destination) -> {
          int originValue = Integer.parseInt(origin.getCode());
          int destValue = Integer.parseInt(destination.getCode());
          return Math.abs(destValue - originValue);
        };

    var childValueSelector =
        SelectorTestUtils.mockIterableValueSelector(variableDescriptor, v1, v2, v3, v4, v5);

    var originValueSelector = SelectorTestUtils.mockReplayingValueSelector(variableDescriptor, v3);
    // Configure iterator(entity) to return the origin value
    when(originValueSelector.iterator(any()))
        .thenAnswer(invocation -> java.util.List.of(v3).iterator());

    NearValueNearbyValueSelector<TestdataSolution> nearbyValueSelector =
        new NearValueNearbyValueSelector<>(
            childValueSelector, originValueSelector, meter, new TestNearbyRandom(), false);

    TestRandom workingRandom = new TestRandom(0);

    InnerScoreDirector<TestdataSolution, ?> scoreDirector = mock(InnerScoreDirector.class);
    SolverScope<TestdataSolution> solverScope =
        SelectorTestUtils.solvingStarted(nearbyValueSelector, scoreDirector, workingRandom);
    AbstractPhaseScope<TestdataSolution> phaseScopeA =
        PlannerTestUtils.delegatingPhaseScope(solverScope);
    nearbyValueSelector.phaseStarted(phaseScopeA);
    AbstractStepScope<TestdataSolution> stepScopeA1 =
        PlannerTestUtils.delegatingStepScope(phaseScopeA);
    nearbyValueSelector.stepStarted(stepScopeA1);

    // Distance order from v3(50): v3(0), v2(5), v4(10), v5(25), v1(40)
    assertAllCodesOfValueSelectorForEntity(
        nearbyValueSelector, entity, "50", "45", "60", "75", "10");

    nearbyValueSelector.stepEnded(stepScopeA1);
    nearbyValueSelector.phaseEnded(phaseScopeA);
    nearbyValueSelector.solvingEnded(solverScope);
  }

  @Test
  void randomSelection() {
    TestdataEntity entity = new TestdataEntity("entity");
    TestdataValue v1 = new TestdataValue("10");
    TestdataValue v2 = new TestdataValue("45");
    TestdataValue v3 = new TestdataValue("50");
    TestdataValue v4 = new TestdataValue("60");
    TestdataValue v5 = new TestdataValue("75");

    EntityDescriptor<TestdataSolution> entityDescriptor =
        SelectorTestUtils.mockEntityDescriptor(TestdataEntity.class);
    GenuineVariableDescriptor<TestdataSolution> variableDescriptor =
        SelectorTestUtils.mockVariableDescriptor(entityDescriptor, "value");

    NearbyDistanceMeter<TestdataValue, TestdataValue> meter =
        (origin, destination) -> {
          int originValue = Integer.parseInt(origin.getCode());
          int destValue = Integer.parseInt(destination.getCode());
          return Math.abs(destValue - originValue);
        };

    var childValueSelector =
        SelectorTestUtils.mockIterableValueSelector(variableDescriptor, v1, v2, v3, v4, v5);

    var originValueSelector = SelectorTestUtils.mockReplayingValueSelector(variableDescriptor, v3);
    // Configure iterator(entity) to return the origin value
    when(originValueSelector.iterator(any()))
        .thenAnswer(invocation -> java.util.List.of(v3).iterator());

    NearValueNearbyValueSelector<TestdataSolution> nearbyValueSelector =
        new NearValueNearbyValueSelector<>(
            childValueSelector, originValueSelector, meter, new TestNearbyRandom(), true);

    // nearbyIndices [3, 2, 1, 4, 0] => destinations [v5, v4, v2, v1, v3]
    TestRandom workingRandom = new TestRandom(3, 2, 1, 4, 0);

    InnerScoreDirector<TestdataSolution, ?> scoreDirector = mock(InnerScoreDirector.class);
    SolverScope<TestdataSolution> solverScope =
        SelectorTestUtils.solvingStarted(nearbyValueSelector, scoreDirector, workingRandom);
    AbstractPhaseScope<TestdataSolution> phaseScopeA =
        PlannerTestUtils.delegatingPhaseScope(solverScope);
    nearbyValueSelector.phaseStarted(phaseScopeA);
    AbstractStepScope<TestdataSolution> stepScopeA1 =
        PlannerTestUtils.delegatingStepScope(phaseScopeA);
    nearbyValueSelector.stepStarted(stepScopeA1);

    assertCodesOfNeverEndingValueSelectorForEntity(
        nearbyValueSelector,
        entity,
        childValueSelector.getSize(entity),
        "75",
        "60",
        "45",
        "10",
        "50");

    nearbyValueSelector.stepEnded(stepScopeA1);
    nearbyValueSelector.phaseEnded(phaseScopeA);
    nearbyValueSelector.solvingEnded(solverScope);
  }
}
