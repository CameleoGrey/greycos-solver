package greycos.solver.core.impl.localsearch.decider.acceptor.tabu;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.SequencedCollection;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.lookup.Lookup;
import greycos.solver.core.api.cotwin.lookup.PlanningId;
import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.solution.ProblemFactCollectionProperty;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.cotwin.variable.PlanningVariable;
import greycos.solver.core.api.score.Score;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.calculator.EasyScoreCalculator;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.heuristic.selector.common.SelectionCacheType;
import greycos.solver.core.config.heuristic.selector.common.SelectionOrder;
import greycos.solver.core.config.heuristic.selector.move.MoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.factory.MoveListFactoryConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.SwapMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.ListChangeMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.SubListChangeMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.SubListSwapMoveSelectorConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.localsearch.decider.acceptor.LocalSearchAcceptorConfig;
import greycos.solver.core.config.localsearch.decider.forager.LocalSearchForagerConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.heuristic.move.AbstractMove;
import greycos.solver.core.impl.heuristic.move.SelectorBasedNoChangeMove;
import greycos.solver.core.impl.heuristic.selector.move.factory.MoveListFactory;
import greycos.solver.core.impl.move.PreparableMove;
import greycos.solver.core.impl.move.PreparedMoveEvaluation;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.score.director.ScoreDirector;
import greycos.solver.core.impl.solver.DefaultSolver;
import greycos.solver.core.preview.api.move.Move;
import greycos.solver.core.preview.api.move.MutableSolutionView;
import greycos.solver.core.preview.api.move.SolutionView;
import greycos.solver.core.preview.api.move.builtin.Moves;
import greycos.solver.core.testcotwin.list.TestdataListEntity;
import greycos.solver.core.testcotwin.list.TestdataListSolution;
import greycos.solver.core.testcotwin.list.TestdataListValue;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class ValueTabuMetadataTest {

  static Stream<Arguments> listSelectors() {
    return Stream.of("NONE", "1", "4")
        .flatMap(
            threads ->
                Stream.of(
                        new ListChangeMoveSelectorConfig(),
                        new SubListChangeMoveSelectorConfig(),
                        new SubListSwapMoveSelectorConfig())
                    .map(selector -> Arguments.of(threads, selector)));
  }

  @ParameterizedTest
  @MethodSource("listSelectors")
  void listSelectorsSupportValueTabu(String threads, MoveSelectorConfig<?> selector) {
    var solver = buildListSolver(threads, selector.withSelectedCountLimit(100L));
    var states = recordListSteps(solver);

    solver.solve(TestdataListSolution.generateInitializedSolution(10, 2));

    assertThat(states).hasSize(3);
  }

  static Stream<Arguments> customFactories() {
    return Stream.of("NONE", "1", "4")
        .flatMap(
            threads ->
                Stream.of(ExecutionOnlyFactory.class, CompositeFactory.class, PreparedFactory.class)
                    .map(factory -> Arguments.of(threads, factory)));
  }

  @ParameterizedTest
  @MethodSource("customFactories")
  void capturesRealizedMoveBeforeUndoAndRebasesClonedValues(
      String threads, Class<? extends MoveListFactory> factory) {
    var solver =
        buildListSolver(
            threads,
            new MoveListFactoryConfig()
                .withMoveListFactoryClass(factory)
                .withCacheType(SelectionCacheType.STEP)
                .withSelectionOrder(SelectionOrder.ORIGINAL));
    var states = recordListSteps(solver);

    solver.solve(TestdataListSolution.generateInitializedSolution(6, 2));

    // First move transfers v0 to entity 1. The next v0 move must be rejected using coordinator
    // identities, so v1 is moved instead. One step later v0 is eligible again.
    assertThat(states)
        .containsExactly(
            List.of("Generated Entity 1", "Generated Entity 1"),
            List.of("Generated Entity 1", "Generated Entity 0"),
            List.of("Generated Entity 0", "Generated Entity 0"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"NONE", "1", "4"})
  void sharedFactWithoutPlanningIdAndNullValue(String threads) {
    var shared = new SharedValue();
    var solution = new SharedSolution();
    solution.values = List.of(shared);
    solution.entities = List.of(new SharedEntity("a", shared), new SharedEntity("b", null));
    var config =
        new SolverConfig()
            .withSolutionClass(SharedSolution.class)
            .withEntityClasses(SharedEntity.class)
            .withEasyScoreCalculatorClass(SharedScoreCalculator.class)
            .withEnvironmentMode(EnvironmentMode.FULL_ASSERT)
            .withMoveThreadCount(threads)
            .withPhases(phase(new SwapMoveSelectorConfig().withSelectedCountLimit(1L)));
    var solver =
        (DefaultSolver<SharedSolution>) SolverFactory.<SharedSolution>create(config).buildSolver();
    var states = new ArrayList<List<SharedValue>>();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void stepEnded(AbstractStepScope<SharedSolution> step) {
            states.add(step.getWorkingSolution().entities.stream().map(e -> e.value).toList());
          }
        });

    solver.solve(solution);

    assertThat(states).hasSize(3);
    assertThat(states.getFirst()).containsExactly(null, shared);
    assertThat(states.get(1)).containsExactly(shared, null);
  }

  private static DefaultSolver<TestdataListSolution> buildListSolver(
      String threads, MoveSelectorConfig<?> selector) {
    var config =
        new SolverConfig()
            .withSolutionClass(TestdataListSolution.class)
            .withEntityClasses(TestdataListEntity.class, TestdataListValue.class)
            .withEasyScoreCalculatorClass(ListScoreCalculator.class)
            .withEnvironmentMode(EnvironmentMode.FULL_ASSERT)
            .withMoveThreadCount(threads)
            .withPhases(phase(selector));
    return (DefaultSolver<TestdataListSolution>)
        SolverFactory.<TestdataListSolution>create(config).buildSolver();
  }

  private static LocalSearchPhaseConfig phase(MoveSelectorConfig<?> selector) {
    return new LocalSearchPhaseConfig()
        .withMoveSelectorConfig(selector)
        .withAcceptorConfig(
            new LocalSearchAcceptorConfig().withValueTabuSize(1).withLateAcceptanceSize(3))
        .withForagerConfig(new LocalSearchForagerConfig().withAcceptedCountLimit(1))
        .withTerminationConfig(new TerminationConfig().withStepCountLimit(3));
  }

  private static List<List<String>> recordListSteps(DefaultSolver<TestdataListSolution> solver) {
    var states = new ArrayList<List<String>>();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void stepEnded(AbstractStepScope<TestdataListSolution> step) {
            states.add(
                step.getWorkingSolution().getValueList().stream()
                    .limit(2)
                    .map(value -> value.getEntity().getCode())
                    .toList());
          }
        });
    return states;
  }

  public static class ExecutionOnlyFactory implements MoveListFactory<TestdataListSolution> {
    @Override
    public List<? extends Move<TestdataListSolution>> createMoveList(
        TestdataListSolution solution) {
      return solution.getValueList().stream()
          .limit(2)
          .map(
              value -> {
                var source = value.getEntity();
                var destination =
                    solution.getEntityList().stream()
                        .filter(e -> e != source)
                        .findFirst()
                        .orElseThrow();
                return wrap(new ExecutionOnlyMove(source, value.getIndex(), destination));
              })
          .toList();
    }

    protected Move<TestdataListSolution> wrap(ExecutionOnlyMove move) {
      return move;
    }
  }

  public static final class CompositeFactory extends ExecutionOnlyFactory {
    @Override
    protected Move<TestdataListSolution> wrap(ExecutionOnlyMove move) {
      return Moves.compose(move, SelectorBasedNoChangeMove.getInstance());
    }
  }

  public static final class PreparedFactory extends ExecutionOnlyFactory {
    @Override
    protected Move<TestdataListSolution> wrap(ExecutionOnlyMove move) {
      return new PreparedRequest(move);
    }
  }

  private static final class ExecutionOnlyMove extends AbstractMove<TestdataListSolution> {
    private final TestdataListEntity source;
    private final int sourceIndex;
    private final TestdataListEntity destination;
    private TestdataListValue movedValue;

    private ExecutionOnlyMove(
        TestdataListEntity source, int sourceIndex, TestdataListEntity destination) {
      this.source = source;
      this.sourceIndex = sourceIndex;
      this.destination = destination;
    }

    @Override
    protected void doMoveOnGenuineVariables(ScoreDirector<TestdataListSolution> director) {
      director.beforeListVariableChanged(source, "valueList", sourceIndex, sourceIndex + 1);
      movedValue = source.getValueList().remove(sourceIndex);
      director.afterListVariableChanged(source, "valueList", sourceIndex, sourceIndex);
      director.beforeListVariableChanged(destination, "valueList", 0, 0);
      destination.getValueList().addFirst(movedValue);
      director.afterListVariableChanged(destination, "valueList", 0, 1);
    }

    @Override
    public ExecutionOnlyMove rebase(ScoreDirector<TestdataListSolution> director) {
      return new ExecutionOnlyMove(
          director.lookUpWorkingObject(source),
          sourceIndex,
          director.lookUpWorkingObject(destination));
    }

    @Override
    public SequencedCollection<Object> getPlanningEntities() {
      return List.of(source, destination);
    }

    @Override
    public SequencedCollection<Object> getPlanningValues() {
      if (movedValue == null || !destination.getValueList().contains(movedValue)) {
        throw new IllegalStateException(
            "Planning values are available only while the move is applied.");
      }
      return List.of(movedValue);
    }
  }

  private record PreparedRequest(ExecutionOnlyMove move)
      implements PreparableMove<TestdataListSolution> {
    @Override
    public <Score_ extends Score<Score_>>
        PreparedMoveEvaluation<TestdataListSolution, Score_> prepare(
            InnerScoreDirector<TestdataListSolution, Score_> director,
            Runnable checkTermination,
            boolean assertFromScratch,
            BiConsumer<SolutionView<TestdataListSolution>, Move<TestdataListSolution>> consumer) {
      checkTermination.run();
      var score =
          director.executeTemporaryMove(
              move,
              view -> {
                if (consumer != null) consumer.accept(view, move);
              },
              assertFromScratch);
      return new PreparedMoveEvaluation<>(PreparedMoveEvaluation.Status.EVALUATED, move, score, 1);
    }

    @Override
    public void execute(MutableSolutionView<TestdataListSolution> view) {
      throw new IllegalStateException("Prepare the request before execution.");
    }

    @Override
    public PreparedRequest rebase(Lookup lookup) {
      return new PreparedRequest((ExecutionOnlyMove) move.rebase(lookup));
    }
  }

  public static final class ListScoreCalculator
      implements EasyScoreCalculator<TestdataListSolution, SimpleScore> {
    @Override
    public SimpleScore calculateScore(TestdataListSolution solution) {
      return SimpleScore.ZERO;
    }
  }

  public static final class SharedValue {}

  @PlanningEntity
  public static class SharedEntity {
    @PlanningId private String id;

    @PlanningVariable(valueRangeProviderRefs = "values", allowsUnassigned = true)
    private SharedValue value;

    public SharedEntity() {}

    SharedEntity(String id, SharedValue value) {
      this.id = id;
      this.value = value;
    }

    public String getId() {
      return id;
    }

    public void setId(String id) {
      this.id = id;
    }

    public SharedValue getValue() {
      return value;
    }

    public void setValue(SharedValue value) {
      this.value = value;
    }
  }

  @PlanningSolution
  public static class SharedSolution {
    @ValueRangeProvider(id = "values")
    @ProblemFactCollectionProperty
    private List<SharedValue> values;

    @PlanningEntityCollectionProperty private List<SharedEntity> entities;
    @PlanningScore private SimpleScore score;

    public List<SharedValue> getValues() {
      return values;
    }

    public void setValues(List<SharedValue> values) {
      this.values = values;
    }

    public List<SharedEntity> getEntities() {
      return entities;
    }

    public void setEntities(List<SharedEntity> entities) {
      this.entities = entities;
    }

    public SimpleScore getScore() {
      return score;
    }

    public void setScore(SimpleScore score) {
      this.score = score;
    }
  }

  public static final class SharedScoreCalculator
      implements EasyScoreCalculator<SharedSolution, SimpleScore> {
    @Override
    public SimpleScore calculateScore(SharedSolution solution) {
      return SimpleScore.ZERO;
    }
  }
}
