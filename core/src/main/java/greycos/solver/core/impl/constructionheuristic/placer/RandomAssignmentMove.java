package greycos.solver.core.impl.constructionheuristic.placer;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.random.RandomGenerator;

import greycos.solver.core.api.cotwin.valuerange.ValueRange;
import greycos.solver.core.impl.cotwin.solution.cloner.DeepCloningUtils;
import greycos.solver.core.impl.cotwin.variable.descriptor.BasicVariableDescriptor;
import greycos.solver.core.impl.cotwin.variable.descriptor.GenuineVariableDescriptor;
import greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import greycos.solver.core.impl.move.InnerMutableSolutionView;
import greycos.solver.core.impl.move.SolutionAssignments;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.preview.api.move.Move;
import greycos.solver.core.preview.api.move.MutableSolutionView;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** One detached random assignment, replayed unchanged during evaluation and commitment. */
@NullMarked
public final class RandomAssignmentMove<Solution_> implements Move<Solution_> {

  private final Solution_ workingSolution;
  private final SolutionAssignments<Solution_> baseline;
  private final SolutionAssignments<Solution_> target;

  private RandomAssignmentMove(
      Solution_ workingSolution,
      SolutionAssignments<Solution_> baseline,
      SolutionAssignments<Solution_> target) {
    this.workingSolution = workingSolution;
    this.baseline = baseline;
    this.target = target;
  }

  /** Returns null on cancellation; sampling never changes the working solution. */
  public static <Solution_> @Nullable RandomAssignmentMove<Solution_> sample(
      InnerScoreDirector<Solution_, ?> director,
      RandomGenerator random,
      BooleanSupplier cancelled) {
    Objects.requireNonNull(
        director, "The RANDOM_ASSIGNMENT scoreDirector (null) must not be null.");
    Objects.requireNonNull(random, "The RANDOM_ASSIGNMENT random (null) must not be null.");
    Objects.requireNonNull(
        cancelled, "The RANDOM_ASSIGNMENT cancelled supplier (null) must not be null.");
    if (cancelled.getAsBoolean()) {
      return null;
    }
    if (!director.isLastVariableUpdateSuccessful()) {
      throw invalid(
          "requires a structurally valid initial solution (%s), but lastVariableUpdateSuccessful is (false).\n"
                  .formatted(identity(director.getWorkingSolution()))
              + "Maybe correct the shadow-variable dependencies or inconsistent initial assignments before construction");
    }
    var sampler = new Sampler<>(director, random, cancelled);
    return sampler.sample();
  }

  @Override
  public void execute(MutableSolutionView<Solution_> solutionView) {
    var director = ((InnerMutableSolutionView<Solution_>) solutionView).getScoreDirector();
    if (director.getWorkingSolution() != workingSolution) {
      throw new IllegalStateException(
          "RANDOM_ASSIGNMENT cannot execute on workingSolution (%s); the sampled workingSolution was (%s).\n"
                  .formatted(identity(director.getWorkingSolution()), identity(workingSolution))
              + "Maybe sample a new move after replacing or cloning the working solution.");
    }
    target.apply(director);
  }

  /** Verifies a completed temporary evaluation restored even an initially incomplete solution. */
  public void verifyRestored(
      InnerScoreDirector<Solution_, ?> director, InnerScore<?> baselineScore) {
    if (director.getWorkingSolution() != workingSolution) {
      throw new IllegalStateException(
          "RANDOM_ASSIGNMENT cannot verify restoration on workingSolution (%s); the baseline workingSolution was (%s)."
              .formatted(identity(director.getWorkingSolution()), identity(workingSolution)));
    }
    baseline
        .getBasicChanges()
        .forEach(
            (variable, records) -> {
              for (var record : records) {
                var actual = variable.getValue(record.entity());
                var expected = record.value();
                if (actual != expected
                    && (expected == null
                        || !DeepCloningUtils.isImmutable(expected.getClass())
                        || !Objects.equals(actual, expected))) {
                  throw new IllegalStateException(
                      "RANDOM_ASSIGNMENT failed to restore basic variable (%s) on entity (%s): expected value (%s), actual value (%s).\n"
                              .formatted(
                                  variable.getSimpleEntityAndVariableName(),
                                  identity(record.entity()),
                                  describe(expected),
                                  describe(actual))
                          + "Maybe check the planning-variable setter and custom listeners for changes outside notified assignments.");
                }
              }
            });
    baseline
        .getListChanges()
        .forEach(
            (variable, records) -> {
              for (var record : records) {
                var actual = variable.getValue(record.entity());
                var expected = record.values();
                if (actual == null || actual.size() != expected.size()) {
                  throw new IllegalStateException(
                      "RANDOM_ASSIGNMENT failed to restore list variable (%s) on owner (%s): expected list size (%d), actual list size (%s)."
                          .formatted(
                              variable.getSimpleEntityAndVariableName(),
                              identity(record.entity()),
                              expected.size(),
                              actual == null ? "null" : actual.size()));
                }
                for (int i = 0; i < expected.size(); i++) {
                  if (actual.get(i) != expected.get(i)) {
                    throw new IllegalStateException(
                        "RANDOM_ASSIGNMENT failed to restore list variable (%s) on owner (%s) at index (%d): expected value (%s), actual value (%s).\n"
                                .formatted(
                                    variable.getSimpleEntityAndVariableName(),
                                    identity(record.entity()),
                                    i,
                                    describe(expected.get(i)),
                                    describe(actual.get(i)))
                            + "Maybe preserve the canonical value instances and notify every list change in custom listeners.");
                  }
                }
              }
            });
    var restoredScore = director.calculateScore();
    if (!director.isLastVariableUpdateSuccessful()
        || restoredScore.isStructurallyFlawed()
        || !restoredScore.equals(baselineScore)) {
      throw new IllegalStateException(
          "RANDOM_ASSIGNMENT failed to restore solution (%s): expected score (%s), restored score (%s), lastVariableUpdateSuccessful (%s), structurallyFlawed (%s).\n"
                  .formatted(
                      identity(workingSolution),
                      baselineScore,
                      restoredScore,
                      director.isLastVariableUpdateSuccessful(),
                      restoredScore.isStructurallyFlawed())
              + "Maybe check custom variable listeners and scoring code for state that is not restored by notified changes.");
    }
  }

  @Override
  public String toString() {
    return "RANDOM_ASSIGNMENT";
  }

  private static IllegalArgumentException invalid(String detail) {
    return new IllegalArgumentException("RANDOM_ASSIGNMENT " + detail + ".");
  }

  private static String identity(@Nullable Object value) {
    if (value == null) return "null";
    var type = value.getClass();
    var name = type.getSimpleName().isEmpty() ? type.getName() : type.getSimpleName();
    return name + "@" + Integer.toHexString(System.identityHashCode(value));
  }

  private static String describe(@Nullable Object value) {
    if (value == null) return "null";
    final String text;
    if (value instanceof Enum<?> enumValue) {
      text = enumValue.name();
    } else if (DeepCloningUtils.IMMUTABLE_CLASSES.contains(value.getClass())
        && !(value instanceof java.util.Optional<?>)) {
      text = String.valueOf(value);
    } else {
      // Entity, record and Optional toString() implementations may walk an entire cyclic graph.
      return identity(value);
    }
    return (text.length() <= 128 ? text : text.substring(0, 128) + "...")
        + " ["
        + identity(value)
        + "]";
  }

  private static final class Sampler<Solution_> {
    private final InnerScoreDirector<Solution_, ?> director;
    private final RandomGenerator random;
    private final BooleanSupplier cancelled;
    private final Solution_ solution;
    private final Map<
            GenuineVariableDescriptor<Solution_>, List<SolutionAssignments.BasicChangeRecord<?>>>
        basics = new LinkedHashMap<>();
    private final Map<
            GenuineVariableDescriptor<Solution_>, List<SolutionAssignments.BasicChangeRecord<?>>>
        oldBasics = new LinkedHashMap<>();
    private final Map<
            ListVariableDescriptor<Solution_>, List<SolutionAssignments.ListChangeRecord<?>>>
        lists = new LinkedHashMap<>();
    private final Map<
            ListVariableDescriptor<Solution_>, List<SolutionAssignments.ListChangeRecord<?>>>
        oldLists = new LinkedHashMap<>();

    private Sampler(
        InnerScoreDirector<Solution_, ?> director,
        RandomGenerator random,
        BooleanSupplier cancelled) {
      this.director = director;
      this.random = random;
      this.cancelled = cancelled;
      solution = director.getWorkingSolution();
    }

    private @Nullable RandomAssignmentMove<Solution_> sample() {
      var descriptor = director.getSolutionDescriptor();
      var entities = new ArrayList<Object>();
      descriptor.visitAllEntities(solution, entities::add);
      var seen = new IdentityHashMap<Object, Boolean>();
      for (var entity : entities) {
        if (cancelled.getAsBoolean()) return null;
        if (seen.put(entity, Boolean.TRUE) != null) {
          throw invalid(
              "found a repeated canonical entity (%s) in solution (%s).\n"
                      .formatted(identity(entity), identity(solution))
                  + "Maybe include each planning entity instance only once across the solution's entity properties");
        }
        var entityDescriptor = descriptor.findEntityDescriptorOrFail(entity.getClass());
        boolean movable = entityDescriptor.isMovable(solution, entity);
        for (var variable : entityDescriptor.getGenuineVariableDescriptorList()) {
          if (cancelled.getAsBoolean()) return null;
          if (variable instanceof BasicVariableDescriptor<Solution_> basic) {
            sampleBasic(entity, basic, movable);
          }
        }
      }
      var listVariable = descriptor.getListVariableDescriptor();
      if (listVariable != null && !sampleLists(listVariable)) return null;
      if (cancelled.getAsBoolean()) return null;
      return new RandomAssignmentMove<>(
          solution,
          SolutionAssignments.of(oldBasics, oldLists),
          SolutionAssignments.of(basics, lists));
    }

    private void sampleBasic(
        Object entity, BasicVariableDescriptor<Solution_> variable, boolean movable) {
      ValueRange<Object> range =
          director.getValueRangeManager().getFromEntity(variable.getValueRangeDescriptor(), entity);
      long size = range.getSize();
      if (size <= 0) {
        throw invalid(
            "requires a nonempty finite value range for variable (%s) on entity (%s), but valueRange (%s) has size (%d)"
                .formatted(
                    variable.getSimpleEntityAndVariableName(),
                    identity(entity),
                    identity(range),
                    size));
      }
      var previous = variable.getValue(entity);
      boolean missing = previous == null && !variable.allowsUnassigned();
      if ((missing && !movable) || (!missing && !accepts(variable, range, previous))) {
        throw invalid(
            "found an invalid initial value (%s) for variable (%s) on entity (%s): movable (%s), allowsUnassigned (%s), missingRequiredValue (%s), expectedType (%s), valueRange (%s), rangeSize (%d).\n"
                    .formatted(
                        describe(previous),
                        variable.getSimpleEntityAndVariableName(),
                        identity(entity),
                        movable,
                        variable.allowsUnassigned(),
                        missing,
                        variable.getVariablePropertyType().getName(),
                        identity(range),
                        size)
                + "Maybe initialize pinned required variables and use values from each variable's declared range");
      }
      oldBasics
          .computeIfAbsent(variable, ignored -> new ArrayList<>())
          .add(new SolutionAssignments.BasicChangeRecord<>(entity, previous));
      if (movable) {
        long selectedIndex = size == 1 ? 0 : random.nextLong(size);
        var value = range.get(selectedIndex);
        if (!accepts(variable, range, value)) {
          throw invalid(
              "sampled invalid value (%s) at range index (%d) for variable (%s) on entity (%s): expectedType (%s), allowsUnassigned (%s), valueRange (%s), rangeSize (%d).\n"
                      .formatted(
                          describe(value),
                          selectedIndex,
                          variable.getSimpleEntityAndVariableName(),
                          identity(entity),
                          variable.getVariablePropertyType().getName(),
                          variable.allowsUnassigned(),
                          identity(range),
                          size)
                  + "Maybe check that the value range's get(index), contains(value), and size agree");
        }
        basics
            .computeIfAbsent(variable, ignored -> new ArrayList<>())
            .add(new SolutionAssignments.BasicChangeRecord<>(entity, value));
      }
    }

    private boolean sampleLists(ListVariableDescriptor<Solution_> variable) {
      var rangeManager = director.getValueRangeManager();
      ValueRange<Object> universe =
          rangeManager.getFromSolution(variable.getValueRangeDescriptor());
      long size = universe.getSize();
      if (size < 0 || size > Integer.MAX_VALUE) {
        throw invalid(
            "requires a finite list universe for variable (%s) with size in [0, %d], but universe (%s) has size (%d)"
                .formatted(
                    variable.getSimpleEntityAndVariableName(),
                    Integer.MAX_VALUE,
                    identity(universe),
                    size));
      }
      var values = new Object[(int) size];
      var ids = new IdentityHashMap<Object, Integer>();
      var iterator = universe.createOriginalIterator();
      for (int i = 0; i < values.length; i++) {
        if (cancelled.getAsBoolean()) return false;
        if (!iterator.hasNext())
          throw invalid(
              "found list universe (%s) for variable (%s) with declared size (%d), but its iterator ended after (%d) values.\n"
                      .formatted(
                          identity(universe), variable.getSimpleEntityAndVariableName(), size, i)
                  + "Maybe make the value range's size and original iterator describe the same values");
        var value = iterator.next();
        if (value == null
            || !variable.acceptsValueType(value.getClass())
            || ids.put(value, i) != null) {
          throw invalid(
              "found a null, incompatible or repeated canonical list value (%s) at universe index (%d) for variable (%s): expectedType (%s), universe (%s), declared size (%d).\n"
                      .formatted(
                          describe(value),
                          i,
                          variable.getSimpleEntityAndVariableName(),
                          variable.getElementType().getName(),
                          identity(universe),
                          size)
                  + "Maybe return each compatible, non-null canonical value instance exactly once from the list value range");
        }
        values[i] = value;
      }
      if (iterator.hasNext())
        throw invalid(
            "found list universe (%s) for variable (%s) with declared size (%d), but its iterator contains at least (%d) values.\n"
                    .formatted(
                        identity(universe),
                        variable.getSimpleEntityAndVariableName(),
                        size,
                        size + 1)
                + "Maybe make the value range's size and original iterator describe the same values");
      var owners = new ArrayList<Object>();
      variable.getEntityDescriptor().visitAllEntities(solution, owners::add);
      var ownerIds = new IdentityHashMap<Object, Boolean>();
      var ranges = new ArrayList<ValueRange<Object>>(owners.size());
      var targets = new ArrayList<List<Object>>(owners.size());
      var movable = new boolean[owners.size()];
      var assigned = new boolean[values.length];
      var pinned = new boolean[values.length];
      var baselineRecords = new ArrayList<SolutionAssignments.ListChangeRecord<?>>();
      for (int ownerId = 0; ownerId < owners.size(); ownerId++) {
        if (cancelled.getAsBoolean()) return false;
        var owner = owners.get(ownerId);
        if (ownerIds.put(owner, Boolean.TRUE) != null)
          throw invalid(
              "found repeated owner (%s) at owner index (%d) for list variable (%s)"
                  .formatted(identity(owner), ownerId, variable.getSimpleEntityAndVariableName()));
        var current = variable.getValue(owner);
        if (current == null)
          throw invalid(
              "requires list variable (%s) on owner (%s) to be non-null, but its current value is (null).\n"
                      .formatted(variable.getSimpleEntityAndVariableName(), identity(owner))
                  + "Maybe initialize the list to an empty mutable list");
        ValueRange<Object> range =
            rangeManager.getFromEntity(variable.getValueRangeDescriptor(), owner);
        long ownerRangeSize = range.getSize();
        if (ownerRangeSize < 0)
          throw invalid(
              "requires a finite owner-specific range for list variable (%s) on owner (%s), but valueRange (%s) has size (%d)"
                  .formatted(
                      variable.getSimpleEntityAndVariableName(),
                      identity(owner),
                      identity(range),
                      ownerRangeSize));
        ranges.add(range);
        movable[ownerId] =
            director
                .getSolutionDescriptor()
                .findEntityDescriptorOrFail(owner.getClass())
                .isMovable(solution, owner);
        int firstUnpinned =
            movable[ownerId] ? variable.getFirstUnpinnedIndex(owner) : current.size();
        if (firstUnpinned < 0 || firstUnpinned > current.size()) {
          throw invalid(
              "found pinned index (%d) outside list size (%d) for variable (%s) on owner (%s), whose movable state is (%s)"
                  .formatted(
                      firstUnpinned,
                      current.size(),
                      variable.getSimpleEntityAndVariableName(),
                      identity(owner),
                      movable[ownerId]));
        }
        var target = new ArrayList<Object>();
        for (int index = 0; index < current.size(); index++) {
          if (cancelled.getAsBoolean()) return false;
          var value = current.get(index);
          var id = ids.get(value);
          if (id == null || assigned[id] || !range.contains(value)) {
            throw invalid(
                "found a noncanonical, duplicate or out-of-range list value (%s) at index (%d) for variable (%s) on owner (%s): canonicalValueId (%s), alreadyAssigned (%s), ownerRange (%s), rangeSize (%d).\n"
                        .formatted(
                            describe(value),
                            index,
                            variable.getSimpleEntityAndVariableName(),
                            identity(owner),
                            id,
                            id != null && assigned[id],
                            identity(range),
                            ownerRangeSize)
                    + "Maybe use the exact list-value-range instances, assign each at most once, and respect each owner's range");
          }
          assigned[id] = true;
          if (index < firstUnpinned) {
            pinned[id] = true;
            target.add(value);
          }
        }
        baselineRecords.add(new SolutionAssignments.ListChangeRecord<>(owner, current));
        targets.add(target);
      }
      var remaining = new ArrayList<Integer>();
      for (int id = 0; id < values.length; id++) {
        if (cancelled.getAsBoolean()) return false;
        if (!pinned[id]) remaining.add(id);
      }
      for (int i = remaining.size() - 1; i > 0; i--) {
        if (cancelled.getAsBoolean()) return false;
        int other = random.nextInt(i + 1);
        var value = remaining.get(i);
        remaining.set(i, remaining.get(other));
        remaining.set(other, value);
      }
      var destinations = new ArrayList<Integer>();
      for (int id : remaining) {
        if (cancelled.getAsBoolean()) return false;
        destinations.clear();
        for (int owner = 0; owner < owners.size(); owner++) {
          if (cancelled.getAsBoolean()) return false;
          if (movable[owner] && ranges.get(owner).contains(values[id])) destinations.add(owner);
        }
        if (variable.allowsUnassignedValues()) destinations.add(-1);
        if (destinations.isEmpty()) {
          throw invalid(
              "cannot assign required list value (%s), canonicalValueId (%d), for variable (%s): no compatible movable owner exists among (%d) owners, and allowsUnassignedValues is (%s).\n"
                      .formatted(
                          describe(values[id]),
                          id,
                          variable.getSimpleEntityAndVariableName(),
                          owners.size(),
                          variable.allowsUnassignedValues())
                  + "Maybe provide a compatible unpinned owner or correct the owner-specific value ranges");
        }
        int owner =
            destinations.get(destinations.size() == 1 ? 0 : random.nextInt(destinations.size()));
        if (owner >= 0) targets.get(owner).add(values[id]);
      }
      var targetRecords = new ArrayList<SolutionAssignments.ListChangeRecord<?>>();
      for (int owner = 0; owner < owners.size(); owner++) {
        if (cancelled.getAsBoolean()) return false;
        if (movable[owner]) {
          targetRecords.add(
              new SolutionAssignments.ListChangeRecord<>(owners.get(owner), targets.get(owner)));
        }
      }
      oldLists.put(variable, baselineRecords);
      lists.put(variable, targetRecords);
      return true;
    }

    private static boolean accepts(
        BasicVariableDescriptor<?> variable, ValueRange<Object> range, @Nullable Object value) {
      return value == null
          ? variable.allowsUnassigned() && range.contains(null)
          : variable.acceptsValueType(value.getClass()) && range.contains(value);
    }
  }
}
