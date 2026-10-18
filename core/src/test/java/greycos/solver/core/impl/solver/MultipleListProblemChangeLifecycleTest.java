package greycos.solver.core.impl.solver;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.localsearch.scope.LocalSearchStepScope;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.testcotwin.TestdataObject;
import greycos.solver.core.testcotwin.multiplelist.TestdataMultipleListFirstValue;
import greycos.solver.core.testcotwin.multiplelist.TestdataMultipleListSecondValue;
import greycos.solver.core.testcotwin.multiplelist.TestdataMultipleListSolution;
import greycos.solver.core.testutil.MultipleListLifecycleTestUtils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(30)
class MultipleListProblemChangeLifecycleTest {

  @Test
  void validProblemChangeRefreshesBothFamiliesAndResumesConstructionAndSearch() {
    var problem = TestdataMultipleListSolution.generateInitializedSolution(6, 8, 2);
    var originalOwner = problem.getEntityList().getFirst();
    originalOwner.setFirstPinnedIndex(2);
    originalOwner.setSecondPinnedIndex(3);
    var originalRemovedFirst = problem.getFirstValueList().get(4);
    var originalRemovedSecond = problem.getSecondValueList().get(6);
    var originalReleasedFirst = problem.getFirstValueList().get(2);
    var originalReleasedSecond = problem.getSecondValueList().get(4);

    var config =
        MultipleListLifecycleTestUtils.config()
            .withTerminationConfig(new TerminationConfig().withStepCountLimit(20));
    // Exercise automatic construction and local search again after the problem change.
    config.setPhaseConfigList(null);
    var solver =
        (DefaultSolver<TestdataMultipleListSolution>)
            SolverFactory.<TestdataMultipleListSolution>create(config).buildSolver();
    var runCount = new AtomicInteger();
    var changeCount = new AtomicInteger();
    var resumedLocalSearchSteps = new AtomicInteger();
    var changedInitializedPublications = new AtomicInteger();
    solver.addEventListener(
        event -> {
          var published = event.getNewBestSolution();
          assertThat(event.isNewBestSolutionInitialized()).isEqualTo(isInitialized(published));
          if (!event.isNewBestSolutionInitialized()) {
            return; // A completed construction phase may leave the next list family unassigned.
          }
          assertCompleteStateAndScore(published);
          if (published.getFirstValueList().stream()
              .anyMatch(value -> value.getCode().equals("first-100"))) {
            assertChangedProblem(published);
            changedInitializedPublications.incrementAndGet();
          }
        });
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void solvingStarted(SolverScope<TestdataMultipleListSolution> scope) {
            runCount.incrementAndGet();
          }

          @Override
          public void stepEnded(AbstractStepScope<TestdataMultipleListSolution> stepScope) {
            if (runCount.get() == 2 && stepScope instanceof LocalSearchStepScope<?>) {
              resumedLocalSearchSteps.incrementAndGet();
            }
          }

          @Override
          public void solvingEnded(SolverScope<TestdataMultipleListSolution> scope) {
            if (runCount.get() != 1) {
              return;
            }
            solver.addProblemChange(
                (solution, director) -> {
                  changeCount.incrementAndGet();
                  var removedFirst = director.lookUpWorkingObject(originalRemovedFirst);
                  var removedSecond = director.lookUpWorkingObject(originalRemovedSecond);
                  var owner = director.lookUpWorkingObject(originalOwner);
                  director.changeProblemProperty(
                      owner,
                      entity -> {
                        entity.setFirstPinnedIndex(1);
                        entity.setSecondPinnedIndex(2);
                      });

                  // These values started outside both pinned prefixes and remain movable.
                  // Locate their current owners from genuine state, independently of shadows.
                  var firstOwner =
                      solution.getEntityList().stream()
                          .filter(entity -> entity.getFirstValueList().contains(removedFirst))
                          .findFirst()
                          .orElseThrow();
                  var secondOwner =
                      solution.getEntityList().stream()
                          .filter(entity -> entity.getSecondValueList().contains(removedSecond))
                          .findFirst()
                          .orElseThrow();
                  director.changeProblemProperty(
                      firstOwner, entity -> entity.getFirstValueList().remove(removedFirst));
                  director.changeProblemProperty(
                      secondOwner, entity -> entity.getSecondValueList().remove(removedSecond));
                  director.updateShadowVariables();
                  assertUnassigned(removedFirst);
                  assertUnassigned(removedSecond);

                  director.removeEntity(removedFirst, solution.getFirstValueList()::remove);
                  director.removeEntity(removedSecond, solution.getSecondValueList()::remove);
                  var addedFirst = new TestdataMultipleListFirstValue("first-100");
                  var addedSecond = new TestdataMultipleListSecondValue("second-100");
                  director.addEntity(addedFirst, solution.getFirstValueList()::add);
                  director.addEntity(addedSecond, solution.getSecondValueList()::add);
                  director.updateShadowVariables();

                  assertUnassigned(addedFirst);
                  assertUnassigned(addedSecond);
                  assertThat(addedSecond.getQuantity()).isNull();
                  var scoreDirector = scope.getScoreDirector();
                  var descriptor = scoreDirector.getSolutionDescriptor();
                  var firstDescriptor =
                      descriptor.resolveListVariableDescriptor(null, "firstValueList");
                  var secondDescriptor =
                      descriptor.resolveListVariableDescriptor(null, "secondValueList");
                  var firstState = scoreDirector.getListVariableState(firstDescriptor);
                  var secondState = scoreDirector.getListVariableState(secondDescriptor);
                  assertThat(firstState.getUnassignedCount()).isOne();
                  assertThat(secondState.getUnassignedCount()).isOne();
                  assertThat(firstDescriptor.getFirstUnpinnedIndex(owner)).isOne();
                  assertThat(secondDescriptor.getFirstUnpinnedIndex(owner)).isEqualTo(2);
                  assertThat(
                          firstState.isPinned(director.lookUpWorkingObject(originalReleasedFirst)))
                      .isFalse();
                  assertThat(
                          secondState.isPinned(
                              director.lookUpWorkingObject(originalReleasedSecond)))
                      .isFalse();
                });
          }
        });

    var result = solver.solve(problem);

    assertThat(runCount.get()).isEqualTo(2);
    assertThat(changeCount.get()).isOne();
    assertThat(resumedLocalSearchSteps.get()).isPositive();
    assertThat(changedInitializedPublications.get()).isPositive();
    assertCompleteStateAndScore(result);
    assertChangedProblem(result);
    assertThat(solver.isEveryProblemChangeProcessed()).isTrue();
    assertThat(solver.isSolving()).isFalse();
  }

  private static void assertCompleteStateAndScore(TestdataMultipleListSolution solution) {
    MultipleListLifecycleTestUtils.assertState(solution);
    assertThat(solution.getScore()).isEqualTo(MultipleListLifecycleTestUtils.recompute(solution));
  }

  private static boolean isInitialized(TestdataMultipleListSolution solution) {
    if (solution.getEntityList().stream().anyMatch(owner -> owner.getBasicValue() == null)
        || solution.getSecondValueList().stream().anyMatch(value -> value.getQuantity() == null)) {
      return false;
    }
    var assignedFirst =
        Collections.newSetFromMap(new IdentityHashMap<TestdataMultipleListFirstValue, Boolean>());
    var assignedSecond =
        Collections.newSetFromMap(new IdentityHashMap<TestdataMultipleListSecondValue, Boolean>());
    for (var owner : solution.getEntityList()) {
      assignedFirst.addAll(owner.getFirstValueList());
      assignedSecond.addAll(owner.getSecondValueList());
    }
    return assignedFirst.containsAll(solution.getFirstValueList())
        && assignedSecond.containsAll(solution.getSecondValueList());
  }

  private static void assertChangedProblem(TestdataMultipleListSolution solution) {
    assertThat(solution.getFirstValueList())
        .hasSize(6)
        .extracting(TestdataObject::getCode)
        .containsOnly("first-0", "first-1", "first-2", "first-3", "first-5", "first-100");
    assertThat(solution.getSecondValueList())
        .hasSize(8)
        .extracting(TestdataObject::getCode)
        .containsOnly(
            "second-0",
            "second-1",
            "second-2",
            "second-3",
            "second-4",
            "second-5",
            "second-7",
            "second-100");
    var owner = solution.getEntityList().getFirst();
    assertThat(owner.getFirstPinnedIndex()).isOne();
    assertThat(owner.getSecondPinnedIndex()).isEqualTo(2);
    assertThat(owner.getFirstValueList().subList(0, 1))
        .extracting(TestdataObject::getCode)
        .containsExactly("first-0");
    assertThat(owner.getSecondValueList().subList(0, 2))
        .extracting(TestdataObject::getCode)
        .containsExactly("second-0", "second-2");
  }

  private static void assertUnassigned(TestdataMultipleListFirstValue value) {
    assertThat(
            new Object[] {
              value.getEntity(), value.getIndex(), value.getPrevious(), value.getNext()
            })
        .containsOnlyNulls();
  }

  private static void assertUnassigned(TestdataMultipleListSecondValue value) {
    assertThat(
            new Object[] {
              value.getEntity(), value.getIndex(), value.getPrevious(), value.getNext()
            })
        .containsOnlyNulls();
  }
}
