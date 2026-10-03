package greycos.solver.core.impl.constructionheuristic;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.random.RandomGenerator;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.solution.ProblemFactCollectionProperty;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.cotwin.variable.PlanningVariable;
import greycos.solver.core.api.cotwin.variable.ShadowSources;
import greycos.solver.core.api.cotwin.variable.ShadowVariable;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.calculator.EasyScoreCalculator;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import greycos.solver.core.config.constructionheuristic.decider.forager.ConstructionHeuristicForagerConfig;
import greycos.solver.core.config.constructionheuristic.decider.forager.ConstructionHeuristicPickEarlyType;
import greycos.solver.core.config.constructionheuristic.placer.PooledEntityPlacerConfig;
import greycos.solver.core.config.heuristic.selector.common.SelectionOrder;
import greycos.solver.core.config.heuristic.selector.move.composite.CartesianProductMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.factory.MoveIteratorFactoryConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.ChangeMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.value.ValueSelectorConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.constructionheuristic.decider.forager.DefaultConstructionHeuristicForager;
import greycos.solver.core.impl.constructionheuristic.scope.ConstructionHeuristicMoveScope;
import greycos.solver.core.impl.constructionheuristic.scope.ConstructionHeuristicStepScope;
import greycos.solver.core.impl.heuristic.move.AbstractSelectorBasedMove;
import greycos.solver.core.impl.heuristic.move.SelectorBasedCompositeMove;
import greycos.solver.core.impl.heuristic.move.SelectorBasedNoChangeMove;
import greycos.solver.core.impl.heuristic.selector.move.factory.MoveIteratorFactory;
import greycos.solver.core.impl.heuristic.selector.move.generic.SelectorBasedChangeMove;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.score.director.ScoreDirector;
import greycos.solver.core.impl.score.director.VariableDescriptorAwareScoreDirector;
import greycos.solver.core.impl.solver.DefaultSolver;
import greycos.solver.core.preview.api.move.Move;
import greycos.solver.core.preview.api.move.MutableSolutionView;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class OptionalCompositeConstructionTest {

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void defaultConstructionIncludesAllNullAlternative(boolean emptyRange) {
    assertAllNull(new ConstructionHeuristicPhaseConfig(), emptyRange, emptyRange ? 1 : 4);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void finitePooledConstructionIncludesAllNullAlternative(boolean emptyRange) {
    assertAllNull(pooledProduct(SelectionOrder.ORIGINAL), emptyRange, emptyRange ? 1 : 4);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void nestedAssignmentAndNoChangeCompositesIncludeAllNullAlternative(boolean emptyRange) {
    var phase =
        new ConstructionHeuristicPhaseConfig()
            .withEntityPlacerConfig(
                new PooledEntityPlacerConfig()
                    .withMoveSelectorConfig(
                        new MoveIteratorFactoryConfig()
                            .withMoveIteratorFactoryClass(NestedAssignments.class)))
            .withForagerConfig(
                new ConstructionHeuristicForagerConfig()
                    .withPickEarlyType(ConstructionHeuristicPickEarlyType.NEVER));
    assertAllNull(phase, emptyRange, emptyRange ? 1 : 4);
  }

  @Test
  void randomNullOnlyProductFinishesByEarlyPick() {
    var phase =
        pooledProduct(SelectionOrder.RANDOM)
            .withForagerConfig(
                new ConstructionHeuristicForagerConfig()
                    .withPickEarlyType(
                        ConstructionHeuristicPickEarlyType.FIRST_NON_DETERIORATING_SCORE))
            .withTerminationConfig(new TerminationConfig().withSpentLimit(Duration.ofSeconds(5)));
    assertAllNull(phase, true, 1);
  }

  private static ConstructionHeuristicPhaseConfig pooledProduct(SelectionOrder order) {
    return new ConstructionHeuristicPhaseConfig()
        .withEntityPlacerConfig(
            new PooledEntityPlacerConfig()
                .withMoveSelectorConfig(
                    new CartesianProductMoveSelectorConfig()
                        .withMoveSelectors(
                            new ChangeMoveSelectorConfig()
                                .withValueSelectorConfig(new ValueSelectorConfig("a")),
                            new ChangeMoveSelectorConfig()
                                .withValueSelectorConfig(new ValueSelectorConfig("b")))
                        .withSelectionOrder(order)))
        .withForagerConfig(
            new ConstructionHeuristicForagerConfig()
                .withPickEarlyType(ConstructionHeuristicPickEarlyType.NEVER));
  }

  private static SolverConfig solverConfig(ConstructionHeuristicPhaseConfig phase) {
    return new SolverConfig()
        .withSolutionClass(OptionalSolution.class)
        .withEntityClasses(OptionalEntity.class)
        .withEasyScoreCalculatorClass(PenalizeAssignments.class)
        .withEnvironmentMode(EnvironmentMode.FULL_ASSERT)
        .withMoveThreadCount("NONE")
        .withRandomSeed(0L)
        .withPhases(phase);
  }

  private static OptionalSolution input(boolean emptyRange) {
    var solution = new OptionalSolution();
    solution.entities = List.of(new OptionalEntity());
    solution.values = emptyRange ? List.of() : List.of("v");
    return solution;
  }

  private static void assertAllNull(
      ConstructionHeuristicPhaseConfig phaseConfig, boolean emptyRange, long evaluatedCount) {
    var solver =
        (DefaultSolver<OptionalSolution>)
            SolverFactory.<OptionalSolution>create(solverConfig(phaseConfig)).buildSolver();
    var selectedCounts = new ArrayList<Long>();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void stepEnded(AbstractStepScope<OptionalSolution> scope) {
            selectedCounts.add(
                ((ConstructionHeuristicStepScope<OptionalSolution>) scope).getSelectedMoveCount());
          }
        });
    var result = solver.solve(input(emptyRange));
    assertNullAssignment(result);
    assertThat(selectedCounts).containsExactly(evaluatedCount);
    assertThat(
            ((DefaultConstructionHeuristicPhase<OptionalSolution>) solver.getPhaseList().getFirst())
                .getTerminationStatus()
                .early())
        .isFalse();
  }

  private static void assertNullAssignment(OptionalSolution result) {
    var entity = result.entities.getFirst();
    assertThat(entity.a).isNull();
    assertThat(entity.b).isNull();
    assertThat(entity.assignedCount).isZero();
    assertThat(result.score).isEqualTo(SimpleScore.ZERO);
    assertThat(result.score).isEqualTo(new PenalizeAssignments().calculateScore(result));
  }

  @ParameterizedTest
  @CsvSource({"spent,false", "spent,true", "cancel,false", "cancel,true", "interrupt,true"})
  void rejectedCandidatesObserveTermination(
      String mode, boolean scoredPrefix, @TempDir Path directory) throws Exception {
    var output = directory.resolve("rejected-candidates.log");
    var process =
        new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Xmx256m",
                "-cp",
                System.getProperty("java.class.path"),
                RejectedCandidatesProbe.class.getName(),
                mode,
                Boolean.toString(scoredPrefix))
            .redirectErrorStream(true)
            .redirectOutput(output.toFile())
            .start();
    try {
      assertThat(process.waitFor(20, TimeUnit.SECONDS))
          .withFailMessage("Rejected-candidate probe timed out:%n%s", Files.readString(output))
          .isTrue();
      assertThat(process.exitValue()).withFailMessage(Files.readString(output)).isZero();
      assertThat(Files.readString(output)).contains("REJECTED_CANDIDATES_TERMINATED");
    } finally {
      if (process.isAlive()) {
        process.destroyForcibly();
        process.waitFor();
      }
    }
  }

  public static class RejectedCandidatesProbe {
    private static final CountDownLatch REJECTED = new CountDownLatch(1);
    private static final AtomicInteger EVALUATED = new AtomicInteger();
    private static boolean scoredPrefix;

    public static void main(String[] args) throws Exception {
      var mode = args[0];
      scoredPrefix = Boolean.parseBoolean(args[1]);
      var phase =
          new ConstructionHeuristicPhaseConfig()
              .withEntityPlacerConfig(
                  new PooledEntityPlacerConfig()
                      .withMoveSelectorConfig(
                          new MoveIteratorFactoryConfig()
                              .withMoveIteratorFactoryClass(RejectedCandidates.class)
                              .withSelectionOrder(SelectionOrder.RANDOM)))
              .withForagerConfig(
                  new ConstructionHeuristicForagerConfig().withForagerClass(CountingForager.class));
      if (mode.equals("spent")) {
        phase.withTerminationConfig(new TerminationConfig().withSpentLimit(Duration.ofSeconds(1)));
      }
      var solver =
          (DefaultSolver<OptionalSolution>)
              SolverFactory.<OptionalSolution>create(solverConfig(phase)).buildSolver();
      var completedSteps = new AtomicInteger();
      solver.addPhaseLifecycleListener(
          new PhaseLifecycleListenerAdapter<>() {
            @Override
            public void stepEnded(AbstractStepScope<OptionalSolution> scope) {
              completedSteps.incrementAndGet();
            }
          });
      var task = new FutureTask<>(() -> solver.solve(input(false)));
      var worker = new Thread(task, "rejected-construction-candidates");
      worker.setDaemon(true);
      worker.start();
      try {
        assertThat(REJECTED.await(5, TimeUnit.SECONDS)).as("non-doable candidate reached").isTrue();
        if (mode.equals("cancel")) {
          solver.terminateEarly();
        } else if (mode.equals("interrupt")) {
          worker.interrupt();
        }
        var result = task.get(5, TimeUnit.SECONDS);
        assertNullAssignment(result);
        assertThat(EVALUATED).hasValue(scoredPrefix ? 1 : 0);
        assertThat(completedSteps).hasValue(0);
        assertThat(
                ((DefaultConstructionHeuristicPhase<OptionalSolution>)
                        solver.getPhaseList().getFirst())
                    .getTerminationStatus()
                    .early())
            .isTrue();
        System.out.println("REJECTED_CANDIDATES_TERMINATED");
      } finally {
        worker.interrupt();
        worker.join(1000);
      }
    }
  }

  public static class CountingForager
      extends DefaultConstructionHeuristicForager<OptionalSolution> {
    public CountingForager() {
      super(ConstructionHeuristicPickEarlyType.FIRST_NON_DETERIORATING_SCORE);
    }

    @Override
    public void addMove(ConstructionHeuristicMoveScope<OptionalSolution> scope) {
      RejectedCandidatesProbe.EVALUATED.incrementAndGet();
      super.addMove(scope);
    }
  }

  public static class RejectedCandidates
      implements MoveIteratorFactory<OptionalSolution, Move<OptionalSolution>> {
    @Override
    public long getSize(ScoreDirector<OptionalSolution> scoreDirector) {
      return 1;
    }

    @Override
    public Iterator<Move<OptionalSolution>> createOriginalMoveIterator(
        ScoreDirector<OptionalSolution> scoreDirector) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Iterator<Move<OptionalSolution>> createRandomMoveIterator(
        ScoreDirector<OptionalSolution> scoreDirector, RandomGenerator workingRandom) {
      var assignment = change(scoreDirector, "a", "v");
      var rejected =
          SelectorBasedCompositeMove.buildMove(
              SelectorBasedNoChangeMove.<OptionalSolution>getInstance(),
              new AbstractSelectorBasedMove<OptionalSolution>() {
                @Override
                public boolean isMoveDoable(ScoreDirector<OptionalSolution> director) {
                  RejectedCandidatesProbe.REJECTED.countDown();
                  return false;
                }

                @Override
                protected void execute(
                    MutableSolutionView<OptionalSolution> view,
                    VariableDescriptorAwareScoreDirector<OptionalSolution> director) {
                  throw new AssertionError("A rejected child must not be executed.");
                }
              });
      return new Iterator<>() {
        private boolean first = RejectedCandidatesProbe.scoredPrefix;

        @Override
        public boolean hasNext() {
          return true;
        }

        @Override
        public Move<OptionalSolution> next() {
          if (first) {
            first = false;
            return assignment;
          }
          return rejected;
        }
      };
    }
  }

  public static class NestedAssignments
      implements MoveIteratorFactory<OptionalSolution, Move<OptionalSolution>> {
    @Override
    public long getSize(ScoreDirector<OptionalSolution> scoreDirector) {
      var valueCount = scoreDirector.getWorkingSolution().values.size() + 1L;
      return valueCount * valueCount;
    }

    @Override
    public Iterator<Move<OptionalSolution>> createOriginalMoveIterator(
        ScoreDirector<OptionalSolution> scoreDirector) {
      var values = new ArrayList<>(scoreDirector.getWorkingSolution().values);
      values.add(null);
      var moves = new ArrayList<Move<OptionalSolution>>();
      for (var a : values) {
        for (var b : values) {
          moves.add(
              SelectorBasedCompositeMove.buildMove(
                  change(scoreDirector, "a", a),
                  SelectorBasedCompositeMove.buildMove(
                      SelectorBasedNoChangeMove.<OptionalSolution>getInstance(),
                      change(scoreDirector, "b", b))));
        }
      }
      return moves.iterator();
    }

    @Override
    public Iterator<Move<OptionalSolution>> createRandomMoveIterator(
        ScoreDirector<OptionalSolution> scoreDirector, RandomGenerator workingRandom) {
      throw new UnsupportedOperationException();
    }
  }

  private static Move<OptionalSolution> change(
      ScoreDirector<OptionalSolution> scoreDirector, String variableName, String value) {
    // Moves must use descriptors from the running factory, including its linked variable state.
    var descriptor =
        ((VariableDescriptorAwareScoreDirector<OptionalSolution>) scoreDirector)
            .getSolutionDescriptor()
            .findEntityDescriptorOrFail(OptionalEntity.class)
            .getGenuineVariableDescriptor(variableName);
    return new SelectorBasedChangeMove<>(
        descriptor, scoreDirector.getWorkingSolution().entities.getFirst(), value);
  }

  @PlanningSolution
  public static class OptionalSolution {
    @PlanningEntityCollectionProperty public List<OptionalEntity> entities;

    @ProblemFactCollectionProperty
    @ValueRangeProvider(id = "range")
    public List<String> values;

    @PlanningScore public SimpleScore score;
  }

  @PlanningEntity
  public static class OptionalEntity {
    @PlanningVariable(valueRangeProviderRefs = "range", allowsUnassigned = true)
    public String a;

    @PlanningVariable(valueRangeProviderRefs = "range", allowsUnassigned = true)
    public String b;

    @ShadowVariable(supplierName = "countAssigned")
    public Integer assignedCount;

    @ShadowSources({"a", "b"})
    public Integer countAssigned() {
      return (a == null ? 0 : 1) + (b == null ? 0 : 1);
    }
  }

  public static class PenalizeAssignments
      implements EasyScoreCalculator<OptionalSolution, SimpleScore> {
    @Override
    public SimpleScore calculateScore(OptionalSolution solution) {
      var entity = solution.entities.getFirst();
      var actualAssignedCount = entity.countAssigned();
      assertThat(entity.assignedCount).isEqualTo(actualAssignedCount);
      return SimpleScore.of(-actualAssignedCount);
    }
  }
}
