package greycos.solver.math.api;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;

import greycos.solver.math.impl.model.MathCompiler;

import org.jspecify.annotations.NullMarked;

/**
 * An immutable model of bounded integer variables, named hard linear constraints, and one
 * objective. Construct a model with {@link #builder()}; every successful build validates exact
 * arithmetic for the incremental backend. A contradictory but numerically supported constraint
 * system is valid.
 */
@NullMarked
public final class MathModel {

  private final List<IntVar> variables;
  private final List<NamedConstraint> constraints;
  private final LinearExpression objective;
  private final OptimizationDirection objectiveDirection;
  private final Map<IntVar, Long> initialValues;
  private final Map<IntVar, Long> fixedValues;

  private MathModel(Builder builder) {
    variables = List.copyOf(builder.variables);
    constraints = List.copyOf(builder.constraints);
    objective = builder.objective;
    objectiveDirection = builder.objectiveDirection;
    initialValues = Map.copyOf(builder.initialValues);
    fixedValues = Map.copyOf(builder.fixedValues);
  }

  /** Creates a new independent builder. */
  public static Builder builder() {
    return new Builder();
  }

  /** Returns immutable variable handles in declaration order. */
  public List<IntVar> variables() {
    return variables;
  }

  /** Returns immutable hard constraints in declaration order. */
  public List<NamedConstraint> constraints() {
    return constraints;
  }

  /** Returns the original objective expression; an omitted objective is constant zero. */
  public LinearExpression objective() {
    return objective;
  }

  /** Returns the objective direction; an omitted objective is minimized. */
  public OptimizationDirection objectiveDirection() {
    return objectiveDirection;
  }

  /** Returns an optional starting value. Initial values do not pin variables. */
  public OptionalLong initialValue(IntVar variable) {
    requireMember(variable);
    Long value = initialValues.get(variable);
    return value == null ? OptionalLong.empty() : OptionalLong.of(value);
  }

  /** Returns an optional fixed value, which is preserved throughout solving. */
  public OptionalLong fixedValue(IntVar variable) {
    requireMember(variable);
    Long value = fixedValues.get(variable);
    return value == null ? OptionalLong.empty() : OptionalLong.of(value);
  }

  private void requireMember(IntVar variable) {
    if (variable == null
        || variable.id() < 0
        || variable.id() >= variables.size()
        || variables.get(variable.id()) != variable) {
      throw new IllegalArgumentException(
          "The variable (" + variable + ") does not belong to this model.");
    }
  }

  /**
   * A mutable builder that becomes closed after a successful {@link #build()}. Variable and
   * constraint names are unique within their respective namespaces. All domains are inclusive.
   */
  public static final class Builder {

    private final Object owner = new Object();
    private final List<IntVar> variables = new ArrayList<>();
    private final List<NamedConstraint> constraints = new ArrayList<>();
    private final Set<String> variableNames = new HashSet<>();
    private final Set<String> constraintNames = new HashSet<>();
    private final Map<IntVar, Long> initialValues = new LinkedHashMap<>();
    private final Map<IntVar, Long> fixedValues = new LinkedHashMap<>();
    private LinearExpression objective = LinearExpression.constant(0);
    private OptimizationDirection objectiveDirection = OptimizationDirection.MINIMIZE;
    private boolean objectiveSpecified;
    private boolean built;

    private Builder() {}

    /** Declares a variable with inclusive integral bounds. */
    public IntVar intVar(String name, long lowerBound, long upperBound) {
      requireOpen();
      requireName(name, "variable");
      if (lowerBound > upperBound) {
        throw new IllegalArgumentException(
            "The variable ("
                + name
                + ") has invalid inclusive bounds ["
                + lowerBound
                + ", "
                + upperBound
                + "].");
      }
      if (!variableNames.add(name)) {
        throw new IllegalArgumentException("The variable name (" + name + ") is duplicated.");
      }
      var variable = new IntVar(owner, variables.size(), name, lowerBound, upperBound);
      variables.add(variable);
      return variable;
    }

    /** Declares a binary variable with domain zero and one. */
    public BinaryVar binaryVar(String name) {
      requireOpen();
      requireName(name, "variable");
      if (!variableNames.add(name)) {
        throw new IllegalArgumentException("The variable name (" + name + ") is duplicated.");
      }
      var variable = new BinaryVar(owner, variables.size(), name);
      variables.add(variable);
      return variable;
    }

    /**
     * Sets or replaces the in-domain starting value; it must agree with any fixed value at build.
     */
    public Builder initialValue(IntVar variable, long value) {
      requireOpen();
      requireValue(variable, value, "initial");
      initialValues.put(variable, value);
      return this;
    }

    /**
     * Sets or replaces the in-domain fixed value; it must agree with any starting value at build.
     */
    public Builder fix(IntVar variable, long value) {
      requireOpen();
      requireValue(variable, value, "fixed");
      fixedValues.put(variable, value);
      return this;
    }

    /** Adds a named hard constraint, preserving its original expression. */
    public Builder addConstraint(String name, LinearConstraint constraint) {
      requireOpen();
      requireName(name, "constraint");
      if ("$objective".equals(name)) {
        throw new IllegalArgumentException(
            "The constraint name ($objective) is reserved for the objective.");
      }
      if (constraint == null) {
        throw new IllegalArgumentException("The constraint (" + name + ") must not be null.");
      }
      if (!constraintNames.add(name)) {
        throw new IllegalArgumentException("The constraint name (" + name + ") is duplicated.");
      }
      constraints.add(
          new NamedConstraint(
              name, constraint.expression(), constraint.relation(), constraint.bound()));
      return this;
    }

    /** Sets the single objective to minimize; specifying a second objective is unsupported. */
    public Builder minimize(LinearExpression expression) {
      return objective(expression, OptimizationDirection.MINIMIZE);
    }

    /** Sets the single objective to maximize; specifying a second objective is unsupported. */
    public Builder maximize(LinearExpression expression) {
      return objective(expression, OptimizationDirection.MAXIMIZE);
    }

    private Builder objective(LinearExpression expression, OptimizationDirection direction) {
      requireOpen();
      if (expression == null) {
        throw new IllegalArgumentException("The objective expression must not be null.");
      }
      if (objectiveSpecified) {
        throw new IllegalArgumentException(
            "The model already has an objective; multiple objectives are unsupported.");
      }
      objective = expression;
      objectiveDirection = direction;
      objectiveSpecified = true;
      return this;
    }

    /**
     * Validates and returns an immutable model, then closes this builder. Validation uses all
     * declared domains, narrowed by fixed values for arithmetic bounds, and never assumes that
     * other constraints restrict values. A validation failure leaves the builder open.
     *
     * @throws IllegalArgumentException if definitions or exact long arithmetic are unsupported
     * @throws IllegalStateException if this builder has already built a model
     */
    public MathModel build() {
      requireOpen();
      for (var entry : fixedValues.entrySet()) {
        Long initial = initialValues.get(entry.getKey());
        if (initial != null && initial.longValue() != entry.getValue().longValue()) {
          throw new IllegalArgumentException(
              "The variable ("
                  + entry.getKey().name()
                  + ") has initial value ("
                  + initial
                  + ") different from its fixed value ("
                  + entry.getValue()
                  + ").");
        }
      }
      var model = new MathModel(this);
      MathCompiler.compile(model);
      built = true;
      return model;
    }

    private void requireValue(IntVar variable, long value, String kind) {
      if (variable == null || !variable.belongsTo(owner)) {
        throw new IllegalArgumentException(
            "The " + kind + " variable (" + variable + ") belongs to another model.");
      }
      if (value < variable.lowerBound() || value > variable.upperBound()) {
        throw new IllegalArgumentException(
            "The "
                + kind
                + " value ("
                + value
                + ") of variable ("
                + variable.name()
                + ") is outside its inclusive domain ["
                + variable.lowerBound()
                + ", "
                + variable.upperBound()
                + "].");
      }
    }

    private static void requireName(String name, String kind) {
      if (name == null || name.isBlank()) {
        throw new IllegalArgumentException(
            "The " + kind + " name (" + name + ") must not be blank.");
      }
    }

    private void requireOpen() {
      if (built) {
        throw new IllegalStateException("This model builder has already built an immutable model.");
      }
    }
  }
}
