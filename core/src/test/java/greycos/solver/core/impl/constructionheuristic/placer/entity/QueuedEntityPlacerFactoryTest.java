package greycos.solver.core.impl.constructionheuristic.placer.entity;

import static greycos.solver.core.impl.constructionheuristic.placer.entity.PlacementAssertions.assertEntityPlacement;
import static greycos.solver.core.impl.heuristic.HeuristicConfigPolicyTestUtils.buildHeuristicConfigPolicy;
import static greycos.solver.core.testutil.PlannerTestUtils.mockSolverScope;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.config.constructionheuristic.placer.QueuedEntityPlacerConfig;
import greycos.solver.core.config.heuristic.selector.common.SelectionOrder;
import greycos.solver.core.config.heuristic.selector.entity.EntitySelectorConfig;
import greycos.solver.core.config.heuristic.selector.entity.EntitySorterManner;
import greycos.solver.core.config.heuristic.selector.move.generic.ChangeMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.value.ValueSelectorConfig;
import greycos.solver.core.impl.constructionheuristic.placer.Placement;
import greycos.solver.core.impl.constructionheuristic.placer.QueuedEntityPlacer;
import greycos.solver.core.impl.constructionheuristic.placer.QueuedEntityPlacerFactory;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import greycos.solver.core.impl.heuristic.HeuristicConfigPolicy;
import greycos.solver.core.impl.heuristic.selector.move.generic.ChangeMoveSelector;
import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.score.director.ValueRangeManager;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.testcotwin.TestdataValue;
import greycos.solver.core.testcotwin.multiplelist.TestdataMultipleListEntity;
import greycos.solver.core.testcotwin.multiplelist.TestdataMultipleListSecondValue;
import greycos.solver.core.testcotwin.multiplelist.TestdataMultipleListSolution;
import greycos.solver.core.testcotwin.multivar.TestdataMultiVarEntity;
import greycos.solver.core.testcotwin.multivar.TestdataMultiVarSolution;
import greycos.solver.core.testcotwin.sort.comparator.TestdataComparatorSortableSolution;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class QueuedEntityPlacerFactoryTest {

  @Test
  void buildFromUnfoldNew() {
    SolutionDescriptor<TestdataMultiVarSolution> solutionDescriptor =
        TestdataMultiVarSolution.buildSolutionDescriptor();

    ChangeMoveSelectorConfig primaryMoveSelectorConfig =
        new ChangeMoveSelectorConfig()
            .withValueSelectorConfig(new ValueSelectorConfig("primaryValue"));
    ChangeMoveSelectorConfig secondaryMoveSelectorConfig =
        new ChangeMoveSelectorConfig()
            .withValueSelectorConfig(new ValueSelectorConfig("secondaryValue"));

    HeuristicConfigPolicy<TestdataMultiVarSolution> configPolicy =
        buildHeuristicConfigPolicy(solutionDescriptor);
    QueuedEntityPlacerConfig placerConfig =
        QueuedEntityPlacerFactory.unfoldNew(
            configPolicy, Arrays.asList(primaryMoveSelectorConfig, secondaryMoveSelectorConfig));

    assertThat(placerConfig.getEntitySelectorConfig().getEntityClass())
        .isAssignableFrom(TestdataMultiVarEntity.class);
    assertThat(placerConfig.getMoveSelectorConfigList())
        .hasSize(2)
        .hasOnlyElementsOfType(ChangeMoveSelectorConfig.class);

    QueuedEntityPlacer<TestdataMultiVarSolution> entityPlacer =
        new QueuedEntityPlacerFactory<TestdataMultiVarSolution>(placerConfig)
            .buildEntityPlacer(configPolicy);

    SolverScope<TestdataMultiVarSolution> solverScope = mockSolverScope();
    entityPlacer.solvingStarted(solverScope);
    AbstractPhaseScope<TestdataMultiVarSolution> phaseScope = mock(AbstractPhaseScope.class);
    when(phaseScope.getSolverScope()).thenReturn(solverScope);
    InnerScoreDirector<TestdataMultiVarSolution, SimpleScore> scoreDirector =
        mock(InnerScoreDirector.class);
    when(solverScope.getScoreDirector()).thenReturn((InnerScoreDirector) scoreDirector);
    when(phaseScope.getScoreDirector()).thenReturn((InnerScoreDirector) scoreDirector);
    var solution = generateTestdataSolution();
    when(scoreDirector.getWorkingSolution()).thenReturn(solution);
    var valueRangeManager = ValueRangeManager.of(solutionDescriptor, solution);
    when(scoreDirector.getValueRangeManager()).thenReturn(valueRangeManager);
    entityPlacer.phaseStarted(phaseScope);
    Iterator<Placement<TestdataMultiVarSolution>> placementIterator = entityPlacer.iterator();
    assertThat(placementIterator).hasNext();

    AbstractStepScope<TestdataMultiVarSolution> stepScope = mock(AbstractStepScope.class);
    when(stepScope.getPhaseScope()).thenReturn(phaseScope);
    when(stepScope.getScoreDirector()).thenReturn((InnerScoreDirector) scoreDirector);
    entityPlacer.stepStarted(stepScope);
    Placement<TestdataMultiVarSolution> placement = placementIterator.next();

    assertEntityPlacement(placement, "e1", "e1v1", "e1v2", "e2v1", "e2v2");
  }

  @ParameterizedTest
  @ValueSource(classes = {TestdataMultipleListEntity.class, TestdataMultipleListSecondValue.class})
  void unnamedExplicitEntitySelectorBindsGeneratedMovesToItsOwnQueue(Class<?> entityClass) {
    var descriptor = TestdataMultipleListSolution.buildSolutionDescriptor();
    var policy = buildHeuristicConfigPolicy(descriptor);
    var configuredSelector = new EntitySelectorConfig(entityClass);
    var placer =
        new QueuedEntityPlacerFactory<TestdataMultipleListSolution>(
                new QueuedEntityPlacerConfig().withEntitySelectorConfig(configuredSelector))
            .buildEntityPlacer(policy);
    var ownerDescriptor = descriptor.findEntityDescriptorOrFail(entityClass);
    assertThat(placer.getEntityDescriptor()).isSameAs(ownerDescriptor);
    assertThat(placer.getCandidateMoveSelectors())
        .singleElement()
        .satisfies(
            selector -> {
              assertThat(selector).isInstanceOf(ChangeMoveSelector.class);
              var change = (ChangeMoveSelector<TestdataMultipleListSolution>) selector;
              assertThat(change.getEntitySelector().getEntityDescriptor())
                  .isSameAs(ownerDescriptor);
              assertThat(change.getValueSelector().getVariableDescriptor().getEntityDescriptor())
                  .isSameAs(ownerDescriptor);
            });
    assertThat(policy.getEntityMimicRecorder(entityClass.getName()).getEntityDescriptor())
        .isSameAs(ownerDescriptor);
    assertThat(configuredSelector.getId()).isNull();
  }

  @Test
  void buildWithEntitySortManner() {
    ChangeMoveSelectorConfig primaryMoveSelectorConfig =
        new ChangeMoveSelectorConfig()
            .withValueSelectorConfig(new ValueSelectorConfig("primaryValue"));
    var configPolicy =
        buildHeuristicConfigPolicy(
            TestdataComparatorSortableSolution.buildSolutionDescriptor(),
            EntitySorterManner.DESCENDING_IF_AVAILABLE);
    QueuedEntityPlacerConfig placerConfig =
        QueuedEntityPlacerFactory.unfoldNew(configPolicy, List.of(primaryMoveSelectorConfig));
    var entityPlacer =
        new QueuedEntityPlacerFactory<TestdataComparatorSortableSolution>(placerConfig);
    var entitySelectorConfig = entityPlacer.buildEntitySelectorConfig(configPolicy);
    assertThat(entitySelectorConfig.getSelectionOrder()).isEqualTo(SelectionOrder.SORTED);
    assertThat(entitySelectorConfig.getSorterManner())
        .isEqualTo(EntitySorterManner.DESCENDING_IF_AVAILABLE);
  }

  private TestdataMultiVarSolution generateTestdataSolution() {
    TestdataMultiVarEntity entity1 = new TestdataMultiVarEntity("e1");
    entity1.setPrimaryValue(new TestdataValue("e1v1"));
    entity1.setSecondaryValue(new TestdataValue("e1v2"));
    TestdataMultiVarEntity entity2 = new TestdataMultiVarEntity("e2");
    entity2.setPrimaryValue(new TestdataValue("e2v1"));
    entity2.setSecondaryValue(new TestdataValue("e2v2"));

    TestdataMultiVarSolution solution = new TestdataMultiVarSolution("s");
    solution.setMultiVarEntityList(Arrays.asList(entity1, entity2));
    solution.setValueList(
        Arrays.asList(
            entity1.getPrimaryValue(),
            entity1.getSecondaryValue(),
            entity2.getPrimaryValue(),
            entity2.getSecondaryValue()));
    return solution;
  }
}
