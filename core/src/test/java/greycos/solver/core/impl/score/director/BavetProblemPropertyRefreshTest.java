package greycos.solver.core.impl.score.director;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.entity.PlanningPin;
import greycos.solver.core.api.cotwin.lookup.PlanningId;
import greycos.solver.core.api.cotwin.solution.ConstraintWeightOverrides;
import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.solution.ProblemFactCollectionProperty;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.cotwin.variable.PlanningListVariable;
import greycos.solver.core.api.cotwin.variable.PlanningVariable;
import greycos.solver.core.api.cotwin.variable.ShadowSources;
import greycos.solver.core.api.cotwin.variable.ShadowVariable;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.calculator.IncrementalScoreCalculator;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.api.score.stream.ConstraintCollectors;
import greycos.solver.core.api.score.stream.ConstraintProvider;
import greycos.solver.core.api.score.stream.Joiners;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import greycos.solver.core.impl.neighborhood.NeighborhoodsBasedMoveRepository;
import greycos.solver.core.impl.neighborhood.stream.DefaultMoveStreamFactory;
import greycos.solver.core.impl.score.director.incremental.IncrementalScoreDirectorFactory;
import greycos.solver.core.impl.score.director.stream.BavetConstraintStreamScoreDirectorFactory;
import greycos.solver.core.impl.solver.change.DefaultProblemChangeDirector;
import greycos.solver.core.preview.api.move.builtin.ChangeMoveProvider;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Timeout(20)
class BavetProblemPropertyRefreshTest {
  private static BavetConstraintStreamScoreDirectorFactory<PropertySolution, SimpleScore> factory(
      ConstraintProvider provider) {
    return new BavetConstraintStreamScoreDirectorFactory<>(
        SolutionDescriptor.buildSolutionDescriptor(PropertySolution.class, PropertyEntity.class),
        provider,
        EnvironmentMode.NO_ASSERT);
  }

  private static ConstraintProvider weightProvider(AtomicInteger calls) {
    return factory ->
        new Constraint[] {
          factory
              .forEach(PropertyEntity.class)
              .penalize(
                  SimpleScore.ONE,
                  entity -> {
                    calls.incrementAndGet();
                    return entity.weight;
                  })
              .asConstraint("weight")
        };
  }

  @Test
  void twentyPropertyChangesRetainNetworkAndOnlyReweighChangedEntities() {
    var calls = new AtomicInteger();
    var factory = factory(weightProvider(calls));
    var solution = PropertySolution.generate(1000);
    try (var director = factory.createScoreDirectorBuilder().withLookUpEnabled(true).build()) {
      director.setWorkingSolution(solution);
      director.calculateScore();
      var session = director.getSession();
      var revision = director.getWorkingEntityListRevision();
      var changes = new DefaultProblemChangeDirector<>(director);
      calls.set(0);
      for (var i = 0; i < 20; i++) {
        changes.changeProblemProperty(solution.entities.get(i), entity -> entity.weight++);
        changes.updateShadowVariables();
        director.calculateScore();
        assertThat(director.getSession()).isSameAs(session);
      }
      assertThat(calls).hasValue(20);
      assertThat(solution.score).isEqualTo(SimpleScore.of(-1020));
      assertThat(solution.score).isEqualTo(solution.replayWeight());
      assertThat(director.getWorkingEntityListRevision()).isEqualTo(revision + 20);
      try (var independent = factory.buildScoreDirector()) {
        independent.setWorkingSolution(solution);
        assertThat(independent.calculateScore()).isEqualTo(director.calculateScore());
      }
    }
  }

  @Test
  void joinsGroupsAndMutableHashKeysAreRetractedBeforeMutation() {
    var factory =
        factory(
            constraints ->
                new Constraint[] {
                  constraints
                      .forEach(PropertyEntity.class)
                      .join(PropertyEntity.class, Joiners.equal(entity -> entity.key))
                      .penalize(SimpleScore.ONE)
                      .asConstraint("join"),
                  constraints
                      .forEach(PropertyEntity.class)
                      .groupBy(entity -> entity.key, ConstraintCollectors.count())
                      .penalize(SimpleScore.ONE, (key, count) -> key.code * count)
                      .asConstraint("group")
                });
    var solution = PropertySolution.generate(3);
    try (var director = factory.createScoreDirectorBuilder().withLookUpEnabled(true).build()) {
      director.setWorkingSolution(solution);
      var session = director.getSession();
      var changes = new DefaultProblemChangeDirector<>(director);
      changes.changeProblemProperty(solution.entities.getFirst(), entity -> entity.key.code = 4);
      changes.updateShadowVariables();
      assertThat(director.getSession()).isSameAs(session);
      assertThat(director.calculateScore().raw()).isEqualTo(solution.replayGroups());
      // Merge a group using a replacement key, then split it again.
      changes.changeProblemProperty(
          solution.entities.getLast(), entity -> entity.key = new MutableKey(2));
      assertThat(director.calculateScore().raw()).isEqualTo(solution.replayGroups());
      changes.changeProblemProperty(
          solution.entities.getLast(), entity -> entity.key = new MutableKey(8));
      assertThat(director.calculateScore().raw()).isEqualTo(solution.replayGroups());
      try (var independent = factory.buildScoreDirector()) {
        independent.setWorkingSolution(solution);
        assertThat(director.calculateScore()).isEqualTo(independent.calculateScore());
      }
    }
  }

  @Test
  void changedPlanningIdRefreshesLookupAtBarrier() {
    var solution = PropertySolution.generate(2);
    try (var director =
        factory(weightProvider(new AtomicInteger()))
            .createScoreDirectorBuilder()
            .withLookUpEnabled(true)
            .build()) {
      director.setWorkingSolution(solution);
      var session = director.getSession();
      var changes = new DefaultProblemChangeDirector<>(director);
      changes.changeProblemProperty(solution.entities.getFirst(), entity -> entity.id = "new");
      changes.updateShadowVariables();
      var external = new PropertyEntity("new", 1);
      assertThat(director.lookUpWorkingObject(external)).isSameAs(solution.entities.getFirst());
      assertThat(director.lookUpWorkingObjectOrReturnNull(new PropertyEntity("0", 1))).isNull();
      assertThat(director.getSession()).isSameAs(session);
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void invalidPlanningIdStillFailsMetadataValidation(boolean nullId) {
    var solution = PropertySolution.generate(2);
    try (var director =
        factory(weightProvider(new AtomicInteger()))
            .createScoreDirectorBuilder()
            .withLookUpEnabled(true)
            .build()) {
      director.setWorkingSolution(solution);
      var changes = new DefaultProblemChangeDirector<>(director);
      changes.changeProblemProperty(
          solution.entities.getFirst(), entity -> entity.id = nullId ? null : "1");
      assertThatThrownBy(changes::updateShadowVariables)
          .isInstanceOf(nullId ? IllegalArgumentException.class : IllegalStateException.class)
          .hasMessageContaining(nullId ? "must not be null" : "same planningId");
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void dynamicSolutionAndEntityRangesAreValidated(boolean entityRange) {
    var solution = PropertySolution.generate(1);
    solution.entities.getFirst().optional = 1;
    try (var director =
        factory(weightProvider(new AtomicInteger()))
            .createScoreDirectorBuilder()
            .withLookUpEnabled(true)
            .build()) {
      director.setWorkingSolution(solution);
      var changes = new DefaultProblemChangeDirector<>(director);
      changes.changeProblemProperty(
          solution.entities.getFirst(),
          entity -> {
            if (entityRange) entity.localRangeRestricted = true;
            else entity.solutionRangeRestricted = true;
          });
      assertThatThrownBy(changes::updateShadowVariables)
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("outside of the related value range");
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void pinningStillValidatesRequiredNullAndAllowsOptionalNull(boolean requiredNull) {
    var solution = PropertySolution.generate(1);
    if (requiredNull) solution.entities.getFirst().value = null;
    else solution.entities.getFirst().optional = null;
    try (var director =
        factory(weightProvider(new AtomicInteger()))
            .createScoreDirectorBuilder()
            .withLookUpEnabled(true)
            .build()) {
      director.setWorkingSolution(solution);
      var session = director.getSession();
      var changes = new DefaultProblemChangeDirector<>(director);
      changes.changeProblemProperty(solution.entities.getFirst(), entity -> entity.pinned = true);
      if (requiredNull) {
        assertThatThrownBy(changes::updateShadowVariables)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("pinned to null");
      } else {
        changes.updateShadowVariables();
        assertThat(director.getWorkingInitScore()).isZero();
        assertThat(director.getSession()).isSameAs(session);
        assertThat(director.calculateScore().raw()).isEqualTo(solution.replayWeight());
      }
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void structuralDirtyWinsInEitherOrder(boolean structureFirst) {
    var solution = PropertySolution.generate(2);
    try (var director =
        factory(weightProvider(new AtomicInteger()))
            .createScoreDirectorBuilder()
            .withLookUpEnabled(true)
            .build()) {
      director.setWorkingSolution(solution);
      var session = director.getSession();
      var changes = new DefaultProblemChangeDirector<>(director);
      if (structureFirst) changes.addEntity(new PropertyEntity("added", 4), solution.entities::add);
      changes.changeProblemProperty(solution.entities.getFirst(), entity -> entity.weight++);
      if (!structureFirst)
        changes.addEntity(new PropertyEntity("added", 4), solution.entities::add);
      changes.updateShadowVariables();
      assertThat(director.getSession()).isNotSameAs(session);
      assertThat(director.getWorkingGenuineEntityCount()).isEqualTo(3);
      assertThat(director.calculateScore().raw()).isEqualTo(solution.replayWeight());
    }
  }

  @Test
  void nestedStructuralCallbackDoesNotInsertIntoObsoleteSession() {
    var solution = PropertySolution.generate(2);
    try (var director =
        factory(weightProvider(new AtomicInteger()))
            .createScoreDirectorBuilder()
            .withLookUpEnabled(true)
            .build()) {
      director.setWorkingSolution(solution);
      var session = director.getSession();
      var changes = new DefaultProblemChangeDirector<>(director);
      changes.changeProblemProperty(
          solution.entities.getFirst(),
          entity -> {
            entity.weight++;
            changes.addEntity(new PropertyEntity("added", 4), solution.entities::add);
            changes.updateShadowVariables();
          });
      changes.updateShadowVariables();
      assertThat(director.getSession()).isNotSameAs(session);
      assertThat(director.calculateScore().raw()).isEqualTo(solution.replayWeight());
    }
  }

  @Test
  void factPropertyRetainsFullRefresh() {
    var solution = PropertySolution.generate(1);
    solution.facts.add(new Fact());
    try (var director =
        factory(
                constraints ->
                    new Constraint[] {
                      constraints
                          .forEach(Fact.class)
                          .penalize(SimpleScore.ONE, fact -> fact.weight)
                          .asConstraint("fact")
                    })
            .createScoreDirectorBuilder()
            .withLookUpEnabled(true)
            .build()) {
      director.setWorkingSolution(solution);
      var session = director.getSession();
      new DefaultProblemChangeDirector<>(director)
          .changeProblemProperty(solution.facts.getFirst(), fact -> fact.weight = 5);
      assertThat(director.calculateScore().raw()).isEqualTo(SimpleScore.of(-5));
      assertThat(director.getSession()).isNotSameAs(session);
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 2, 3, 4})
  void precomputeOfEveryArityRetainsFullRefresh(int arity) {
    var solution = PropertySolution.generate(2);
    var factory =
        factory(
            constraints -> {
              Constraint precomputed =
                  switch (arity) {
                    case 1 ->
                        constraints
                            .precompute(data -> data.forEachUnfiltered(PropertyEntity.class))
                            .penalize(SimpleScore.ONE)
                            .asConstraint("precompute");
                    case 2 ->
                        constraints
                            .precompute(
                                data ->
                                    data.forEachUnfiltered(PropertyEntity.class)
                                        .expand(entity -> entity.weight))
                            .penalize(SimpleScore.ONE)
                            .asConstraint("precompute");
                    case 3 ->
                        constraints
                            .precompute(
                                data ->
                                    data.forEachUnfiltered(PropertyEntity.class)
                                        .expand(entity -> entity.weight, entity -> entity.id))
                            .penalize(SimpleScore.ONE)
                            .asConstraint("precompute");
                    case 4 ->
                        constraints
                            .precompute(
                                data ->
                                    data.forEachUnfiltered(PropertyEntity.class)
                                        .expand(
                                            entity -> entity.weight,
                                            entity -> entity.id,
                                            entity -> entity.key))
                            .penalize(SimpleScore.ONE)
                            .asConstraint("precompute");
                    default -> throw new IllegalArgumentException();
                  };
              return new Constraint[] {precomputed};
            });
    try (var director = factory.createScoreDirectorBuilder().withLookUpEnabled(true).build()) {
      director.setWorkingSolution(solution);
      var session = director.getSession();
      new DefaultProblemChangeDirector<>(director)
          .changeProblemProperty(solution.entities.getFirst(), entity -> entity.weight++);
      director.updateShadowVariables();
      assertThat(director.getSession()).isNotSameAs(session);
    }
  }

  @Test
  void constraintWeightSupplierRetainsFullRefresh() {
    var solution = new WeightedSolution();
    solution.entities.add(new PropertyEntity("0", 1));
    solution.constraintWeightOverrides =
        ConstraintWeightOverrides.of(Map.of("weight", SimpleScore.of(3)));
    var factory =
        new BavetConstraintStreamScoreDirectorFactory<WeightedSolution, SimpleScore>(
            SolutionDescriptor.buildSolutionDescriptor(
                WeightedSolution.class, PropertyEntity.class),
            weightProvider(new AtomicInteger()),
            EnvironmentMode.NO_ASSERT);
    try (var director = factory.createScoreDirectorBuilder().withLookUpEnabled(true).build()) {
      director.setWorkingSolution(solution);
      var session = director.getSession();
      new DefaultProblemChangeDirector<>(director)
          .changeProblemProperty(solution.entities.getFirst(), entity -> entity.weight++);
      assertThat(director.calculateScore().raw()).isEqualTo(SimpleScore.of(-6));
      assertThat(director.getSession()).isNotSameAs(session);
    }
  }

  @Test
  void listVariableWithoutShadowsRetainsFullRefresh() {
    var solution = new ListPropertySolution();
    var descriptor =
        SolutionDescriptor.buildSolutionDescriptor(
            ListPropertySolution.class, ListPropertyEntity.class);
    assertThat(
            descriptor
                .findEntityDescriptorOrFail(ListPropertyEntity.class)
                .getShadowVariableDescriptors())
        .isEmpty();
    var factory =
        new BavetConstraintStreamScoreDirectorFactory<ListPropertySolution, SimpleScore>(
            descriptor,
            constraints ->
                new Constraint[] {
                  constraints
                      .forEach(ListPropertyEntity.class)
                      .penalize(SimpleScore.ONE, entity -> entity.weight)
                      .asConstraint("list weight")
                },
            EnvironmentMode.NO_ASSERT);
    try (var director = factory.createScoreDirectorBuilder().withLookUpEnabled(true).build()) {
      director.setWorkingSolution(solution);
      var session = director.getSession();
      new DefaultProblemChangeDirector<>(director)
          .changeProblemProperty(solution.entities.getFirst(), entity -> entity.weight = 2);
      director.updateShadowVariables();
      assertThat(director.getSession()).isNotSameAs(session);
      assertThat(director.calculateScore().raw()).isEqualTo(SimpleScore.of(-2));
      try (var independent = factory.buildScoreDirector()) {
        independent.setWorkingSolution(solution);
        assertThat(director.calculateScore()).isEqualTo(independent.calculateScore());
      }
    }
  }

  @Test
  void attachedNeighborhoodRepositoryRetainsFullRefresh() {
    var solution = PropertySolution.generate(2);
    var factory = factory(weightProvider(new AtomicInteger()));
    var descriptor = factory.getSolutionDescriptor();
    var variable =
        descriptor
            .getMetaModel()
            .genuineEntity(PropertyEntity.class)
            .basicVariable("value", Integer.class);
    var repository =
        new NeighborhoodsBasedMoveRepository<>(
            new DefaultMoveStreamFactory<>(descriptor, EnvironmentMode.NO_ASSERT),
            List.of(new ChangeMoveProvider<>(variable)));
    try (var director = factory.createScoreDirectorBuilder().withLookUpEnabled(true).build()) {
      director.setWorkingSolution(solution);
      director.setMoveRepository(repository);
      var session = director.getSession();
      var changes = new DefaultProblemChangeDirector<>(director);
      changes.changeProblemProperty(solution.entities.getFirst(), entity -> entity.pinned = true);
      changes.changeProblemProperty(solution.entities.getLast(), entity -> entity.weight = 3);
      changes.updateShadowVariables();
      assertThat(director.getSession()).isNotSameAs(session);
      assertThat(director.calculateScore().raw()).isEqualTo(solution.replayWeight());
      var move = repository.iterator(new java.util.Random(0)).next();
      assertThat(move.getPlanningEntities()).containsExactly(solution.entities.getLast());
      director.executeMove(move);
      assertThat(solution.entities.getFirst().value).isEqualTo(1);
      assertThat(solution.entities.getLast().value).isEqualTo(2);
      assertThat(director.calculateScore().raw()).isEqualTo(solution.replayWeight());
    }
  }

  @Test
  void inheritedShadowDescriptorRetainsFullRefreshAndRecomputesShadow() {
    var solution = new InheritedShadowSolution();
    var entity = new InheritedShadowEntity();
    entity.id = "inherited";
    solution.entities.add(entity);
    var descriptor =
        SolutionDescriptor.buildSolutionDescriptor(
            InheritedShadowSolution.class, ShadowBase.class, InheritedShadowEntity.class);
    var entityDescriptor = descriptor.findEntityDescriptorOrFail(InheritedShadowEntity.class);
    assertThat(entityDescriptor.getDeclaredShadowVariableDescriptors()).isEmpty();
    assertThat(entityDescriptor.getShadowVariableDescriptors()).hasSize(1);
    var factory =
        new BavetConstraintStreamScoreDirectorFactory<InheritedShadowSolution, SimpleScore>(
            descriptor,
            constraints ->
                new Constraint[] {
                  constraints
                      .forEach(InheritedShadowEntity.class)
                      .penalize(SimpleScore.ONE, inherited -> inherited.weightedValue)
                      .asConstraint("inherited shadow")
                },
            EnvironmentMode.NO_ASSERT);
    try (var director = factory.createScoreDirectorBuilder().withLookUpEnabled(true).build()) {
      director.setWorkingSolution(solution);
      assertThat(entity.weightedValue).isEqualTo(1);
      var session = director.getSession();
      new DefaultProblemChangeDirector<>(director)
          .changeProblemProperty(entity, changed -> changed.weight = 4);
      director.updateShadowVariables();
      assertThat(director.getSession()).isNotSameAs(session);
      assertThat(entity.weightedValue).isEqualTo(entity.value * entity.weight);
      assertThat(director.calculateScore().raw()).isEqualTo(SimpleScore.of(-4));
      try (var independent = factory.buildScoreDirector()) {
        independent.setWorkingSolution(solution);
        assertThat(director.calculateScore()).isEqualTo(independent.calculateScore());
      }
    }
  }

  @Test
  void incrementalBackendRetainsFullRefresh() {
    var resets = new AtomicInteger();
    var solution = PropertySolution.generate(2);
    var calculator =
        new IncrementalScoreCalculator<PropertySolution, SimpleScore>() {
          private SimpleScore score;

          public void resetWorkingSolution(PropertySolution workingSolution) {
            resets.incrementAndGet();
            score = workingSolution.replayWeight();
          }

          public void beforeVariableChanged(Object entity, String variable) {}

          public void afterVariableChanged(Object entity, String variable) {}

          public SimpleScore calculateScore() {
            return score;
          }
        };
    try (var director =
        new IncrementalScoreDirectorFactory<>(
                SolutionDescriptor.buildSolutionDescriptor(
                    PropertySolution.class, PropertyEntity.class),
                () -> calculator,
                EnvironmentMode.NO_ASSERT)
            .createScoreDirectorBuilder()
            .withLookUpEnabled(true)
            .build()) {
      director.setWorkingSolution(solution);
      new DefaultProblemChangeDirector<>(director)
          .changeProblemProperty(solution.entities.getFirst(), entity -> entity.weight++);
      assertThat(director.calculateScore().raw()).isEqualTo(solution.replayWeight());
      assertThat(resets).hasValue(2);
    }
  }

  @PlanningSolution
  public static class PropertySolution {
    @PlanningEntityCollectionProperty public List<PropertyEntity> entities = new ArrayList<>();
    @ProblemFactCollectionProperty public List<Fact> facts = new ArrayList<>();
    @ProblemFactCollectionProperty public List<Integer> integerFacts = List.of(1, 2);
    @PlanningScore public SimpleScore score;

    @ValueRangeProvider(id = "values")
    public List<Integer> getValues() {
      return entities.stream().anyMatch(entity -> entity.solutionRangeRestricted)
          ? List.of(2)
          : List.of(1, 2);
    }

    static PropertySolution generate(int size) {
      var solution = new PropertySolution();
      for (var i = 0; i < size; i++)
        solution.entities.add(new PropertyEntity(Integer.toString(i), i + 1));
      return solution;
    }

    SimpleScore replayWeight() {
      return SimpleScore.of(
          -entities.stream()
              .filter(entity -> entity.value != null && entity.optional != null)
              .mapToInt(entity -> entity.weight)
              .sum());
    }

    SimpleScore replayGroups() {
      var counts = new HashMap<Integer, Integer>();
      entities.forEach(entity -> counts.merge(entity.key.code, 1, Integer::sum));
      return SimpleScore.of(
          -counts.entrySet().stream()
              .mapToInt(
                  entry -> entry.getValue() * entry.getValue() + entry.getKey() * entry.getValue())
              .sum());
    }
  }

  @PlanningSolution
  public static class WeightedSolution extends PropertySolution {
    public ConstraintWeightOverrides<SimpleScore> constraintWeightOverrides =
        ConstraintWeightOverrides.none();
  }

  @PlanningEntity
  public static class PropertyEntity {
    @PlanningId public String id;

    @PlanningVariable(valueRangeProviderRefs = "values")
    public Integer value = 1;

    @PlanningVariable(valueRangeProviderRefs = "localValues", allowsUnassigned = true)
    public Integer optional = 1;

    @PlanningPin public boolean pinned;
    public boolean localRangeRestricted;
    public boolean solutionRangeRestricted;
    public int weight = 1;
    public MutableKey key;

    public PropertyEntity() {}

    PropertyEntity(String id, int key) {
      this.id = id;
      this.key = new MutableKey(key);
    }

    @ValueRangeProvider(id = "localValues")
    public List<Integer> getLocalValues() {
      return localRangeRestricted ? List.of(2) : List.of(1, 2);
    }
  }

  @PlanningSolution
  public static class ListPropertySolution {
    @PlanningEntityCollectionProperty
    public List<ListPropertyEntity> entities = List.of(new ListPropertyEntity());

    @ValueRangeProvider(id = "values")
    @ProblemFactCollectionProperty
    public List<Integer> values = List.of(1, 2);

    @PlanningScore public SimpleScore score;
  }

  @PlanningEntity
  public static class ListPropertyEntity {
    @PlanningId public String id = "list entity";

    @PlanningListVariable(valueRangeProviderRefs = "values")
    public List<Integer> values = new ArrayList<>(List.of(1, 2));

    public int weight = 1;
  }

  @PlanningSolution
  public static class InheritedShadowSolution {
    @PlanningEntityCollectionProperty
    public List<InheritedShadowEntity> entities = new ArrayList<>();

    @ValueRangeProvider(id = "values")
    public List<Integer> values = List.of(1, 2);

    @PlanningScore public SimpleScore score;
  }

  @PlanningEntity
  public static class ShadowBase {
    @PlanningId public String id;

    @PlanningVariable(valueRangeProviderRefs = "values")
    public Integer value = 1;

    public int weight = 1;

    @ShadowVariable(supplierName = "weightedValueSupplier")
    public Integer weightedValue;

    @ShadowSources("value")
    public Integer weightedValueSupplier() {
      return value == null ? 0 : value * weight;
    }
  }

  @PlanningEntity
  public static class InheritedShadowEntity extends ShadowBase {}

  public static class Fact {
    @PlanningId public String id = "fact";
    public int weight = 1;
  }

  public static class MutableKey {
    public int code;

    MutableKey(int code) {
      this.code = code;
    }

    @Override
    public boolean equals(Object other) {
      return other instanceof MutableKey key && code == key.code;
    }

    @Override
    public int hashCode() {
      return code;
    }
  }
}
