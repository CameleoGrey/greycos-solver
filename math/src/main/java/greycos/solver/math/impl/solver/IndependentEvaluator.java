package greycos.solver.math.impl.solver;

import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import greycos.solver.core.api.score.HardSoftScore;
import greycos.solver.math.api.ConstraintReport;
import greycos.solver.math.api.IntVar;
import greycos.solver.math.api.LinearExpression;
import greycos.solver.math.api.MathModel;
import greycos.solver.math.api.OptimizationDirection;

import org.jspecify.annotations.NullMarked;

/** Replays original expression nodes independently of compiler rows and backend arithmetic. */
@NullMarked
public final class IndependentEvaluator {

  private IndependentEvaluator() {}

  public static Evaluation evaluate(
      MathModel model, Map<IntVar, Long> values, HardSoftScore backendScore) {
    Objects.requireNonNull(backendScore, "backendScore");
    var snapshot = new LinkedHashMap<IntVar, Long>();
    if (values.size() != model.variables().size()) {
      throw new IllegalStateException(
          "Assignment contains "
              + values.size()
              + " values for "
              + model.variables().size()
              + " model variables.");
    }
    for (var variable : model.variables()) {
      var value = values.get(variable);
      if (value == null || value < variable.lowerBound() || value > variable.upperBound()) {
        throw new IllegalStateException(
            "Variable ("
                + variable.name()
                + ") has missing or out-of-domain value ("
                + value
                + "); expected ["
                + variable.lowerBound()
                + ", "
                + variable.upperBound()
                + "].");
      }
      var fixed = model.fixedValue(variable);
      if (fixed.isPresent() && fixed.getAsLong() != value) {
        throw new IllegalStateException(
            "Fixed variable ("
                + variable.name()
                + ") changed from "
                + fixed.getAsLong()
                + " to "
                + value
                + ".");
      }
      snapshot.put(variable, value);
    }
    var evaluator = new ExpressionEvaluator(snapshot);
    var reports = new ArrayList<ConstraintReport>(model.constraints().size());
    var totalViolation = BigInteger.ZERO;
    for (var row : model.constraints()) {
      var activity = evaluator.evaluate(row.expression());
      var residual = activity.subtract(BigInteger.valueOf(row.bound()));
      var violation =
          switch (row.relation()) {
            case LE -> residual.max(BigInteger.ZERO);
            case GE -> residual.negate().max(BigInteger.ZERO);
            case EQ -> residual.abs();
          };
      reports.add(
          new ConstraintReport(
              row.name(),
              row.relation(),
              exactLong(activity, row.name()),
              row.bound(),
              exactLong(residual, row.name()),
              exactLong(violation, row.name())));
      totalViolation = totalViolation.add(violation);
    }
    var objective = evaluator.evaluate(model.objective());
    var soft =
        model.objectiveDirection() == OptimizationDirection.MINIMIZE
            ? objective.negate()
            : objective;
    // Negate before converting: a total violation of 2^63 is representable as signed hard MIN.
    var expected =
        HardSoftScore.of(
            exactLong(totalViolation.negate(), "total hard score"),
            exactLong(soft, "objective score"));
    if (!expected.equals(backendScore)) {
      throw new IllegalStateException(
          "Independent mathematical replay score ("
              + expected
              + ") differs from backend score ("
              + backendScore
              + ").");
    }
    return new Evaluation(
        Collections.unmodifiableMap(snapshot),
        exactLong(objective, "objective"),
        List.copyOf(reports));
  }

  private static long exactLong(BigInteger value, String context) {
    try {
      return value.longValueExact();
    } catch (ArithmeticException e) {
      throw new IllegalStateException(
          "Independent replay value ("
              + value
              + ") for ("
              + context
              + ") exceeds the supported long range.",
          e);
    }
  }

  public record Evaluation(
      Map<IntVar, Long> values, long objectiveValue, List<ConstraintReport> reports) {}

  private static final class ExpressionEvaluator {
    private final Map<IntVar, Long> values;
    private final Map<LinearExpression, BigInteger> evaluated = new IdentityHashMap<>();

    private ExpressionEvaluator(Map<IntVar, Long> values) {
      this.values = values;
    }

    private BigInteger evaluate(LinearExpression root) {
      var pending = new ArrayDeque<Visit>();
      pending.push(new Visit(root, false));
      while (!pending.isEmpty()) {
        var visit = pending.pop();
        var expression = visit.expression();
        if (evaluated.containsKey(expression)) {
          continue;
        }
        switch (expression) {
          case IntVar variable -> evaluated.put(variable, BigInteger.valueOf(values.get(variable)));
          case LinearExpression.Constant constant ->
              evaluated.put(expression, BigInteger.valueOf(constant.value()));
          case LinearExpression.Scale scale -> {
            if (visit.expanded()) {
              evaluated.put(
                  expression,
                  evaluated
                      .get(scale.expression())
                      .multiply(BigInteger.valueOf(scale.coefficient())));
            } else {
              pending.push(new Visit(expression, true));
              pending.push(new Visit(scale.expression(), false));
            }
          }
          case LinearExpression.Sum sum -> {
            if (visit.expanded()) {
              var result = BigInteger.ZERO;
              for (var operand : sum.operands()) {
                result = result.add(evaluated.get(operand));
              }
              evaluated.put(expression, result);
            } else {
              pending.push(new Visit(expression, true));
              for (var operand : sum.operands()) {
                pending.push(new Visit(operand, false));
              }
            }
          }
        }
      }
      return evaluated.get(root);
    }

    private record Visit(LinearExpression expression, boolean expanded) {}
  }
}
