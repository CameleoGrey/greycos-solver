package ai.greycos.solver.core.impl.heuristic.selector.value.nearby;

import static ai.greycos.solver.core.testutil.PlannerAssert.assertAllCodesOfValueSelectorForEntity;
import static ai.greycos.solver.core.testutil.PlannerAssert.assertCodesOfNeverEndingValueSelectorForEntity;
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
import ai.greycos.solver.core.testdomain.chained.TestdataChainedAnchor;
import ai.greycos.solver.core.testdomain.chained.TestdataChainedEntity;
import ai.greycos.solver.core.testdomain.chained.TestdataChainedObject;
import ai.greycos.solver.core.testdomain.chained.TestdataChainedSolution;
import ai.greycos.solver.core.testutil.PlannerTestUtils;
import ai.greycos.solver.core.testutil.TestNearbyRandom;
import ai.greycos.solver.core.testutil.TestRandom;

import org.junit.jupiter.api.Test;

/** Tests for {@link NearEntityNearbyValueSelector}. */
class NearEntityNearbyValueSelectorTest {

  @Test
  void randomSelection() {
    final TestdataEntity africa = new TestdataEntity("Africa");
    final TestdataEntity europe = new TestdataEntity("Europe");
    final TestdataEntity oceania = new TestdataEntity("Oceania");
    final TestdataValue morocco = new TestdataValue("Morocco");
    final TestdataValue spain = new TestdataValue("Spain");
    final TestdataValue australia = new TestdataValue("Australia");
    final TestdataValue brazil = new TestdataValue("Brazil");

    EntityDescriptor<TestdataSolution> entityDescriptor =
        SelectorTestUtils.mockEntityDescriptor(TestdataEntity.class);
    GenuineVariableDescriptor<TestdataSolution> variableDescriptor =
        SelectorTestUtils.mockVariableDescriptor(entityDescriptor, "location");
    when(variableDescriptor.getVariablePropertyType()).thenReturn((Class) TestdataValue.class);

    NearbyDistanceMeter<TestdataEntity, TestdataValue> meter =
        (origin, destination) -> {
          if (origin == africa) {
            if (destination.getCode().equals("Morocco")) return 0.0;
            else if (destination.getCode().equals("Spain")) return 1.0;
            else if (destination.getCode().equals("Australia")) return 100.0;
            else if (destination.getCode().equals("Brazil")) return 50.0;
          } else if (origin == europe) {
            if (destination.getCode().equals("Morocco")) return 1.0;
            else if (destination.getCode().equals("Spain")) return 0.0;
            else if (destination.getCode().equals("Australia")) return 101.0;
            else if (destination.getCode().equals("Brazil")) return 51.0;
          } else if (origin == oceania) {
            if (destination.getCode().equals("Morocco")) return 100.0;
            else if (destination.getCode().equals("Spain")) return 101.0;
            else if (destination.getCode().equals("Australia")) return 0.0;
            else if (destination.getCode().equals("Brazil")) return 60.0;
          }
          return Double.MAX_VALUE;
        };

    var childValueSelector =
        SelectorTestUtils.mockIterableValueSelector(
            variableDescriptor, morocco, spain, australia, brazil);

    var mimicReplayingEntitySelector =
        SelectorTestUtils.mockReplayingEntitySelector(
            entityDescriptor, europe, europe, europe, europe);

    NearEntityNearbyValueSelector<TestdataSolution> valueSelector =
        new NearEntityNearbyValueSelector<>(
            childValueSelector, mimicReplayingEntitySelector, meter, new TestNearbyRandom(), true);

    TestRandom workingRandom = new TestRandom(3, 0, 2, 1);

    InnerScoreDirector<TestdataSolution, ?> scoreDirector = mock(InnerScoreDirector.class);
    SolverScope<TestdataSolution> solverScope =
        SelectorTestUtils.solvingStarted(valueSelector, scoreDirector, workingRandom);
    AbstractPhaseScope<TestdataSolution> phaseScopeA =
        PlannerTestUtils.delegatingPhaseScope(solverScope);
    valueSelector.phaseStarted(phaseScopeA);
    AbstractStepScope<TestdataSolution> stepScopeA1 =
        PlannerTestUtils.delegatingStepScope(phaseScopeA);
    valueSelector.stepStarted(stepScopeA1);

    assertCodesOfNeverEndingValueSelectorForEntity(
        valueSelector,
        europe,
        childValueSelector.getSize(europe),
        "Australia",
        "Spain",
        "Brazil",
        "Morocco");

    valueSelector.stepEnded(stepScopeA1);
    valueSelector.phaseEnded(phaseScopeA);
    valueSelector.solvingEnded(solverScope);
  }

  @Test
  void originalSelection() {
    final TestdataEntity africa = new TestdataEntity("Africa");
    final TestdataEntity europe = new TestdataEntity("Europe");
    final TestdataEntity oceania = new TestdataEntity("Oceania");
    final TestdataValue morocco = new TestdataValue("Morocco");
    final TestdataValue spain = new TestdataValue("Spain");
    final TestdataValue australia = new TestdataValue("Australia");
    final TestdataValue brazil = new TestdataValue("Brazil");

    EntityDescriptor<TestdataSolution> entityDescriptor =
        SelectorTestUtils.mockEntityDescriptor(TestdataEntity.class);
    GenuineVariableDescriptor<TestdataSolution> variableDescriptor =
        SelectorTestUtils.mockVariableDescriptor(entityDescriptor, "location");
    when(variableDescriptor.getVariablePropertyType()).thenReturn((Class) TestdataValue.class);

    NearbyDistanceMeter<TestdataEntity, TestdataValue> meter =
        (origin, destination) -> {
          if (origin == africa) {
            if (destination.getCode().equals("Morocco")) return 0.0;
            else if (destination.getCode().equals("Spain")) return 1.0;
            else if (destination.getCode().equals("Australia")) return 100.0;
            else if (destination.getCode().equals("Brazil")) return 50.0;
          } else if (origin == europe) {
            if (destination.getCode().equals("Morocco")) return 1.0;
            else if (destination.getCode().equals("Spain")) return 0.0;
            else if (destination.getCode().equals("Australia")) return 101.0;
            else if (destination.getCode().equals("Brazil")) return 51.0;
          } else if (origin == oceania) {
            if (destination.getCode().equals("Morocco")) return 100.0;
            else if (destination.getCode().equals("Spain")) return 101.0;
            else if (destination.getCode().equals("Australia")) return 0.0;
            else if (destination.getCode().equals("Brazil")) return 60.0;
          }
          return Double.MAX_VALUE;
        };

    var childValueSelector =
        SelectorTestUtils.mockIterableValueSelector(
            variableDescriptor, morocco, spain, australia, brazil);

    var mimicReplayingEntitySelector =
        SelectorTestUtils.mockReplayingEntitySelector(entityDescriptor, europe, europe, europe);

    NearEntityNearbyValueSelector<TestdataSolution> valueSelector =
        new NearEntityNearbyValueSelector<>(
            childValueSelector, mimicReplayingEntitySelector, meter, new TestNearbyRandom(), false);

    TestRandom workingRandom = new TestRandom(0);

    InnerScoreDirector<TestdataSolution, ?> scoreDirector = mock(InnerScoreDirector.class);
    SolverScope<TestdataSolution> solverScope =
        SelectorTestUtils.solvingStarted(valueSelector, scoreDirector, workingRandom);

    AbstractPhaseScope<TestdataSolution> phaseScopeA =
        PlannerTestUtils.delegatingPhaseScope(solverScope);
    valueSelector.phaseStarted(phaseScopeA);

    AbstractStepScope<TestdataSolution> stepScopeA1 =
        PlannerTestUtils.delegatingStepScope(phaseScopeA);
    valueSelector.stepStarted(stepScopeA1);
    assertAllCodesOfValueSelectorForEntity(
        valueSelector, europe, "Spain", "Morocco", "Brazil", "Australia");
    valueSelector.stepEnded(stepScopeA1);

    AbstractStepScope<TestdataSolution> stepScopeA2 =
        PlannerTestUtils.delegatingStepScope(phaseScopeA);
    valueSelector.stepStarted(stepScopeA2);
    assertAllCodesOfValueSelectorForEntity(
        valueSelector, europe, "Spain", "Morocco", "Brazil", "Australia");
    valueSelector.stepEnded(stepScopeA2);

    valueSelector.phaseEnded(phaseScopeA);

    AbstractPhaseScope<TestdataSolution> phaseScopeB =
        PlannerTestUtils.delegatingPhaseScope(solverScope);
    valueSelector.phaseStarted(phaseScopeB);

    AbstractStepScope<TestdataSolution> stepScopeB1 =
        PlannerTestUtils.delegatingStepScope(phaseScopeB);
    valueSelector.stepStarted(stepScopeB1);
    assertAllCodesOfValueSelectorForEntity(
        valueSelector, europe, "Spain", "Morocco", "Brazil", "Australia");
    valueSelector.stepEnded(stepScopeB1);

    AbstractStepScope<TestdataSolution> stepScopeB2 =
        PlannerTestUtils.delegatingStepScope(phaseScopeB);
    valueSelector.stepStarted(stepScopeB2);
    assertAllCodesOfValueSelectorForEntity(
        valueSelector, europe, "Spain", "Morocco", "Brazil", "Australia");
    valueSelector.stepEnded(stepScopeB2);

    AbstractStepScope<TestdataSolution> stepScopeB3 =
        PlannerTestUtils.delegatingStepScope(phaseScopeB);
    valueSelector.stepStarted(stepScopeB3);
    assertAllCodesOfValueSelectorForEntity(
        valueSelector, europe, "Spain", "Morocco", "Brazil", "Australia");
    valueSelector.stepEnded(stepScopeB3);

    valueSelector.phaseEnded(phaseScopeB);
    valueSelector.solvingEnded(solverScope);
  }

  @Test
  void originalSelectionChained() {
    // Test with chained entities (e.g., vehicle routing with trailers)
    // Anchors should be excluded from distance matrix
    final TestdataChainedEntity morocco = new TestdataChainedEntity("Morocco");
    final TestdataChainedEntity spain = new TestdataChainedEntity("Spain");
    final TestdataChainedEntity australia = new TestdataChainedEntity("Australia");
    final TestdataChainedAnchor brazil = new TestdataChainedAnchor("Brazil");

    GenuineVariableDescriptor<TestdataChainedSolution> variableDescriptor =
        TestdataChainedEntity.buildVariableDescriptorForChainedObject();
    EntityDescriptor<TestdataChainedSolution> entityDescriptor =
        variableDescriptor.getEntityDescriptor();

    final TestdataChainedEntity africa = new TestdataChainedEntity("Africa");

    NearbyDistanceMeter<TestdataChainedEntity, TestdataChainedObject> meter =
        (origin, destination) -> {
          if (origin.getCode().equals("Africa")) {
            if (destination == africa) return 0.0; // Origin to itself
            else if (destination == morocco) return 0.5;
            else if (destination == spain) return 1.0;
            else if (destination == australia) return 100.0;
            else if (destination == brazil) return 50.0;
          }
          return Double.MAX_VALUE;
        };

    var childValueSelector =
        SelectorTestUtils.mockIterableValueSelector(
            variableDescriptor, africa, morocco, spain, australia, brazil);

    var mimicReplayingEntitySelector =
        SelectorTestUtils.mockReplayingEntitySelector(entityDescriptor, africa, africa);

    NearEntityNearbyValueSelector<TestdataChainedSolution> valueSelector =
        new NearEntityNearbyValueSelector<>(
            childValueSelector, mimicReplayingEntitySelector, meter, new TestNearbyRandom(), false);

    TestRandom workingRandom = new TestRandom(0);

    InnerScoreDirector<TestdataChainedSolution, ?> scoreDirector = mock(InnerScoreDirector.class);
    SolverScope<TestdataChainedSolution> solverScope =
        SelectorTestUtils.solvingStarted(valueSelector, scoreDirector, workingRandom);
    AbstractPhaseScope<TestdataChainedSolution> phaseScopeA =
        PlannerTestUtils.delegatingPhaseScope(solverScope);
    valueSelector.phaseStarted(phaseScopeA);
    AbstractStepScope<TestdataChainedSolution> stepScopeA1 =
        PlannerTestUtils.delegatingStepScope(phaseScopeA);
    valueSelector.stepStarted(stepScopeA1);

    // Excludes anchors from distance matrix, only returns chained entities
    assertAllCodesOfValueSelectorForEntity(
        valueSelector, africa, "Morocco", "Spain", "Australia", "Brazil");

    valueSelector.stepEnded(stepScopeA1);
    valueSelector.phaseEnded(phaseScopeA);
    valueSelector.solvingEnded(solverScope);
  }
}
