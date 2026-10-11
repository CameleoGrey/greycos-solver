package greycos.solver.core.impl.multiplelist;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.entity.PlanningPinToIndex;
import greycos.solver.core.api.cotwin.lookup.PlanningId;
import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.solution.ProblemFactCollectionProperty;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.cotwin.variable.IndexShadowVariable;
import greycos.solver.core.api.cotwin.variable.InverseRelationShadowVariable;
import greycos.solver.core.api.cotwin.variable.NextElementShadowVariable;
import greycos.solver.core.api.cotwin.variable.PlanningListVariable;
import greycos.solver.core.api.cotwin.variable.PlanningVariable;
import greycos.solver.core.api.cotwin.variable.PreviousElementShadowVariable;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.api.score.stream.ConstraintFactory;
import greycos.solver.core.api.score.stream.ConstraintProvider;
import greycos.solver.core.api.score.stream.Joiners;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(30)
class MultipleListInheritanceSolverTest {

  @Test
  void automaticPhasesInitializeThreeFamiliesAndKeepInheritedPinsAndShadows() {
    var descriptor =
        SolutionDescriptor.buildSolutionDescriptor(
            ThreeListSolution.class, BaseOwner.class, ChildOwner.class, Visit.class, Cargo.class);
    var first = descriptor.resolveListVariableDescriptor(BaseOwner.class, "firstList");
    var second = descriptor.resolveListVariableDescriptor(ChildOwner.class, "secondList");
    var third = descriptor.resolveListVariableDescriptor(ChildOwner.class, "thirdList");
    assertThat(descriptor.getListVariableDescriptorList()).containsExactly(first, second, third);
    assertThat(
            descriptor.getEntityDescriptorStrict(ChildOwner.class).getListVariableDescriptorList())
        .containsExactly(first, second, third);
    assertThat(
            descriptor.getEntityDescriptorStrict(BaseOwner.class).getListVariableDescriptorList())
        .containsExactly(first);

    var problem = problem();
    var pinnedOwner = problem.owners.getFirst();
    assertThat(first.getFirstUnpinnedIndex(pinnedOwner)).isEqualTo(1);
    assertThat(second.getFirstUnpinnedIndex(pinnedOwner)).isZero();
    assertThat(third.getFirstUnpinnedIndex(pinnedOwner)).isEqualTo(2);
    assertThat(problem.owners).allSatisfy(owner -> assertThat(owner.level).isNull());
    assertThat(problem.cargo).allSatisfy(value -> assertThat(value.quantity).isNull());

    var config =
        new SolverConfig()
            .withSolutionClass(ThreeListSolution.class)
            .withEntityClasses(BaseOwner.class, ChildOwner.class, Visit.class, Cargo.class)
            .withConstraintProviderClass(ThreeListConstraints.class)
            .withEnvironmentMode(EnvironmentMode.FULL_ASSERT)
            .withRandomSeed(17L)
            .withTerminationConfig(new TerminationConfig().withStepCountLimit(45));
    // No explicit phases or selectors: all three list families must be discovered automatically.
    var result = SolverFactory.<ThreeListSolution>create(config).buildSolver().solve(problem);

    assertThat(result.owners.getFirst().firstList.getFirst().id).isEqualTo("visit-0");
    assertThat(result.owners.getFirst().thirdList.subList(0, 2))
        .containsExactly("label-0", "label-1");
    assertCompleteState(result);
    assertThat(result.score).isEqualTo(scoreFromGenuineState(result));
  }

  private static ThreeListSolution problem() {
    var solution = new ThreeListSolution();
    for (int i = 0; i < 2; i++) {
      var owner = new ChildOwner();
      owner.id = "owner-" + i;
      solution.owners.add(owner);
    }
    for (int i = 0; i < 4; i++) {
      var visit = new Visit();
      visit.id = "visit-" + i;
      visit.weight = i + 1;
      solution.visits.add(visit);
      solution.labels.add("label-" + i);
    }
    for (int i = 0; i < 3; i++) {
      var cargo = new Cargo();
      cargo.id = "cargo-" + i;
      cargo.weight = i + 2;
      solution.cargo.add(cargo);
    }
    var owner = solution.owners.getFirst();
    owner.firstList.add(solution.visits.getFirst());
    owner.firstPinnedIndex = 1;
    owner.thirdList.addAll(solution.labels.subList(0, 2));
    owner.thirdPinnedIndex = 2;
    return solution;
  }

  private static void assertCompleteState(ThreeListSolution solution) {
    var seen = new IdentityHashMap<Object, Boolean>();
    for (var owner : solution.owners) {
      assertThat(owner.level).isIn(solution.numbers);
      for (int i = 0; i < owner.firstList.size(); i++) {
        var value = owner.firstList.get(i);
        assertThat(seen.put(value, true)).isNull();
        assertThat(solution.visits).contains(value);
        assertThat(value.owner).isSameAs(owner);
        assertThat(value.index).isEqualTo(i);
        assertThat(value.previous).isSameAs(i == 0 ? null : owner.firstList.get(i - 1));
        assertThat(value.next)
            .isSameAs(i + 1 == owner.firstList.size() ? null : owner.firstList.get(i + 1));
      }
      for (int i = 0; i < owner.secondList.size(); i++) {
        var value = owner.secondList.get(i);
        assertThat(seen.put(value, true)).isNull();
        assertThat(solution.cargo).contains(value);
        assertThat(value.quantity).isIn(solution.numbers);
        assertThat(value.owner).isSameAs(owner);
        assertThat(value.index).isEqualTo(i);
        assertThat(value.previous).isSameAs(i == 0 ? null : owner.secondList.get(i - 1));
        assertThat(value.next)
            .isSameAs(i + 1 == owner.secondList.size() ? null : owner.secondList.get(i + 1));
      }
      for (var value : owner.thirdList) {
        assertThat(seen.put(value, true)).isNull();
        assertThat(solution.labels).contains(value);
      }
    }
    assertThat(seen)
        .hasSize(solution.visits.size() + solution.cargo.size() + solution.labels.size());
  }

  private static SimpleScore scoreFromGenuineState(ThreeListSolution solution) {
    int penalty = 0;
    for (var owner : solution.owners) {
      penalty +=
          Math.abs(owner.firstList.size() - owner.secondList.size())
              + Math.abs(owner.secondList.size() - owner.thirdList.size())
              + Math.abs(owner.level - owner.firstList.size() % 3);
      for (int i = 0; i < owner.firstList.size(); i++) {
        penalty += (i + 1) * owner.firstList.get(i).weight + owner.level;
      }
      for (int i = 0; i < owner.secondList.size(); i++) {
        var cargo = owner.secondList.get(i);
        penalty += (i + 1) * cargo.weight + cargo.quantity;
      }
    }
    return SimpleScore.of(-penalty);
  }

  public static class ThreeListConstraints implements ConstraintProvider {
    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      return new Constraint[] {
        factory
            .forEach(BaseOwner.class)
            .penalize(
                SimpleScore.ONE,
                base -> {
                  var owner = (ChildOwner) base;
                  return Math.abs(owner.firstList.size() - owner.secondList.size())
                      + Math.abs(owner.secondList.size() - owner.thirdList.size())
                      + Math.abs(owner.level - owner.firstList.size() % 3);
                })
            .asConstraint("coupled lists and owner basic variable"),
        factory
            .forEach(Visit.class)
            .join(BaseOwner.class, Joiners.equal(value -> value.owner, owner -> owner))
            .penalize(
                SimpleScore.ONE, (value, owner) -> (value.index + 1) * value.weight + owner.level)
            .asConstraint("visit shadow position"),
        factory
            .forEach(Cargo.class)
            .penalize(SimpleScore.ONE, value -> (value.index + 1) * value.weight + value.quantity)
            .asConstraint("cargo shadow position and basic variable")
      };
    }
  }

  @PlanningSolution
  public static class ThreeListSolution {
    @PlanningEntityCollectionProperty public List<ChildOwner> owners = new ArrayList<>();

    @PlanningEntityCollectionProperty
    @ValueRangeProvider(id = "visitRange")
    public List<Visit> visits = new ArrayList<>();

    @PlanningEntityCollectionProperty
    @ValueRangeProvider(id = "cargoRange")
    public List<Cargo> cargo = new ArrayList<>();

    @ProblemFactCollectionProperty
    @ValueRangeProvider(id = "labelRange")
    public List<String> labels = new ArrayList<>();

    @ProblemFactCollectionProperty
    @ValueRangeProvider(id = "numberRange")
    public List<Integer> numbers = List.of(0, 1, 2);

    @PlanningScore public SimpleScore score;
  }

  @PlanningEntity
  public static class BaseOwner {
    @PlanningId public String id;

    @PlanningListVariable(valueRangeProviderRefs = "visitRange")
    public List<Visit> firstList = new ArrayList<>();

    @PlanningPinToIndex public int firstPinnedIndex;

    @PlanningVariable(valueRangeProviderRefs = "numberRange")
    public Integer level;
  }

  @PlanningEntity
  public static class ChildOwner extends BaseOwner {
    @PlanningListVariable(valueRangeProviderRefs = "cargoRange")
    public List<Cargo> secondList = new ArrayList<>();

    @PlanningListVariable(valueRangeProviderRefs = "labelRange")
    public List<String> thirdList = new ArrayList<>();

    @PlanningPinToIndex(variableName = "thirdList")
    public int thirdPinnedIndex;
  }

  @PlanningEntity
  public static class Visit {
    @PlanningId public String id;
    public int weight;

    @InverseRelationShadowVariable(sourceVariableName = "firstList")
    public BaseOwner owner;

    @IndexShadowVariable(sourceVariableName = "firstList")
    public Integer index;

    @PreviousElementShadowVariable(sourceVariableName = "firstList")
    public Visit previous;

    @NextElementShadowVariable(sourceVariableName = "firstList")
    public Visit next;
  }

  @PlanningEntity
  public static class Cargo {
    @PlanningId public String id;
    public int weight;

    @InverseRelationShadowVariable(sourceVariableName = "secondList")
    public ChildOwner owner;

    @IndexShadowVariable(sourceVariableName = "secondList")
    public Integer index;

    @PreviousElementShadowVariable(sourceVariableName = "secondList")
    public Cargo previous;

    @NextElementShadowVariable(sourceVariableName = "secondList")
    public Cargo next;

    @PlanningVariable(valueRangeProviderRefs = "numberRange")
    public Integer quantity;
  }
}
