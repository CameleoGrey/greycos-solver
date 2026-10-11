package greycos.solver.math.benchmark;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.entity.PlanningPin;
import greycos.solver.core.api.cotwin.lookup.PlanningId;
import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.solution.ProblemFactCollectionProperty;
import greycos.solver.core.api.cotwin.solution.cloner.SolutionCloner;
import greycos.solver.core.api.cotwin.valuerange.ValueRange;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeFactory;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.cotwin.variable.PlanningVariable;
import greycos.solver.core.api.score.HardSoftScore;
import greycos.solver.core.api.score.calculator.EasyScoreCalculator;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.api.score.stream.ConstraintCollectors;
import greycos.solver.core.api.score.stream.ConstraintFactory;
import greycos.solver.core.api.score.stream.ConstraintProvider;
import greycos.solver.math.api.IntVar;
import greycos.solver.math.api.LinearExpression;
import greycos.solver.math.api.MathModel;
import greycos.solver.math.api.OptimizationDirection;
import greycos.solver.math.api.Relation;

/**
 * A separate domain model/provider: decision-rooted resource incidence expansion, without compiled
 * terms, ID joins, the mathematical compiler, or the shipped provider's row arithmetic.
 */
public final class HandwrittenMathBaseline {

  private HandwrittenMathBaseline() {}

  static HandSolution initialize(MathModel model, long[] values) {
    var resources = new ArrayList<Resource>();
    var incidences = new ArrayList<List<Incidence>>();
    model.variables().forEach(ignored -> incidences.add(new ArrayList<>()));
    for (var constraint : model.constraints()) {
      addResource(
          resources,
          incidences,
          constraint.expression(),
          constraint.relation(),
          constraint.bound(),
          0);
    }
    addResource(
        resources,
        incidences,
        model.objective(),
        null,
        0,
        model.objectiveDirection() == OptimizationDirection.MINIMIZE ? -1 : 1);
    var decisions = new ArrayList<HandDecision>();
    for (var variable : model.variables()) {
      decisions.add(
          new HandDecision(
              variable.id(),
              variable.lowerBound(),
              variable.upperBound(),
              values[variable.id()],
              model.fixedValue(variable).isPresent()
                  || variable.lowerBound() == variable.upperBound(),
              List.copyOf(incidences.get(variable.id()))));
    }
    return new HandSolution(model, decisions, List.copyOf(resources), null);
  }

  private static void addResource(
      List<Resource> resources,
      List<List<Incidence>> incidences,
      LinearExpression expression,
      Relation relation,
      long bound,
      int objectiveSign) {
    var coefficients = new HashMap<Integer, BigInteger>();
    var constant = expand(expression, BigInteger.ONE, coefficients);
    var resource =
        new Resource(resources.size(), constant.longValueExact(), relation, bound, objectiveSign);
    resources.add(resource);
    coefficients.forEach(
        (id, coefficient) -> {
          if (coefficient.signum() != 0) {
            incidences.get(id).add(new Incidence(resource, coefficient.longValueExact()));
          }
        });
  }

  private static BigInteger expand(
      LinearExpression expression, BigInteger multiplier, Map<Integer, BigInteger> coefficients) {
    return switch (expression) {
      case IntVar variable -> {
        coefficients.merge(variable.id(), multiplier, BigInteger::add);
        yield BigInteger.ZERO;
      }
      case LinearExpression.Constant constant ->
          multiplier.multiply(BigInteger.valueOf(constant.value()));
      case LinearExpression.Scale scale ->
          expand(
              scale.expression(),
              multiplier.multiply(BigInteger.valueOf(scale.coefficient())),
              coefficients);
      case LinearExpression.Sum sum -> {
        var constant = BigInteger.ZERO;
        for (var operand : sum.operands()) {
          constant = constant.add(expand(operand, multiplier, coefficients));
        }
        yield constant;
      }
    };
  }

  public record Resource(int id, long constant, Relation relation, long bound, int objectiveSign) {
    long violation(long sum) {
      long residual = Math.subtractExact(Math.addExact(constant, sum), bound);
      return switch (relation) {
        case LE -> Math.max(0, residual);
        case GE -> residual < 0 ? Math.negateExact(residual) : 0;
        case EQ -> residual < 0 ? Math.negateExact(residual) : residual;
      };
    }
  }

  public record Incidence(Resource resource, long coefficient) {}

  @PlanningEntity
  public static final class HandDecision {
    private int id;
    private long lower;
    private long upper;
    private ValueRange<Long> range;
    private Long value;
    private boolean pinned;
    private List<Incidence> incidences;

    public HandDecision() {}

    HandDecision(
        int id, long lower, long upper, Long value, boolean pinned, List<Incidence> incidences) {
      this.id = id;
      this.lower = lower;
      this.upper = upper;
      this.range = ValueRangeFactory.createLongValueRange(lower, Math.addExact(upper, 1));
      this.value = value;
      this.pinned = pinned;
      this.incidences = incidences;
    }

    @PlanningId
    public int getId() {
      return id;
    }

    @PlanningVariable(valueRangeProviderRefs = "range")
    public Long getValue() {
      return value;
    }

    public void setValue(Long value) {
      this.value = value;
    }

    @PlanningPin
    public boolean isPinned() {
      return pinned;
    }

    @ValueRangeProvider(id = "range")
    public ValueRange<Long> getRange() {
      return range;
    }

    public List<Incidence> getIncidences() {
      return incidences;
    }

    HandDecision copy() {
      return new HandDecision(id, lower, upper, value, pinned, incidences);
    }
  }

  @PlanningSolution(solutionCloner = HandSolutionCloner.class)
  public static final class HandSolution {
    private MathModel model;
    private List<HandDecision> decisions;
    private List<Resource> resources;
    private HardSoftScore score;

    public HandSolution() {}

    HandSolution(
        MathModel model,
        List<HandDecision> decisions,
        List<Resource> resources,
        HardSoftScore score) {
      this.model = model;
      this.decisions = decisions;
      this.resources = resources;
      this.score = score;
    }

    @PlanningEntityCollectionProperty
    public List<HandDecision> getDecisions() {
      return decisions;
    }

    @ProblemFactCollectionProperty
    public List<Resource> getResources() {
      return resources;
    }

    @PlanningScore
    public HardSoftScore getScore() {
      return score;
    }

    public void setScore(HardSoftScore score) {
      this.score = score;
    }

    long[] values() {
      var values = new long[decisions.size()];
      for (var decision : decisions) {
        values[decision.id] = decision.value;
      }
      return values;
    }
  }

  public static final class HandSolutionCloner implements SolutionCloner<HandSolution> {
    @Override
    public HandSolution cloneSolution(HandSolution original) {
      var decisions = new ArrayList<HandDecision>(original.decisions.size());
      original.decisions.forEach(decision -> decisions.add(decision.copy()));
      return new HandSolution(original.model, decisions, original.resources, original.score);
    }
  }

  public static final class Provider implements ConstraintProvider {
    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      var usage =
          factory
              .forEach(HandDecision.class)
              .expand(HandDecision::getIncidences)
              .flattenLast(incidences -> incidences)
              .groupBy(
                  (decision, incidence) -> incidence.resource(),
                  ConstraintCollectors.sum(
                      (HandDecision decision, Incidence incidence) ->
                          Math.multiplyExact(decision.getValue(), incidence.coefficient())))
              .complement(Resource.class, resource -> 0L);
      return new Constraint[] {
        usage
            .filter((resource, sum) -> resource.objectiveSign() == 0)
            .penalize(HardSoftScore.ONE_HARD, Resource::violation)
            .asConstraint("resourceCapacity"),
        usage
            .filter((resource, sum) -> resource.objectiveSign() != 0)
            .impact(
                HardSoftScore.ONE_SOFT,
                (resource, sum) ->
                    Math.multiplyExact(
                        resource.objectiveSign(), Math.addExact(resource.constant(), sum)))
            .asConstraint("resourceObjective")
      };
    }
  }

  public static final class FullReevaluation
      implements EasyScoreCalculator<HandSolution, HardSoftScore> {
    @Override
    public HardSoftScore calculateScore(HandSolution solution) {
      return MathQualificationSupport.replay(solution.model, solution.values());
    }
  }
}
