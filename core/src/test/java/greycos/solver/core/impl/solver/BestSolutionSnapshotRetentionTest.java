package greycos.solver.core.impl.solver;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import greycos.solver.core.api.score.Score;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.calculator.EasyScoreCalculator;
import greycos.solver.core.api.solver.SolutionManager;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.api.solver.phase.PhaseCommand;
import greycos.solver.core.config.phase.custom.CustomPhaseConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.PreviewFeature;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.preview.api.move.builtin.Moves;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataObject;
import greycos.solver.core.testcotwin.TestdataValue;
import greycos.solver.core.testcotwin.clone.customcloner.TestdataCorrectlyClonedSolution;
import greycos.solver.core.testcotwin.list.TestdataListEntity;
import greycos.solver.core.testcotwin.list.TestdataListSolution;
import greycos.solver.core.testcotwin.list.TestdataListValue;
import greycos.solver.core.testcotwin.mixed.singleentity.TestdataMixedEntity;
import greycos.solver.core.testcotwin.mixed.singleentity.TestdataMixedOtherValue;
import greycos.solver.core.testcotwin.mixed.singleentity.TestdataMixedSolution;
import greycos.solver.core.testcotwin.mixed.singleentity.TestdataMixedValue;
import greycos.solver.core.testcotwin.shadow.TestdataShadowedEntity;
import greycos.solver.core.testcotwin.shadow.TestdataShadowedSolution;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(10)
class BestSolutionSnapshotRetentionTest {

  @Test
  void basicAssignmentsAndDeclarativeShadowsSurviveLaterImprovementsAndDirectorReplacement() {
    var config =
        baseConfig()
            .withSolutionClass(TestdataShadowedSolution.class)
            .withEntityClasses(TestdataShadowedEntity.class)
            .withEasyScoreCalculatorClass(ShadowedCalculator.class)
            .withPhases(
                phase(changeShadowedValue(1)),
                phase(changeShadowedValue(2)).withEnvironmentMode(EnvironmentMode.FULL_ASSERT));
    var problem = TestdataShadowedSolution.generateSolution(3, 1);

    var snapshots =
        solveAndCheck(
            config,
            problem,
            solution ->
                solution.getEntityList().stream()
                    .map(
                        entity ->
                            entity.getCode()
                                + ":"
                                + code(entity.getValue())
                                + ":"
                                + entity.getFirstShadow())
                    .toList()
                    .toString());

    assertThat(snapshots.stream().map(Snapshot::score))
        .containsExactly(SimpleScore.ZERO, SimpleScore.ONE, SimpleScore.of(2));
    assertThat(snapshots.getFirst().solution().getEntityList().getFirst().getFirstShadow())
        .isEqualTo(problem.getValueList().getFirst().getCode() + "/firstShadow");
  }

  @Test
  void listAssignmentsOwnershipAndIndexesSurvivePhaseHandoffsAndCleanup() {
    var config =
        baseConfig()
            .withSolutionClass(TestdataListSolution.class)
            .withEntityClasses(TestdataListEntity.class, TestdataListValue.class)
            .withEasyScoreCalculatorClass(ListCalculator.class)
            .withPhases(
                phase(moveListValue()),
                phase(moveListValue()).withEnvironmentMode(EnvironmentMode.FULL_ASSERT));
    var problem = TestdataListSolution.generateUninitializedSolution(3, 2);
    problem.getEntityList().getFirst().getValueList().addAll(problem.getValueList());

    var snapshots =
        solveAndCheck(
            config,
            problem,
            solution ->
                solution.getEntityList().stream()
                        .map(
                            entity ->
                                entity.getCode()
                                    + ":"
                                    + entity.getValueList().stream()
                                        .map(TestdataObject::getCode)
                                        .toList())
                        .toList()
                    + ";"
                    + solution.getValueList().stream()
                        .map(
                            value ->
                                value.getCode()
                                    + ":"
                                    + code(value.getEntity())
                                    + ":"
                                    + value.getIndex())
                        .toList());

    assertThat(snapshots.stream().map(Snapshot::score))
        .containsExactly(SimpleScore.ZERO, SimpleScore.ONE, SimpleScore.of(2));
    assertThat(snapshots.getFirst().solution().getEntityList().getFirst().getValueList())
        .hasSize(3);
  }

  @Test
  void mixedAssignmentsInverseCollectionsAndCascadingShadowsRemainStable() {
    var config =
        baseConfig()
            .withSolutionClass(TestdataMixedSolution.class)
            .withEntityClasses(
                TestdataMixedEntity.class, TestdataMixedValue.class, TestdataMixedOtherValue.class)
            .withEasyScoreCalculatorClass(MixedCalculator.class)
            .withPhases(
                phase(changeMixedAssignments(0)),
                phase(changeMixedAssignments(1)).withEnvironmentMode(EnvironmentMode.FULL_ASSERT));
    var problem = TestdataMixedSolution.generateUninitializedSolution(2, 3, 2);
    for (var entity : problem.getEntityList()) {
      entity.setBasicValue(problem.getOtherValueList().getFirst());
      entity.setSecondBasicValue(problem.getOtherValueList().getFirst());
    }
    problem.getEntityList().getFirst().getValueList().addAll(problem.getValueList());

    var snapshots =
        solveAndCheck(config, problem, BestSolutionSnapshotRetentionTest::mixedFingerprint);

    assertThat(snapshots.stream().map(Snapshot::score))
        .containsExactly(SimpleScore.ZERO, SimpleScore.of(2), SimpleScore.of(4));
    assertThat(snapshots.getFirst().solution().getOtherValueList().getFirst().getEntityList())
        .hasSize(2);
    assertThat(snapshots.getLast().solution().getOtherValueList().getFirst().getEntityList())
        .isEmpty();
  }

  @Test
  void supportedCustomClonerSnapshotsSurviveSubsequentMutation() {
    var config =
        baseConfig()
            .withSolutionClass(TestdataCorrectlyClonedSolution.class)
            .withEntityClasses(TestdataEntity.class)
            .withEasyScoreCalculatorClass(CustomClonerCalculator.class)
            .withPhases(
                phase(changeCustomClonedValue(1)),
                phase(changeCustomClonedValue(0)).withEnvironmentMode(EnvironmentMode.FULL_ASSERT));
    var problem = new TestdataCorrectlyClonedSolution();
    problem.getEntity().setValue(problem.valueRange().getFirst());

    var snapshots =
        solveAndCheck(config, problem, solution -> code(solution.getEntity().getValue()));

    assertThat(snapshots.stream().map(Snapshot::score))
        .containsExactly(SimpleScore.ZERO, SimpleScore.ONE);
    assertThat(snapshots)
        .allSatisfy(snapshot -> assertThat(snapshot.solution().isClonedByCustomCloner()).isTrue());
  }

  private static SolverConfig baseConfig() {
    return new SolverConfig()
        .withEnvironmentMode(EnvironmentMode.REPRODUCIBLE)
        .withMoveThreadCount(SolverConfig.MOVE_THREAD_COUNT_NONE)
        .withPreviewFeature(PreviewFeature.NEIGHBORHOODS);
  }

  private static <Solution_> CustomPhaseConfig phase(PhaseCommand<Solution_> command) {
    return new CustomPhaseConfig().withCustomPhaseCommands(command);
  }

  private static PhaseCommand<TestdataShadowedSolution> changeShadowedValue(int index) {
    return context -> {
      var solution = context.getWorkingSolution();
      var variable =
          context
              .getSolutionMetaModel()
              .genuineEntity(TestdataShadowedEntity.class)
              .basicVariable("value", TestdataValue.class);
      context.executeAndCalculateScore(
          Moves.change(
              variable, solution.getEntityList().getFirst(), solution.getValueList().get(index)));
    };
  }

  private static PhaseCommand<TestdataListSolution> moveListValue() {
    return context -> {
      var entities = context.getWorkingSolution().getEntityList();
      var variable =
          context
              .getSolutionMetaModel()
              .genuineEntity(TestdataListEntity.class)
              .listVariable("valueList", TestdataListValue.class);
      context.executeAndCalculateScore(
          Moves.change(
              variable,
              entities.getFirst(),
              0,
              entities.get(1),
              entities.get(1).getValueList().size()));
    };
  }

  private static PhaseCommand<TestdataMixedSolution> changeMixedAssignments(int entityIndex) {
    return context -> {
      var solution = context.getWorkingSolution();
      var metaModel = context.getSolutionMetaModel().genuineEntity(TestdataMixedEntity.class);
      context.execute(
          Moves.change(
              metaModel.basicVariable("basicValue", TestdataMixedOtherValue.class),
              solution.getEntityList().get(entityIndex),
              solution.getOtherValueList().get(1)));
      var target = solution.getEntityList().get(1);
      context.executeAndCalculateScore(
          Moves.change(
              metaModel.listVariable("valueList", TestdataMixedValue.class),
              solution.getEntityList().getFirst(),
              0,
              target,
              target.getValueList().size()));
    };
  }

  private static PhaseCommand<TestdataCorrectlyClonedSolution> changeCustomClonedValue(int index) {
    return context -> {
      var solution = context.getWorkingSolution();
      context.executeAndCalculateScore(
          Moves.change(
              context
                  .getSolutionMetaModel()
                  .genuineEntity(TestdataEntity.class)
                  .basicVariable("value", TestdataValue.class),
              solution.getEntity(),
              solution.valueRange().get(index)));
    };
  }

  private static String mixedFingerprint(TestdataMixedSolution solution) {
    return solution.getEntityList().stream()
            .map(
                entity ->
                    entity.getCode()
                        + ":"
                        + code(entity.getBasicValue())
                        + ":"
                        + code(entity.getSecondBasicValue())
                        + ":"
                        + entity.getDeclarativeShadowVariableValue()
                        + ":"
                        + entity.getValueList().stream().map(TestdataObject::getCode).toList())
            .toList()
        + ";"
        + solution.getValueList().stream()
            .map(
                value ->
                    value.getCode()
                        + ":"
                        + code(value.getEntity())
                        + ":"
                        + value.getIndex()
                        + ":"
                        + code(value.getPreviousElement())
                        + ":"
                        + code(value.getNextElement())
                        + ":"
                        + value.getDeclarativeShadowVariableValue()
                        + ":"
                        + value.getCascadingShadowVariableValue())
            .toList()
        + ";"
        + solution.getOtherValueList().stream()
            .map(
                value ->
                    value.getCode()
                        + ":"
                        + value.getEntityList().stream().map(TestdataObject::getCode).toList()
                        + ":"
                        + value.getDeclarativeShadowVariableValue())
            .toList();
  }

  private static String code(TestdataObject value) {
    return value == null ? "null" : value.getCode();
  }

  private static <Solution_> List<Snapshot<Solution_>> solveAndCheck(
      SolverConfig config, Solution_ problem, Function<Solution_, String> fingerprint) {
    var factory = SolverFactory.<Solution_>create(config);
    var solver = (DefaultSolver<Solution_>) factory.buildSolver();
    var snapshots = new ArrayList<Snapshot<Solution_>>();
    solver.addEventListener(
        event -> {
          for (var previous : snapshots) {
            assertThat(fingerprint.apply(previous.solution())).isEqualTo(previous.fingerprint());
          }
          assertThat(event.isNewBestSolutionInitialized()).isTrue();
          var snapshot = event.getNewBestSolution();
          assertThat(snapshot)
              .isNotSameAs(solver.getSolverScope().getScoreDirector().getWorkingSolution());
          snapshots.add(
              new Snapshot<>(snapshot, fingerprint.apply(snapshot), event.getNewBestScore()));
        });

    solver.solve(problem);

    assertThat(solver.getSolverScope().getScoreDirector().getWorkingSolution()).isNull();
    var manager = SolutionManager.<Solution_, SimpleScore>create(factory);
    for (var snapshot : snapshots) {
      assertThat(fingerprint.apply(snapshot.solution())).isEqualTo(snapshot.fingerprint());
      var assessment = manager.assess(snapshot.solution());
      assertThat(assessment.score()).isEqualTo(snapshot.score());
      assertThat(assessment.isInitialized()).isTrue();
      assertThat(fingerprint.apply(snapshot.solution())).isEqualTo(snapshot.fingerprint());
    }
    return snapshots;
  }

  private record Snapshot<Solution_>(Solution_ solution, String fingerprint, Score<?> score) {}

  public static class ShadowedCalculator
      implements EasyScoreCalculator<TestdataShadowedSolution, SimpleScore> {
    @Override
    public SimpleScore calculateScore(TestdataShadowedSolution solution) {
      return SimpleScore.of(
          solution.getValueList().indexOf(solution.getEntityList().getFirst().getValue()));
    }
  }

  public static class ListCalculator
      implements EasyScoreCalculator<TestdataListSolution, SimpleScore> {
    @Override
    public SimpleScore calculateScore(TestdataListSolution solution) {
      return SimpleScore.of(solution.getEntityList().get(1).getValueList().size());
    }
  }

  public static class MixedCalculator
      implements EasyScoreCalculator<TestdataMixedSolution, SimpleScore> {
    @Override
    public SimpleScore calculateScore(TestdataMixedSolution solution) {
      return SimpleScore.of(
          solution.getEntityList().get(1).getValueList().size()
              + solution.getEntityList().stream()
                  .filter(entity -> entity.getBasicValue() == solution.getOtherValueList().get(1))
                  .count());
    }
  }

  public static class CustomClonerCalculator
      implements EasyScoreCalculator<TestdataCorrectlyClonedSolution, SimpleScore> {
    @Override
    public SimpleScore calculateScore(TestdataCorrectlyClonedSolution solution) {
      return SimpleScore.of(solution.valueRange().indexOf(solution.getEntity().getValue()));
    }
  }
}
