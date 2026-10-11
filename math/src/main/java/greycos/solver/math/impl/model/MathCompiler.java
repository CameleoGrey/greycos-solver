package greycos.solver.math.impl.model;

import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import greycos.solver.math.api.IntVar;
import greycos.solver.math.api.LinearExpression;
import greycos.solver.math.api.MathModel;
import greycos.solver.math.api.OptimizationDirection;

import org.jspecify.annotations.NullMarked;

/** Exact normalization and admission checks for the checked-long incremental backend. */
@NullMarked
public final class MathCompiler {

  private static final BigInteger MIN = BigInteger.valueOf(Long.MIN_VALUE);
  private static final BigInteger MAX = BigInteger.valueOf(Long.MAX_VALUE);
  private static final BigInteger MAX_TOTAL_VIOLATION = MIN.negate();

  private MathCompiler() {}

  public static CompiledModel compile(MathModel model) {
    if (model == null) {
      throw new IllegalArgumentException("The model to compile must not be null.");
    }
    validateDomains(model);
    var rows = new ArrayList<Row>(model.constraints().size() + 1);
    var terms = new ArrayList<Term>();
    BigInteger maximumTotalViolation = BigInteger.ZERO;
    for (var constraint : model.constraints()) {
      var kind =
          switch (constraint.relation()) {
            case LE -> RowKind.LE;
            case GE -> RowKind.GE;
            case EQ -> RowKind.EQ;
          };
      BigInteger maximumViolation =
          compileRow(
              model,
              constraint.name(),
              constraint.expression(),
              kind,
              constraint.bound(),
              rows,
              terms);
      maximumTotalViolation = maximumTotalViolation.add(maximumViolation);
      if (maximumTotalViolation.compareTo(MAX_TOTAL_VIOLATION) > 0) {
        throw invalid(
            constraint.name(),
            "the sum of maximum hard violations ("
                + maximumTotalViolation
                + ") exceeds 2^63, so the total hard score may overflow");
      }
    }
    compileRow(
        model,
        "$objective",
        model.objective(),
        model.objectiveDirection() == OptimizationDirection.MINIMIZE
            ? RowKind.MINIMIZE
            : RowKind.MAXIMIZE,
        0,
        rows,
        terms);
    return new CompiledModel(model, rows, terms);
  }

  private static void validateDomains(MathModel model) {
    for (var variable : model.variables()) {
      BigInteger cardinality =
          BigInteger.valueOf(variable.upperBound())
              .subtract(BigInteger.valueOf(variable.lowerBound()))
              .add(BigInteger.ONE);
      if (cardinality.signum() <= 0 || cardinality.compareTo(MAX) > 0) {
        throw new IllegalArgumentException(
            "The variable ("
                + variable.name()
                + ") has inclusive domain ["
                + variable.lowerBound()
                + ", "
                + variable.upperBound()
                + "] with unsupported cardinality ("
                + cardinality
                + "); cardinality must be between 1 and Long.MAX_VALUE.");
      }
    }
  }

  private static BigInteger compileRow(
      MathModel model,
      String name,
      LinearExpression expression,
      RowKind kind,
      long bound,
      List<Row> rows,
      List<Term> terms) {
    var normalized = normalize(model, name, expression);
    long constant = checkedLong(normalized.constant(), name, "normalized constant");
    var row = new Row(rows.size(), name, kind, constant, bound);
    BigInteger minimumSum = BigInteger.ZERO;
    BigInteger maximumSum = BigInteger.ZERO;
    BigInteger minimumSubsetSum = BigInteger.ZERO;
    BigInteger maximumSubsetSum = BigInteger.ZERO;
    for (var entry : normalized.coefficients().entrySet()) {
      BigInteger coefficient = entry.getValue();
      if (coefficient.signum() == 0) {
        continue;
      }
      var variable = model.variables().get(entry.getKey());
      String context = "variable (" + variable.name() + ") ";
      long longCoefficient = checkedLong(coefficient, name, context + "normalized coefficient");
      var fixedValue = model.fixedValue(variable);
      long lower = fixedValue.orElse(variable.lowerBound());
      long upper = fixedValue.orElse(variable.upperBound());
      BigInteger firstProduct = coefficient.multiply(BigInteger.valueOf(lower));
      BigInteger secondProduct = coefficient.multiply(BigInteger.valueOf(upper));
      BigInteger minimumProduct = firstProduct.min(secondProduct);
      BigInteger maximumProduct = firstProduct.max(secondProduct);
      checkedLong(minimumProduct, name, context + "minimum product");
      checkedLong(maximumProduct, name, context + "maximum product");
      BigInteger replacementWidth = maximumProduct.subtract(minimumProduct);
      if (replacementWidth.compareTo(MAX) > 0) {
        throw invalid(
            name,
            context
                + "replacement delta may reach ("
                + replacementWidth
                + "), exceeding Long.MAX_VALUE");
      }
      minimumSum = minimumSum.add(minimumProduct);
      maximumSum = maximumSum.add(maximumProduct);
      minimumSubsetSum = minimumSubsetSum.add(minimumProduct.min(BigInteger.ZERO));
      maximumSubsetSum = maximumSubsetSum.add(maximumProduct.max(BigInteger.ZERO));
      checkedLong(minimumSubsetSum, name, context + "minimum partial aggregate");
      checkedLong(maximumSubsetSum, name, context + "maximum partial aggregate");
      terms.add(new Term(row, variable.id(), longCoefficient));
    }
    BigInteger minimumActivity = minimumSum.add(normalized.constant());
    BigInteger maximumActivity = maximumSum.add(normalized.constant());
    checkedLong(minimumActivity, name, "minimum activity");
    checkedLong(maximumActivity, name, "maximum activity");
    rows.add(row);
    if (row.isObjective()) {
      if (kind == RowKind.MINIMIZE) {
        checkedLong(minimumActivity.negate(), name, "negated minimum objective");
        checkedLong(maximumActivity.negate(), name, "negated maximum objective");
      }
      return BigInteger.ZERO;
    }
    BigInteger minimumResidual = minimumActivity.subtract(BigInteger.valueOf(bound));
    BigInteger maximumResidual = maximumActivity.subtract(BigInteger.valueOf(bound));
    checkedLong(minimumResidual, name, "minimum residual");
    checkedLong(maximumResidual, name, "maximum residual");
    BigInteger maximumViolation =
        switch (kind) {
          case LE -> maximumResidual.max(BigInteger.ZERO);
          case GE -> minimumResidual.negate().max(BigInteger.ZERO);
          case EQ -> minimumResidual.abs().max(maximumResidual.abs());
          default ->
              throw new IllegalStateException("Unexpected constraint row kind (" + kind + ").");
        };
    checkedLong(maximumViolation, name, "maximum violation");
    return maximumViolation;
  }

  private static Normalized normalize(
      MathModel model, String rowName, LinearExpression expression) {
    // Identity visitation avoids recursive record equality and repeated expansion of shared
    // subgraphs.
    // Ownership is checked during this complete traversal, including children of zero multipliers.
    Set<LinearExpression> visited = Collections.newSetFromMap(new IdentityHashMap<>());
    var postorder = new ArrayList<LinearExpression>();
    var stack = new ArrayDeque<Visit>();
    stack.push(new Visit(expression, false));
    while (!stack.isEmpty()) {
      var visit = stack.pop();
      var node = visit.expression();
      if (visit.expanded()) {
        postorder.add(node);
      } else if (visited.add(node)) {
        stack.push(new Visit(node, true));
        switch (node) {
          case IntVar variable -> {
            if (variable.id() < 0
                || variable.id() >= model.variables().size()
                || model.variables().get(variable.id()) != variable) {
              throw invalid(rowName, "variable (" + variable.name() + ") belongs to another model");
            }
          }
          case LinearExpression.Constant ignored -> {}
          case LinearExpression.Scale scale -> stack.push(new Visit(scale.expression(), false));
          case LinearExpression.Sum sum -> {
            for (int i = sum.operands().size() - 1; i >= 0; i--) {
              stack.push(new Visit(sum.operands().get(i), false));
            }
          }
        }
      }
    }
    // Propagate exact scalar weights once per graph edge in reverse topological order.
    Map<LinearExpression, BigInteger> factors = new IdentityHashMap<>();
    factors.put(expression, BigInteger.ONE);
    Map<Integer, BigInteger> coefficients = new TreeMap<>();
    BigInteger constant = BigInteger.ZERO;
    for (int i = postorder.size() - 1; i >= 0; i--) {
      var node = postorder.get(i);
      BigInteger factor = factors.remove(node);
      if (factor == null || factor.signum() == 0) {
        continue;
      }
      switch (node) {
        case IntVar variable -> coefficients.merge(variable.id(), factor, BigInteger::add);
        case LinearExpression.Constant literal ->
            constant = constant.add(factor.multiply(BigInteger.valueOf(literal.value())));
        case LinearExpression.Scale scale ->
            factors.merge(
                scale.expression(),
                factor.multiply(BigInteger.valueOf(scale.coefficient())),
                BigInteger::add);
        case LinearExpression.Sum sum -> {
          for (var operand : sum.operands()) {
            factors.merge(operand, factor, BigInteger::add);
          }
        }
      }
    }
    return new Normalized(constant, coefficients);
  }

  private static long checkedLong(BigInteger value, String rowName, String quantity) {
    if (value.compareTo(MIN) < 0 || value.compareTo(MAX) > 0) {
      throw invalid(rowName, quantity + " (" + value + ") is outside the signed long range");
    }
    return value.longValue();
  }

  private static IllegalArgumentException invalid(String rowName, String detail) {
    return new IllegalArgumentException(
        "The row (" + rowName + ") is unsupported: " + detail + ".");
  }

  private record Visit(LinearExpression expression, boolean expanded) {}

  private record Normalized(BigInteger constant, Map<Integer, BigInteger> coefficients) {}
}
