package greycos.solver.core.impl.geneticalgorithm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

import greycos.solver.core.api.score.HardSoftScore;
import greycos.solver.core.api.score.Score;
import greycos.solver.core.api.score.SimpleBigDecimalScore;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.api.score.stream.ConstraintProvider;
import greycos.solver.core.api.score.stream.Joiners;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import greycos.solver.core.impl.score.director.stream.BavetConstraintStreamScoreDirectorFactory;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.multientity.TestdataHerdEntity;
import greycos.solver.core.testcotwin.multientity.TestdataLeadEntity;
import greycos.solver.core.testcotwin.multientity.TestdataMultiEntitySolution;
import greycos.solver.core.testcotwin.multivar.TestdataMultiVarEntity;
import greycos.solver.core.testcotwin.multivar.TestdataMultiVarSolution;
import greycos.solver.core.testcotwin.score.TestdataHardSoftScoreSolution;
import greycos.solver.core.testcotwin.score.TestdataSimpleBigDecimalScoreSolution;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(20)
class GeneticAlgorithmWorkspaceModelTest {

  @Test
  void multiLevelAndDecimalFitnessRemainExact() {
    verifyScoreType(
        TestdataHardSoftScoreSolution.buildSolutionDescriptor(),
        TestdataHardSoftScoreSolution.generateSolution(3, 5),
        HardSoftScore.of(2, 7));
    verifyScoreType(
        TestdataSimpleBigDecimalScoreSolution.buildSolutionDescriptor(),
        TestdataSimpleBigDecimalScoreSolution.generateSolution(3, 5),
        SimpleBigDecimalScore.of(new BigDecimal("0.125")));
  }

  private <Solution_, Score_ extends Score<Score_>> void verifyScoreType(
      SolutionDescriptor<Solution_> descriptor, Solution_ solution, Score_ weight) {
    ConstraintProvider provider =
        constraints ->
            new Constraint[] {
              constraints
                  .forEach(TestdataEntity.class)
                  .join(TestdataEntity.class, Joiners.equal(TestdataEntity::getValue))
                  .penalize(weight)
                  .asConstraint("same assignment")
            };
    var factory =
        new BavetConstraintStreamScoreDirectorFactory<Solution_, Score_>(
            descriptor, provider, EnvironmentMode.NO_ASSERT);
    verifyTransitions(factory, solution);
  }

  @Test
  void multipleEntityTypesUseTheirOwnDescriptorsAndCanonicalValues() {
    var solution = TestdataMultiEntitySolution.generateUninitializedSolution(3, 3);
    for (var i = 0; i < 3; i++) {
      solution.getLeadEntityList().get(i).setValue(solution.getValueList().get(i));
      solution.getHerdEntityList().get(i).setLeadEntity(solution.getLeadEntityList().get(i));
    }
    var factory =
        new BavetConstraintStreamScoreDirectorFactory<TestdataMultiEntitySolution, SimpleScore>(
            TestdataMultiEntitySolution.buildSolutionDescriptor(),
            constraints ->
                new Constraint[] {
                  constraints
                      .forEach(TestdataLeadEntity.class)
                      .join(
                          TestdataHerdEntity.class,
                          Joiners.equal(lead -> lead, TestdataHerdEntity::getLeadEntity))
                      .penalize(SimpleScore.ONE)
                      .asConstraint("herd ownership"),
                  constraints
                      .forEach(TestdataLeadEntity.class)
                      .join(TestdataLeadEntity.class, Joiners.equal(TestdataLeadEntity::getValue))
                      .penalize(SimpleScore.ONE)
                      .asConstraint("shared value")
                },
            EnvironmentMode.NO_ASSERT);
    verifyTransitions(factory, solution);
  }

  @Test
  void multipleVariablesPreserveStableSlotsAndRecipientRanges() {
    var solution = TestdataMultiVarSolution.generateSolution(3, 3, 2);
    var factory =
        new BavetConstraintStreamScoreDirectorFactory<TestdataMultiVarSolution, SimpleScore>(
            TestdataMultiVarSolution.buildSolutionDescriptor(),
            constraints ->
                new Constraint[] {
                  constraints
                      .forEachIncludingUnassigned(TestdataMultiVarEntity.class)
                      .filter(
                          entity ->
                              Objects.equals(entity.getPrimaryValue(), entity.getSecondaryValue()))
                      .penalize(SimpleScore.ONE)
                      .asConstraint("matching values")
                },
            EnvironmentMode.NO_ASSERT);
    verifyTransitions(factory, solution);
    try (var director = factory.createScoreDirectorBuilder().build()) {
      director.setWorkingSolution(solution);
      var workspace = new GeneticAlgorithmWorkspace<>(director, director.calculateScore());
      assertThat(workspace.slots()).hasSize(9);
      assertThat(workspace.slots().subList(0, 3))
          .extracting(GeneticAlgorithmSlot::entity)
          .containsOnly(solution.getMultiVarEntityList().getFirst());
      assertThat(workspace.slots().subList(0, 3))
          .extracting(slot -> slot.variableDescriptor().getVariableName())
          .containsExactlyInAnyOrder(
              "primaryValue", "secondaryValue", "tertiaryValueAllowedUnassigned");
      var proposal = workspace.genome().toArray();
      for (var i = 0; i < proposal.length; i++) {
        if (workspace
            .slots()
            .get(i)
            .variableDescriptor()
            .getVariableName()
            .equals("tertiaryValueAllowedUnassigned")) {
          proposal[i] = solution.getValueList().getFirst();
          break;
        }
      }
      assertThat(workspace.transition(new GeneticAlgorithmGenome(proposal)).valid()).isFalse();
      assertThatThrownBy(() -> workspace.slots().clear())
          .isInstanceOf(UnsupportedOperationException.class);
    }
  }

  @Test
  void requiredNullAndEmptyRangesAreRejectedAndSingletonsAreImmovable() {
    var factory =
        new BavetConstraintStreamScoreDirectorFactory<TestdataHardSoftScoreSolution, HardSoftScore>(
            TestdataHardSoftScoreSolution.buildSolutionDescriptor(),
            constraints ->
                new Constraint[] {
                  constraints
                      .forEach(TestdataEntity.class)
                      .penalize(HardSoftScore.ONE_SOFT)
                      .asConstraint("entities")
                },
            EnvironmentMode.NO_ASSERT);
    var solution = TestdataHardSoftScoreSolution.generateSolution(1, 2);
    try (var director = factory.createScoreDirectorBuilder().build()) {
      director.setWorkingSolution(solution);
      var workspace = new GeneticAlgorithmWorkspace<>(director, director.calculateScore());
      assertThat(workspace.slots()).extracting(GeneticAlgorithmSlot::movable).containsOnly(false);
      assertThat(
              workspace.transition(new GeneticAlgorithmGenome(new Object[] {null, null})).valid())
          .isFalse();
    }
    solution.setValueList(List.of());
    try (var director = factory.createScoreDirectorBuilder().build()) {
      // The existing initialization validation rejects the empty required range before GA starts.
      assertThatThrownBy(() -> director.setWorkingSolution(solution))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("outside of the related value range")
          .hasMessageContaining("planning variable (value)")
          .hasMessageContaining("Generated Entity 0");
    }
  }

  private <Solution_, Score_ extends Score<Score_>> void verifyTransitions(
      BavetConstraintStreamScoreDirectorFactory<Solution_, Score_> factory, Solution_ solution) {
    try (var director = factory.createScoreDirectorBuilder().build()) {
      director.setWorkingSolution(solution);
      var workspace = new GeneticAlgorithmWorkspace<>(director, director.calculateScore());
      var initial = workspace.genome();
      var initialScore = workspace.score();
      var session = director.getSession();
      var values = initial.toArray();
      for (var i = 0; i < values.length; i++) {
        var range = workspace.slots().get(i).valueRange();
        values[i] = range.get(range.getSize() - 1);
      }
      assertThat(workspace.transition(new GeneticAlgorithmGenome(values)).valid()).isTrue();
      workspace.scored(director.calculateScore());
      try (var fresh = factory.createScoreDirectorBuilder().build()) {
        fresh.setWorkingSolution(director.cloneWorkingSolution());
        assertThat(workspace.score()).isEqualTo(fresh.calculateScore());
      }
      assertThat(director.getSession()).isSameAs(session);
      workspace.restore(initial, initialScore);
      assertThat(workspace.genome()).isEqualTo(initial);
      assertThat(workspace.score()).isEqualTo(initialScore);
      assertThat(director.getSession()).isSameAs(session);
    }
  }
}
