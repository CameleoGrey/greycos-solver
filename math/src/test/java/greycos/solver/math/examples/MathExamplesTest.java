package greycos.solver.math.examples;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;

import greycos.solver.math.api.IntVar;
import greycos.solver.math.api.LinearExpression;
import greycos.solver.math.api.MathModel;
import greycos.solver.math.api.MathResult;
import greycos.solver.math.api.MathSolver;
import greycos.solver.math.api.OptimizationDirection;
import greycos.solver.math.api.Relation;
import greycos.solver.math.config.SolveOptions;

import org.junit.jupiter.api.Test;

class MathExamplesTest {

  @Test
  void knapsackMatchesEveryBinaryAssignmentAndTheReturnedIncumbent() {
    var model = KnapsackExample.model();
    assertThat(model.objectiveDirection()).isEqualTo(OptimizationDirection.MAXIMIZE);
    assertThat(model.constraints()).hasSize(1);
    assertThat(model.constraints().getFirst().relation()).isEqualTo(Relation.LE);
    assertThat(model.constraints().getFirst().bound()).isEqualTo(7);
    long optimum = Long.MIN_VALUE;
    for (int mask = 0; mask < 16; mask++) {
      var values = binaryValues(model, mask);
      long[] x = vector(model, values);
      long weight = 4 * x[0] + 3 * x[1] + 2 * x[2] + x[3];
      long value = 7 * x[0] + 5 * x[1] + 4 * x[2] + x[3];
      assertFormulation(model, values, value, weight);
      if (weight <= 7) {
        optimum = Math.max(optimum, value);
      }
    }
    assertThat(optimum).isEqualTo(12);

    var result = MathSolver.solve(model, SolveOptions.moveEvaluationLimit(5_000).withSeed(7));
    long[] x = vector(model, result.values());
    assertThat(result.status()).isEqualTo(MathResult.Status.FEASIBLE);
    assertThat(result.objectiveValue()).isEqualTo(7 * x[0] + 5 * x[1] + 4 * x[2] + x[3]);
    assertThat(result.objectiveValue()).isBetween(0L, optimum);
    assertReports(model, result, 4 * x[0] + 3 * x[1] + 2 * x[2] + x[3]);
  }

  @Test
  void resourceAllocationMatchesEveryAllowedAssignmentAndKeepsTheReservationFixed() {
    var model = ResourceAllocationExample.model();
    assertThat(model.objectiveDirection()).isEqualTo(OptimizationDirection.MAXIMIZE);
    assertThat(model.constraints()).hasSize(2);
    assertThat(model.constraints())
        .allSatisfy(row -> assertThat(row.relation()).isEqualTo(Relation.LE));
    assertThat(model.constraints().stream().map(row -> row.bound()).toList())
        .containsExactly(13L, 8L);
    long optimum = Long.MIN_VALUE;
    for (long tables = 0; tables <= 3; tables++) {
      for (long chairs = 0; chairs <= 4; chairs++) {
        var values = values(model, tables, chairs, 1);
        long wood = 4 * tables + 2 * chairs + 1;
        long labor = 3 * tables + chairs;
        long value = 9 * tables + 4 * chairs;
        assertFormulation(model, values, value, wood, labor);
        if (wood <= 13 && labor <= 8) {
          optimum = Math.max(optimum, value);
        }
      }
    }
    assertThat(optimum).isEqualTo(26);

    var initial = MathSolver.solve(model, SolveOptions.moveEvaluationLimit(0));
    assertThat(vector(model, initial.values())).containsExactly(0, 0, 1);
    var result = MathSolver.solve(model, SolveOptions.moveEvaluationLimit(5_000).withSeed(7));
    long[] x = vector(model, result.values());
    assertThat(result.status()).isEqualTo(MathResult.Status.FEASIBLE);
    assertThat(x[2]).isEqualTo(1);
    assertThat(result.objectiveValue()).isEqualTo(9 * x[0] + 4 * x[1]);
    assertThat(result.objectiveValue()).isBetween(1L, optimum);
    assertReports(model, result, 4 * x[0] + 2 * x[1] + 1, 3 * x[0] + x[1]);
  }

  @Test
  void assignmentOracleExposesTheScalarMoveEqualityLimit() {
    var model = AssignmentExample.model();
    assertThat(model.objectiveDirection()).isEqualTo(OptimizationDirection.MINIMIZE);
    assertThat(model.constraints()).hasSize(4);
    assertThat(model.constraints())
        .allSatisfy(
            row -> {
              assertThat(row.relation()).isEqualTo(Relation.EQ);
              assertThat(row.bound()).isEqualTo(1);
            });
    long optimum = Long.MAX_VALUE;
    int feasibleCount = 0;
    for (int mask = 0; mask < 16; mask++) {
      var values = binaryValues(model, mask);
      long[] x = vector(model, values);
      long value = 9 * x[0] + x[1] + 2 * x[2] + 8 * x[3];
      assertFormulation(model, values, value, x[0] + x[1], x[2] + x[3], x[0] + x[2], x[1] + x[3]);
      if (x[0] + x[1] == 1 && x[2] + x[3] == 1 && x[0] + x[2] == 1 && x[1] + x[3] == 1) {
        feasibleCount++;
        optimum = Math.min(optimum, value);
      }
    }
    assertThat(feasibleCount).isEqualTo(2);
    assertThat(optimum).isEqualTo(3);

    // Mask 9 denotes the initial diagonal. Every distinct scalar neighbor violates two rows.
    for (int i = 0; i < 4; i++) {
      long[] x = vector(model, binaryValues(model, 9 ^ (1 << i)));
      long violation =
          Math.abs(x[0] + x[1] - 1)
              + Math.abs(x[2] + x[3] - 1)
              + Math.abs(x[0] + x[2] - 1)
              + Math.abs(x[1] + x[3] - 1);
      assertThat(violation).isEqualTo(2);
    }

    // One scalar evaluation cannot reach the other feasible assignment, four binary flips away.
    var result = MathSolver.solve(model, AssignmentExample.options().withMoveEvaluationLimit(1));
    assertThat(result.status()).isEqualTo(MathResult.Status.FEASIBLE);
    assertThat(vector(model, result.values())).containsExactly(1, 0, 0, 1);
    assertThat(result.objectiveValue()).isEqualTo(17).isGreaterThan(optimum);
    assertReports(model, result, 1, 1, 1, 1);
  }

  private static void assertFormulation(
      MathModel model, Map<IntVar, Long> values, long objective, long... activities) {
    assertThat(evaluate(model.objective(), values)).isEqualTo(BigInteger.valueOf(objective));
    assertThat(model.constraints()).hasSize(activities.length);
    for (int i = 0; i < activities.length; i++) {
      assertThat(evaluate(model.constraints().get(i).expression(), values))
          .isEqualTo(BigInteger.valueOf(activities[i]));
    }
  }

  private static void assertReports(MathModel model, MathResult result, long... activities) {
    assertThat(result.constraintReports()).hasSize(activities.length);
    for (int i = 0; i < activities.length; i++) {
      var report = result.constraintReports().get(i);
      var constraint = model.constraints().get(i);
      assertThat(report.name()).isEqualTo(constraint.name());
      assertThat(report.relation()).isEqualTo(constraint.relation());
      assertThat(report.bound()).isEqualTo(constraint.bound());
      assertThat(report.activity()).isEqualTo(activities[i]);
      assertThat(report.residual()).isEqualTo(activities[i] - report.bound());
      assertThat(report.violation()).isZero();
    }
  }

  // The oracle walks only the public original AST. It uses no compiler or scoring helpers.
  private static BigInteger evaluate(LinearExpression expression, Map<IntVar, Long> values) {
    return switch (expression) {
      case IntVar variable -> BigInteger.valueOf(values.get(variable));
      case LinearExpression.Constant constant -> BigInteger.valueOf(constant.value());
      case LinearExpression.Sum sum ->
          sum.operands().stream()
              .map(operand -> evaluate(operand, values))
              .reduce(BigInteger.ZERO, BigInteger::add);
      case LinearExpression.Scale scale ->
          evaluate(scale.expression(), values).multiply(BigInteger.valueOf(scale.coefficient()));
    };
  }

  private static Map<IntVar, Long> binaryValues(MathModel model, int mask) {
    var values = new LinkedHashMap<IntVar, Long>();
    for (int i = 0; i < model.variables().size(); i++) {
      values.put(model.variables().get(i), (long) ((mask >> i) & 1));
    }
    return values;
  }

  private static Map<IntVar, Long> values(MathModel model, long... values) {
    assertThat(model.variables()).hasSize(values.length);
    var result = new LinkedHashMap<IntVar, Long>();
    for (int i = 0; i < values.length; i++) {
      result.put(model.variables().get(i), values[i]);
    }
    return result;
  }

  private static long[] vector(MathModel model, Map<IntVar, Long> values) {
    assertThat(values).containsOnlyKeys(model.variables().toArray(IntVar[]::new));
    return model.variables().stream().mapToLong(values::get).toArray();
  }
}
