package greycos.solver.core.impl.exhaustivesearch;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.solution.ProblemFactCollectionProperty;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.cotwin.variable.PlanningListVariable;
import greycos.solver.core.api.cotwin.variable.PlanningVariable;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.calculator.EasyScoreCalculator;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.exhaustivesearch.ExhaustiveSearchPhaseConfig;
import greycos.solver.core.config.exhaustivesearch.ExhaustiveSearchType;
import greycos.solver.core.config.exhaustivesearch.NodeExplorationType;
import greycos.solver.core.config.heuristic.selector.entity.EntitySelectorConfig;
import greycos.solver.core.config.heuristic.selector.list.DestinationSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.composite.CartesianProductMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.ChangeMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.ListChangeMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.value.ValueSelectorConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.impl.exhaustivesearch.StructuralExhaustiveSearchPhaseTest.ListRoute;
import greycos.solver.core.impl.exhaustivesearch.StructuralExhaustiveSearchPhaseTest.ListValue;
import greycos.solver.core.impl.exhaustivesearch.scope.ExhaustiveSearchPhaseScope;
import greycos.solver.core.impl.exhaustivesearch.scope.ExhaustiveSearchStepScope;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.solver.DefaultSolver;
import greycos.solver.core.impl.solver.DefaultSolverFactory;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class JointExhaustiveSearchPartialStateTest {

  static Stream<Arguments> modes() {
    return Stream.of(false, true)
        .flatMap(
            product ->
                Stream.concat(
                    Stream.of(
                        Arguments.of(
                            ExhaustiveSearchType.BRUTE_FORCE,
                            NodeExplorationType.ORIGINAL_ORDER,
                            product)),
                    Stream.of(NodeExplorationType.values())
                        .map(
                            order ->
                                Arguments.of(
                                    ExhaustiveSearchType.BRANCH_AND_BOUND, order, product))));
  }

  @ParameterizedTest
  @MethodSource("modes")
  void preservesNullDecisionsAndRepairableShadowLoops(
      ExhaustiveSearchType type, NodeExplorationType order, boolean product) {
    var phase =
        new ExhaustiveSearchPhaseConfig()
            .withExhaustiveSearchType(type)
            .withNodeExplorationType(order);
    if (product) {
      phase.withMoveSelectorConfig(
          new CartesianProductMoveSelectorConfig()
              .withMoveSelectors(
                  listSelector(ListRoute.class),
                  listSelector(ExtraOwner.class),
                  new ChangeMoveSelectorConfig()
                      .withEntitySelectorConfig(new EntitySelectorConfig(OptionalBasic.class))
                      .withValueSelectorConfig(new ValueSelectorConfig("choice"))));
    }
    var config =
        new SolverConfig()
            .withSolutionClass(PartialSolution.class)
            .withEntityClasses(
                ListRoute.class, ListValue.class, ExtraOwner.class, OptionalBasic.class)
            .withEasyScoreCalculatorClass(PartialCalculator.class)
            .withEnvironmentMode(EnvironmentMode.FULL_ASSERT)
            .withPhases(phase);
    var factory = SolverFactory.<PartialSolution>create(config);
    var solver = (DefaultSolver<PartialSolution>) factory.buildSolver();
    var sawRepairablePartial = new AtomicBoolean();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void stepStarted(AbstractStepScope<PartialSolution> step) {
            var node = ((ExhaustiveSearchStepScope<PartialSolution>) step).getExpandingNode();
            if (node.getScore() != null && node.getScore().isStructurallyFlawed()) {
              sawRepairablePartial.set(true);
              assertThat(node.getOptimisticBound()).isNull();
            }
            var bound =
                ((ExhaustiveSearchPhaseScope<PartialSolution>) step.getPhaseScope())
                    .getBestPessimisticBound();
            if (bound != null) {
              assertThat(bound.isFullyAssigned()).isTrue();
              assertThat(bound.isStructurallyFlawed()).isFalse();
              assertThat(bound.raw())
                  .isIn(
                      SimpleScore.of(10),
                      SimpleScore.of(20),
                      SimpleScore.of(-15),
                      SimpleScore.of(-5));
            }
          }
        });
    solver.addEventListener(
        event -> assertThat(event.getNewBestScore().structuralScore()).isZero());

    var result = solver.solve(problem());

    assertThat(sawRepairablePartial).isTrue();
    assertThat(result.basic.getFirst().choice).isNull();
    assertThat(result.routes.getFirst().values)
        .extracting(value -> value.id)
        .containsExactly("B", "C", "A");
    assertThat(result.extraOwners.getFirst().values).containsExactly(new ExtraValue("extra"));
    assertThat(result.score).isEqualTo(SimpleScore.of(independentOptimum()));
    for (var value : result.values) {
      assertThat(value.foo).isEqualTo(1);
      assertThat(value.bar).isEqualTo(1);
    }
    try (var director =
        ((DefaultSolverFactory<PartialSolution>) factory)
            .<SimpleScore>getScoreDirectorFactory()
            .buildScoreDirector()) {
      director.setWorkingSolution(result);
      assertThat(director.calculateScore().raw()).isEqualTo(result.score);
      assertThat(director.computeVariableLoops()).isEmpty();
    }
  }

  private static ListChangeMoveSelectorConfig listSelector(Class<?> entityClass) {
    return new ListChangeMoveSelectorConfig()
        .withValueSelectorConfig(new ValueSelectorConfig("values"))
        .withDestinationSelectorConfig(
            new DestinationSelectorConfig()
                .withEntitySelectorConfig(new EntitySelectorConfig(entityClass))
                .withValueSelectorConfig(new ValueSelectorConfig("values")));
  }

  private static PartialSolution problem() {
    var a = new ListValue("A");
    var b = new ListValue("B");
    var c = new ListValue("C");
    var d = new ListValue("D");
    a.dependency = c.dependency = d.dependency = d;
    b.dependency = a;
    var solution = new PartialSolution();
    solution.values = List.of(b, a, c, d);
    solution.listRange = List.of(b, a, c);
    solution.routes = List.of(new ListRoute());
    solution.extraOwners = List.of(new ExtraOwner());
    solution.extraValues = List.of(new ExtraValue("extra"));
    solution.basic = List.of(new OptionalBasic());
    return solution;
  }

  private static int independentOptimum() {
    int optimum = Integer.MIN_VALUE;
    for (String permutation : List.of("ABC", "ACB", "BAC", "BCA", "CAB", "CBA")) {
      // B.bar depends on A.foo, and A.foo depends on its predecessor's bar. Only immediate BA
      // cycles.
      if (permutation.contains("BA")) continue;
      for (boolean assignedBasic : List.of(false, true)) {
        int score = (permutation.equals("BCA") ? 13 : 3) + 2 + (assignedBasic ? -20 : 5);
        optimum = Math.max(optimum, score);
      }
    }
    return optimum;
  }

  @PlanningSolution
  public static class PartialSolution {
    @PlanningEntityCollectionProperty public List<ListRoute> routes;
    @PlanningEntityCollectionProperty public List<ListValue> values;

    @ValueRangeProvider(id = "values")
    public List<ListValue> listRange;

    @PlanningEntityCollectionProperty public List<ExtraOwner> extraOwners;

    @ProblemFactCollectionProperty
    @ValueRangeProvider(id = "extraValues")
    public List<ExtraValue> extraValues;

    @PlanningEntityCollectionProperty public List<OptionalBasic> basic;

    @ProblemFactCollectionProperty
    @ValueRangeProvider(id = "choices")
    public List<Integer> choices = List.of(1);

    @PlanningScore public SimpleScore score;
  }

  public record ExtraValue(String code) {}

  @PlanningEntity
  public static class ExtraOwner {
    @PlanningListVariable(valueRangeProviderRefs = "extraValues")
    public List<ExtraValue> values = new ArrayList<>();
  }

  @PlanningEntity
  public static class OptionalBasic {
    @PlanningVariable(valueRangeProviderRefs = "choices", allowsUnassigned = true)
    public Integer choice;
  }

  public static class PartialCalculator
      implements EasyScoreCalculator<PartialSolution, SimpleScore> {
    @Override
    public SimpleScore calculateScore(PartialSolution solution) {
      var order =
          solution.routes.getFirst().values.stream()
              .map(value -> value.id)
              .reduce("", String::concat);
      return SimpleScore.of(
          (order.equals("BCA") ? 13 : order.length())
              + 2 * solution.extraOwners.getFirst().values.size()
              + (solution.basic.getFirst().choice == null ? 5 : -20));
    }
  }
}
