package greycos.solver.core.impl.alns;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;
import java.util.random.RandomGenerator;
import java.util.stream.Stream;

import greycos.solver.core.api.score.HardSoftScore;
import greycos.solver.core.api.solver.alns.AlnsAssignment;
import greycos.solver.core.api.solver.alns.AlnsBasicVariable;
import greycos.solver.core.api.solver.alns.AlnsChange;
import greycos.solver.core.api.solver.alns.AlnsContext;
import greycos.solver.core.api.solver.alns.AlnsDestroyOperator;
import greycos.solver.core.api.solver.alns.AlnsEvaluation;
import greycos.solver.core.api.solver.alns.AlnsListVariable;
import greycos.solver.core.api.solver.alns.AlnsRanking;
import greycos.solver.core.api.solver.alns.AlnsRelatedness;
import greycos.solver.core.api.solver.alns.AlnsRepairOperator;
import greycos.solver.core.api.solver.alns.AlnsTarget;
import greycos.solver.core.api.solver.alns.AlnsVariable;
import greycos.solver.core.config.alns.AlnsDestroyOperatorConfig;
import greycos.solver.core.config.alns.AlnsDestroyOperatorType;
import greycos.solver.core.config.alns.AlnsRepairOperatorConfig;
import greycos.solver.core.config.alns.AlnsRepairOperatorType;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

class BuiltinAlnsOperatorsTest {

  @ParameterizedTest
  @MethodSource("invalidConfigurations")
  void rejectsAmbiguousOrInapplicableConfiguration(Runnable factory) {
    assertThatIllegalArgumentException().isThrownBy(factory::run).withMessageContaining("ALNS");
  }

  private static Stream<Runnable> invalidConfigurations() {
    return Stream.of(
        () ->
            BuiltinAlnsOperators.destroy(
                new AlnsDestroyOperatorConfig()
                    .withCustomClass(AlnsDestroyOperator.class)
                    .withType(AlnsDestroyOperatorType.RANDOM)),
        () ->
            BuiltinAlnsOperators.destroy(
                new AlnsDestroyOperatorConfig()
                    .withCustomClass(AlnsDestroyOperator.class)
                    .withRelatednessClass(Distance.class)),
        () ->
            BuiltinAlnsOperators.destroy(
                new AlnsDestroyOperatorConfig()
                    .withType(AlnsDestroyOperatorType.RANDOM)
                    .withRelatednessClass(Distance.class)),
        () ->
            BuiltinAlnsOperators.destroy(
                new AlnsDestroyOperatorConfig()
                    .withType(AlnsDestroyOperatorType.RELATEDNESS)
                    .withRankingClass(LabelRanking.class)),
        () ->
            BuiltinAlnsOperators.destroy(
                new AlnsDestroyOperatorConfig()
                    .withType(AlnsDestroyOperatorType.WORST_REMOVAL)
                    .withRelatednessClass(Distance.class)),
        () ->
            BuiltinAlnsOperators.repair(
                new AlnsRepairOperatorConfig()
                    .withCustomClass(AlnsRepairOperator.class)
                    .withType(AlnsRepairOperatorType.GREEDY)));
  }

  @Test
  void randomDestroyRespectsScopeIdentityAndCountWithoutMutating() {
    var context = new TestContext();
    for (int i = 0; i < 8; i++) {
      context.basic("item" + i, "first", false, true, "x", "y");
    }
    context.basic("other", "second", false, true, "x");
    var before = new LinkedHashMap<>(context.state.assignments);
    var operator =
        BuiltinAlnsOperators.<State, HardSoftScore>destroy(
            new AlnsDestroyOperatorConfig().withVariableName("first"));
    var selected = operator.select(context, 20);
    assertThat(selected).hasSize(8).doesNotHaveDuplicates();
    assertThat(selected).allMatch(target -> target.variable().variableName().equals("first"));
    assertThat(context.state.assignments).isEqualTo(before);
  }

  @Test
  void inheritedVariableScopeChecksTheActualEntity() {
    var variable =
        new AlnsBasicVariable<State, Object, String>(Object.class, "value", String.class, false);
    assertThat(
            BuiltinAlnsOperators.matches(
                new AlnsTarget<>(variable, "subclass entity", "x"), String.class, "value"))
        .isTrue();
    assertThat(
            BuiltinAlnsOperators.matches(
                new AlnsTarget<>(variable, 123, "x"), String.class, "value"))
        .isFalse();
    var list =
        new AlnsListVariable<State, Object, String>(Object.class, "list", String.class, true);
    assertThat(
            BuiltinAlnsOperators.matches(
                new AlnsTarget<>(list, null, "unassigned"), String.class, "list"))
        .isTrue();
  }

  @Test
  void relatednessUsesCallbackAndRejectsInvalidDistance() {
    var context = new TestContext();
    for (int i = 0; i < 8; i++) {
      context.basic(Integer.toString(i), "first", false, true, "x");
    }
    var config =
        new AlnsDestroyOperatorConfig()
            .withType(AlnsDestroyOperatorType.RELATEDNESS)
            .withRelatednessClass(Distance.class)
            .withRankExponent(1e9);
    var selected = BuiltinAlnsOperators.<State, HardSoftScore>destroy(config).select(context, 4);
    int first = Integer.parseInt(context.state.labels.get(selected.get(0)));
    int second = Integer.parseInt(context.state.labels.get(selected.get(1)));
    assertThat(Math.abs(first - second)).isEqualTo(1);
    assertThat(selected).hasSize(4).doesNotHaveDuplicates();
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                BuiltinAlnsOperators.<State, HardSoftScore>destroy(
                        config.withRelatednessClass(InvalidDistance.class))
                    .select(context, 2));
  }

  @Test
  void listBlockIsContiguousAndWithinOneOwner() {
    var context = new TestContext();
    var ownerA = new Object();
    var ownerB = new Object();
    for (int i = 0; i < 5; i++) {
      context.list("a" + i, ownerA, i + 2); // An excluded pinned prefix before index two.
      context.list("b" + i, ownerB, i);
    }
    var selected =
        BuiltinAlnsOperators.<State, HardSoftScore>destroy(
                new AlnsDestroyOperatorConfig().withType(AlnsDestroyOperatorType.LIST_BLOCK))
            .select(context, 3);
    assertThat(selected).hasSize(3);
    var owner = context.currentAssignment(selected.get(0)).entity();
    assertThat(selected).allMatch(target -> context.currentAssignment(target).entity() == owner);
    var indices =
        selected.stream()
            .map(target -> context.currentAssignment(target).index())
            .sorted()
            .toList();
    assertThat(indices).containsExactly(indices.get(0), indices.get(0) + 1, indices.get(0) + 2);
  }

  @Test
  void exactWorstReevaluatesAfterEachSelectedRemovalAndRestoresScratchState() {
    var context = new TestContext();
    var a = context.basic("A", "first", false, true, "x");
    var b = context.basic("B", "first", false, true, "x");
    var c = context.basic("C", "first", false, true, "x");
    context.scorer =
        ctx -> {
          boolean removedA = ctx.value(a) == null;
          boolean removedB = ctx.value(b) == null;
          boolean removedC = ctx.value(c) == null;
          long score = (removedA ? 100 : 0) + (removedB ? 90 : 0) + (removedC ? 80 : 0);
          if (removedA && removedB) score -= 89;
          if (removedA && removedC) score += 20;
          return HardSoftScore.ofSoft(score);
        };
    var before = new LinkedHashMap<>(context.state.assignments);
    var selected =
        BuiltinAlnsOperators.<State, HardSoftScore>destroy(
                new AlnsDestroyOperatorConfig()
                    .withType(AlnsDestroyOperatorType.WORST_REMOVAL)
                    .withRankExponent(1e9))
            .select(context, 2);
    assertThat(selected).containsExactly(a, c); // Once-ranked scoring would select A then B.
    assertThat(context.state.assignments).isEqualTo(before);
    assertThat(context.evaluations).isEqualTo(6); // Five one-target probes and one outer scratch.
    assertThat(context.destructions)
        .isEqualTo(7); // Five probes plus two retained scratch removals.
  }

  @Test
  void removalRankingCallbackIsUsed() {
    var context = new TestContext();
    var a = context.basic("1", "first", false, true, "x");
    var b = context.basic("2", "first", false, true, "x");
    var selected =
        BuiltinAlnsOperators.<State, HardSoftScore>destroy(
                new AlnsDestroyOperatorConfig()
                    .withType(AlnsDestroyOperatorType.WORST_REMOVAL)
                    .withRankingClass(LabelRanking.class)
                    .withRankExponent(1e9))
            .select(context, 1);
    assertThat(selected).containsExactly(b).doesNotContain(a);
    assertThat(context.evaluations).isEqualTo(1); // Callback ranking needs only the outer scratch.
    assertThat(context.destructions).isEqualTo(1);
  }

  @Test
  void callbackSeesRetainedRemovalsAndFailureRestoresTheOuterScratch() {
    var context = new TestContext();
    var a = context.basic("1", "first", false, true, "x");
    var b = context.basic("2", "first", false, true, "x");
    var before = new LinkedHashMap<>(context.state.assignments);
    var operator =
        BuiltinAlnsOperators.<State, HardSoftScore>destroy(
            new AlnsDestroyOperatorConfig()
                .withType(AlnsDestroyOperatorType.WORST_REMOVAL)
                .withRankingClass(FailAfterRemovalRanking.class)
                .withRankExponent(1e9));
    org.assertj.core.api.Assertions.assertThatIllegalStateException()
        .isThrownBy(() -> operator.select(context, 2))
        .withMessage("observed retained removal");
    assertThat(context.destructions).isEqualTo(1);
    assertThat(context.state.assignments).isEqualTo(before);
    assertThat(context.targets()).containsExactly(a, b);
  }

  @Test
  void callbacksAreClosedNormallyAndAfterConfigurationFailure() throws Exception {
    ClosingDistance.closed = 0;
    var config =
        new AlnsDestroyOperatorConfig()
            .withType(AlnsDestroyOperatorType.RELATEDNESS)
            .withRelatednessClass(ClosingDistance.class);
    var operator = BuiltinAlnsOperators.<State, HardSoftScore>destroy(config);
    ((AutoCloseable) operator).close();
    assertThat(ClosingDistance.closed).isEqualTo(1);
    org.assertj.core.api.Assertions.assertThatIllegalStateException()
        .isThrownBy(
            () ->
                BuiltinAlnsOperators.<State, HardSoftScore>destroy(
                    config.withCustomProperties(Map.of("missing", "value"))));
    assertThat(ClosingDistance.closed).isEqualTo(2);
  }

  @ParameterizedTest
  @EnumSource(
      value = AlnsRepairOperatorType.class,
      names = {"GREEDY", "REGRET_2", "REGRET_3"})
  void repairsMandatoryAndLeavesOptionalUnassignedWhenBest(AlnsRepairOperatorType type) {
    var context = new TestContext();
    var optional = context.basic("optional", "nullable", true, false, "x");
    var mandatory = context.basic("mandatory", "required", false, false, "bad", "good");
    context.scorer =
        ctx ->
            HardSoftScore.ofSoft(
                ("good".equals(ctx.value(mandatory)) ? 10 : 0)
                    - (ctx.value(optional) != null ? 100 : 0));
    assertThat(
            BuiltinAlnsOperators.<State, HardSoftScore>repair(
                    new AlnsRepairOperatorConfig().withType(type))
                .repair(context, List.of(optional, mandatory)))
        .isTrue();
    assertThat(context.value(mandatory)).isEqualTo("good");
    assertThat(context.value(optional)).isNull();
    assertThat(context.applied).containsExactly(mandatory, optional);
  }

  @ParameterizedTest
  @EnumSource(
      value = AlnsRepairOperatorType.class,
      names = {"REGRET_2", "REGRET_3"})
  void regretUsesExactLevelsAndRecomputesAfterInsertion(AlnsRepairOperatorType type) {
    var context = new TestContext();
    var a = context.basic("A", "first", false, false, "x", "y");
    var b = context.basic("B", "first", false, false, "x", "y");
    context.scorer =
        ctx -> {
          // Regret for B crosses the signed-long boundary; subtracting long scores would overflow.
          if (ctx.value(a) == null && ctx.value(b) != null) {
            return HardSoftScore.ofSoft("x".equals(ctx.value(b)) ? Long.MAX_VALUE : Long.MIN_VALUE);
          }
          if (ctx.value(b) == null && ctx.value(a) != null) {
            return HardSoftScore.ofSoft("x".equals(ctx.value(a)) ? 100 : 99);
          }
          if (ctx.value(b) != null && ctx.value(a) != null) {
            return HardSoftScore.ofSoft("y".equals(ctx.value(a)) ? 200 : 100);
          }
          return HardSoftScore.ZERO;
        };
    assertThat(
            BuiltinAlnsOperators.<State, HardSoftScore>repair(
                    new AlnsRepairOperatorConfig().withType(type))
                .repair(context, List.of(a, b)))
        .isTrue();
    assertThat(context.applied).containsExactly(b, a);
    assertThat(context.value(a)).isEqualTo("y"); // A's cached best before B was x.
  }

  @Test
  void forcedAssignmentsComeFirstAndMissingMandatoryCandidatesFail() {
    var context = new TestContext();
    var flexible = context.basic("flexible", "first", false, false, "x", "y");
    var forced = context.basic("forced", "first", false, false, "x");
    assertThat(
            BuiltinAlnsOperators.<State, HardSoftScore>repair(
                    new AlnsRepairOperatorConfig().withType(AlnsRepairOperatorType.REGRET_3))
                .repair(context, List.of(flexible, forced)))
        .isTrue();
    assertThat(context.applied).containsExactly(forced, flexible);
    var impossible = context.basic("impossible", "first", false, false);
    assertThat(
            BuiltinAlnsOperators.<State, HardSoftScore>repair(new AlnsRepairOperatorConfig())
                .repair(context, List.of(impossible)))
        .isFalse();
  }

  @Test
  void randomizedRepairUsesTopKAndFixedSeed() {
    var first = new TestContext();
    var second = new TestContext();
    var a = first.basic("a", "first", false, false, "1", "2", "3", "4", "5");
    var b = second.basic("a", "first", false, false, "1", "2", "3", "4", "5");
    first.scorer =
        ctx ->
            HardSoftScore.ofSoft(
                ctx.value(a) == null ? 0 : Integer.parseInt(ctx.value(a).toString()));
    second.scorer =
        ctx ->
            HardSoftScore.ofSoft(
                ctx.value(b) == null ? 0 : Integer.parseInt(ctx.value(b).toString()));
    var config =
        new AlnsRepairOperatorConfig()
            .withType(AlnsRepairOperatorType.RANDOMIZED_GREEDY)
            .withTopK(3);
    BuiltinAlnsOperators.<State, HardSoftScore>repair(config).repair(first, List.of(a));
    BuiltinAlnsOperators.<State, HardSoftScore>repair(config).repair(second, List.of(b));
    assertThat(first.value(a)).isIn("3", "4", "5").isEqualTo(second.value(b));
  }

  @Test
  void repairScopeDoesNotSilentlyDropPendingTargets() {
    var context = new TestContext();
    var a = context.basic("A", "first", false, false, "x");
    var b = context.basic("B", "second", true, false, "x");
    assertThat(
            BuiltinAlnsOperators.<State, HardSoftScore>repair(
                    new AlnsRepairOperatorConfig().withVariableName("first"))
                .repair(context, List.of(a, b)))
        .isFalse();
    assertThat(context.applied).isEmpty();
  }

  public static final class Distance implements AlnsRelatedness<State> {
    @Override
    public double distance(State state, AlnsTarget<State> left, AlnsTarget<State> right) {
      return Math.abs(
          Integer.parseInt(state.labels.get(left)) - Integer.parseInt(state.labels.get(right)));
    }
  }

  public static final class InvalidDistance implements AlnsRelatedness<State> {
    @Override
    public double distance(State state, AlnsTarget<State> left, AlnsTarget<State> right) {
      return Double.NaN;
    }
  }

  public static final class ClosingDistance implements AlnsRelatedness<State>, AutoCloseable {
    static int closed;

    @Override
    public double distance(State state, AlnsTarget<State> left, AlnsTarget<State> right) {
      return 0;
    }

    @Override
    public void close() {
      closed++;
    }
  }

  public static final class LabelRanking implements AlnsRanking<State> {
    @Override
    public double rank(State state, AlnsTarget<State> target) {
      return Integer.parseInt(state.labels.get(target));
    }
  }

  public static final class FailAfterRemovalRanking implements AlnsRanking<State> {
    @Override
    public double rank(State state, AlnsTarget<State> target) {
      if (state.assignments.values().stream().anyMatch(AlnsAssignment::isUnassigned)) {
        throw new IllegalStateException("observed retained removal");
      }
      return Integer.parseInt(state.labels.get(target));
    }
  }

  private static final class State {
    final Map<AlnsTarget<State>, String> labels = new LinkedHashMap<>();
    final Map<AlnsTarget<State>, AlnsAssignment<State>> assignments = new LinkedHashMap<>();
  }

  private static final class TestContext implements AlnsContext<State, HardSoftScore> {
    final State state = new State();
    final Map<AlnsTarget<State>, List<AlnsAssignment<State>>> choices = new LinkedHashMap<>();
    final List<AlnsTarget<State>> applied = new ArrayList<>();
    final Random random = new Random(0);
    Function<TestContext, HardSoftScore> scorer = ignored -> HardSoftScore.ZERO;
    int evaluations;
    int destructions;

    AlnsTarget<State> basic(
        String label, String variable, boolean optional, boolean assigned, String... values) {
      var handle =
          new AlnsBasicVariable<State, Object, String>(
              Object.class, variable, String.class, optional);
      var entity = new Object();
      var target = new AlnsTarget<State>(handle, entity, assigned ? values[0] : null);
      state.labels.put(target, label);
      var alternatives = new ArrayList<AlnsAssignment<State>>();
      for (var value : values) alternatives.add(new AlnsAssignment<>(target, entity, value, -1));
      if (optional) alternatives.add(new AlnsAssignment<>(target, entity, null, -1));
      choices.put(target, alternatives);
      state.assignments.put(
          target, new AlnsAssignment<>(target, entity, assigned ? values[0] : null, -1));
      return target;
    }

    void list(String label, Object owner, int index) {
      var handle =
          new AlnsListVariable<State, Object, String>(Object.class, "list", String.class, false);
      var target = new AlnsTarget<State>(handle, owner, label);
      state.labels.put(target, label);
      var assignment = new AlnsAssignment<>(target, owner, label, index);
      state.assignments.put(target, assignment);
      choices.put(target, List.of(assignment));
    }

    Object value(AlnsTarget<State> target) {
      return currentAssignment(target).value();
    }

    @Override
    public State workingSolution() {
      return state;
    }

    @Override
    public List<AlnsVariable<State>> variables() {
      return state.labels.keySet().stream().map(AlnsTarget::variable).distinct().toList();
    }

    @Override
    public List<AlnsTarget<State>> targets() {
      return state.labels.keySet().stream()
          .filter(target -> !currentAssignment(target).isUnassigned())
          .toList();
    }

    @Override
    public List<AlnsTarget<State>> unassignedTargets() {
      return state.labels.keySet().stream()
          .filter(target -> currentAssignment(target).isUnassigned())
          .toList();
    }

    @Override
    public List<AlnsTarget<State>> pendingTargets() {
      return unassignedTargets();
    }

    @Override
    public List<AlnsAssignment<State>> assignments(AlnsTarget<State> target) {
      return choices.get(target);
    }

    @Override
    public AlnsAssignment<State> currentAssignment(AlnsTarget<State> target) {
      return state.assignments.get(target);
    }

    @Override
    public AlnsEvaluation<HardSoftScore> score() {
      int unassigned =
          (int)
              unassignedTargets().stream()
                  .filter(target -> !target.variable().allowsUnassigned())
                  .count();
      return new AlnsEvaluation<>(scorer.apply(this), unassigned);
    }

    @Override
    public HardSoftScore incumbentScore() {
      return score().score();
    }

    @Override
    public AlnsEvaluation<HardSoftScore> evaluate(AlnsChange<State> change) {
      evaluations++;
      var before = new LinkedHashMap<>(state.assignments);
      int appliedSize = applied.size();
      try {
        change.apply(this);
        return score();
      } finally {
        state.assignments.clear();
        state.assignments.putAll(before);
        applied.subList(appliedSize, applied.size()).clear();
      }
    }

    @Override
    public void execute(AlnsChange<State> change) {
      change.apply(this);
    }

    @Override
    public void assign(AlnsAssignment<State> assignment) {
      state.assignments.put(assignment.target(), assignment);
      applied.add(assignment.target());
    }

    @Override
    public void destroy(AlnsTarget<State> target) {
      destructions++;
      state.assignments.put(
          target,
          new AlnsAssignment<>(
              target,
              target.isList() ? null : target.entity(),
              target.isList() ? target.value() : null,
              -1));
    }

    @Override
    public RandomGenerator random() {
      return random;
    }

    @Override
    public void checkTermination() {}
  }
}
