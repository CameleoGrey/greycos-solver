package greycos.solver.math.benchmark;

import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;

import greycos.solver.core.api.score.HardSoftScore;
import greycos.solver.math.api.IntVar;
import greycos.solver.math.api.LinearExpression;
import greycos.solver.math.api.MathModel;
import greycos.solver.math.api.OptimizationDirection;

/** Independent original-expression arithmetic and process counters for opt-in qualification. */
final class MathQualificationSupport {

  private MathQualificationSupport() {}

  static BigInteger evaluate(LinearExpression expression, long[] values) {
    return switch (expression) {
      case IntVar variable -> BigInteger.valueOf(values[variable.id()]);
      case LinearExpression.Constant constant -> BigInteger.valueOf(constant.value());
      case LinearExpression.Scale scale ->
          evaluate(scale.expression(), values).multiply(BigInteger.valueOf(scale.coefficient()));
      case LinearExpression.Sum sum -> {
        var value = BigInteger.ZERO;
        for (var operand : sum.operands()) {
          value = value.add(evaluate(operand, values));
        }
        yield value;
      }
    };
  }

  static HardSoftScore replay(MathModel model, long[] values) {
    if (values.length != model.variables().size()) {
      throw new IllegalStateException("Incomplete assignment: " + values.length);
    }
    for (var variable : model.variables()) {
      long value = values[variable.id()];
      if (value < variable.lowerBound()
          || value > variable.upperBound()
          || (model.fixedValue(variable).isPresent()
              && value != model.fixedValue(variable).getAsLong())) {
        throw new IllegalStateException("Invalid assignment for " + variable.name() + ": " + value);
      }
    }
    var hard = BigInteger.ZERO;
    for (var constraint : model.constraints()) {
      var residual =
          evaluate(constraint.expression(), values)
              .subtract(BigInteger.valueOf(constraint.bound()));
      var violation =
          switch (constraint.relation()) {
            case LE -> residual.max(BigInteger.ZERO);
            case GE -> residual.negate().max(BigInteger.ZERO);
            case EQ -> residual.abs();
          };
      hard = hard.subtract(violation);
    }
    var objective = evaluate(model.objective(), values);
    var soft =
        model.objectiveDirection() == OptimizationDirection.MINIMIZE
            ? objective.negate()
            : objective;
    return HardSoftScore.of(hard.longValueExact(), soft.longValueExact());
  }

  static Map<String, String> arguments(String[] args, Map<String, String> defaults) {
    var options = new LinkedHashMap<>(defaults);
    for (String argument : args) {
      int separator = argument.indexOf('=');
      if (!argument.startsWith("--")
          || separator < 3
          || !options.containsKey(argument.substring(2, separator))) {
        throw new IllegalArgumentException(
            "Expected --name=value, known options " + options.keySet() + "; received " + argument);
      }
      options.put(argument.substring(2, separator), argument.substring(separator + 1));
    }
    return options;
  }

  static long gcCount() {
    return ManagementFactory.getGarbageCollectorMXBeans().stream()
        .mapToLong(bean -> Math.max(0L, bean.getCollectionCount()))
        .sum();
  }

  static long gcMillis() {
    return ManagementFactory.getGarbageCollectorMXBeans().stream()
        .mapToLong(bean -> Math.max(0L, bean.getCollectionTime()))
        .sum();
  }

  record Snapshot(long nanos, long allocated, long collections, long gcMillis) {
    static Snapshot capture(AllocationMeter allocation) {
      return new Snapshot(
          System.nanoTime(), allocation.bytes(), gcCount(), MathQualificationSupport.gcMillis());
    }

    long allocatedSince(Snapshot before) {
      return allocated < 0 || before.allocated < 0 ? -1 : allocated - before.allocated;
    }
  }

  static final class AllocationMeter {
    private final Object bean;
    private final Method allocatedBytes;

    AllocationMeter() {
      Object candidate = null;
      Method method = null;
      try {
        var type = Class.forName("com.sun.management.ThreadMXBean");
        candidate = ManagementFactory.getThreadMXBean();
        if (type.isInstance(candidate)
            && (boolean) type.getMethod("isThreadAllocatedMemorySupported").invoke(candidate)) {
          type.getMethod("setThreadAllocatedMemoryEnabled", boolean.class).invoke(candidate, true);
          method = type.getMethod("getThreadAllocatedBytes", long.class);
        }
      } catch (ReflectiveOperationException | SecurityException unavailable) {
        candidate = null;
      }
      bean = candidate;
      allocatedBytes = method;
    }

    long bytes() {
      if (allocatedBytes == null) {
        return -1L;
      }
      try {
        return (long) allocatedBytes.invoke(bean, Thread.currentThread().threadId());
      } catch (ReflectiveOperationException unavailable) {
        return -1L;
      }
    }
  }
}
