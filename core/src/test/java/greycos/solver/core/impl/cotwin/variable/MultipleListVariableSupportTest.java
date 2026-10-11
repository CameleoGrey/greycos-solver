package greycos.solver.core.impl.cotwin.variable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.cotwin.variable.CascadingUpdateShadowVariable;
import greycos.solver.core.api.cotwin.variable.IndexShadowVariable;
import greycos.solver.core.api.cotwin.variable.InverseRelationShadowVariable;
import greycos.solver.core.api.cotwin.variable.PlanningListVariable;
import greycos.solver.core.api.cotwin.variable.PreviousElementShadowVariable;
import greycos.solver.core.api.cotwin.variable.ShadowSources;
import greycos.solver.core.api.cotwin.variable.ShadowVariable;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.solver.SolutionManager;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import greycos.solver.core.impl.cotwin.variable.declarative.GraphStructure;
import greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import greycos.solver.core.impl.cotwin.variable.violation.VariableId;
import greycos.solver.core.impl.score.director.WorkingSolutionMutationObserver;
import greycos.solver.core.impl.score.director.easy.EasyScoreDirectorFactory;
import greycos.solver.core.testcotwin.multiplelist.TestdataMultipleListSolution;

import org.junit.jupiter.api.Test;

class MultipleListVariableSupportTest {

  @Test
  void independentStatesAndTrackersRetainDescriptorIdentityAcrossOpenChanges() {
    var descriptor = TestdataMultipleListSolution.buildSolutionDescriptor();
    var firstDescriptor = descriptor.getListVariableDescriptorList().get(0);
    var secondDescriptor = descriptor.getListVariableDescriptorList().get(1);
    var solution = TestdataMultipleListSolution.generateInitializedSolution(3, 3, 1);
    var owner = solution.getEntityList().getFirst();
    try (var director =
        new EasyScoreDirectorFactory<>(
                descriptor, ignored -> SimpleScore.ZERO, EnvironmentMode.NO_ASSERT)
            .buildScoreDirector()) {
      director.setWorkingSolution(solution);
      @SuppressWarnings("unchecked")
      var support = (VariableSupport<TestdataMultipleListSolution>) director.getSupplyManager();
      var firstState = support.getListVariableState(firstDescriptor);
      var secondState = support.getListVariableState(secondDescriptor);
      var firstTracker = support.getListVariableTracker(firstDescriptor);
      var secondTracker = support.getListVariableTracker(secondDescriptor);
      assertThat(firstState).isNotSameAs(secondState);
      assertThat(firstTracker).isNotSameAs(secondTracker);
      assertThat(support.getListVariableState(firstDescriptor)).isSameAs(firstState);
      assertThat(support.getListVariableTracker(secondDescriptor)).isSameAs(secondTracker);

      director.beforeListVariableChanged(owner, "firstValueList", 0, 3);
      director.beforeListVariableChanged(owner, "secondValueList", 0, 3);
      Collections.reverse(owner.getFirstValueList());
      Collections.swap(owner.getSecondValueList(), 0, 1);
      director.afterListVariableChanged(owner, "secondValueList", 0, 3);
      director.afterListVariableChanged(owner, "firstValueList", 0, 3);
      director.updateShadowVariables();
      assertRelationships(solution);
      assertThat(
              firstTracker.getEntitiesMissingBeforeAfterEvents(
                  List.of(new VariableId<>(firstDescriptor, owner))))
          .isEmpty();
      assertThat(
              secondTracker.getEntitiesMissingBeforeAfterEvents(
                  List.of(new VariableId<>(secondDescriptor, owner))))
          .isEmpty();

      director.beforeListVariableChanged(owner, "firstValueList", 0, 3);
      director.afterListVariableChanged(owner, "firstValueList", 0, 3);
      director.updateShadowVariables();
      assertThat(
              firstTracker.getEntitiesMissingBeforeAfterEvents(
                  List.of(new VariableId<>(firstDescriptor, owner))))
          .isEmpty();
      assertThat(
              secondTracker.getEntitiesMissingBeforeAfterEvents(
                  List.of(new VariableId<>(secondDescriptor, owner))))
          .hasSize(2);
      assertThatThrownBy(
              () ->
                  support.getListVariableState(
                      TestdataMultipleListSolution.buildSolutionDescriptor()
                          .getListVariableDescriptorList()
                          .getFirst()))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("does not belong");
    }
  }

  @Test
  void unassignmentAndReplacementClearOnlyTheApplicableState() {
    var descriptor = TestdataMultipleListSolution.buildSolutionDescriptor();
    var solution = TestdataMultipleListSolution.generateInitializedSolution(2, 2, 1);
    var owner = solution.getEntityList().getFirst();
    var removed = owner.getFirstValueList().getFirst();
    var second = owner.getSecondValueList().getFirst();
    var firstDescriptor = descriptor.getListVariableDescriptorList().getFirst();
    try (var director =
        new EasyScoreDirectorFactory<>(
                descriptor, ignored -> SimpleScore.ZERO, EnvironmentMode.NO_ASSERT)
            .buildScoreDirector()) {
      director.setWorkingSolution(solution);
      director.beforeListVariableElementUnassigned(firstDescriptor, removed);
      director.beforeListVariableChanged(owner, "firstValueList", 0, 1);
      owner.getFirstValueList().removeFirst();
      director.afterListVariableChanged(owner, "firstValueList", 0, 0);
      director.afterListVariableElementUnassigned(firstDescriptor, removed);
      director.updateShadowVariables();
      assertThat(removed.getEntity()).isNull();
      assertThat(removed.getIndex()).isNull();
      assertThat(removed.getPrevious()).isNull();
      assertThat(removed.getNext()).isNull();
      assertThat(second.getEntity()).isSameAs(owner);
      assertThat(second.getIndex()).isZero();
      assertRelationships(solution);

      var replacement = TestdataMultipleListSolution.generateInitializedSolution(1, 3, 1);
      director.setWorkingSolution(replacement);
      assertRelationships(replacement);
      assertThat(second.getEntity()).isSameAs(owner);
    }
  }

  @Test
  void mutationObserverAttachesAndDetachesAcrossEveryContext() {
    var solution = TestdataMultipleListSolution.generateInitializedSolution(2, 2, 1);
    var owner = solution.getEntityList().getFirst();
    var descriptor = TestdataMultipleListSolution.buildSolutionDescriptor();
    var observed = new ArrayList<ListVariableDescriptor<TestdataMultipleListSolution>>();
    var observer =
        new WorkingSolutionMutationObserver<TestdataMultipleListSolution>() {
          @Override
          public void workingSolutionChanged() {}

          @Override
          public boolean requiresListVariableRelationshipChanges() {
            return true;
          }

          @Override
          public void afterListVariableRelationshipChanged(
              ListVariableDescriptor<TestdataMultipleListSolution> variableDescriptor,
              Object element) {
            observed.add(variableDescriptor);
          }
        };
    try (var director =
        new EasyScoreDirectorFactory<>(
                descriptor, ignored -> SimpleScore.ZERO, EnvironmentMode.NO_ASSERT)
            .buildScoreDirector()) {
      director.setWorkingSolution(solution);
      director.setWorkingSolutionMutationObserver(observer);
      for (var variable : descriptor.getListVariableDescriptorList()) {
        director.beforeListVariableChanged(variable, owner, 0, 2);
        Collections.reverse(variable.getValue(owner));
        director.afterListVariableChanged(variable, owner, 0, 2);
      }
      director.updateShadowVariables();
      assertThat(observed)
          .containsExactly(
              descriptor.getListVariableDescriptorList().get(0),
                  descriptor.getListVariableDescriptorList().get(0),
              descriptor.getListVariableDescriptorList().get(1),
                  descriptor.getListVariableDescriptorList().get(1));
      observed.clear();
      director.setWorkingSolutionMutationObserver(null);
      for (var variable : descriptor.getListVariableDescriptorList()) {
        director.beforeListVariableChanged(variable, owner, 0, 2);
        Collections.reverse(variable.getValue(owner));
        director.afterListVariableChanged(variable, owner, 0, 2);
      }
      director.updateShadowVariables();
      assertThat(observed).isEmpty();
      assertRelationships(solution);
    }
  }

  @Test
  void entityOnlyHelperRepairsBothListsAndRemovedValues() {
    var solution = TestdataMultipleListSolution.generateInitializedSolution(2, 3, 1);
    var owner = solution.getEntityList().getFirst();
    var removed = owner.getSecondValueList().removeFirst();
    Collections.reverse(owner.getFirstValueList());
    var entities = new ArrayList<Object>(solution.getEntityList());
    entities.addAll(solution.getFirstValueList());
    entities.addAll(solution.getSecondValueList());
    Collections.reverse(entities);
    SolutionManager.updateShadowVariables(TestdataMultipleListSolution.class, entities.toArray());
    assertRelationships(solution);
    assertThat(removed.getEntity()).isNull();
    assertThat(removed.getIndex()).isNull();
    assertThat(removed.getPrevious()).isNull();
    assertThat(removed.getNext()).isNull();
  }

  private static void assertRelationships(TestdataMultipleListSolution solution) {
    for (var owner : solution.getEntityList()) {
      for (var i = 0; i < owner.getFirstValueList().size(); i++) {
        var value = owner.getFirstValueList().get(i);
        assertThat(value.getEntity()).isSameAs(owner);
        assertThat(value.getIndex()).isEqualTo(i);
        assertThat(value.getPrevious())
            .isSameAs(i == 0 ? null : owner.getFirstValueList().get(i - 1));
        assertThat(value.getNext())
            .isSameAs(
                i + 1 == owner.getFirstValueList().size()
                    ? null
                    : owner.getFirstValueList().get(i + 1));
      }
      for (var i = 0; i < owner.getSecondValueList().size(); i++) {
        var value = owner.getSecondValueList().get(i);
        assertThat(value.getEntity()).isSameAs(owner);
        assertThat(value.getIndex()).isEqualTo(i);
        assertThat(value.getPrevious())
            .isSameAs(i == 0 ? null : owner.getSecondValueList().get(i - 1));
        assertThat(value.getNext())
            .isSameAs(
                i + 1 == owner.getSecondValueList().size()
                    ? null
                    : owner.getSecondValueList().get(i + 1));
      }
    }
  }

  @Test
  void directionalGraphUsesItsOwnSourceAndCascadesStayLocal() {
    var solution = ShadowSolution.create();
    var descriptor = ShadowSolution.descriptor();
    assertThat(
            GraphStructure.determineGraphStructure(descriptor, solution.seconds.toArray())
                .structure())
        .isEqualTo(GraphStructure.SINGLE_DIRECTIONAL_PARENT);
    try (var director =
        new EasyScoreDirectorFactory<>(
                descriptor, ignored -> SimpleScore.ZERO, EnvironmentMode.NO_ASSERT)
            .buildScoreDirector()) {
      director.setWorkingSolution(solution);
      assertThat(solution.seconds).extracting(v -> v.total).containsExactly(1, 3, 6);
      assertThat(solution.firsts).extracting(v -> v.total).containsExactly(1, 2, 3);
      solution.firsts.forEach(v -> v.calls = 0);
      solution.seconds.forEach(v -> v.calls = 0);
      director.beforeListVariableChanged(solution.owner, "seconds", 0, 3);
      Collections.reverse(solution.owner.seconds);
      director.afterListVariableChanged(solution.owner, "seconds", 0, 3);
      director.updateShadowVariables();
      assertThat(solution.owner.seconds).extracting(v -> v.total).containsExactly(1, 3, 6);
      assertThat(solution.owner.seconds).extracting(v -> v.cascaded).containsExactly(2, 6, 12);
      assertThat(solution.firsts).allSatisfy(v -> assertThat(v.calls).isZero());
    }
  }

  @Test
  void cascadeFailurePoisonsAllQueuesUntilRefreshAndReplacementDropsOldReferences() {
    var solution = ShadowSolution.create();
    try (var director =
        new EasyScoreDirectorFactory<>(
                ShadowSolution.descriptor(), ignored -> SimpleScore.ZERO, EnvironmentMode.NO_ASSERT)
            .buildScoreDirector()) {
      director.setWorkingSolution(solution);
      director.beforeListVariableChanged(solution.owner, "firsts", 0, 3);
      Collections.reverse(solution.owner.firsts);
      director.afterListVariableChanged(solution.owner, "firsts", 0, 3);
      director.beforeListVariableChanged(solution.owner, "seconds", 0, 3);
      Collections.reverse(solution.owner.seconds);
      director.afterListVariableChanged(solution.owner, "seconds", 0, 3);
      solution.owner.firsts.getFirst().fail = true;
      assertThatThrownBy(director::updateShadowVariables)
          .isInstanceOf(IllegalStateException.class)
          .hasRootCauseMessage("cascade failure");
      assertThat(director.isLastVariableUpdateSuccessful()).isFalse();
      var secondCalls = solution.seconds.stream().mapToInt(v -> v.calls).sum();
      solution.owner.firsts.getFirst().fail = false;
      director.updateShadowVariables();
      assertThat(solution.seconds.stream().mapToInt(v -> v.calls).sum()).isEqualTo(secondCalls);
      director.forceUpdateShadowVariables();
      assertThat(director.isLastVariableUpdateSuccessful()).isTrue();
      assertThat(solution.owner.firsts).extracting(v -> v.total).containsExactly(1, 2, 3);
      assertThat(solution.owner.seconds).extracting(v -> v.cascaded).containsExactly(2, 6, 12);

      director.beforeListVariableChanged(solution.owner, "seconds", 0, 3);
      director.afterListVariableChanged(solution.owner, "seconds", 0, 3);
      secondCalls = solution.seconds.stream().mapToInt(v -> v.calls).sum();
      director.setWorkingSolution(ShadowSolution.create());
      assertThat(solution.seconds.stream().mapToInt(v -> v.calls).sum()).isEqualTo(secondCalls);
    }
  }

  @Test
  void entityOnlyHelperRunsEachCascadeInItsListOrder() {
    var solution = ShadowSolution.create();
    var entities = new ArrayList<Object>();
    entities.addAll(solution.seconds);
    entities.addAll(solution.firsts);
    entities.add(solution.owner);
    Collections.reverse(entities);
    SolutionManager.updateShadowVariables(ShadowSolution.class, entities.toArray());
    assertThat(solution.owner.firsts).extracting(v -> v.total).containsExactly(1, 2, 3);
    assertThat(solution.owner.seconds).extracting(v -> v.total).containsExactly(1, 3, 6);
    assertThat(solution.owner.seconds).extracting(v -> v.cascaded).containsExactly(2, 6, 12);
  }

  @Test
  void sharedDeclarativeDependencyObservesBothListContexts() {
    var source = ShadowSolution.create();
    var solution = new CrossSolution();
    solution.owner = source.owner;
    solution.owners = source.owners;
    solution.firsts = source.firsts;
    solution.seconds = source.seconds;
    var summary = new Summary();
    summary.first = solution.firsts.getFirst();
    summary.second = solution.seconds.getFirst();
    solution.summaries = List.of(summary);
    var descriptor =
        SolutionDescriptor.buildSolutionDescriptor(
            CrossSolution.class, Owner.class, First.class, Second.class, Summary.class);
    try (var director =
        new EasyScoreDirectorFactory<>(
                descriptor, ignored -> SimpleScore.ZERO, EnvironmentMode.NO_ASSERT)
            .buildScoreDirector()) {
      director.setWorkingSolution(solution);
      assertThat(summary.total).isOne();
      director.beforeListVariableChanged(solution.owner, "firsts", 0, 3);
      Collections.reverse(solution.owner.firsts);
      director.afterListVariableChanged(solution.owner, "firsts", 0, 3);
      director.updateShadowVariables();
      assertThat(summary.total).isEqualTo(3);
      director.beforeListVariableChanged(solution.owner, "seconds", 0, 3);
      Collections.reverse(solution.owner.seconds);
      director.afterListVariableChanged(solution.owner, "seconds", 0, 3);
      director.updateShadowVariables();
      assertThat(summary.total).isEqualTo(8);
    }
  }

  @PlanningSolution
  public static class CrossSolution extends ShadowSolution {
    @PlanningEntityCollectionProperty public List<Summary> summaries;
  }

  @PlanningEntity
  public static class Summary {
    public First first;
    public Second second;

    @ShadowVariable(supplierName = "totalSupplier")
    public Integer total;

    @ShadowSources({"first.index", "second.total"})
    public Integer totalSupplier() {
      return first.index + second.total;
    }
  }

  @PlanningSolution
  public static class ShadowSolution {
    @PlanningEntityCollectionProperty public List<Owner> owners;

    @PlanningEntityCollectionProperty
    @ValueRangeProvider(id = "firsts")
    public List<First> firsts;

    @PlanningEntityCollectionProperty
    @ValueRangeProvider(id = "seconds")
    public List<Second> seconds;

    @PlanningScore public SimpleScore score;
    public Owner owner;

    static SolutionDescriptor<ShadowSolution> descriptor() {
      return SolutionDescriptor.buildSolutionDescriptor(
          ShadowSolution.class, Owner.class, First.class, Second.class);
    }

    static ShadowSolution create() {
      var solution = new ShadowSolution();
      solution.owner = new Owner();
      solution.owners = List.of(solution.owner);
      solution.firsts = List.of(new First(), new First(), new First());
      solution.seconds = List.of(new Second(), new Second(), new Second());
      solution.owner.firsts.addAll(solution.firsts);
      solution.owner.seconds.addAll(solution.seconds);
      return solution;
    }
  }

  @PlanningEntity
  public static class Owner {
    @PlanningListVariable(valueRangeProviderRefs = "firsts", allowsUnassignedValues = true)
    public List<First> firsts = new ArrayList<>();

    @PlanningListVariable(valueRangeProviderRefs = "seconds", allowsUnassignedValues = true)
    public List<Second> seconds = new ArrayList<>();
  }

  @PlanningEntity
  public static class First {
    @InverseRelationShadowVariable(sourceVariableName = "firsts")
    public Owner owner;

    @IndexShadowVariable(sourceVariableName = "firsts")
    public Integer index;

    @PreviousElementShadowVariable(sourceVariableName = "firsts")
    public First previous;

    @CascadingUpdateShadowVariable(targetMethodName = "updateTotal")
    public Integer total;

    public int calls;
    public boolean fail;

    public void updateTotal() {
      if (fail) {
        throw new IllegalStateException("cascade failure");
      }
      calls++;
      total = owner == null ? 0 : 1 + (previous == null ? 0 : previous.total);
    }
  }

  @PlanningEntity
  public static class Second {
    @InverseRelationShadowVariable(sourceVariableName = "seconds")
    public Owner owner;

    @IndexShadowVariable(sourceVariableName = "seconds")
    public Integer index;

    @PreviousElementShadowVariable(sourceVariableName = "seconds")
    public Second previous;

    @ShadowVariable(supplierName = "totalSupplier")
    public Integer total;

    @CascadingUpdateShadowVariable(targetMethodName = "updateCascade")
    public Integer cascaded;

    public int calls;

    @ShadowSources({"previous.total", "index"})
    public Integer totalSupplier() {
      return index == null ? 0 : index + 1 + (previous == null ? 0 : previous.total);
    }

    public void updateCascade() {
      calls++;
      cascaded = total * 2;
    }
  }
}
