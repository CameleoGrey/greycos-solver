package greycos.solver.core.testutil;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.api.score.stream.ConstraintFactory;
import greycos.solver.core.api.score.stream.ConstraintProvider;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.phase.custom.CustomPhaseConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.impl.solver.DefaultSolver;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.testcotwin.TestdataObject;
import greycos.solver.core.testcotwin.multiplelist.TestdataMultipleListEntity;
import greycos.solver.core.testcotwin.multiplelist.TestdataMultipleListFirstValue;
import greycos.solver.core.testcotwin.multiplelist.TestdataMultipleListSecondValue;
import greycos.solver.core.testcotwin.multiplelist.TestdataMultipleListSolution;

/** Independent genuine-state replay shared by worker, migration and partition lifecycle tests. */
public final class MultipleListLifecycleTestUtils {

  private MultipleListLifecycleTestUtils() {}

  public static SolverConfig config() {
    return new SolverConfig()
        .withSolutionClass(TestdataMultipleListSolution.class)
        .withEntityClasses(
            TestdataMultipleListEntity.class,
            TestdataMultipleListFirstValue.class,
            TestdataMultipleListSecondValue.class)
        .withConstraintProviderClass(Constraints.class)
        .withEnvironmentMode(EnvironmentMode.FULL_ASSERT)
        .withRandomSeed(37L)
        .withPhases(new CustomPhaseConfig().withCustomPhaseCommands(context -> {}));
  }

  public static SolverScope<TestdataMultipleListSolution> scope() {
    return ((DefaultSolver<TestdataMultipleListSolution>)
            SolverFactory.<TestdataMultipleListSolution>create(config()).buildSolver())
        .getSolverScope();
  }

  /** Uses genuine lists and basic values; never reads shadow fields to calculate the score. */
  public static SimpleScore recompute(TestdataMultipleListSolution solution) {
    long score = 0;
    for (var owner : solution.getEntityList()) {
      long firstSize = owner.getFirstValueList().size();
      long secondSize = owner.getSecondValueList().size();
      score -= firstSize * firstSize + 2 * secondSize * secondSize;
      score += owner.getBasicValue() == null ? 0 : owner.getBasicValue();
      for (int index = 0; index < firstSize; index++) {
        score -= (long) index * ordinal(owner.getFirstValueList().get(index));
      }
      for (int index = 0; index < secondSize; index++) {
        var value = owner.getSecondValueList().get(index);
        score -= (long) index * ordinal(value);
        score += value.getQuantity() == null ? 0 : value.getQuantity();
      }
    }
    return SimpleScore.of(score);
  }

  public static void assertState(TestdataMultipleListSolution solution) {
    var firstSeen =
        Collections.newSetFromMap(new IdentityHashMap<TestdataMultipleListFirstValue, Boolean>());
    var secondSeen =
        Collections.newSetFromMap(new IdentityHashMap<TestdataMultipleListSecondValue, Boolean>());
    for (var owner : solution.getEntityList()) {
      assertThat(solution.getBasicValueRange()).contains(owner.getBasicValue());
      var first = owner.getFirstValueList();
      for (int index = 0; index < first.size(); index++) {
        var value = first.get(index);
        assertThat(firstSeen.add(value)).isTrue();
        assertThat(value.getEntity()).isSameAs(owner);
        assertThat(value.getIndex()).isEqualTo(index);
        assertThat(value.getPrevious()).isSameAs(index == 0 ? null : first.get(index - 1));
        assertThat(value.getNext())
            .isSameAs(index + 1 == first.size() ? null : first.get(index + 1));
      }
      var second = owner.getSecondValueList();
      for (int index = 0; index < second.size(); index++) {
        var value = second.get(index);
        assertThat(secondSeen.add(value)).isTrue();
        assertThat(value.getEntity()).isSameAs(owner);
        assertThat(value.getIndex()).isEqualTo(index);
        assertThat(value.getPrevious()).isSameAs(index == 0 ? null : second.get(index - 1));
        assertThat(value.getNext())
            .isSameAs(index + 1 == second.size() ? null : second.get(index + 1));
        assertThat(solution.getBasicValueRange()).contains(value.getQuantity());
      }
    }
    assertThat(firstSeen).containsExactlyInAnyOrderElementsOf(solution.getFirstValueList());
    assertThat(secondSeen).containsExactlyInAnyOrderElementsOf(solution.getSecondValueList());
  }

  public static String snapshot(TestdataMultipleListSolution solution) {
    var state = new ArrayList<String>();
    for (var entity : solution.getEntityList()) {
      state.add(
          entity.getCode()
              + ":"
              + entity.getBasicValue()
              + ":"
              + entity.getFirstValueList().stream().map(TestdataObject::getCode).toList()
              + ":"
              + entity.getSecondValueList().stream()
                  .map(value -> value.getCode() + "=" + value.getQuantity())
                  .toList());
    }
    return state.toString();
  }

  /** Changes both descriptors and both types of basic variable without relying on shadow state. */
  public static void rearrange(TestdataMultipleListSolution solution, int step) {
    var owners = solution.getEntityList();
    for (var owner : owners) {
      owner.getFirstValueList().clear();
      owner.getSecondValueList().clear();
      owner.setBasicValue((step + ordinal(owner)) % solution.getBasicValueRange().size());
    }
    for (int index = solution.getFirstValueList().size() - 1; index >= 0; index--) {
      owners
          .get((index + step) % owners.size())
          .getFirstValueList()
          .add(solution.getFirstValueList().get(index));
    }
    for (int index = 0; index < solution.getSecondValueList().size(); index++) {
      var value = solution.getSecondValueList().get(index);
      value.setQuantity((step + index) % solution.getBasicValueRange().size());
      owners.get((index + step + 1) % owners.size()).getSecondValueList().add(value);
    }
  }

  private static int ordinal(TestdataObject value) {
    return Integer.parseInt(value.getCode().substring(value.getCode().lastIndexOf('-') + 1)) + 1;
  }

  public static final class Constraints implements ConstraintProvider {
    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      return new Constraint[] {
        factory
            .forEachIncludingUnassigned(TestdataMultipleListEntity.class)
            .penalize(
                SimpleScore.ONE,
                entity ->
                    (long) entity.getFirstValueList().size() * entity.getFirstValueList().size()
                        + 2L
                            * entity.getSecondValueList().size()
                            * entity.getSecondValueList().size())
            .asConstraint("Both list loads"),
        factory
            .forEach(TestdataMultipleListEntity.class)
            .reward(SimpleScore.ONE, TestdataMultipleListEntity::getBasicValue)
            .asConstraint("Owner basic value"),
        factory
            .forEach(TestdataMultipleListFirstValue.class)
            .penalize(SimpleScore.ONE, value -> (long) value.getIndex() * ordinal(value))
            .asConstraint("First list order"),
        factory
            .forEach(TestdataMultipleListSecondValue.class)
            .penalize(SimpleScore.ONE, value -> (long) value.getIndex() * ordinal(value))
            .asConstraint("Second list order"),
        factory
            .forEach(TestdataMultipleListSecondValue.class)
            .reward(SimpleScore.ONE, TestdataMultipleListSecondValue::getQuantity)
            .asConstraint("Second list quantity")
      };
    }
  }
}
