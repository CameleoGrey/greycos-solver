package greycos.solver.core.impl.islandmodel.resumable;

import static greycos.solver.core.impl.islandmodel.resumable.ResumableSearchTestSupport.sessions;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.random.RandomGenerator;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.lookup.PlanningId;
import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.solution.ProblemFactCollectionProperty;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.cotwin.variable.PlanningVariable;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.calculator.EasyScoreCalculator;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.heuristic.selector.common.SelectionOrder;
import greycos.solver.core.config.heuristic.selector.move.factory.MoveIteratorFactoryConfig;
import greycos.solver.core.config.islandmodel.ResumableSearchCooperationPolicy;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.localsearch.decider.acceptor.AcceptorType;
import greycos.solver.core.config.localsearch.decider.acceptor.LocalSearchAcceptorConfig;
import greycos.solver.core.config.localsearch.decider.forager.LocalSearchForagerConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.heuristic.selector.move.factory.MoveIteratorFactory;
import greycos.solver.core.impl.heuristic.selector.move.generic.ChangeMove;
import greycos.solver.core.impl.localsearch.decider.forager.AbstractLocalSearchForager;
import greycos.solver.core.impl.localsearch.scope.LocalSearchMoveScope;
import greycos.solver.core.impl.localsearch.scope.LocalSearchPhaseScope;
import greycos.solver.core.impl.localsearch.scope.LocalSearchStepScope;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.score.director.ScoreDirector;

import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** An explicit plateau makes restart timing independent of random improvement opportunities. */
@Isolated("Records the independently created scripted foragers.")
@Timeout(30)
class ResumableCooperationBoundaryTest {

  @ParameterizedTest
  @ValueSource(ints = {1, 2})
  void restartUsesCompletedRoundsAndReplacementRunsInTheImmediatelyFollowingRound(int threshold)
      throws Exception {
    List<List<Long>> expected =
        threshold == 1
            ? List.of(
                List.of(0L, 0L, 0L), List.of(0L, 1L, 0L), List.of(0L, 1L, 1L), List.of(0L, 1L, 1L))
            : List.of(
                List.of(0L, 0L, 0L), List.of(0L, 0L, 0L), List.of(0L, 1L, 0L), List.of(0L, 1L, 1L));
    for (int workers : new int[] {1, 3}) {
      ScriptForager.INSTANCES.clear();
      var search =
          new LocalSearchPhaseConfig()
              .withMoveSelectorConfig(
                  new MoveIteratorFactoryConfig()
                      .withMoveIteratorFactoryClass(ScriptMoves.class)
                      .withSelectionOrder(SelectionOrder.ORIGINAL))
              .withAcceptorConfig(
                  new LocalSearchAcceptorConfig()
                      .withAcceptorTypeList(List.of(AcceptorType.HILL_CLIMBING)))
              .withForagerConfig(
                  new LocalSearchForagerConfig().withForagerClass(ScriptForager.class))
              .withTerminationConfig(new TerminationConfig().withStepCountLimit(100));
      var phase =
          sessions(search, 3, workers, 1, ResumableSearchCooperationPolicy.RESTART_WEAKEST)
              .withTerminationConfig(new TerminationConfig().withStepCountLimit(12));
      phase.getResumableSearchConfig().setStagnationRounds(threshold);
      var config =
          new SolverConfig()
              .withSolutionClass(ScriptSolution.class)
              .withEntityClasses(ScriptEntity.class)
              .withEasyScoreCalculatorClass(ScriptScore.class)
              .withEnvironmentMode(EnvironmentMode.FULL_ASSERT)
              .withRandomSeed(37L)
              .withMoveThreadCount("NONE")
              .withPhases(phase);
      var episodes = new ArrayList<List<Long>>();
      try (var ignored =
          ResumableSearchRuntime.observeRounds(
              round -> {
                assertThat(round.round()).isEqualTo(episodes.size());
                assertThat(round.sessions()).hasSize(3);
                episodes.add(
                    round.sessions().stream()
                        .map(ResumableSearchRuntime.SessionSummary::episode)
                        .toList());
                assertThat(round.sessions())
                    .allSatisfy(
                        session -> {
                          assertThat(session.steps()).hasSize(1);
                          var step = session.steps().getFirst();
                          var solution = (ScriptSolution) step.solution();
                          assertThat(step.score().raw())
                              .isEqualTo(new ScriptScore().calculateScore(solution));
                        });
              })) {
        var result = SolverFactory.<ScriptSolution>create(config).buildSolver().solve(problem());
        assertThat(result.getScore()).isEqualTo(SimpleScore.ZERO);
      }
      assertThat(episodes).isEqualTo(expected);
      // Three original episodes and exactly two replacements; each lifecycle closes once.
      assertThat(ScriptForager.INSTANCES)
          .hasSize(5)
          .allSatisfy(
              forager -> {
                assertThat(forager.started).isEqualTo(1);
                assertThat(forager.ended).isEqualTo(1);
              });
    }
  }

  private static ScriptSolution problem() {
    var solution = new ScriptSolution();
    solution.values =
        List.of(
            new ScriptValue(0, -1),
            new ScriptValue(1, -1),
            new ScriptValue(2, 0),
            new ScriptValue(3, 0));
    var entity = new ScriptEntity();
    entity.value = solution.values.getFirst();
    solution.entities = List.of(entity);
    solution.score = SimpleScore.of(-1);
    return solution;
  }

  public record ScriptValue(@PlanningId int id, long score) {}

  @PlanningEntity
  public static class ScriptEntity {
    @PlanningId private int id = 0;

    @PlanningVariable(valueRangeProviderRefs = "values")
    private ScriptValue value;

    public ScriptEntity() {}

    public int getId() {
      return id;
    }

    public ScriptValue getValue() {
      return value;
    }

    public void setValue(ScriptValue value) {
      this.value = value;
    }
  }

  @PlanningSolution
  public static class ScriptSolution {
    @ProblemFactCollectionProperty
    @ValueRangeProvider(id = "values")
    private List<ScriptValue> values;

    @PlanningEntityCollectionProperty private List<ScriptEntity> entities;
    @PlanningScore private SimpleScore score;
    // A deterministic controller role, assigned from the logical session path at phase start.
    private boolean protectedLane;

    public ScriptSolution() {}

    public List<ScriptValue> getValues() {
      return values;
    }

    public void setValues(List<ScriptValue> values) {
      this.values = values;
    }

    public List<ScriptEntity> getEntities() {
      return entities;
    }

    public void setEntities(List<ScriptEntity> entities) {
      this.entities = entities;
    }

    public SimpleScore getScore() {
      return score;
    }

    public void setScore(SimpleScore score) {
      this.score = score;
    }
  }

  public static final class ScriptScore
      implements EasyScoreCalculator<ScriptSolution, SimpleScore> {
    @Override
    public SimpleScore calculateScore(ScriptSolution solution) {
      return SimpleScore.of(solution.entities.getFirst().value.score());
    }
  }

  public static final class ScriptMoves
      implements MoveIteratorFactory<ScriptSolution, ChangeMove<ScriptSolution>> {
    @Override
    public long getSize(ScoreDirector<ScriptSolution> director) {
      return 1;
    }

    @Override
    public Iterator<ChangeMove<ScriptSolution>> createOriginalMoveIterator(
        ScoreDirector<ScriptSolution> director) {
      var solution = director.getWorkingSolution();
      var entity = solution.entities.getFirst();
      int target =
          entity.value.score() == 0
              ? (entity.value.id() == 2 ? 3 : 2)
              : solution.protectedLane ? 2 : (entity.value.id() == 0 ? 1 : 0);
      var variable =
          ((InnerScoreDirector<ScriptSolution, ?>) director)
              .getSolutionDescriptor()
              .findEntityDescriptor(ScriptEntity.class)
              .getGenuineVariableDescriptor("value");
      return List.of(new ChangeMove<ScriptSolution>(variable, entity, solution.values.get(target)))
          .iterator();
    }

    @Override
    public Iterator<ChangeMove<ScriptSolution>> createRandomMoveIterator(
        ScoreDirector<ScriptSolution> director, RandomGenerator random) {
      return createOriginalMoveIterator(director);
    }
  }

  public static final class ScriptForager extends AbstractLocalSearchForager<ScriptSolution> {
    static final ConcurrentLinkedQueue<ScriptForager> INSTANCES = new ConcurrentLinkedQueue<>();
    private LocalSearchMoveScope<ScriptSolution> selected;
    int started;
    int ended;

    public ScriptForager() {
      INSTANCES.add(this);
    }

    @Override
    public void phaseStarted(LocalSearchPhaseScope<ScriptSolution> scope) {
      started++;
      String segment =
          java.util.Arrays.stream(scope.getSolverScope().getMetricSource().split("/"))
              .filter(part -> part.startsWith("island-"))
              .findFirst()
              .orElseThrow();
      scope.getWorkingSolution().protectedLane =
          Integer.parseInt(segment.substring("island-".length())) == 0;
    }

    @Override
    public void phaseEnded(LocalSearchPhaseScope<ScriptSolution> scope) {
      ended++;
    }

    @Override
    public void stepStarted(LocalSearchStepScope<ScriptSolution> scope) {
      selected = null;
    }

    @Override
    public boolean supportsNeverEndingMoveSelector() {
      return false;
    }

    @Override
    public void addMove(LocalSearchMoveScope<ScriptSolution> move) {
      assertThat(move.getAccepted()).isTrue();
      selected = move;
    }

    @Override
    public boolean isQuitEarly() {
      return selected != null;
    }

    @Override
    public LocalSearchMoveScope<ScriptSolution> pickMove(
        LocalSearchStepScope<ScriptSolution> scope) {
      scope.setSelectedMoveCount(selected == null ? 0L : 1L);
      scope.setAcceptedMoveCount(selected == null ? 0L : 1L);
      return selected;
    }
  }
}
