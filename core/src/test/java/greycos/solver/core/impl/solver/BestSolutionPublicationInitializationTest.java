package greycos.solver.core.impl.solver;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import greycos.solver.core.api.score.HardSoftScore;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.api.score.stream.ConstraintFactory;
import greycos.solver.core.api.score.stream.ConstraintProvider;
import greycos.solver.core.api.solver.SolutionManager;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.api.solver.event.EventProducerId;
import greycos.solver.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.score.TestdataHardSoftScoreSolution;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(10)
class BestSolutionPublicationInitializationTest {

  @Test
  void incompleteImmediateProblemChangePublicationIsSuppressed() {
    var factory = factory(new ConstructionHeuristicPhaseConfig());
    var solver = (DefaultSolver<TestdataHardSoftScoreSolution>) factory.buildSolver();
    var queued = new AtomicBoolean();
    var publications = new ArrayList<EventProducerId>();
    solver.addEventListener(
        event -> {
          publications.add(event.getProducerId());
          assertThat(event.isNewBestSolutionInitialized()).isTrue();
        });
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void solvingEnded(SolverScope<TestdataHardSoftScoreSolution> scope) {
            if (queued.compareAndSet(false, true)) {
              solver.addProblemChange(
                  (solution, director) ->
                      director.addEntity(
                          new TestdataEntity("new-unassigned"), solution.getEntityList()::add));
            }
          }
        });

    var result = solver.solve(TestdataHardSoftScoreSolution.generateSolution(1, 1));
    assertThat(publications).isNotEmpty().doesNotContain(EventProducerId.problemChange());
    assertThat(result.getEntityList())
        .allSatisfy(entity -> assertThat(entity.getValue()).isNotNull());
    assertThat(result.getScore()).isEqualTo(HardSoftScore.of(0, -2));
  }

  @Test
  void initializedAndFeasiblePredicateRejectsIncompleteConstructionPublication() {
    var factory =
        factory(
            new ConstructionHeuristicPhaseConfig()
                .withTerminationConfig(new TerminationConfig().withStepCountLimit(1)));
    var solver = factory.buildSolver();
    var manager = SolutionManager.<TestdataHardSoftScoreSolution, HardSoftScore>create(factory);
    var publications = new ArrayList<TestdataHardSoftScoreSolution>();
    solver.addEventListener(
        event -> {
          var publication = event.getNewBestSolution();
          publications.add(publication);
          assertThat(event.isEveryProblemChangeProcessed()).isTrue();
          assertThat(event.isNewBestSolutionInitialized()).isFalse();
          assertThat(publication.getEntityList()).hasSize(2);
          assertThat(publication.getEntityList().stream().filter(e -> e.getValue() == null).count())
              .isEqualTo(1);
          assertThat(publication.getScore()).isEqualTo(HardSoftScore.of(0, -1));
          assertThat(publication.getScore().isFeasible()).isTrue();
          // Exact predicate in the current real-time planning guide.
          var documentedPredicate =
              event.isEveryProblemChangeProcessed()
                  && event.isNewBestSolutionInitialized()
                  && publication.getScore().isFeasible();
          assertThat(documentedPredicate).isFalse();
          // A fresh director independently confirms assignment state and score.
          var analysis = manager.analyze(publication);
          assertThat(analysis.isSolutionInitialized()).isFalse();
          assertThat(analysis.score()).isEqualTo(HardSoftScore.of(0, -1));
        });
    var input = TestdataHardSoftScoreSolution.generateSolution(1, 2);
    input.getEntityList().forEach(entity -> entity.setValue(null));
    var result = solver.solve(input);
    assertThat(publications).hasSize(1);
    assertThat(result.getEntityList().stream().filter(e -> e.getValue() == null).count())
        .isEqualTo(1);
    assertThat(result.getScore()).isEqualTo(HardSoftScore.of(0, -1));
  }

  private static SolverFactory<TestdataHardSoftScoreSolution> factory(
      ConstructionHeuristicPhaseConfig phase) {
    return SolverFactory.create(
        new SolverConfig()
            .withSolutionClass(TestdataHardSoftScoreSolution.class)
            .withEntityClasses(TestdataEntity.class)
            .withConstraintProviderClass(AssignedEntityCost.class)
            .withEnvironmentMode(EnvironmentMode.FULL_ASSERT)
            .withMoveThreadCount("NONE")
            .withPhases(phase));
  }

  public static final class AssignedEntityCost implements ConstraintProvider {
    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      return new Constraint[] {
        factory
            .forEach(TestdataEntity.class)
            .penalize(HardSoftScore.ONE_SOFT)
            .asConstraint("Assigned entity cost")
      };
    }
  }
}
