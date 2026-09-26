package greycos.solver.core.impl.localsearch.decider.acceptor.tabu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.calculator.EasyScoreCalculator;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.localsearch.decider.acceptor.LocalSearchAcceptorConfig;
import greycos.solver.core.config.localsearch.decider.forager.LocalSearchForagerConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.PreviewFeature;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.heuristic.move.SelectorBasedCompositeMove;
import greycos.solver.core.impl.heuristic.move.SelectorBasedNoChangeMove;
import greycos.solver.core.impl.localsearch.decider.acceptor.tabu.size.FixedTabuSizeStrategy;
import greycos.solver.core.impl.localsearch.scope.LocalSearchMoveScope;
import greycos.solver.core.impl.localsearch.scope.LocalSearchPhaseScope;
import greycos.solver.core.impl.localsearch.scope.LocalSearchStepScope;
import greycos.solver.core.impl.move.MoveDirector;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.solver.DefaultSolver;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.preview.api.cotwin.metamodel.ElementPosition;
import greycos.solver.core.preview.api.move.Move;
import greycos.solver.core.preview.api.move.builtin.Moves;
import greycos.solver.core.preview.api.neighborhood.Neighborhood;
import greycos.solver.core.preview.api.neighborhood.NeighborhoodBuilder;
import greycos.solver.core.preview.api.neighborhood.NeighborhoodProvider;
import greycos.solver.core.preview.api.neighborhood.stream.dataset.sample.Sample;
import greycos.solver.core.testcotwin.list.unassignedvar.TestdataAllowsUnassignedValuesListEntity;
import greycos.solver.core.testcotwin.list.unassignedvar.TestdataAllowsUnassignedValuesListSolution;
import greycos.solver.core.testcotwin.list.unassignedvar.TestdataAllowsUnassignedValuesListValue;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class MassListEntityTabuAcceptorTest {

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  @SuppressWarnings("unchecked")
  void candidateSourcesAreResolvedFreshThroughNestedComposites(boolean selectorWrapper) {
    var model = TestdataAllowsUnassignedValuesListSolution.buildMetaModel();
    var variable =
        model
            .genuineEntity(TestdataAllowsUnassignedValuesListEntity.class)
            .listVariable("valueList", TestdataAllowsUnassignedValuesListValue.class);
    var value = new TestdataAllowsUnassignedValuesListValue("v");
    var entityA = new TestdataAllowsUnassignedValuesListEntity("A", value);
    var entityB = new TestdataAllowsUnassignedValuesListEntity("B");
    var entityC = new TestdataAllowsUnassignedValuesListEntity("C");
    MoveDirector<TestdataAllowsUnassignedValuesListSolution, SimpleScore> view =
        mock(MoveDirector.class);
    when(view.getEntity(variable, value)).thenReturn(entityA);
    var massMove = Moves.massChange(variable, Sample.of(List.of(value)), null);
    massMove.execute(view);
    assertThat(massMove.getPlanningEntities()).containsExactly(entityA);

    var otherMove = Moves.swap(variable, entityC, 0, entityC, 1);
    var nested = Moves.compose(massMove, otherMove);
    var candidate =
        selectorWrapper
            ? SelectorBasedCompositeMove.buildMove(nested, otherMove)
            : Moves.compose(nested, otherMove);
    InnerScoreDirector<TestdataAllowsUnassignedValuesListSolution, SimpleScore> scoreDirector =
        mock(InnerScoreDirector.class);
    when(scoreDirector.getMoveDirector()).thenReturn(view);
    var solverScope = new SolverScope<TestdataAllowsUnassignedValuesListSolution>();
    solverScope.setScoreDirector(scoreDirector);
    solverScope.setInitializedBestScore(SimpleScore.ZERO);
    var phaseScope = new LocalSearchPhaseScope<>(solverScope, 0);
    var stepScope = new LocalSearchStepScope<>(phaseScope, 1);
    var moveScope = new LocalSearchMoveScope<>(stepScope, 0, candidate);
    moveScope.setInitializedScore(SimpleScore.ZERO);
    var acceptor = new EntityTabuAcceptor<TestdataAllowsUnassignedValuesListSolution>("");
    acceptor.setTabuSizeStrategy(new FixedTabuSizeStrategy<>(2));
    acceptor.phaseStarted(phaseScope);
    acceptor.adjustTabuList(0, List.of(entityB));

    // The old execution snapshot names A, but this candidate now removes the value from B.
    when(view.getEntity(variable, value)).thenReturn(entityB);
    assertThat(acceptor.findTabu(moveScope)).containsExactly(entityB, entityC);
    assertThat(acceptor.isAccepted(moveScope)).isFalse();
    when(view.getEntity(variable, value)).thenReturn(entityA);
    assertThat(acceptor.isAccepted(moveScope)).isTrue();
    assertThat(massMove.getPlanningEntities()).containsExactly(entityA);

    stepScope.setStep(candidate);
    assertThat(acceptor.findNewTabu(stepScope)).containsExactly(entityA, entityC);
    acceptor.phaseEnded(phaseScope);
  }

  static Stream<Arguments> moveThreadsAndComposites() {
    return Stream.of("NONE", "1", "8")
        .flatMap(
            threads ->
                Stream.of(
                        MassNeighborhoodProvider.class,
                        CompositeNeighborhoodProvider.class,
                        SelectorCompositeNeighborhoodProvider.class,
                        NestedSelectorCompositeNeighborhoodProvider.class)
                    .map(providerClass -> Arguments.of(threads, providerClass)));
  }

  @ParameterizedTest
  @MethodSource("moveThreadsAndComposites")
  void rejectsCandidateTouchingCommittedMassMoveSource(
      String moveThreads,
      Class<? extends NeighborhoodProvider<TestdataAllowsUnassignedValuesListSolution>>
          providerClass) {
    var values =
        Stream.of("a0", "a1", "a2", "a3", "b0", "b1")
            .map(TestdataAllowsUnassignedValuesListValue::new)
            .toList();
    var entityA =
        new TestdataAllowsUnassignedValuesListEntity("A", new ArrayList<>(values.subList(0, 4)));
    var entityB =
        new TestdataAllowsUnassignedValuesListEntity("B", new ArrayList<>(values.subList(4, 6)));
    var entityC = new TestdataAllowsUnassignedValuesListEntity("C");
    var solution = new TestdataAllowsUnassignedValuesListSolution();
    solution.setEntityList(List.of(entityA, entityB, entityC));
    solution.setValueList(values);
    var config =
        new SolverConfig()
            .withSolutionClass(TestdataAllowsUnassignedValuesListSolution.class)
            .withEntityClasses(
                TestdataAllowsUnassignedValuesListEntity.class,
                TestdataAllowsUnassignedValuesListValue.class)
            .withEasyScoreCalculatorClass(ConstantScoreCalculator.class)
            .withEnvironmentMode(EnvironmentMode.FULL_ASSERT)
            .withPreviewFeature(PreviewFeature.NEIGHBORHOODS)
            .withMoveThreadCount(moveThreads)
            .withPhases(
                new LocalSearchPhaseConfig()
                    .withMoveProviderClass(providerClass)
                    .withAcceptorConfig(new LocalSearchAcceptorConfig().withEntityTabuSize(2))
                    .withForagerConfig(new LocalSearchForagerConfig().withAcceptedCountLimit(1))
                    .withTerminationConfig(new TerminationConfig().withStepCountLimit(2)));
    var solver =
        (DefaultSolver<TestdataAllowsUnassignedValuesListSolution>)
            SolverFactory.<TestdataAllowsUnassignedValuesListSolution>create(config).buildSolver();
    var stepStates = new ArrayList<List<List<String>>>();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void stepEnded(
              AbstractStepScope<TestdataAllowsUnassignedValuesListSolution> scope) {
            stepStates.add(
                scope.getWorkingSolution().getEntityList().stream()
                    .map(e -> e.getValueList().stream().map(v -> v.getCode()).toList())
                    .toList());
          }
        });

    solver.solve(solution);

    // Step 0 gathers a0/a1 from A into C, making both entities tabu. Step 1 must reject
    // mass-unassigning a2/a3 from A and choose the otherwise eligible B swap instead.
    assertThat(stepStates)
        .containsExactly(
            List.of(List.of("a2", "a3"), List.of("b0", "b1"), List.of("a0", "a1")),
            List.of(List.of("a2", "a3"), List.of("b1", "b0"), List.of("a0", "a1")));
  }

  public static final class ConstantScoreCalculator
      implements EasyScoreCalculator<TestdataAllowsUnassignedValuesListSolution, SimpleScore> {
    @Override
    public SimpleScore calculateScore(TestdataAllowsUnassignedValuesListSolution solution) {
      return SimpleScore.ZERO;
    }
  }

  public static class MassNeighborhoodProvider
      implements NeighborhoodProvider<TestdataAllowsUnassignedValuesListSolution> {

    protected Move<TestdataAllowsUnassignedValuesListSolution> wrapCandidate(
        Move<TestdataAllowsUnassignedValuesListSolution> candidate,
        Move<TestdataAllowsUnassignedValuesListSolution> fallback) {
      return candidate;
    }

    @Override
    public Neighborhood defineNeighborhood(
        NeighborhoodBuilder<TestdataAllowsUnassignedValuesListSolution> builder) {
      var variable =
          builder
              .getSolutionMetaModel()
              .genuineEntity(TestdataAllowsUnassignedValuesListEntity.class)
              .listVariable("valueList", TestdataAllowsUnassignedValuesListValue.class);
      return builder
          .add(
              factory -> {
                var entities =
                    factory
                        .forEach(TestdataAllowsUnassignedValuesListEntity.class, false)
                        .asCachedDataset();
                return factory.buildMoveStream(
                    (session, random) -> {
                      var orderedEntities =
                          new ArrayList<TestdataAllowsUnassignedValuesListEntity>();
                      session
                          .getInstance(entities)
                          .exhaustiveIterator(random)
                          .forEachRemaining(orderedEntities::add);
                      orderedEntities.sort(
                          Comparator.comparing(TestdataAllowsUnassignedValuesListEntity::getCode));
                      var a = orderedEntities.get(0);
                      var b = orderedEntities.get(1);
                      var c = orderedEntities.get(2);
                      if (c.getValueList().isEmpty()) {
                        return List.of(
                                Moves.massChange(
                                    variable,
                                    Sample.of(new ArrayList<>(a.getValueList().subList(0, 2))),
                                    ElementPosition.of(c, 0)))
                            .iterator();
                      }
                      Move<TestdataAllowsUnassignedValuesListSolution> candidate =
                          Moves.massChange(
                              variable, Sample.of(new ArrayList<>(a.getValueList())), null);
                      var fallback = Moves.swap(variable, b, 0, b, 1);
                      return List.of(wrapCandidate(candidate, fallback), fallback).iterator();
                    });
              })
          .build();
    }
  }

  public static final class CompositeNeighborhoodProvider extends MassNeighborhoodProvider {
    @Override
    protected Move<TestdataAllowsUnassignedValuesListSolution> wrapCandidate(
        Move<TestdataAllowsUnassignedValuesListSolution> candidate,
        Move<TestdataAllowsUnassignedValuesListSolution> fallback) {
      return Moves.compose(candidate, fallback);
    }
  }

  public static final class SelectorCompositeNeighborhoodProvider extends MassNeighborhoodProvider {
    @Override
    protected Move<TestdataAllowsUnassignedValuesListSolution> wrapCandidate(
        Move<TestdataAllowsUnassignedValuesListSolution> candidate,
        Move<TestdataAllowsUnassignedValuesListSolution> fallback) {
      return SelectorBasedCompositeMove.buildMove(candidate, fallback);
    }
  }

  public static final class NestedSelectorCompositeNeighborhoodProvider
      extends MassNeighborhoodProvider {
    @Override
    protected Move<TestdataAllowsUnassignedValuesListSolution> wrapCandidate(
        Move<TestdataAllowsUnassignedValuesListSolution> candidate,
        Move<TestdataAllowsUnassignedValuesListSolution> fallback) {
      return SelectorBasedCompositeMove.buildMove(
          Moves.compose(candidate, fallback), SelectorBasedNoChangeMove.getInstance());
    }
  }
}
