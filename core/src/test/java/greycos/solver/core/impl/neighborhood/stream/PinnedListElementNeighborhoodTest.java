package greycos.solver.core.impl.neighborhood.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.List;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.entity.PlanningPin;
import greycos.solver.core.api.cotwin.entity.PlanningPinToIndex;
import greycos.solver.core.api.cotwin.lookup.PlanningId;
import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.solution.ProblemFactCollectionProperty;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.cotwin.variable.IndexShadowVariable;
import greycos.solver.core.api.cotwin.variable.PlanningListVariable;
import greycos.solver.core.api.cotwin.variable.PlanningVariable;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.calculator.EasyScoreCalculator;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.localsearch.LocalSearchType;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.PreviewFeature;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import greycos.solver.core.impl.neighborhood.NeighborhoodsBasedMoveRepository;
import greycos.solver.core.impl.neighborhood.stream.enumerating.uni.AbstractUniEnumeratingStream;
import greycos.solver.core.impl.neighborhood.stream.enumerating.uni.UniLeftDataset;
import greycos.solver.core.impl.score.director.SessionContext;
import greycos.solver.core.impl.score.director.easy.EasyScoreDirectorFactory;
import greycos.solver.core.preview.api.cotwin.metamodel.ElementPosition;
import greycos.solver.core.preview.api.move.builtin.ChangeMoveProvider;
import greycos.solver.core.preview.api.move.builtin.ListAssignMoveProvider;
import greycos.solver.core.preview.api.move.builtin.ListChangeMoveProvider;
import greycos.solver.core.preview.api.move.builtin.ListSwapMoveProvider;
import greycos.solver.core.preview.api.move.builtin.ListUnassignMoveProvider;
import greycos.solver.core.preview.api.move.builtin.Moves;
import greycos.solver.core.preview.api.neighborhood.MoveProvider;
import greycos.solver.core.preview.api.neighborhood.Neighborhood;
import greycos.solver.core.preview.api.neighborhood.NeighborhoodBuilder;
import greycos.solver.core.preview.api.neighborhood.NeighborhoodProvider;
import greycos.solver.core.preview.api.neighborhood.stream.enumerating.UniEnumeratingStream;
import greycos.solver.core.preview.api.neighborhood.test.NeighborhoodTester;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PinnedListElementNeighborhoodTest {

  @Test
  void genericAndListEnumerationUseTheirOwnPinsAndReactToChanges() {
    var descriptor = buildDescriptor();
    var solution = problem();
    var factory = new DefaultMoveStreamFactory<>(descriptor, EnvironmentMode.PHASE_ASSERT);
    var ownerModel = descriptor.getMetaModel().genuineEntity(Owner.class);
    var firstModel = ownerModel.listVariable("first", FirstValue.class);
    var secondModel = ownerModel.listVariable("second", SecondValue.class);
    assertThat(factory.forEach(Value.class, true)).isSameAs(factory.forEach(Value.class, true));
    assertThat(factory.forEachAssignedValue(firstModel))
        .isSameAs(factory.forEachAssignedValue(firstModel));
    var genuine = dataset(factory.forEach(FirstValue.class, false));
    var shadowOnly = dataset(factory.forEach(SecondValue.class, false));
    var common = dataset(factory.forEach(Value.class, true));
    var firstAssigned = dataset(factory.forEachAssignedValue(firstModel));
    var firstUnassigned = dataset(factory.forEachUnassignedValue(firstModel));
    var secondAssigned = dataset(factory.forEachAssignedValue(secondModel));
    var firstDestinations = dataset(factory.forEachDestinationIncludingUnassigned(firstModel));
    var secondDestinations = dataset(factory.forEachDestination(secondModel));
    var first = solution.firstValues;
    var second = solution.secondValues;
    var prefixOwner = solution.owners.get(0);
    var freeOwner = solution.owners.get(2);

    try (var scoreDirector =
        new EasyScoreDirectorFactory<>(
                descriptor, new BasicScoreCalculator(), EnvironmentMode.PHASE_ASSERT)
            .buildScoreDirector()) {
      scoreDirector.setWorkingSolution(solution);
      var session = factory.createSession(new SessionContext<>(scoreDirector));
      descriptor.visitAll(solution, session::insert);
      session.settle();
      assertValues(
          session, genuine, first.get(0), first.get(1), first.get(2), first.get(3), first.get(4));
      assertValues(session, shadowOnly, second.get(1), second.get(3), second.get(4));
      assertValues(
          session,
          common,
          first.get(0),
          first.get(1),
          first.get(2),
          first.get(3),
          first.get(4),
          second.get(1),
          second.get(3),
          second.get(4),
          null);
      assertValues(session, firstAssigned, first.get(1), first.get(3));
      assertValues(session, firstUnassigned, first.get(4));
      assertValues(session, secondAssigned, second.get(1), second.get(3));
      assertValues(
          session,
          firstDestinations,
          ElementPosition.of(prefixOwner, 1),
          ElementPosition.of(prefixOwner, 2),
          ElementPosition.of(freeOwner, 0),
          ElementPosition.of(freeOwner, 1),
          ElementPosition.unassigned());
      assertValues(
          session,
          secondDestinations,
          ElementPosition.of(prefixOwner, 1),
          ElementPosition.of(prefixOwner, 2),
          ElementPosition.of(freeOwner, 0),
          ElementPosition.of(freeOwner, 1));

      NeighborhoodsBasedMoveRepository<Plan> repository =
          mock(NeighborhoodsBasedMoveRepository.class);
      doAnswer(
              invocation -> {
                session.update(invocation.getArgument(0));
                return null;
              })
          .when(repository)
          .update(any());
      scoreDirector.getNeighborhoodNotifier().setMoveRepository(repository);
      scoreDirector.getNeighborhoodNotifier().setTracking(true);

      scoreDirector.beforeVariableChanged(first.get(0), "basic");
      first.get(0).basic = 2;
      scoreDirector.afterVariableChanged(first.get(0), "basic");
      scoreDirector.beforeListVariableChanged(freeOwner, "first", 1, 1);
      scoreDirector.beforeListVariableElementAssigned(freeOwner, "first", first.get(4));
      freeOwner.first.add(first.get(4));
      scoreDirector.afterListVariableElementAssigned(freeOwner, "first", first.get(4));
      scoreDirector.afterListVariableChanged(freeOwner, "first", 1, 2);
      scoreDirector.triggerVariableListeners();
      session.settle();
      assertValues(
          session, genuine, first.get(0), first.get(1), first.get(2), first.get(3), first.get(4));
      assertValues(session, firstAssigned, first.get(1), first.get(3), first.get(4));
      assertValues(session, firstUnassigned);
      assertValues(session, secondAssigned, second.get(1), second.get(3));
      assertValues(
          session,
          firstDestinations,
          ElementPosition.of(prefixOwner, 1),
          ElementPosition.of(prefixOwner, 2),
          ElementPosition.of(freeOwner, 0),
          ElementPosition.of(freeOwner, 1),
          ElementPosition.of(freeOwner, 2),
          ElementPosition.unassigned());

      scoreDirector.beforeListVariableChanged(freeOwner, "second", 0, 1);
      scoreDirector.beforeListVariableElementUnassigned(freeOwner, "second", second.get(3));
      freeOwner.second.clear();
      scoreDirector.afterListVariableElementUnassigned(freeOwner, "second", second.get(3));
      scoreDirector.afterListVariableChanged(freeOwner, "second", 0, 0);
      scoreDirector.triggerVariableListeners();
      session.settle();
      assertValues(session, secondAssigned, second.get(1));
      assertValues(session, shadowOnly, second.get(1), second.get(3), second.get(4));
      assertValues(session, firstAssigned, first.get(1), first.get(3), first.get(4));
      assertValues(
          session,
          secondDestinations,
          ElementPosition.of(prefixOwner, 1),
          ElementPosition.of(prefixOwner, 2),
          ElementPosition.of(freeOwner, 0));
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"change", "changeAssigned", "swap", "assign", "unassign"})
  void listProvidersKeepGenuineElementsInPinnedListPositions(String providerType) {
    var metaModel = buildDescriptor().getMetaModel();
    var listModel = metaModel.genuineEntity(Owner.class).listVariable("first", FirstValue.class);
    MoveProvider<Plan> provider =
        switch (providerType) {
          case "change" -> new ListChangeMoveProvider<>(listModel);
          case "changeAssigned" -> new ListChangeMoveProvider<>(listModel, false);
          case "swap" -> new ListSwapMoveProvider<>(listModel);
          case "assign" -> new ListAssignMoveProvider<>(listModel);
          case "unassign" -> new ListUnassignMoveProvider<>(listModel);
          default -> throw new IllegalArgumentException(providerType);
        };
    var solution = problem();
    var prefixOwner = solution.owners.get(0);
    var pinnedOwner = solution.owners.get(1);
    var freeOwner = solution.owners.get(2);
    var incoming = solution.firstValues.get(4);
    var context = NeighborhoodTester.build(provider, metaModel).using(solution);
    var expectedMove =
        switch (providerType) {
          case "change", "changeAssigned" -> Moves.change(listModel, prefixOwner, 1, freeOwner, 0);
          case "swap" ->
              Moves.compose(
                  Moves.unassign(listModel, freeOwner, 0),
                  Moves.assign(listModel, incoming, freeOwner, 0));
          case "assign" -> Moves.assign(listModel, incoming, freeOwner, 0);
          case "unassign" -> Moves.unassign(listModel, freeOwner, 0);
          default -> throw new IllegalArgumentException(providerType);
        };
    context.producesAllOf(expectedMove);
    if (providerType.equals("change")) {
      context.producesAllOf(
          Moves.unassign(listModel, prefixOwner, 1),
          Moves.assign(listModel, incoming, prefixOwner, 1));
    }
    var moves = context.getMovesAsStream().limit(100).toList();
    assertThat(moves).isNotEmpty();
    for (var move : moves) {
      context
          .getMoveTestContext()
          .executeTemporarily(
              move,
              ignored -> {
                assertThat(prefixOwner.first.getFirst()).isSameAs(solution.firstValues.get(0));
                assertThat(pinnedOwner.first).containsExactly(solution.firstValues.get(2));
                assertThat(prefixOwner.second)
                    .containsExactly(solution.secondValues.get(0), solution.secondValues.get(1));
                assertThat(pinnedOwner.second).containsExactly(solution.secondValues.get(2));
                assertThat(freeOwner.second).containsExactly(solution.secondValues.get(3));
                assertThat(solution.firstValues.get(5).basic).isEqualTo(1);
              });
    }
  }

  @Test
  void basicNeighborhoodImprovesElementsInPinnedPrefixesAndPinnedOwners() {
    var phase =
        new LocalSearchPhaseConfig()
            .withLocalSearchType(LocalSearchType.HILL_CLIMBING)
            .withTerminationConfig(
                new TerminationConfig().withStepCountLimit(20).withBestScoreLimit("11"));
    phase.setNeighborhoodProviderClass(BasicNeighborhoodProvider.class);
    var config =
        new SolverConfig()
            .withSolutionClass(Plan.class)
            .withEntityClasses(Owner.class, FirstValue.class, SecondValue.class)
            .withEasyScoreCalculatorClass(BasicScoreCalculator.class)
            .withEnvironmentMode(EnvironmentMode.FULL_ASSERT)
            .withRandomSeed(37L)
            .withPreviewFeature(PreviewFeature.NEIGHBORHOODS)
            .withPhases(phase);
    var solved = SolverFactory.<Plan>create(config).buildSolver().solve(problem());
    assertThat(solved.score).isEqualTo(SimpleScore.of(11));
    assertThat(solved.score).isEqualTo(new BasicScoreCalculator().calculateScore(solved));
    assertThat(solved.firstValues.subList(0, 5))
        .allSatisfy(value -> assertThat(value.basic).isEqualTo(2));
    assertThat(solved.firstValues.get(5).basic).isEqualTo(1);
    assertThat(solved.owners.get(0).first)
        .containsExactly(solved.firstValues.get(0), solved.firstValues.get(1));
    assertThat(solved.owners.get(1).first).containsExactly(solved.firstValues.get(2));
    assertThat(solved.owners.get(2).first).containsExactly(solved.firstValues.get(3));
  }

  private static SolutionDescriptor<Plan> buildDescriptor() {
    return SolutionDescriptor.buildSolutionDescriptor(
        Plan.class, Owner.class, FirstValue.class, SecondValue.class);
  }

  private static Plan problem() {
    var solution = new Plan();
    for (int i = 0; i < 6; i++) {
      var value = new FirstValue();
      value.id = "first-" + i;
      value.pinned = i == 5;
      solution.firstValues.add(value);
    }
    for (int i = 0; i < 5; i++) {
      var value = new SecondValue();
      value.id = "second-" + i;
      solution.secondValues.add(value);
    }
    for (int i = 0; i < 3; i++) {
      var owner = new Owner();
      owner.id = "owner-" + i;
      owner.pinned = i == 1;
      owner.firstPin = i == 0 ? 1 : 0;
      owner.secondPin = i == 0 ? 1 : 0;
      if (i == 0) {
        owner.first.addAll(solution.firstValues.subList(0, 2));
        owner.second.addAll(solution.secondValues.subList(0, 2));
      } else {
        owner.first.add(solution.firstValues.get(i + 1));
        owner.second.add(solution.secondValues.get(i + 1));
      }
      solution.owners.add(owner);
    }
    return solution;
  }

  private static <A> UniLeftDataset<Plan, A> dataset(UniEnumeratingStream<Plan, A> stream) {
    return ((AbstractUniEnumeratingStream<Plan, A>) stream).asCachedDataset();
  }

  private static void assertValues(
      DefaultNeighborhoodSession<Plan> session, UniLeftDataset<Plan, ?> dataset, Object... values) {
    assertThat(session.getLeftDatasetInstance(dataset).iterator())
        .toIterable()
        .map(tuple -> (Object) tuple.getA())
        .containsExactlyInAnyOrder(values);
  }

  public interface Value {}

  @PlanningEntity
  public static class FirstValue implements Value {
    @PlanningId public String id;
    @PlanningPin public boolean pinned;

    @PlanningVariable(valueRangeProviderRefs = "basicRange")
    public Integer basic = 1;
  }

  @PlanningEntity
  public static class SecondValue implements Value {
    @PlanningId public String id;

    @IndexShadowVariable(sourceVariableName = "second")
    public Integer index;
  }

  @PlanningEntity
  public static class Owner {
    @PlanningId public String id;
    @PlanningPin public boolean pinned;

    @PlanningPinToIndex(variableName = "first")
    public int firstPin;

    @PlanningPinToIndex(variableName = "second")
    public int secondPin;

    @PlanningListVariable(valueRangeProviderRefs = "firstRange", allowsUnassignedValues = true)
    public List<FirstValue> first = new ArrayList<>();

    @PlanningListVariable(valueRangeProviderRefs = "secondRange", allowsUnassignedValues = true)
    public List<SecondValue> second = new ArrayList<>();
  }

  @PlanningSolution
  public static class Plan {
    @PlanningEntityCollectionProperty public List<Owner> owners = new ArrayList<>();

    @PlanningEntityCollectionProperty
    @ValueRangeProvider(id = "firstRange")
    public List<FirstValue> firstValues = new ArrayList<>();

    @PlanningEntityCollectionProperty
    @ValueRangeProvider(id = "secondRange")
    public List<SecondValue> secondValues = new ArrayList<>();

    @ProblemFactCollectionProperty
    @ValueRangeProvider(id = "basicRange")
    public List<Integer> basicRange = List.of(1, 2);

    @PlanningScore public SimpleScore score;
  }

  public static final class BasicNeighborhoodProvider implements NeighborhoodProvider<Plan> {
    @Override
    public Neighborhood defineNeighborhood(NeighborhoodBuilder<Plan> builder) {
      var basicModel =
          builder
              .getSolutionMetaModel()
              .genuineEntity(FirstValue.class)
              .basicVariable("basic", Integer.class);
      return builder.add(new ChangeMoveProvider<>(basicModel)).build();
    }
  }

  public static final class BasicScoreCalculator implements EasyScoreCalculator<Plan, SimpleScore> {
    @Override
    public SimpleScore calculateScore(Plan solution) {
      return SimpleScore.of(solution.firstValues.stream().mapToInt(value -> value.basic).sum());
    }
  }
}
