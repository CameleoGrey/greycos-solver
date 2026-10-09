package greycos.solver.core.impl.constructionheuristic.placer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.random.RandomGenerator;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.valuerange.ValueRange;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.cotwin.variable.PlanningVariable;
import greycos.solver.core.api.cotwin.variable.ShadowSources;
import greycos.solver.core.api.cotwin.variable.ShadowVariable;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.score.director.ValueRangeManager;
import greycos.solver.core.impl.score.director.stream.BavetConstraintStreamScoreDirectorFactory;
import greycos.solver.core.testcotwin.list.pinned.TestdataPinnedListSolution;
import greycos.solver.core.testcotwin.list.pinned.TestdataPinnedListValue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RandomAssignmentMoveTest {

  @Test
  void restoresInitiallyIncompleteAssignmentsAndRetainsBavetSession() {
    var solution = new NumberSolution();
    var entity = new NumberEntity();
    solution.entities = List.of(entity);
    try (var director = numberFactory().createScoreDirectorBuilder().build()) {
      director.setWorkingSolution(solution);
      var baselineScore = director.calculateScore();
      var session = director.getSession();
      var move = RandomAssignmentMove.sample(director, new Random(7), () -> false);

      assertThat(entity.getValue()).isNull();
      var candidateScore =
          director.executeTemporaryMoveWithScore(
              move, (view, score) -> assertThat(entity.getValue()).isIn(entity.range), true);

      assertThat(candidateScore.isFullyAssigned()).isTrue();
      assertThatCode(() -> move.verifyRestored(director, baselineScore)).doesNotThrowAnyException();
      assertThat(entity.getValue()).isNull();
      assertThat(director.getSession()).isSameAs(session);
    }
  }

  @Test
  void restoredImmutableValuesMayBeEqualWithoutSharingIdentity() {
    var solution = new NumberSolution();
    var entity = new NumberEntity();
    entity.setValue(BigInteger.valueOf(1000));
    solution.entities = List.of(entity);
    try (var director = numberFactory().createScoreDirectorBuilder().build()) {
      director.setWorkingSolution(solution);
      var baselineScore = director.calculateScore();
      var move = RandomAssignmentMove.sample(director, new Random(0), () -> false);

      assertThat(entity.getValue()).isEqualTo(entity.getValue()).isNotSameAs(entity.getValue());
      director.executeTemporaryMove(move, true);

      assertThatCode(() -> move.verifyRestored(director, baselineScore)).doesNotThrowAnyException();
    }
  }

  @Test
  void structuralCycleIsUndoneIncludingShadowsAndNativeScore() {
    var factory =
        new BavetConstraintStreamScoreDirectorFactory<CycleSolution, SimpleScore>(
            SolutionDescriptor.buildSolutionDescriptor(CycleSolution.class, CycleEntity.class),
            constraints ->
                new Constraint[] {
                  constraints
                      .forEachIncludingUnassigned(CycleEntity.class)
                      .penalize(SimpleScore.ONE, entity -> entity.depth)
                      .asConstraint("depth")
                },
            EnvironmentMode.NO_ASSERT);
    var solution = new CycleSolution();
    var root = new CycleEntity();
    var child = new CycleEntity();
    child.previous = root;
    solution.entities = new ArrayList<>(List.of(root, child));
    try (var director = factory.createScoreDirectorBuilder().build()) {
      director.setWorkingSolution(solution);
      var baselineScore = director.calculateScore();
      var session = director.getSession();
      var move =
          RandomAssignmentMove.sample(
              director,
              new Random() {
                @Override
                public long nextLong(long bound) {
                  return bound - 1; // Every previous points to the last entity, including itself.
                }
              },
              () -> false);

      var candidateScore = director.executeTemporaryMove(move, false);

      assertThat(candidateScore.isStructurallyFlawed()).isTrue();
      assertThatCode(() -> move.verifyRestored(director, baselineScore)).doesNotThrowAnyException();
      assertThat(root.previous).isNull();
      assertThat(child.previous).isSameAs(root);
      assertThat(root.depth).isZero();
      assertThat(child.depth).isEqualTo(1);
      assertThat(director.getSession()).isSameAs(session);
    }
  }

  @Test
  void singletonInitializationDoesNotConsumeRandomNumbers() {
    var solution = new NumberSolution();
    var entity = new NumberEntity();
    entity.range = List.of(BigInteger.valueOf(1000));
    solution.entities = List.of(entity);
    try (var director = numberFactory().createScoreDirectorBuilder().build()) {
      director.setWorkingSolution(solution);
      director.calculateScore();
      var random = mock(RandomGenerator.class);
      var move = RandomAssignmentMove.sample(director, random, () -> false);

      director.executeMove(move);

      assertThat(entity.getValue()).isEqualTo(BigInteger.valueOf(1000));
      verifyNoInteractions(random);
    }
  }

  @Test
  void emptyBasicRangeFailsBeforeMutation() {
    var solution = new NumberSolution();
    var entity = new NumberEntity();
    entity.range = List.of();
    solution.entities = List.of(entity);
    try (var director = numberFactory().createScoreDirectorBuilder().build()) {
      director.setWorkingSolution(solution);
      director.calculateScore();

      assertThatThrownBy(() -> RandomAssignmentMove.sample(director, new Random(0), () -> false))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContainingAll(
              "nonempty finite value range",
              "NumberEntity.value",
              "entity (NumberEntity@",
              "has size (0)");
      assertThat(entity.getValue()).isNull();
    }
  }

  @Test
  void cancellationDuringDetachedSamplingDoesNotChangeAnyAssignments() {
    var solution = new NumberSolution();
    var first = new NumberEntity();
    var second = new NumberEntity();
    first.setValue(BigInteger.valueOf(1000));
    solution.entities = List.of(first, second);
    try (var director = numberFactory().createScoreDirectorBuilder().build()) {
      director.setWorkingSolution(solution);
      var baselineScore = director.calculateScore();
      var checks = new AtomicInteger();

      assertThat(
              RandomAssignmentMove.sample(
                  director, new Random(3), () -> checks.incrementAndGet() >= 4))
          .isNull();

      assertThat(first.getValue()).isEqualTo(BigInteger.valueOf(1000));
      assertThat(second.getValue()).isNull();
      assertThat(director.calculateScore()).isEqualTo(baselineScore);
    }
  }

  @Test
  void requiredListValueWithoutMovableDestinationFailsBeforeMutation() {
    var solution = TestdataPinnedListSolution.generateUninitializedSolution(1, 1);
    solution.getEntityList().getFirst().setPinned(true);
    try (var director = listFactory().createScoreDirectorBuilder().build()) {
      director.setWorkingSolution(solution);
      var baselineScore = director.calculateScore();

      assertThatThrownBy(() -> RandomAssignmentMove.sample(director, new Random(0), () -> false))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContainingAll(
              "no compatible movable owner",
              "TestdataPinnedListEntity.valueList",
              "among (1) owners",
              "allowsUnassignedValues is (false)",
              "\nMaybe");
      assertThat(solution.getEntityList().getFirst().getValueList()).isEmpty();
      assertThat(director.calculateScore()).isEqualTo(baselineScore);
    }
  }

  @Test
  void duplicateListMembershipIsRejectedWithoutRepairingInput() {
    var solution = TestdataPinnedListSolution.generateUninitializedSolution(1, 1);
    try (var director = listFactory().createScoreDirectorBuilder().build()) {
      director.setWorkingSolution(solution);
      director.calculateScore();
      var value = solution.getValueList().getFirst();
      var list = solution.getEntityList().getFirst().getValueList();
      // Corrupt the input after score-director setup to exercise the sampler's own preflight.
      list.add(value);
      list.add(value);

      assertThatThrownBy(() -> RandomAssignmentMove.sample(director, new Random(0), () -> false))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContainingAll(
              "noncanonical, duplicate or out-of-range",
              "at index (1)",
              "TestdataPinnedListEntity.valueList",
              "canonicalValueId (0)",
              "alreadyAssigned (true)",
              "rangeSize (1)",
              "\nMaybe");
      assertThat(list).hasSize(2);
      assertThat(list.get(0)).isSameAs(value);
      assertThat(list.get(1)).isSameAs(value);
    }
  }

  @ParameterizedTest
  @ValueSource(longs = {-1L, 2147483648L})
  void invalidListUniverseSizeIncludesVariableAndActualSize(long declaredSize) {
    var solution = TestdataPinnedListSolution.generateUninitializedSolution(1, 1);
    var director =
        malformedListDirector(solution, declaredSize, new ArrayList<>(solution.getValueList()));
    var random = mock(RandomGenerator.class);

    assertThatThrownBy(() -> RandomAssignmentMove.sample(director, random, () -> false))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContainingAll(
            "TestdataPinnedListEntity.valueList",
            "size in [0, 2147483647]",
            "has size (" + declaredSize + ")");

    assertThat(solution.getEntityList().getFirst().getValueList()).isEmpty();
    verifyNoInteractions(random);
  }

  @ParameterizedTest
  @ValueSource(longs = {0L, 2L})
  void inconsistentListUniverseSizeIncludesObservedCount(long declaredSize) {
    var solution = TestdataPinnedListSolution.generateUninitializedSolution(1, 1);
    var director =
        malformedListDirector(solution, declaredSize, new ArrayList<>(solution.getValueList()));
    var random = mock(RandomGenerator.class);

    assertThatThrownBy(() -> RandomAssignmentMove.sample(director, random, () -> false))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContainingAll(
            "TestdataPinnedListEntity.valueList", "declared size (" + declaredSize + ")", "\nMaybe")
        .hasMessageContaining(declaredSize == 0 ? "at least (1) values" : "ended after (1) values");

    assertThat(solution.getEntityList().getFirst().getValueList()).isEmpty();
    verifyNoInteractions(random);
  }

  @Test
  void incompatibleUniverseValueIncludesActualValueTypeAndIndex() {
    var solution = TestdataPinnedListSolution.generateUninitializedSolution(1, 1);
    var director = malformedListDirector(solution, 1, List.of("wrong list value"));

    assertThatThrownBy(() -> RandomAssignmentMove.sample(director, new Random(0), () -> false))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContainingAll(
            "wrong list value",
            "universe index (0)",
            "TestdataPinnedListEntity.valueList",
            "expectedType (" + TestdataPinnedListValue.class.getName() + ")",
            "declared size (1)",
            "\nMaybe");
  }

  @Test
  void invalidBasicValueIncludesCurrentValueRangeAndMovableState() {
    var solution = new NumberSolution();
    var entity = new NumberEntity();
    solution.entities = List.of(entity);
    try (var director = numberFactory().createScoreDirectorBuilder().build()) {
      director.setWorkingSolution(solution);
      director.calculateScore();
      // Corrupt after native initialization to exercise the sampler's own validation.
      entity.setValue(BigInteger.valueOf(999));

      assertThatThrownBy(() -> RandomAssignmentMove.sample(director, new Random(0), () -> false))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContainingAll(
              "invalid initial value (999",
              "NumberEntity.value",
              "movable (true)",
              "allowsUnassigned (false)",
              "missingRequiredValue (false)",
              "rangeSize (2)",
              "\nMaybe");
      assertThat(entity.getValue()).isEqualTo(BigInteger.valueOf(999));
    }
  }

  @Test
  void failedBasicRestorationIncludesExpectedAndActualValues() {
    var solution = new NumberSolution();
    var entity = new NumberEntity();
    entity.setValue(BigInteger.valueOf(1000));
    solution.entities = List.of(entity);
    try (var director = numberFactory().createScoreDirectorBuilder().build()) {
      director.setWorkingSolution(solution);
      var baselineScore = director.calculateScore();
      var move = RandomAssignmentMove.sample(director, new Random(0), () -> false);
      entity.setValue(BigInteger.valueOf(1001));

      assertThatThrownBy(() -> move.verifyRestored(director, baselineScore))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContainingAll(
              "NumberEntity.value",
              "expected value (1000",
              "actual value (1001",
              "entity (NumberEntity@",
              "\nMaybe");
    }
  }

  @Test
  void failedListRestorationIncludesOwnerSizesAndExactIdentityDifference() {
    var solution = TestdataPinnedListSolution.generateUninitializedSolution(2, 1);
    var owner = solution.getEntityList().getFirst();
    var first = solution.getValueList().get(0);
    var second = solution.getValueList().get(1);
    owner.getValueList().addAll(solution.getValueList());
    try (var director = listFactory().createScoreDirectorBuilder().build()) {
      director.setWorkingSolution(solution);
      var baselineScore = director.calculateScore();
      var move = RandomAssignmentMove.sample(director, new Random(0), () -> false);
      owner.getValueList().removeLast();

      assertThatThrownBy(() -> move.verifyRestored(director, baselineScore))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContainingAll(
              "TestdataPinnedListEntity.valueList",
              "owner (TestdataPinnedListEntity@",
              "expected list size (2)",
              "actual list size (1)");

      owner.getValueList().clear();
      owner.getValueList().addAll(List.of(second, first));
      assertThatThrownBy(() -> move.verifyRestored(director, baselineScore))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContainingAll(
              "TestdataPinnedListEntity.valueList",
              "at index (0)",
              "expected value (TestdataPinnedListValue@"
                  + Integer.toHexString(System.identityHashCode(first)),
              "actual value (TestdataPinnedListValue@"
                  + Integer.toHexString(System.identityHashCode(second)),
              "\nMaybe");
    }
  }

  @Test
  void failedScoreRestorationIncludesBothScoresAndShadowStatusWithoutPrintingSolution() {
    var solution =
        new NumberSolution() {
          @Override
          public String toString() {
            throw new AssertionError("Diagnostics must not stringify the solution graph.");
          }
        };
    solution.entities = List.of(new NumberEntity());
    try (var director = numberFactory().createScoreDirectorBuilder().build()) {
      director.setWorkingSolution(solution);
      var actualScore = director.calculateScore();
      var move = RandomAssignmentMove.sample(director, new Random(0), () -> false);
      var wrongExpected = InnerScore.fullyAssigned(SimpleScore.of(77));

      assertThatThrownBy(() -> move.verifyRestored(director, wrongExpected))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContainingAll(
              "expected score (" + wrongExpected + ")",
              "restored score (" + actualScore + ")",
              "lastVariableUpdateSuccessful (true)",
              "structurallyFlawed (false)",
              "\nMaybe");
    }
  }

  @SuppressWarnings("unchecked")
  private static InnerScoreDirector<TestdataPinnedListSolution, SimpleScore> malformedListDirector(
      TestdataPinnedListSolution solution, long size, List<Object> contents) {
    var descriptor = TestdataPinnedListSolution.buildSolutionDescriptor();
    var variable = descriptor.getListVariableDescriptor();
    var director =
        (InnerScoreDirector<TestdataPinnedListSolution, SimpleScore>)
            mock(InnerScoreDirector.class);
    var manager = (ValueRangeManager<TestdataPinnedListSolution>) mock(ValueRangeManager.class);
    var range = (ValueRange<Object>) mock(ValueRange.class);
    when(director.getWorkingSolution()).thenReturn(solution);
    when(director.getSolutionDescriptor()).thenReturn(descriptor);
    when(director.isLastVariableUpdateSuccessful()).thenReturn(true);
    when(director.getValueRangeManager()).thenReturn(manager);
    when(manager.<Object>getFromSolution(variable.getValueRangeDescriptor())).thenReturn(range);
    when(range.getSize()).thenReturn(size);
    when(range.createOriginalIterator()).thenAnswer(invocation -> contents.iterator());
    return director;
  }

  private static BavetConstraintStreamScoreDirectorFactory<NumberSolution, SimpleScore>
      numberFactory() {
    return new BavetConstraintStreamScoreDirectorFactory<>(
        SolutionDescriptor.buildSolutionDescriptor(NumberSolution.class, NumberEntity.class),
        constraints ->
            new Constraint[] {
              constraints
                  .forEachIncludingUnassigned(NumberEntity.class)
                  .penalize(SimpleScore.ONE)
                  .asConstraint("entities")
            },
        EnvironmentMode.NO_ASSERT);
  }

  private static BavetConstraintStreamScoreDirectorFactory<TestdataPinnedListSolution, SimpleScore>
      listFactory() {
    return new BavetConstraintStreamScoreDirectorFactory<>(
        TestdataPinnedListSolution.buildSolutionDescriptor(),
        constraints ->
            new Constraint[] {
              constraints
                  .forEachIncludingUnassigned(TestdataPinnedListValue.class)
                  .penalize(SimpleScore.ONE)
                  .asConstraint("values")
            },
        EnvironmentMode.NO_ASSERT);
  }

  @PlanningSolution
  public static class NumberSolution {
    @PlanningEntityCollectionProperty public List<NumberEntity> entities;
    @PlanningScore public SimpleScore score;
  }

  @PlanningEntity
  public static class NumberEntity {
    private BigInteger value;

    @ValueRangeProvider(id = "range")
    public List<BigInteger> range = List.of(BigInteger.valueOf(1000), BigInteger.valueOf(1001));

    @PlanningVariable(valueRangeProviderRefs = "range")
    public BigInteger getValue() {
      return value == null ? null : new BigInteger(value.toString());
    }

    public void setValue(BigInteger value) {
      this.value = value;
    }
  }

  @PlanningSolution
  public static class CycleSolution {
    @PlanningEntityCollectionProperty @ValueRangeProvider public List<CycleEntity> entities;
    @PlanningScore public SimpleScore score;
  }

  @PlanningEntity
  public static class CycleEntity {
    @PlanningVariable(allowsUnassigned = true)
    public CycleEntity previous;

    @ShadowVariable(supplierName = "calculateDepth")
    public Integer depth;

    @ShadowSources("previous.depth")
    public Integer calculateDepth() {
      return previous == null ? 0 : previous.depth == null ? null : previous.depth + 1;
    }
  }
}
