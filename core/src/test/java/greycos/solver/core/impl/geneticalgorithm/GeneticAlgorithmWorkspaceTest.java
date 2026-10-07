package greycos.solver.core.impl.geneticalgorithm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.entity.PlanningPin;
import greycos.solver.core.api.cotwin.lookup.PlanningId;
import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.valuerange.ValueRange;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeFactory;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.cotwin.variable.PlanningVariable;
import greycos.solver.core.api.cotwin.variable.ShadowSources;
import greycos.solver.core.api.cotwin.variable.ShadowVariable;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.api.score.stream.ConstraintCollectors;
import greycos.solver.core.api.score.stream.ConstraintProvider;
import greycos.solver.core.api.score.stream.Joiners;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.impl.score.director.stream.BavetConstraintStreamScoreDirectorFactory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(20)
class GeneticAlgorithmWorkspaceTest {

  @Test
  void genomeOwnsItsArrayAndComparesAssignmentsByEquals() {
    var values = new Object[] {new String("equal"), null};
    var genome = new GeneticAlgorithmGenome(values);
    values[0] = "mutated";
    var exported = genome.toArray();
    exported[1] = "mutated";
    assertThat(genome).isEqualTo(new GeneticAlgorithmGenome(new Object[] {"equal", null}));
    assertThat(genome.value(0)).isEqualTo("equal");
    assertThat(genome.value(1)).isNull();
  }

  @Test
  void sparseDeltaRetainsSessionAndDoesNotReweighUntouchedContribution() {
    var calls = new HashMap<Integer, Integer>();
    var factory =
        factory(
            constraints ->
                new Constraint[] {
                  constraints
                      .forEachIncludingUnassigned(Entity.class)
                      .penalize(
                          SimpleScore.ONE,
                          entity -> {
                            calls.merge(entity.id, 1, Integer::sum);
                            return entity.doubled;
                          })
                      .asConstraint("localized")
                });
    var solution = solution(1L, 2L, 3L);
    try (var director = factory.createScoreDirectorBuilder().build()) {
      director.setWorkingSolution(solution);
      var initialScore = director.calculateScore();
      var workspace = new GeneticAlgorithmWorkspace<>(director, initialScore);
      var captured = workspace.genome();
      var session = director.getSession();
      var bestClone = director.cloneWorkingSolution();
      calls.clear();
      solution.entities.forEach(entity -> entity.setterCalls = 0);

      assertThat(workspace.transition(new GeneticAlgorithmGenome(new Object[] {4L, 2L, 3L})))
          .isEqualTo(new GeneticAlgorithmWorkspace.Transition(true, 1));
      workspace.scored(director.calculateScore());
      assertThat(director.getSession()).isSameAs(session);
      assertThat(calls).containsExactly(Map.entry(0, 1));
      assertThat(solution.entities)
          .extracting(entity -> entity.setterCalls)
          .containsExactly(1, 0, 0);
      assertThat(solution.entities)
          .extracting(entity -> entity.doubled)
          .containsExactly(8L, 4L, 6L);
      assertThat(captured.toArray()).containsExactly(1L, 2L, 3L);
      assertThat(bestClone.entities).extracting(Entity::getValue).containsExactly(1L, 2L, 3L);
      assertThat(workspace.score()).isEqualTo(InnerScore.fullyAssigned(SimpleScore.of(-18)));
      try (var fresh = factory.createScoreDirectorBuilder().build()) {
        fresh.setWorkingSolution(director.cloneWorkingSolution());
        assertThat(fresh.calculateScore()).isEqualTo(workspace.score());
      }
    }
  }

  @Test
  void joinsGroupingAndExistenceRemainExactAcrossBroadAndNullTransitions() {
    var factory =
        factory(
            constraints ->
                new Constraint[] {
                  constraints
                      .forEach(Entity.class)
                      .join(Entity.class, Joiners.equal(Entity::getValue))
                      .penalize(SimpleScore.ONE)
                      .asConstraint("join"),
                  constraints
                      .forEach(Entity.class)
                      .groupBy(Entity::getValue, ConstraintCollectors.count())
                      .penalize(SimpleScore.ONE, (value, count) -> value * count)
                      .asConstraint("group"),
                  constraints
                      .forEach(Entity.class)
                      .ifExistsOther(Entity.class, Joiners.equal(Entity::getValue))
                      .penalize(SimpleScore.ONE)
                      .asConstraint("exists")
                });
    var solution = solution(1L, 1L, 2L, 3L);
    try (var director = factory.createScoreDirectorBuilder().build()) {
      director.setWorkingSolution(solution);
      var workspace = new GeneticAlgorithmWorkspace<>(director, director.calculateScore());
      var session = director.getSession();
      for (var values :
          List.of(
              new Object[] {3L, 2L, 3L, 2L},
              new Object[] {null, 4L, 3L, null},
              new Object[] {1L, 1L, 1L, 1L})) {
        assertThat(workspace.transition(new GeneticAlgorithmGenome(values)).valid()).isTrue();
        workspace.scored(director.calculateScore());
        assertThat(director.getSession()).isSameAs(session);
        try (var fresh = factory.createScoreDirectorBuilder().build()) {
          var freshSolution = solution(Arrays.copyOf(values, values.length, Long[].class));
          fresh.setWorkingSolution(freshSolution);
          assertThat(workspace.score()).isEqualTo(fresh.calculateScore());
          assertThat(solution.entities)
              .extracting(entity -> entity.doubled)
              .containsExactlyElementsOf(
                  freshSolution.entities.stream().map(entity -> entity.doubled).toList());
        }
      }
    }
  }

  @Test
  void rangeAndPinPreflightRejectsWholeProposalWithoutNotifications() {
    var factory = simpleFactory();
    var solution = solution(1L, 2L, null);
    solution.entities.get(1).pinned = true;
    // Optional empty range contains only null and is therefore a singleton.
    solution.entities.get(2).rangeEnd = 0;
    try (var director = factory.createScoreDirectorBuilder().build()) {
      director.setWorkingSolution(solution);
      var workspace = new GeneticAlgorithmWorkspace<>(director, director.calculateScore());
      var session = director.getSession();
      assertThat(workspace.slots())
          .extracting(GeneticAlgorithmSlot::movable)
          .containsExactly(true, false, false);
      for (var proposal :
          List.of(
              new Object[] {4L, 3L, null},
              new Object[] {4L, 2L, 1L},
              new Object[] {9L, 2L, null},
              new Object[] {Integer.valueOf(1), 2L, null})) {
        assertThat(workspace.transition(new GeneticAlgorithmGenome(proposal)).valid()).isFalse();
        assertThat(solution.entities).extracting(Entity::getValue).containsExactly(1L, 2L, null);
        assertThat(solution.entities).extracting(entity -> entity.setterCalls).containsOnly(0);
        assertThat(director.getSession()).isSameAs(session);
      }
      assertThat(
              workspace
                  .transition(new GeneticAlgorithmGenome(new Object[] {null, 2L, null}))
                  .valid())
          .isTrue();
      workspace.scored(director.calculateScore());
      assertThat(workspace.score().isFullyAssigned()).isTrue();
    }
  }

  @Test
  void longIndexedRangesAreNotMaterializedOrNarrowedToInt() {
    var factory = simpleFactory();
    var solution = solution(0L);
    solution.entities.getFirst().rangeEnd = 1L << 40;
    try (var director = factory.createScoreDirectorBuilder().build()) {
      director.setWorkingSolution(solution);
      var workspace = new GeneticAlgorithmWorkspace<>(director, director.calculateScore());
      var range = workspace.slots().getFirst().valueRange();
      assertThat(range.getSize()).isEqualTo((1L << 40) + 1);
      var largeValue = range.get(1L << 35);
      assertThat(largeValue).isInstanceOf(Long.class);
      assertThat((Long) largeValue).isGreaterThan(Integer.MAX_VALUE);
      assertThat(
              workspace.transition(new GeneticAlgorithmGenome(new Object[] {largeValue})).valid())
          .isTrue();
      workspace.scored(director.calculateScore());
      assertThat(solution.entities.getFirst().getValue()).isEqualTo(largeValue);
    }
  }

  @Test
  void noChangeDoesNotNotifyOrCalculateAndUnscoredStateCannotBecomeRollbackBaseline() {
    var factory = simpleFactory();
    var solution = solution(1L);
    try (var director = factory.createScoreDirectorBuilder().build()) {
      director.setWorkingSolution(solution);
      var workspace = new GeneticAlgorithmWorkspace<>(director, director.calculateScore());
      var beforeCount = director.getCalculationCount();
      var previous = workspace.genome();
      var previousScore = workspace.score();
      assertThat(workspace.transition(new GeneticAlgorithmGenome(new Object[] {1L})))
          .isEqualTo(new GeneticAlgorithmWorkspace.Transition(true, 0));
      assertThat(director.getCalculationCount()).isEqualTo(beforeCount);
      assertThat(solution.entities.getFirst().setterCalls).isZero();
      assertThat(workspace.transition(new GeneticAlgorithmGenome(new Object[] {2L})).valid())
          .isTrue();
      assertThatThrownBy(workspace::score).hasMessageContaining("not been scored");
      assertThatThrownBy(() -> workspace.transition(previous))
          .hasMessageContaining("before another transition");
      workspace.restore(previous, previousScore);
      assertThat(workspace.genome()).isEqualTo(previous);
      assertThat(workspace.score()).isEqualTo(previousScore);
      assertThat(solution.entities.getFirst().getValue()).isEqualTo(1L);
      assertThat(solution.entities.getFirst().doubled).isEqualTo(2L);
    }
  }

  @Test
  void structuralCycleRestoresAssignmentsShadowsScoreAndRetainsSession() {
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
    var first = new CycleEntity();
    var second = new CycleEntity();
    first.previous = root;
    second.previous = first;
    solution.entities = new ArrayList<>(List.of(root, first, second));
    try (var director = factory.createScoreDirectorBuilder().build()) {
      director.setWorkingSolution(solution);
      var workspace = new GeneticAlgorithmWorkspace<>(director, director.calculateScore());
      var session = director.getSession();
      var previous = workspace.genome();
      var previousScore = workspace.score();
      var count = director.getCalculationCount();
      assertThat(
              workspace.transition(new GeneticAlgorithmGenome(new Object[] {null, second, first})))
          .isEqualTo(new GeneticAlgorithmWorkspace.Transition(false, 1));
      assertThat(workspace.genome()).isEqualTo(previous);
      assertThat(workspace.score()).isEqualTo(previousScore);
      assertThat(director.getCalculationCount()).isEqualTo(count + 1);
      assertThat(solution.entities).extracting(entity -> entity.depth).containsExactly(0, 1, 2);
      assertThat(director.isLastVariableUpdateSuccessful()).isTrue();
      assertThat(director.getSession()).isSameAs(session);
      assertThat(
              workspace
                  .transition(new GeneticAlgorithmGenome(new Object[] {null, root, root}))
                  .valid())
          .isTrue();
      workspace.scored(director.calculateScore());
      assertThat(solution.entities).extracting(entity -> entity.depth).containsExactly(0, 1, 1);
      assertThat(workspace.score()).isEqualTo(InnerScore.fullyAssigned(SimpleScore.of(-2)));
      assertThat(director.getSession()).isSameAs(session);
      try (var fresh = factory.createScoreDirectorBuilder().build()) {
        fresh.setWorkingSolution(director.cloneWorkingSolution());
        assertThat(fresh.calculateScore()).isEqualTo(workspace.score());
      }
    }
  }

  @Test
  void setterAndShadowFailuresAbortInsteadOfBecomingInvalidCandidates() {
    var failure = new IllegalStateException("injected setter failure");
    var factory = simpleFactory();
    var solution = solution(1L);
    try (var director = factory.createScoreDirectorBuilder().build()) {
      director.setWorkingSolution(solution);
      var workspace = new GeneticAlgorithmWorkspace<>(director, director.calculateScore());
      solution.entities.getFirst().setterFailure = failure;
      assertThatThrownBy(() -> workspace.transition(new GeneticAlgorithmGenome(new Object[] {2L})))
          .hasRootCause(failure);
    }
    solution = solution(1L);
    try (var director = factory.createScoreDirectorBuilder().build()) {
      director.setWorkingSolution(solution);
      var workspace = new GeneticAlgorithmWorkspace<>(director, director.calculateScore());
      solution.entities.getFirst().shadowFailure = failure;
      assertThatThrownBy(() -> workspace.transition(new GeneticAlgorithmGenome(new Object[] {2L})))
          .hasRootCause(failure);
    }
  }

  private static BavetConstraintStreamScoreDirectorFactory<Solution, SimpleScore> simpleFactory() {
    return factory(
        constraints ->
            new Constraint[] {
              constraints
                  .forEachIncludingUnassigned(Entity.class)
                  .penalize(SimpleScore.ONE, entity -> entity.doubled)
                  .asConstraint("doubled")
            });
  }

  private static BavetConstraintStreamScoreDirectorFactory<Solution, SimpleScore> factory(
      ConstraintProvider provider) {
    return new BavetConstraintStreamScoreDirectorFactory<>(
        SolutionDescriptor.buildSolutionDescriptor(Solution.class, Entity.class),
        provider,
        EnvironmentMode.NO_ASSERT);
  }

  private static Solution solution(Long... values) {
    var solution = new Solution();
    solution.entities = new ArrayList<>();
    for (var i = 0; i < values.length; i++) {
      var entity = new Entity();
      entity.id = i;
      entity.value = values[i];
      solution.entities.add(entity);
    }
    return solution;
  }

  @PlanningSolution
  public static class Solution {
    @PlanningEntityCollectionProperty public List<Entity> entities;
    @PlanningScore public SimpleScore score;
  }

  @PlanningEntity
  public static class Entity {
    @PlanningId public int id;
    @PlanningPin public boolean pinned;
    private Long value;
    public long rangeEnd = 5;
    public int setterCalls;
    public RuntimeException setterFailure;
    public RuntimeException shadowFailure;

    @ShadowVariable(supplierName = "doubleValue")
    public Long doubled;

    @PlanningVariable(valueRangeProviderRefs = "range", allowsUnassigned = true)
    public Long getValue() {
      return value;
    }

    public void setValue(Long value) {
      setterCalls++;
      if (setterFailure != null) {
        throw setterFailure;
      }
      this.value = value;
    }

    @ValueRangeProvider(id = "range")
    public ValueRange<Long> getRange() {
      return ValueRangeFactory.createLongValueRange(0, rangeEnd);
    }

    @ShadowSources("value")
    public Long doubleValue() {
      if (shadowFailure != null) {
        throw shadowFailure;
      }
      return value == null ? 0L : value * 2;
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
