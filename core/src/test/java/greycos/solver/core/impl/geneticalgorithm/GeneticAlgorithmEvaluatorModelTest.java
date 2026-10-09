package greycos.solver.core.impl.geneticalgorithm;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.random.RandomGenerator;

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
import greycos.solver.core.api.score.stream.Joiners;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmListWorkspaceTest.Model;
import greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmListWorkspaceTest.Owner;
import greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmListWorkspaceTest.Task;
import greycos.solver.core.impl.score.director.stream.BavetConstraintStreamScoreDirector;
import greycos.solver.core.impl.score.director.stream.BavetConstraintStreamScoreDirectorFactory;
import greycos.solver.core.testcotwin.multientity.TestdataHerdEntity;
import greycos.solver.core.testcotwin.multientity.TestdataLeadEntity;
import greycos.solver.core.testcotwin.multientity.TestdataMultiEntitySolution;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(20)
class GeneticAlgorithmEvaluatorModelTest {

  @Test
  void encodedListsStayFrozenWhenCoordinatorAndDecodedCopiesChange() {
    var factory = mixedFactory();
    try (var donor = factory.createScoreDirectorBuilder().withLookUpEnabled(true).build();
        var worker = factory.createScoreDirectorBuilder().withLookUpEnabled(true).build()) {
      donor.setWorkingSolution(mixedProblem());
      var source = new GeneticAlgorithmWorkspace<>(donor, donor.calculateScore());
      var codec = new GeneticAlgorithmEvaluationCodec<>(donor, source);
      worker.setWorkingSolution(donor.cloneWorkingSolution());
      var target = new GeneticAlgorithmWorkspace<>(worker, worker.calculateScore());
      var decoder = codec.createDecoder(worker, target);
      var original = source.genome();
      var encoded = codec.encode(original);
      var changed = original.withLists(new int[][] {{0, 4}, {2, 1}, {3}});
      assertThat(source.transition(changed).valid()).isTrue();
      source.scored(donor.calculateScore());

      original.lists()[0][0] = -1;
      var firstDecoded = decoder.decode(encoded);
      firstDecoded.list(0)[0] = -2;
      firstDecoded.lists()[1] = new int[0];
      firstDecoded.toArray()[0] = null;
      var secondDecoded = decoder.decode(encoded);
      assertThat(secondDecoded).isEqualTo(target.genome());
      assertThat(secondDecoded.listSnapshot()).isEqualTo(original.listSnapshot());
      assertThat(target.transition(secondDecoded).valid()).isTrue();
      target.scored(worker.calculateScore());
      assertMixedReplay(worker.getWorkingSolution(), target.score().raw());
      assertFreshParity(factory, worker, target);
    }
  }

  @Test
  void entityValuedBasicsRebaseAcrossDifferentEntityAndRangeOrders() {
    var input = TestdataMultiEntitySolution.generateUninitializedSolution(3, 3);
    for (int i = 0; i < 3; i++) {
      input.getLeadEntityList().get(i).setValue(input.getValueList().get(i));
      input.getHerdEntityList().get(i).setLeadEntity(input.getLeadEntityList().get(i));
    }
    var factory =
        new BavetConstraintStreamScoreDirectorFactory<TestdataMultiEntitySolution, SimpleScore>(
            TestdataMultiEntitySolution.buildSolutionDescriptor(),
            constraints ->
                new Constraint[] {
                  constraints
                      .forEach(TestdataLeadEntity.class)
                      .join(TestdataLeadEntity.class, Joiners.equal(TestdataLeadEntity::getValue))
                      .penalize(SimpleScore.ONE)
                      .asConstraint("Shared lead value"),
                  constraints
                      .forEach(TestdataHerdEntity.class)
                      .join(
                          TestdataHerdEntity.class,
                          Joiners.equal(TestdataHerdEntity::getLeadEntity))
                      .penalize(SimpleScore.ONE)
                      .asConstraint("Shared herd leader")
                },
            EnvironmentMode.NO_ASSERT);
    try (var donor = factory.createScoreDirectorBuilder().withLookUpEnabled(true).build();
        var worker = factory.createScoreDirectorBuilder().withLookUpEnabled(true).build()) {
      donor.setWorkingSolution(input);
      var source = new GeneticAlgorithmWorkspace<>(donor, donor.calculateScore());
      var codec = new GeneticAlgorithmEvaluationCodec<>(donor, source);
      var workerInput = donor.cloneWorkingSolution();
      Collections.reverse(workerInput.getLeadEntityList());
      Collections.reverse(workerInput.getHerdEntityList());
      workerInput.setValueList(new ArrayList<>(workerInput.getValueList()));
      Collections.reverse(workerInput.getValueList());
      worker.setWorkingSolution(workerInput);
      var target = new GeneticAlgorithmWorkspace<>(worker, worker.calculateScore());
      var decoder = codec.createDecoder(worker, target);
      var sourceSession = donor.getSession();
      var workerSession = worker.getSession();
      var original = source.genome();
      var values = original.toArray();
      for (int i = 0; i < values.length; i++) {
        values[i] =
            source.slots().get(i).entity() instanceof TestdataHerdEntity
                ? input.getLeadEntityList().getLast()
                : input.getValueList().getLast();
      }
      for (var proposal : List.of(new GeneticAlgorithmGenome(values), original)) {
        var encoded = codec.encode(proposal);
        assertThat(source.transition(proposal).valid()).isTrue();
        source.scored(donor.calculateScore());
        assertThat(target.transition(decoder.decode(encoded)).valid()).isTrue();
        target.scored(worker.calculateScore());
        assertThat(target.score()).isEqualTo(source.score());
        assertFreshParity(factory, worker, target);
        assertThat(donor.getSession()).isSameAs(sourceSession);
        assertThat(worker.getSession()).isSameAs(workerSession);
        for (var herd : workerInput.getHerdEntityList()) {
          assertThat(workerInput.getLeadEntityList())
              .anyMatch(lead -> lead == herd.getLeadEntity());
          assertThat(input.getLeadEntityList()).noneMatch(lead -> lead == herd.getLeadEntity());
          var sourceHerd =
              input.getHerdEntityList().stream()
                  .filter(entity -> entity.getCode().equals(herd.getCode()))
                  .findFirst()
                  .orElseThrow();
          assertThat(herd.getLeadEntity().getCode())
              .isEqualTo(sourceHerd.getLeadEntity().getCode());
        }
      }
    }
  }

  @Test
  void mixedReorderingPreservesPinnedOwnersPrefixesAndOptionalMembership() {
    var factory = mixedFactory();
    var input = mixedProblem();
    try (var donor = factory.createScoreDirectorBuilder().withLookUpEnabled(true).build();
        var worker = factory.createScoreDirectorBuilder().withLookUpEnabled(true).build()) {
      donor.setWorkingSolution(input);
      var source = new GeneticAlgorithmWorkspace<>(donor, donor.calculateScore());
      var codec = new GeneticAlgorithmEvaluationCodec<>(donor, source);
      var workerInput = donor.cloneWorkingSolution();
      Collections.reverse(workerInput.owners);
      Collections.reverse(workerInput.tasks);
      workerInput.owners.forEach(owner -> Collections.reverse(owner.range));
      worker.setWorkingSolution(workerInput);
      var target = new GeneticAlgorithmWorkspace<>(worker, worker.calculateScore());
      var decoder = codec.createDecoder(worker, target);
      var session = worker.getSession();
      var original = source.genome();
      var basics = original.toArray();
      setBasic(source, basics, input.owners.get(1), "offset", 3);
      setBasic(source, basics, input.tasks.get(4), "duration", 2);
      var changed = new GeneticAlgorithmGenome(basics, new int[][] {{0, 4}, {2, 1}, {3}});
      assertThat(source.transition(changed).valid()).isTrue();
      source.scored(donor.calculateScore());
      assertThat(target.transition(decoder.decode(codec.encode(changed))).valid()).isTrue();
      target.scored(worker.calculateScore());
      assertThat(target.score()).isEqualTo(source.score());
      assertFreshParity(factory, worker, target);
      assertMixedReplay(workerInput, target.score().raw());
      assertThat(workerInput.owners.getFirst().id).isEqualTo(2);
      assertThat(workerInput.owners.getFirst().tasks)
          .extracting(task -> task.id)
          .containsExactly(3);
      assertThat(
              workerInput.tasks.stream()
                  .filter(task -> task.id == 5)
                  .findFirst()
                  .orElseThrow()
                  .owner)
          .isNull();

      var retained = target.genome();
      var retainedScore = target.score();
      var calculations = worker.getCalculationCount();
      for (var lists :
          List.of(
              new int[][] {{0, 0}, {2, 1}, {3}},
              new int[][] {{4, 0}, {2, 1}, {3}},
              new int[][] {{0, 4}, {2, 1, 3}, {}},
              new int[][] {{0, 4}, {2, 1}, {3, 5}})) {
        var invalid = new GeneticAlgorithmGenome(basics, lists);
        assertThat(target.transition(decoder.decode(codec.encode(invalid))).valid()).isFalse();
        assertThat(target.genome()).isEqualTo(retained);
        assertThat(target.score()).isEqualTo(retainedScore);
        assertThat(worker.getCalculationCount()).isEqualTo(calculations);
      }
      var pinnedBasic = basics.clone();
      setBasic(source, pinnedBasic, input.tasks.get(1), "duration", 4);
      assertThat(
              target
                  .transition(
                      decoder.decode(
                          codec.encode(new GeneticAlgorithmGenome(pinnedBasic, changed.lists()))))
                  .valid())
          .isFalse();
      assertThat(worker.getSession()).isSameAs(session);
      assertMixedReplay(workerInput, target.score().raw());
      assertFreshParity(factory, worker, target);

      assertThat(target.transition(decoder.decode(codec.encode(original))).valid()).isTrue();
      target.scored(worker.calculateScore());
      assertMixedReplay(workerInput, target.score().raw());
      assertFreshParity(factory, worker, target);
      assertThat(worker.getSession()).isSameAs(session);
    }
  }

  @Test
  void hugeNumericRangesAreNeverEnumeratedDuringTransportOrScoring() {
    long size = 1L << 40;
    var input = new RangeSolution();
    input.entities =
        new ArrayList<>(
            List.of(
                new RangeEntity(0, 0, size, false),
                new RangeEntity(1, size, 2 * size, true),
                new RangeEntity(2, 2 * size, 2 * size + 1, false)));
    var factory =
        new BavetConstraintStreamScoreDirectorFactory<RangeSolution, SimpleScore>(
            SolutionDescriptor.buildSolutionDescriptor(RangeSolution.class, RangeEntity.class),
            constraints ->
                new Constraint[] {
                  constraints
                      .forEach(RangeEntity.class)
                      .reward(SimpleScore.ONE, entity -> (int) (entity.value % 1_000))
                      .asConstraint("Remainder")
                },
            EnvironmentMode.NO_ASSERT);
    try (var donor = factory.createScoreDirectorBuilder().withLookUpEnabled(true).build();
        var worker = factory.createScoreDirectorBuilder().withLookUpEnabled(true).build()) {
      donor.setWorkingSolution(input);
      var source = new GeneticAlgorithmWorkspace<>(donor, donor.calculateScore());
      var codec = new GeneticAlgorithmEvaluationCodec<>(donor, source);
      var clone = donor.cloneWorkingSolution();
      Collections.reverse(clone.entities);
      worker.setWorkingSolution(clone);
      var target = new GeneticAlgorithmWorkspace<>(worker, worker.calculateScore());
      var decoder = codec.createDecoder(worker, target);
      var session = worker.getSession();
      for (long value : new long[] {size - 1, 1, size / 2, 0}) {
        var values = source.genome().toArray();
        setBasic(source, values, input.entities.getFirst(), "value", value);
        var proposal = new GeneticAlgorithmGenome(values);
        assertThat(target.transition(decoder.decode(codec.encode(proposal))).valid()).isTrue();
        target.scored(worker.calculateScore());
        assertThat(target.score().raw())
            .isEqualTo(SimpleScore.of((int) (value % 1_000 + size % 1_000 + (2 * size) % 1_000)));
        assertThat(clone.entities)
            .extracting(entity -> entity.value)
            .containsExactly(2 * size, size, value);
        assertFreshParity(factory, worker, target);
        assertThat(worker.getSession()).isSameAs(session);
      }
      assertThat(input.entities)
          .extracting(entity -> entity.value)
          .containsExactly(0L, size, 2 * size);
    }
  }

  @Test
  void structuralCycleRestoresRecipientAssignmentsAndNativeScoreBeforeNextTrial() {
    var root = new CycleEntity(0);
    var first = new CycleEntity(1);
    var second = new CycleEntity(2);
    first.previous = root;
    second.previous = first;
    var input = new CycleSolution();
    input.entities = new ArrayList<>(List.of(root, first, second));
    var factory =
        new BavetConstraintStreamScoreDirectorFactory<CycleSolution, SimpleScore>(
            SolutionDescriptor.buildSolutionDescriptor(CycleSolution.class, CycleEntity.class),
            constraints ->
                new Constraint[] {
                  constraints
                      .forEachIncludingUnassigned(CycleEntity.class)
                      .penalize(SimpleScore.ONE, entity -> entity.depth)
                      .asConstraint("Depth")
                },
            EnvironmentMode.NO_ASSERT);
    try (var donor = factory.createScoreDirectorBuilder().withLookUpEnabled(true).build();
        var worker = factory.createScoreDirectorBuilder().withLookUpEnabled(true).build()) {
      donor.setWorkingSolution(input);
      var source = new GeneticAlgorithmWorkspace<>(donor, donor.calculateScore());
      var codec = new GeneticAlgorithmEvaluationCodec<>(donor, source);
      var clone = donor.cloneWorkingSolution();
      Collections.reverse(clone.entities);
      worker.setWorkingSolution(clone);
      var target = new GeneticAlgorithmWorkspace<>(worker, worker.calculateScore());
      var decoder = codec.createDecoder(worker, target);
      var before = target.genome();
      var beforeScore = target.score();
      var session = worker.getSession();
      var calculations = worker.getCalculationCount();
      var invalid = new GeneticAlgorithmGenome(new Object[] {null, second, first});
      assertThat(target.transition(decoder.decode(codec.encode(invalid))).valid()).isFalse();
      assertThat(target.genome()).isEqualTo(before);
      assertThat(target.score()).isEqualTo(beforeScore);
      assertThat(worker.getCalculationCount()).isEqualTo(calculations + 1);
      assertThat(clone.entities).extracting(entity -> entity.depth).containsExactly(2, 1, 0);
      assertThat(worker.isLastVariableUpdateSuccessful()).isTrue();
      assertFreshParity(factory, worker, target);
      var valid = new GeneticAlgorithmGenome(new Object[] {null, root, root});
      assertThat(target.transition(decoder.decode(codec.encode(valid))).valid()).isTrue();
      target.scored(worker.calculateScore());
      assertThat(target.score().raw()).isEqualTo(SimpleScore.of(-2));
      assertThat(clone.entities).extracting(entity -> entity.depth).containsExactly(1, 1, 0);
      assertFreshParity(factory, worker, target);
      assertThat(worker.getSession()).isSameAs(session);
      assertThat(input.entities).extracting(entity -> entity.depth).containsExactly(0, 1, 2);
    }
  }

  private static <Solution_> void assertFreshParity(
      BavetConstraintStreamScoreDirectorFactory<Solution_, SimpleScore> factory,
      BavetConstraintStreamScoreDirector<Solution_, SimpleScore> worker,
      GeneticAlgorithmWorkspace<Solution_, SimpleScore> workspace) {
    try (var fresh = factory.createScoreDirectorBuilder().withLookUpEnabled(true).build()) {
      fresh.setWorkingSolution(worker.cloneWorkingSolution());
      assertThat(fresh.calculateScore()).isEqualTo(workspace.score());
    }
  }

  private static <Solution_> void setBasic(
      GeneticAlgorithmWorkspace<Solution_, SimpleScore> workspace,
      Object[] values,
      Object entity,
      String variable,
      Object value) {
    for (int i = 0; i < values.length; i++) {
      var slot = workspace.slots().get(i);
      if (slot.entity() == entity && slot.variableDescriptor().getVariableName().equals(variable)) {
        values[i] = value;
        return;
      }
    }
    throw new AssertionError("Missing basic slot " + variable);
  }

  private static BavetConstraintStreamScoreDirectorFactory<Model, SimpleScore> mixedFactory() {
    return new BavetConstraintStreamScoreDirectorFactory<>(
        SolutionDescriptor.buildSolutionDescriptor(Model.class, Owner.class, Task.class),
        constraints ->
            new Constraint[] {
              constraints
                  .forEach(Owner.class)
                  .penalize(SimpleScore.ONE, owner -> owner.totalDuration)
                  .asConstraint("Total"),
              constraints
                  .forEachIncludingUnassigned(Task.class)
                  .penalize(SimpleScore.ONE, task -> task.cumulative)
                  .asConstraint("Cumulative")
            },
        EnvironmentMode.NO_ASSERT);
  }

  private static Model mixedProblem() {
    var solution = new Model();
    for (int i = 0; i < 6; i++) {
      var task = new Task();
      task.id = i;
      solution.tasks.add(task);
    }
    for (int i = 0; i < 3; i++) {
      var owner = new Owner();
      owner.id = i;
      owner.range.addAll(solution.tasks);
      solution.owners.add(owner);
    }
    solution.owners.get(0).tasks.addAll(solution.tasks.subList(0, 2));
    solution.owners.get(0).pinIndex = 1;
    solution.owners.get(1).tasks.add(solution.tasks.get(2));
    solution.owners.get(2).tasks.add(solution.tasks.get(3));
    solution.owners.get(2).pinned = true;
    solution.tasks.get(1).pinned = true;
    return solution;
  }

  private static void assertMixedReplay(Model solution, SimpleScore score) {
    int penalty = 0;
    for (var owner : solution.owners) {
      int total = 0;
      int cumulative = 0;
      for (int i = 0; i < owner.tasks.size(); i++) {
        var task = owner.tasks.get(i);
        total += task.duration + 1;
        cumulative += task.duration + 1 + owner.offset;
        penalty += cumulative;
        assertThat(task.owner).isSameAs(owner);
        assertThat(task.index).isEqualTo(i);
        assertThat(task.cumulative).isEqualTo(cumulative);
        assertThat(task.previous).isSameAs(i == 0 ? null : owner.tasks.get(i - 1));
        assertThat(task.next).isSameAs(i + 1 == owner.tasks.size() ? null : owner.tasks.get(i + 1));
        assertThat(solution.tasks).anyMatch(value -> value == task);
      }
      assertThat(owner.totalDuration).isEqualTo(total);
      penalty += total;
    }
    assertThat(score).isEqualTo(SimpleScore.of(-penalty));
  }

  @PlanningSolution
  public static class RangeSolution {
    @PlanningEntityCollectionProperty public List<RangeEntity> entities;
    @PlanningScore public SimpleScore score;
  }

  @PlanningEntity
  public static class RangeEntity {
    @PlanningId public int id;
    public long from;
    public long to;
    @PlanningPin public boolean pinned;

    @PlanningVariable(valueRangeProviderRefs = "range")
    public Long value;

    public RangeEntity() {}

    RangeEntity(int id, long from, long to, boolean pinned) {
      this.id = id;
      this.from = from;
      this.to = to;
      this.pinned = pinned;
      value = from;
    }

    @ValueRangeProvider(id = "range")
    public ValueRange<Long> getRange() {
      return new NonEnumeratingRange(from, to);
    }
  }

  private record NonEnumeratingRange(long from, long to) implements ValueRange<Long> {
    @Override
    public boolean isEmpty() {
      return from == to;
    }

    @Override
    public boolean contains(Long value) {
      return value != null && value >= from && value < to;
    }

    @Override
    public long getSize() {
      return to - from;
    }

    @Override
    public Long get(long index) {
      return from + index;
    }

    @Override
    public Iterator<Long> createOriginalIterator() {
      throw new AssertionError("A numeric evaluator range must not be enumerated.");
    }

    @Override
    public Iterator<Long> createRandomIterator(RandomGenerator random) {
      return ValueRangeFactory.createLongValueRange(from, to).createRandomIterator(random);
    }
  }

  @PlanningSolution
  public static class CycleSolution {
    @PlanningEntityCollectionProperty @ValueRangeProvider public List<CycleEntity> entities;
    @PlanningScore public SimpleScore score;
  }

  @PlanningEntity
  public static class CycleEntity {
    @PlanningId public int id;

    @PlanningVariable(allowsUnassigned = true)
    public CycleEntity previous;

    @ShadowVariable(supplierName = "calculateDepth")
    public Integer depth;

    public CycleEntity() {}

    CycleEntity(int id) {
      this.id = id;
    }

    @ShadowSources("previous.depth")
    public Integer calculateDepth() {
      return previous == null ? 0 : previous.depth == null ? null : previous.depth + 1;
    }
  }
}
