package greycos.solver.core.impl.geneticalgorithm;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.api.score.stream.ConstraintFactory;
import greycos.solver.core.api.score.stream.ConstraintProvider;
import greycos.solver.core.api.solver.SolutionManager;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.api.solver.event.EventProducerId;
import greycos.solver.core.config.geneticalgorithm.GeneticAlgorithmMutationOperatorConfig;
import greycos.solver.core.config.geneticalgorithm.GeneticAlgorithmMutationType;
import greycos.solver.core.config.geneticalgorithm.GeneticAlgorithmPhaseConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.impl.score.director.stream.BavetConstraintStreamScoreDirector;
import greycos.solver.core.impl.score.director.stream.BavetConstraintStreamScoreDirectorFactory;
import greycos.solver.core.impl.solver.DefaultSolver;
import greycos.solver.core.testcotwin.list.TestdataListEntity;
import greycos.solver.core.testcotwin.list.TestdataListSolution;
import greycos.solver.core.testcotwin.list.TestdataListValue;
import greycos.solver.core.testcotwin.list.unassignedvar.pinned.TestdataPinnedUnassignedValuesListEntity;
import greycos.solver.core.testcotwin.list.unassignedvar.pinned.TestdataPinnedUnassignedValuesListSolution;
import greycos.solver.core.testcotwin.list.unassignedvar.pinned.TestdataPinnedUnassignedValuesListValue;
import greycos.solver.core.testcotwin.list.valuerange.TestdataListEntityProvidingEntity;
import greycos.solver.core.testcotwin.list.valuerange.TestdataListEntityProvidingSolution;
import greycos.solver.core.testcotwin.list.valuerange.TestdataListEntityProvidingValue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

@Timeout(30)
class GeneticAlgorithmListIntegrationTest {

  @ParameterizedTest
  @EnumSource(GeneticAlgorithmMutationType.class)
  void nativeListMutationsKeepTheSessionAndReplayEveryCompletedAttempt(
      GeneticAlgorithmMutationType mutation) {
    var solver =
        solver(
            config(
                    new GeneticAlgorithmPhaseConfig()
                        .withPopulationSize(5)
                        .withMutationRateMultiplier(1.0)
                        .withMutationOperators(
                            new GeneticAlgorithmMutationOperatorConfig()
                                .withType(mutation)
                                .withProbability(1.0))
                        .withTerminationConfig(new TerminationConfig().withStepCountLimit(40)))
                .withEnvironmentMode(EnvironmentMode.FULL_ASSERT));
    var trace = trace(solver);

    var result = solver.solve(problem(12, 3));

    assertThat(trace).hasSize(40);
    assertThat(trace).anySatisfy(step -> assertThat(step.changedAssignments()).isPositive());
    assertThat(solver.getSolverScope().getMoveEvaluationCount()).isEqualTo(40);
    assertFreshReplay(result);
  }

  @ParameterizedTest
  @ValueSource(longs = {0, 37, 997})
  void fixedSeedReproducesEveryLogicalAttemptAndPhysicalScoreCount(long seed) {
    var config =
        config(
                new GeneticAlgorithmPhaseConfig()
                    .withPopulationSize(5)
                    .withPBestRate(0.8)
                    .withTabuEntityRate(0.25)
                    .withTerminationConfig(new TerminationConfig().withMoveCountLimit(44L)))
            .withRandomSeed(seed);
    var firstSolver = solver(config);
    var firstTrace = trace(firstSolver);
    var first = firstSolver.solve(problem(12, 3));
    var secondSolver = solver(config.copyConfig());
    var secondTrace = trace(secondSolver);

    var second = secondSolver.solve(problem(12, 3));

    assertThat(firstTrace).hasSize(44);
    assertThat(secondTrace).containsExactlyElementsOf(firstTrace);
    assertThat(assignments(second)).isEqualTo(assignments(first));
    assertThat(second.getScore()).isEqualTo(first.getScore());
    assertThat(secondSolver.getSolverScope().getScoreCalculationCount())
        .isEqualTo(firstSolver.getSolverScope().getScoreCalculationCount());
    assertFreshReplay(first);
    assertFreshReplay(second);
  }

  @Test
  void solverReuseRebuildsListIndexesPopulationAndRandomState() {
    var solver =
        solver(
            config(
                new GeneticAlgorithmPhaseConfig()
                    .withPopulationSize(5)
                    .withTerminationConfig(new TerminationConfig().withStepCountLimit(34))));
    var trace = trace(solver);
    var first = solver.solve(problem(10, 3));
    var firstTrace = List.copyOf(trace);
    var calculations = solver.getSolverScope().getScoreCalculationCount();
    trace.clear();

    var second = solver.solve(problem(10, 3));

    assertThat(trace).containsExactlyElementsOf(firstTrace);
    assertThat(assignments(second)).isEqualTo(assignments(first));
    assertThat(solver.getSolverScope().getMoveEvaluationCount()).isEqualTo(34);
    assertThat(solver.getSolverScope().getScoreCalculationCount()).isEqualTo(calculations);
    assertFreshReplay(first);
    assertFreshReplay(second);
  }

  @Test
  void publishedBestClonesAndInputListsStayIsolatedFromLaterTrials() {
    var solver =
        solver(
            config(
                new GeneticAlgorithmPhaseConfig()
                    .withPopulationSize(7)
                    .withPBestRate(1.0)
                    .withTerminationConfig(new TerminationConfig().withStepCountLimit(70))));
    var bestClones = new ArrayList<TestdataListSolution>();
    var snapshots = new ArrayList<List<List<String>>>();
    var scores = new ArrayList<SimpleScore>();
    solver.addEventListener(
        event -> {
          var best = event.getNewBestSolution();
          assertReplay(best);
          bestClones.add(best);
          snapshots.add(assignments(best));
          scores.add(best.getScore());
        });
    var input = problem(12, 3);
    var original = assignments(input);

    var result = solver.solve(input);

    assertThat(assignments(input)).isEqualTo(original);
    assertStructure(input);
    assertThat(bestClones).hasSizeGreaterThan(1);
    for (var i = 0; i < bestClones.size(); i++) {
      var best = bestClones.get(i);
      assertThat(assignments(best)).isEqualTo(snapshots.get(i));
      assertThat(best.getScore()).isEqualTo(scores.get(i));
      assertReplay(best);
      assertThat(best.getEntityList().getFirst()).isNotSameAs(input.getEntityList().getFirst());
      assertThat(best.getEntityList().getFirst().getValueList())
          .isNotSameAs(input.getEntityList().getFirst().getValueList());
      assertThat(best.getValueList().getFirst()).isNotSameAs(input.getValueList().getFirst());
      if (i > 0) {
        assertThat(scores.get(i)).isGreaterThan(scores.get(i - 1));
        assertThat(best.getValueList().getFirst())
            .isNotSameAs(bestClones.get(i - 1).getValueList().getFirst());
      }
    }
    assertThat(result.getScore()).isEqualTo(scores.getLast());
    assertFreshReplay(result);
  }

  @Test
  void localSearchStartsFromThePublishedGeneticAlgorithmBestWithValidListShadows() {
    var ga =
        new GeneticAlgorithmPhaseConfig()
            .withPopulationSize(5)
            .withTerminationConfig(new TerminationConfig().withStepCountLimit(24));
    var solver =
        solver(
            config(ga)
                .withPhases(
                    ga,
                    new LocalSearchPhaseConfig()
                        .withTerminationConfig(new TerminationConfig().withStepCountLimit(4))));
    var phaseIds = new ArrayList<EventProducerId>();
    var bestAtHandoff = new ArrayList<List<List<String>>>();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void phaseStarted(AbstractPhaseScope<TestdataListSolution> scope) {
            if (scope.getPhaseIndex() == 1) {
              assertReplay(scope.getWorkingSolution());
              assertThat(assignments(scope.getWorkingSolution()))
                  .isEqualTo(assignments(scope.getSolverScope().getBestSolution()));
              bestAtHandoff.add(assignments(scope.getWorkingSolution()));
            }
          }

          @Override
          public void phaseEnded(AbstractPhaseScope<TestdataListSolution> scope) {
            phaseIds.add(scope.getPhaseId());
          }
        });

    var result = solver.solve(problem(10, 3));

    assertThat(phaseIds)
        .containsExactly(EventProducerId.geneticAlgorithm(0), EventProducerId.localSearch(1));
    assertThat(bestAtHandoff).hasSize(1);
    assertFreshReplay(result);
  }

  @Test
  void optionalMembershipAndPinnedPrefixesRemainValidThroughoutTheSolve() {
    var input = new TestdataPinnedUnassignedValuesListSolution();
    var values = new ArrayList<TestdataPinnedUnassignedValuesListValue>();
    for (var i = 0; i < 5; i++) values.add(new TestdataPinnedUnassignedValuesListValue("" + i));
    var first = new TestdataPinnedUnassignedValuesListEntity("0", values.get(0), values.get(1));
    var second = new TestdataPinnedUnassignedValuesListEntity("1", values.get(2), values.get(3));
    first.setPlanningPinToIndex(1);
    second.setPlanningPinToIndex(2);
    input.setEntityList(new ArrayList<>(List.of(first, second)));
    input.setValueList(values);
    var config =
        new SolverConfig()
            .withSolutionClass(TestdataPinnedUnassignedValuesListSolution.class)
            .withEntityClasses(
                TestdataPinnedUnassignedValuesListEntity.class,
                TestdataPinnedUnassignedValuesListValue.class)
            .withConstraintProviderClass(OptionalConstraints.class)
            .withEnvironmentMode(EnvironmentMode.FULL_ASSERT)
            .withRandomSeed(37L)
            .withPhases(
                new GeneticAlgorithmPhaseConfig()
                    .withPopulationSize(7)
                    .withTerminationConfig(new TerminationConfig().withStepCountLimit(60)));
    var solver =
        (DefaultSolver<TestdataPinnedUnassignedValuesListSolution>)
            SolverFactory.<TestdataPinnedUnassignedValuesListSolution>create(config).buildSolver();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void stepEnded(
              AbstractStepScope<TestdataPinnedUnassignedValuesListSolution> step) {
            assertThat(step.getScore().raw()).isEqualTo(optionalReplay(step.getWorkingSolution()));
          }
        });

    var result = solver.solve(input);

    assertThat(result.getScore()).isEqualTo(optionalReplay(result));
    assertThat(result.getScore()).isGreaterThanOrEqualTo(SimpleScore.of(-4));
    assertThat(first.getValueList()).containsExactly(values.get(0), values.get(1));
    assertThat(second.getValueList()).containsExactly(values.get(2), values.get(3));
    var factory =
        new BavetConstraintStreamScoreDirectorFactory<
            TestdataPinnedUnassignedValuesListSolution, SimpleScore>(
            TestdataPinnedUnassignedValuesListSolution.buildSolutionDescriptor(),
            new OptionalConstraints(),
            EnvironmentMode.NO_ASSERT);
    try (var director = factory.createScoreDirectorBuilder().build()) {
      director.setWorkingSolution(
          factory.getSolutionDescriptor().getSolutionCloner().cloneSolution(result));
      assertThat(director.calculateScore().raw()).isEqualTo(optionalReplay(result));
    }
  }

  @Test
  void ownerSpecificRangesRemainValidAfterPopulationSeedingAndCrossover() {
    var values =
        List.of(
            new TestdataListEntityProvidingValue("0"),
            new TestdataListEntityProvidingValue("1"),
            new TestdataListEntityProvidingValue("2"),
            new TestdataListEntityProvidingValue("3"));
    var first =
        new TestdataListEntityProvidingEntity(
            "0", values.subList(0, 3), new ArrayList<>(values.subList(0, 2)));
    var second =
        new TestdataListEntityProvidingEntity(
            "1", values.subList(2, 4), new ArrayList<>(values.subList(2, 4)));
    var input = new TestdataListEntityProvidingSolution();
    input.setEntityList(new ArrayList<>(List.of(first, second)));
    var config =
        new SolverConfig()
            .withSolutionClass(TestdataListEntityProvidingSolution.class)
            .withEntityClasses(
                TestdataListEntityProvidingEntity.class, TestdataListEntityProvidingValue.class)
            .withConstraintProviderClass(RangeConstraints.class)
            .withEnvironmentMode(EnvironmentMode.FULL_ASSERT)
            .withRandomSeed(37L)
            .withPhases(
                new GeneticAlgorithmPhaseConfig()
                    .withPopulationSize(7)
                    .withTerminationConfig(new TerminationConfig().withStepCountLimit(60)));
    var solver =
        (DefaultSolver<TestdataListEntityProvidingSolution>)
            SolverFactory.<TestdataListEntityProvidingSolution>create(config).buildSolver();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void stepEnded(AbstractStepScope<TestdataListEntityProvidingSolution> step) {
            assertThat(step.getScore().raw()).isEqualTo(rangeReplay(step.getWorkingSolution()));
          }
        });

    var result = solver.solve(input);

    assertThat(result.getScore()).isEqualTo(rangeReplay(result));
    assertThat(first.getValueList()).containsExactly(values.get(0), values.get(1));
    assertThat(second.getValueList()).containsExactly(values.get(2), values.get(3));
    var factory =
        new BavetConstraintStreamScoreDirectorFactory<
            TestdataListEntityProvidingSolution, SimpleScore>(
            TestdataListEntityProvidingSolution.buildSolutionDescriptor(),
            new RangeConstraints(),
            EnvironmentMode.NO_ASSERT);
    try (var director = factory.createScoreDirectorBuilder().build()) {
      director.setWorkingSolution(
          factory.getSolutionDescriptor().getSolutionCloner().cloneSolution(result));
      assertThat(director.calculateScore().raw()).isEqualTo(rangeReplay(result));
    }
  }

  private static SimpleScore optionalReplay(TestdataPinnedUnassignedValuesListSolution solution) {
    assertThat(solution.getEntityList().getFirst().getValueList().getFirst().getCode())
        .isEqualTo("0");
    // A pin index equal to the initial size fixes that prefix but still permits appending.
    assertThat(solution.getEntityList().getLast().getValueList())
        .extracting(TestdataPinnedUnassignedValuesListValue::getCode)
        .startsWith("2", "3");
    var seen =
        Collections.newSetFromMap(
            new IdentityHashMap<TestdataPinnedUnassignedValuesListValue, Boolean>());
    for (var owner : solution.getEntityList()) {
      for (var i = 0; i < owner.getValueList().size(); i++) {
        var value = owner.getValueList().get(i);
        assertThat(seen.add(value)).isTrue();
        assertThat(value.getEntity()).isSameAs(owner);
        assertThat(value.getIndex()).isEqualTo(i);
        assertThat(value.getPrevious()).isSameAs(i == 0 ? null : owner.getValueList().get(i - 1));
        assertThat(value.getNext())
            .isSameAs(
                i + 1 == owner.getValueList().size() ? null : owner.getValueList().get(i + 1));
      }
    }
    for (var value : solution.getValueList()) {
      if (!seen.contains(value)) {
        assertThat(value.getEntity()).isNull();
        assertThat(value.getIndex()).isNull();
        assertThat(value.getPrevious()).isNull();
        assertThat(value.getNext()).isNull();
      }
    }
    return SimpleScore.of(-seen.size());
  }

  private static SimpleScore rangeReplay(TestdataListEntityProvidingSolution solution) {
    var seen =
        Collections.newSetFromMap(new IdentityHashMap<TestdataListEntityProvidingValue, Boolean>());
    var score = 0;
    for (var owner : solution.getEntityList()) {
      for (var i = 0; i < owner.getValueList().size(); i++) {
        var value = owner.getValueList().get(i);
        assertThat(seen.add(value)).isTrue();
        assertThat(owner.getValueRange())
            .anySatisfy(canonical -> assertThat(canonical).isSameAs(value));
        assertThat(value.getEntity()).isSameAs(owner);
        assertThat(value.getIndex()).isEqualTo(i);
        score += (Integer.parseInt(value.getCode()) + 1) * (i + 1);
      }
    }
    assertThat(seen).hasSize(solution.getValueList().size());
    return SimpleScore.of(score);
  }

  static SolverConfig config(GeneticAlgorithmPhaseConfig phase) {
    return new SolverConfig()
        .withSolutionClass(TestdataListSolution.class)
        .withEntityClasses(TestdataListEntity.class, TestdataListValue.class)
        .withConstraintProviderClass(ListConstraints.class)
        .withEnvironmentMode(EnvironmentMode.NO_ASSERT)
        .withRandomSeed(37L)
        .withPhases(phase);
  }

  static DefaultSolver<TestdataListSolution> solver(SolverConfig config) {
    return (DefaultSolver<TestdataListSolution>)
        SolverFactory.<TestdataListSolution>create(config).buildSolver();
  }

  static TestdataListSolution problem(int valueCount, int ownerCount) {
    var solution = new TestdataListSolution();
    var owners = new ArrayList<TestdataListEntity>();
    for (var i = 0; i < ownerCount; i++) owners.add(new TestdataListEntity("" + i));
    var values = new ArrayList<TestdataListValue>();
    for (var i = 0; i < valueCount; i++) {
      var value = new TestdataListValue("" + i);
      values.add(value);
      owners.get(i % ownerCount).getValueList().add(value);
    }
    solution.setEntityList(owners);
    solution.setValueList(values);
    SolutionManager.updateShadowVariables(solution);
    return solution;
  }

  static List<List<String>> assignments(TestdataListSolution solution) {
    return solution.getEntityList().stream()
        .map(owner -> owner.getValueList().stream().map(TestdataListValue::getCode).toList())
        .toList();
  }

  static SimpleScore replayScore(TestdataListSolution solution) {
    var score = 0;
    for (var owner : solution.getEntityList()) {
      for (var i = 0; i < owner.getValueList().size(); i++) {
        score +=
            (Integer.parseInt(owner.getCode()) + 1)
                * (i + 1)
                * (Integer.parseInt(owner.getValueList().get(i).getCode()) + 1);
      }
    }
    return SimpleScore.of(score);
  }

  static void assertStructure(TestdataListSolution solution) {
    var seen = Collections.newSetFromMap(new IdentityHashMap<TestdataListValue, Boolean>());
    for (var owner : solution.getEntityList()) {
      for (var i = 0; i < owner.getValueList().size(); i++) {
        var value = owner.getValueList().get(i);
        assertThat(seen.add(value)).isTrue();
        assertThat(solution.getValueList())
            .anySatisfy(canonical -> assertThat(canonical).isSameAs(value));
        assertThat(value.getEntity()).isSameAs(owner);
        assertThat(value.getIndex()).isEqualTo(i);
      }
    }
    assertThat(seen).hasSize(solution.getValueList().size());
  }

  static void assertReplay(TestdataListSolution solution) {
    assertStructure(solution);
    assertThat(solution.getScore()).isEqualTo(replayScore(solution));
  }

  static void assertFreshReplay(TestdataListSolution solution) {
    assertReplay(solution);
    var factory =
        new BavetConstraintStreamScoreDirectorFactory<TestdataListSolution, SimpleScore>(
            TestdataListSolution.buildSolutionDescriptor(),
            new ListConstraints(),
            EnvironmentMode.NO_ASSERT);
    try (var director = factory.createScoreDirectorBuilder().build()) {
      director.setWorkingSolution(
          factory.getSolutionDescriptor().getSolutionCloner().cloneSolution(solution));
      assertThat(director.calculateScore().raw()).isEqualTo(replayScore(solution));
      assertStructure(director.getWorkingSolution());
    }
  }

  static List<Step> trace(DefaultSolver<TestdataListSolution> solver) {
    var trace = new ArrayList<Step>();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          private Object session;

          @Override
          public void phaseStarted(AbstractPhaseScope<TestdataListSolution> scope) {
            session =
                ((BavetConstraintStreamScoreDirector<?, ?>) scope.getScoreDirector()).getSession();
          }

          @Override
          public void stepEnded(AbstractStepScope<TestdataListSolution> scope) {
            var step = (GeneticAlgorithmStepScope<TestdataListSolution>) scope;
            assertStructure(step.getWorkingSolution());
            assertThat(step.getScore().raw()).isEqualTo(replayScore(step.getWorkingSolution()));
            assertThat(
                    ((BavetConstraintStreamScoreDirector<?, ?>) scope.getScoreDirector())
                        .getSession())
                .isSameAs(session);
            trace.add(
                new Step(
                    step.getStepIndex(),
                    step.isSeeding(),
                    step.getGeneration(),
                    step.getCandidateId(),
                    step.getFirstParentId(),
                    step.getSecondParentId(),
                    step.getNativeId(),
                    step.isCrossed(),
                    step.getMutationType(),
                    step.getMutationGroup(),
                    step.getOutcome(),
                    step.isAdmitted(),
                    step.getChangedAssignmentCount(),
                    step.getBeforeScore(),
                    step.getBestBeforeScore(),
                    step.getCandidateScore(),
                    step.getScore(),
                    step.getBestScoreImproved(),
                    assignments(step.getWorkingSolution())));
          }
        });
    return trace;
  }

  record Step(
      int index,
      boolean seeding,
      long generation,
      long candidate,
      long firstParent,
      long secondParent,
      long nativeMember,
      boolean crossed,
      GeneticAlgorithmMutationType mutation,
      String group,
      GeneticAlgorithmOutcome outcome,
      boolean admitted,
      int changedAssignments,
      InnerScore<?> beforeScore,
      InnerScore<?> bestBeforeScore,
      InnerScore<?> candidateScore,
      InnerScore<?> workspaceScore,
      boolean bestImproved,
      List<List<String>> assignments) {}

  public static final class ListConstraints implements ConstraintProvider {
    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      return new Constraint[] {
        factory
            .forEach(TestdataListValue.class)
            .reward(
                SimpleScore.ONE,
                value ->
                    (Integer.parseInt(value.getCode()) + 1)
                        * (Integer.parseInt(value.getEntity().getCode()) + 1)
                        * (value.getIndex() + 1))
            .asConstraint("Weighted position")
      };
    }
  }

  public static final class OptionalConstraints implements ConstraintProvider {
    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      return new Constraint[] {
        factory
            .forEachIncludingUnassigned(TestdataPinnedUnassignedValuesListValue.class)
            .filter(value -> value.getEntity() != null)
            .penalize(SimpleScore.ONE)
            .asConstraint("Assigned values")
      };
    }
  }

  public static final class RangeConstraints implements ConstraintProvider {
    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      return new Constraint[] {
        factory
            .forEach(TestdataListEntityProvidingValue.class)
            .reward(
                SimpleScore.ONE,
                value -> (Integer.parseInt(value.getCode()) + 1) * (value.getIndex() + 1))
            .asConstraint("Weighted range position")
      };
    }
  }
}
