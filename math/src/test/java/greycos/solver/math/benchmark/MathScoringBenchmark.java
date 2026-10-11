package greycos.solver.math.benchmark;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

import greycos.solver.core.api.score.HardSoftScore;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import greycos.solver.core.impl.score.constraint.ConstraintMatchPolicy;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.score.director.stream.BavetConstraintStreamScoreDirectorFactory;
import greycos.solver.math.api.IntVar;
import greycos.solver.math.api.LinearExpression;
import greycos.solver.math.api.MathModel;
import greycos.solver.math.benchmark.HandwrittenMathBaseline.HandDecision;
import greycos.solver.math.benchmark.HandwrittenMathBaseline.HandSolution;
import greycos.solver.math.benchmark.MathQualificationSupport.AllocationMeter;
import greycos.solver.math.benchmark.MathQualificationSupport.Snapshot;
import greycos.solver.math.impl.cotwin.MathDecision;
import greycos.solver.math.impl.cotwin.MathSolution;
import greycos.solver.math.impl.model.MathCompiler;
import greycos.solver.math.impl.score.MathConstraintProvider;

/** Manual matched-update benchmark. See math/qualification.adoc; no timing assertions. */
public final class MathScoringBenchmark {
  private static volatile long checksumSink;

  private MathScoringBenchmark() {}

  public static void main(String[] args) {
    var options =
        MathQualificationSupport.arguments(
            args,
            Map.of(
                "variables",
                "64",
                "rows",
                "64",
                "degree",
                "4",
                "updates",
                "3000",
                "warmups",
                "2",
                "repetitions",
                "3",
                "variant",
                "all",
                "objective",
                "max",
                "seed",
                "0"));
    int variables = Integer.parseInt(options.get("variables"));
    int rows = Integer.parseInt(options.get("rows"));
    int degree = Integer.parseInt(options.get("degree"));
    int updates = Integer.parseInt(options.get("updates"));
    int warmups = Integer.parseInt(options.get("warmups"));
    int repetitions = Integer.parseInt(options.get("repetitions"));
    if (warmups < 0 || repetitions < 1) {
      throw new IllegalArgumentException("Require warmups >= 0 and repetitions >= 1.");
    }
    var workload =
        workload(
            variables,
            rows,
            degree,
            updates,
            options.get("objective"),
            Long.parseLong(options.get("seed")));
    var variants = variants(options.get("variant"));
    var allocation = new AllocationMeter();
    for (int warmup = 0; warmup < warmups; warmup++) {
      for (var variant : variants) {
        run(workload, variant, allocation);
      }
    }
    System.out.println(
        "variant,variables,rows,degree,nonzeros,incidence_min,incidence_max,objective,seed,repetition,updates,"
            + "initialization_ns,initialization_allocated_bytes,initialization_gc_count,initialization_gc_millis,"
            + "update_ns,updates_per_second,update_allocated_bytes,bytes_per_update,update_gc_count,update_gc_millis,checksum,parity");
    for (int repetition = 0; repetition < repetitions; repetition++) {
      for (int offset = 0; offset < variants.size(); offset++) {
        var variant = variants.get((offset + repetition) % variants.size());
        var measurement = run(workload, variant, allocation);
        var start = measurement.beforeInitialization();
        var ready = measurement.afterInitialization();
        var updating = measurement.beforeUpdates();
        var end = measurement.afterUpdates();
        long nanos = end.nanos() - updating.nanos();
        long allocated = end.allocatedSince(updating);
        System.out.printf(
            Locale.ROOT,
            "%s,%d,%d,%d,%d,%d,%d,%s,%s,%d,%d,%d,%d,%d,%d,%d,%.3f,%d,%.3f,%d,%d,%d,true%n",
            variant,
            variables,
            rows,
            degree,
            workload.nonzeros(),
            workload.minIncidence(),
            workload.maxIncidence(),
            options.get("objective"),
            options.get("seed"),
            repetition,
            updates,
            ready.nanos() - start.nanos(),
            ready.allocatedSince(start),
            ready.collections() - start.collections(),
            ready.gcMillis() - start.gcMillis(),
            nanos,
            updates * 1_000_000_000.0 / nanos,
            allocated,
            allocated < 0 ? -1.0 : (double) allocated / updates,
            end.collections() - updating.collections(),
            end.gcMillis() - updating.gcMillis(),
            measurement.checksum());
      }
    }
  }

  static List<String> variants(String selected) {
    var all = List.of("generic", "handwritten", "full");
    if (selected.equals("all")) {
      return all;
    }
    if (!all.contains(selected)) {
      throw new IllegalArgumentException(
          "Unknown variant " + selected + "; expected " + all + " or all.");
    }
    return List.of(selected);
  }

  static Workload workload(
      int variables, int rows, int degree, int updates, String objective, long seed) {
    if (variables < 1
        || rows < 1
        || degree < 1
        || degree > variables
        || updates < 1
        || !List.of("none", "min", "max").contains(objective)) {
      throw new IllegalArgumentException(
          "Require variables/rows/updates >= 1, 1 <= degree <= variables, objective=none|min|max.");
    }
    var builder = MathModel.builder();
    var decisions = new ArrayList<IntVar>();
    for (int i = 0; i < variables; i++) {
      var variable = builder.intVar("x" + i, -5, 5);
      decisions.add(variable);
      builder.initialValue(variable, 0);
    }
    var incidence = new int[variables];
    for (int row = 0; row < rows; row++) {
      var terms = new ArrayList<LinearExpression>();
      terms.add(LinearExpression.constant(row % 7 - 3));
      for (int term = 0; term < degree; term++) {
        int id = (row + term) % variables;
        long coefficient = 1 + (row * 7L + term * 3L) % 5;
        if ((row + term) % 2 == 0) {
          coefficient = -coefficient;
        }
        terms.add(decisions.get(id).times(coefficient));
        incidence[id]++;
      }
      var expression = LinearExpression.sum(terms);
      long bound = row % 11 - 5;
      builder.addConstraint(
          "row" + row,
          switch (row % 3) {
            case 0 -> expression.le(bound);
            case 1 -> expression.ge(bound);
            default -> expression.eq(bound);
          });
    }
    int nonzeros = Math.multiplyExact(rows, degree);
    if (!objective.equals("none")) {
      var terms = new ArrayList<LinearExpression>();
      terms.add(LinearExpression.constant(-7));
      for (int id = 0; id < variables; id++) {
        terms.add(decisions.get(id).times((id % 2 == 0 ? -1 : 1) * (id % 5 + 1L)));
        incidence[id]++;
      }
      if (objective.equals("min")) {
        builder.minimize(LinearExpression.sum(terms));
      } else {
        builder.maximize(LinearExpression.sum(terms));
      }
      nonzeros = Math.addExact(nonzeros, variables);
    }
    var model = builder.build();
    var initial = new long[variables];
    var values = initial.clone();
    var trace = new Operation[updates];
    var expected = new HardSoftScore[updates];
    var random = new Random(seed);
    for (int i = 0; i < updates; i++) {
      int id = random.nextInt(variables);
      long value;
      do {
        value = random.nextInt(11) - 5;
      } while (value == values[id]);
      trace[i] = new Operation(id, value);
      values[id] = value;
      expected[i] = MathQualificationSupport.replay(model, values);
    }
    int min = Integer.MAX_VALUE;
    int max = 0;
    for (int count : incidence) {
      min = Math.min(min, count);
      max = Math.max(max, count);
    }
    return new Workload(
        model,
        initial,
        trace,
        expected,
        MathQualificationSupport.replay(model, initial),
        nonzeros,
        min,
        max);
  }

  static Measurement run(Workload workload, String variant, AllocationMeter allocation) {
    var beforeInitialization = Snapshot.capture(allocation);
    try (var state = state(workload, variant)) {
      verify(variant, -1, state.score(), workload.initialScore());
      var afterInitialization = Snapshot.capture(allocation);
      long checksum = checksum(1L, workload.initialScore());
      var beforeUpdates = Snapshot.capture(allocation);
      for (int i = 0; i < workload.trace().length; i++) {
        var operation = workload.trace()[i];
        state.update(operation.id(), operation.value());
        var score = state.score();
        verify(variant, i, score, workload.expected()[i]);
        checksum = checksum(checksum, score);
      }
      var afterUpdates = Snapshot.capture(allocation);
      checksumSink = checksum;
      return new Measurement(
          beforeInitialization, afterInitialization, beforeUpdates, afterUpdates, checksum);
    }
  }

  private static void verify(
      String variant, int update, HardSoftScore actual, HardSoftScore expected) {
    if (!actual.equals(expected)) {
      throw new IllegalStateException(
          variant
              + " parity failed at update "
              + update
              + ": expected "
              + expected
              + ", actual "
              + actual);
    }
  }

  private static long checksum(long checksum, HardSoftScore score) {
    return 31L * (31L * checksum + score.hardScore()) + score.softScore();
  }

  private static State state(Workload workload, String variant) {
    return switch (variant) {
      case "generic" -> new GenericState(workload);
      case "handwritten" -> new HandwrittenState(workload);
      case "full" -> new FullState(workload);
      default -> throw new IllegalArgumentException("Unknown variant " + variant);
    };
  }

  private interface State extends AutoCloseable {
    void update(int id, long value);

    HardSoftScore score();

    @Override
    default void close() {}
  }

  private static final class GenericState implements State {
    private final MathSolution solution;
    private final InnerScoreDirector<MathSolution, HardSoftScore> director;

    private GenericState(Workload workload) {
      solution = MathSolution.initialize(MathCompiler.compile(workload.model()), 0L);
      var factory =
          new BavetConstraintStreamScoreDirectorFactory<MathSolution, HardSoftScore>(
              SolutionDescriptor.buildSolutionDescriptor(MathSolution.class, MathDecision.class),
              new MathConstraintProvider(),
              EnvironmentMode.NO_ASSERT);
      director =
          factory
              .createScoreDirectorBuilder()
              .withConstraintMatchPolicy(ConstraintMatchPolicy.DISABLED)
              .build();
      director.setWorkingSolution(solution);
    }

    @Override
    public void update(int id, long value) {
      var decision = solution.getDecisions().get(id);
      director.beforeVariableChanged(decision, "value");
      decision.setValue(value);
      director.afterVariableChanged(decision, "value");
      director.updateShadowVariables();
    }

    @Override
    public HardSoftScore score() {
      return director.calculateScore().raw();
    }

    @Override
    public void close() {
      director.close();
    }
  }

  private static final class HandwrittenState implements State {
    private final HandSolution solution;
    private final InnerScoreDirector<HandSolution, HardSoftScore> director;

    private HandwrittenState(Workload workload) {
      solution = HandwrittenMathBaseline.initialize(workload.model(), workload.initial());
      var factory =
          new BavetConstraintStreamScoreDirectorFactory<HandSolution, HardSoftScore>(
              SolutionDescriptor.buildSolutionDescriptor(HandSolution.class, HandDecision.class),
              new HandwrittenMathBaseline.Provider(),
              EnvironmentMode.NO_ASSERT);
      director =
          factory
              .createScoreDirectorBuilder()
              .withConstraintMatchPolicy(ConstraintMatchPolicy.DISABLED)
              .build();
      director.setWorkingSolution(solution);
    }

    @Override
    public void update(int id, long value) {
      var decision = solution.getDecisions().get(id);
      director.beforeVariableChanged(decision, "value");
      decision.setValue(value);
      director.afterVariableChanged(decision, "value");
      director.updateShadowVariables();
    }

    @Override
    public HardSoftScore score() {
      return director.calculateScore().raw();
    }

    @Override
    public void close() {
      director.close();
    }
  }

  private static final class FullState implements State {
    private final MathModel model;
    private final long[] values;

    private FullState(Workload workload) {
      model = workload.model();
      values = workload.initial().clone();
    }

    @Override
    public void update(int id, long value) {
      values[id] = value;
    }

    @Override
    public HardSoftScore score() {
      return MathQualificationSupport.replay(model, values);
    }
  }

  record Operation(int id, long value) {}

  record Workload(
      MathModel model,
      long[] initial,
      Operation[] trace,
      HardSoftScore[] expected,
      HardSoftScore initialScore,
      int nonzeros,
      int minIncidence,
      int maxIncidence) {}

  record Measurement(
      Snapshot beforeInitialization,
      Snapshot afterInitialization,
      Snapshot beforeUpdates,
      Snapshot afterUpdates,
      long checksum) {}
}
