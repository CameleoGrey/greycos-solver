package greycos.solver.core.impl.neighborhood;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.calculator.EasyScoreCalculator;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.heuristic.selector.move.composite.UnionMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.ChangeMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.SwapMoveSelectorConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.PreviewFeature;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.preview.api.move.Move;
import greycos.solver.core.preview.api.move.builtin.Moves;
import greycos.solver.core.preview.api.neighborhood.Neighborhood;
import greycos.solver.core.preview.api.neighborhood.NeighborhoodBuilder;
import greycos.solver.core.preview.api.neighborhood.NeighborhoodProvider;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testcotwin.TestdataValue;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class NeighborhoodSolverIntegrationTest {

  @ParameterizedTest
  @CsvSource({"NONE,false", "2,false", "NONE,true", "2,true"})
  void finiteDatasetIteratorIsReproducibleWithPhaseThreading(String threads, boolean mixed) {
    var phase =
        new LocalSearchPhaseConfig()
            .withMoveThreadCount(threads)
            .withTerminationConfig(new TerminationConfig().withStepCountLimit(30));
    phase.setNeighborhoodProviderClass(FiniteNeighborhoodProvider.class);
    if (mixed) {
      phase.withMoveSelectorConfig(
          new UnionMoveSelectorConfig()
              .withMoveSelectors(new ChangeMoveSelectorConfig(), new SwapMoveSelectorConfig()));
    }
    var factory = SolverFactory.<TestdataSolution>create(config(phase));
    var first = factory.buildSolver().solve(TestdataSolution.generateSolution(3, 6));
    var second = factory.buildSolver().solve(TestdataSolution.generateSolution(3, 6));
    assertThat(first.getScore()).isEqualTo(new CollisionScoreCalculator().calculateScore(first));
    assertThat(second.getScore()).isEqualTo(first.getScore());
    assertThat(assignment(second)).isEqualTo(assignment(first));
  }

  @ParameterizedTest
  @ValueSource(strings = {"NONE", "2"})
  void emptyCustomIteratorEndsThePhase(String threads) {
    var phase =
        new LocalSearchPhaseConfig()
            .withMoveThreadCount(threads)
            .withTerminationConfig(new TerminationConfig().withStepCountLimit(30));
    phase.setNeighborhoodProviderClass(EmptyNeighborhoodProvider.class);
    var input = TestdataSolution.generateSolution(3, 6);
    var expected = assignment(input);
    var solved = SolverFactory.<TestdataSolution>create(config(phase)).buildSolver().solve(input);
    assertThat(assignment(solved)).isEqualTo(expected);
    assertThat(solved.getScore()).isEqualTo(new CollisionScoreCalculator().calculateScore(solved));
  }

  private static SolverConfig config(LocalSearchPhaseConfig phase) {
    return new SolverConfig()
        .withSolutionClass(TestdataSolution.class)
        .withEntityClasses(TestdataEntity.class)
        .withEasyScoreCalculatorClass(CollisionScoreCalculator.class)
        .withPreviewFeature(PreviewFeature.NEIGHBORHOODS)
        .withEnvironmentMode(EnvironmentMode.PHASE_ASSERT)
        .withRandomSeed(37L)
        .withMoveThreadCount("NONE")
        .withPhases(phase);
  }

  private static List<String> assignment(TestdataSolution solution) {
    return solution.getEntityList().stream().map(e -> e.getValue().getCode()).toList();
  }

  public static final class FiniteNeighborhoodProvider
      implements NeighborhoodProvider<TestdataSolution> {
    @Override
    public Neighborhood defineNeighborhood(NeighborhoodBuilder<TestdataSolution> builder) {
      var variable =
          builder
              .getSolutionMetaModel()
              .genuineEntity(TestdataEntity.class)
              .basicVariable("value", TestdataValue.class);
      return builder
          .add(
              factory -> {
                var entities = factory.forEach(TestdataEntity.class, false).asCachedDataset();
                var values = factory.forEach(TestdataValue.class, false).asCachedDataset();
                return factory.buildMoveStream(
                    (session, random) -> {
                      var result = new ArrayList<Move<TestdataSolution>>();
                      var entityIterator = session.getInstance(entities).exhaustiveIterator(random);
                      while (entityIterator.hasNext()) {
                        var entity = Objects.requireNonNull(entityIterator.next());
                        var valueIterator = session.getInstance(values).exhaustiveIterator(random);
                        while (valueIterator.hasNext()) {
                          var value = Objects.requireNonNull(valueIterator.next());
                          if (session.getSolutionView().getValue(variable, entity) != value) {
                            result.add(Moves.change(variable, entity, value));
                          }
                        }
                      }
                      return result.iterator();
                    });
              })
          .build();
    }
  }

  public static final class EmptyNeighborhoodProvider
      implements NeighborhoodProvider<TestdataSolution> {
    @Override
    public Neighborhood defineNeighborhood(NeighborhoodBuilder<TestdataSolution> builder) {
      return builder
          .add(factory -> factory.buildMoveStream((session, random) -> Collections.emptyIterator()))
          .build();
    }
  }

  public static final class CollisionScoreCalculator
      implements EasyScoreCalculator<TestdataSolution, SimpleScore> {
    @Override
    public SimpleScore calculateScore(TestdataSolution solution) {
      long collisions = 0;
      for (int i = 0; i < solution.getEntityList().size(); i++) {
        for (int j = i + 1; j < solution.getEntityList().size(); j++) {
          if (solution.getEntityList().get(i).getValue()
              == solution.getEntityList().get(j).getValue()) {
            collisions++;
          }
        }
      }
      return SimpleScore.of(-collisions);
    }
  }
}
