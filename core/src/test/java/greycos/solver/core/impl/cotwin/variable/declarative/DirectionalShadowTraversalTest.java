package greycos.solver.core.impl.cotwin.variable.declarative;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.stream.Stream;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.cotwin.variable.IndexShadowVariable;
import greycos.solver.core.api.cotwin.variable.InverseRelationShadowVariable;
import greycos.solver.core.api.cotwin.variable.NextElementShadowVariable;
import greycos.solver.core.api.cotwin.variable.PlanningListVariable;
import greycos.solver.core.api.cotwin.variable.PlanningVariable;
import greycos.solver.core.api.cotwin.variable.PreviousElementShadowVariable;
import greycos.solver.core.api.cotwin.variable.ShadowSources;
import greycos.solver.core.api.cotwin.variable.ShadowVariable;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.score.director.easy.EasyScoreDirectorFactory;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class DirectionalShadowTraversalTest {

  enum Storage {
    ARRAY,
    LINKED_EXTERNALIZED,
    LINKED_ITERATOR
  }

  static Stream<Arguments> scenarios() {
    return Stream.of(true, false)
        .flatMap(
            forward -> Stream.of(Storage.values()).map(storage -> Arguments.of(forward, storage)));
  }

  @ParameterizedTest
  @MethodSource("scenarios")
  void fullPropagationHasLinearTraversal(boolean forward, Storage storage) {
    var solution = TraversalSolution.create(forward, storage, 1, 256, false);
    try (var director = director(forward, storage)) {
      director.setWorkingSolution(solution);
      var state =
          director.getListVariableState(
              director.getSolutionDescriptor().getListVariableDescriptorList().getFirst());
      assertThat(
              forward ? state.isNextElementExternalized() : state.isPreviousElementExternalized())
          .isEqualTo(storage == Storage.LINKED_EXTERNALIZED);
      var root = solution.values.get(forward ? 0 : 255);
      changeInput(director, root);
      resetCounters(solution);
      director.updateShadowVariables();

      assertOperations(solution.routes.getFirst(), storage, 255, 255, 1);
      assertThat(solution.values).allSatisfy(value -> assertThat(value.calls).isOne());
      assertReplay(solution, forward);
    }
  }

  @ParameterizedTest
  @MethodSource("scenarios")
  void sparseSeedsShareOneCursorPerOwnerAndDiscardItBetweenUpdates(
      boolean forward, Storage storage) {
    var solution = TraversalSolution.create(forward, storage, 2, 64, true);
    try (var director = director(forward, storage)) {
      director.setWorkingSolution(solution);
      // Equal owners and equal seed indexes must still have independent cursors.
      assertThat(solution.routes.get(0))
          .isEqualTo(solution.routes.get(1))
          .isNotSameAs(solution.routes.get(1));
      for (var route : solution.routes) {
        var values = List.copyOf(route.values);
        for (var offset = 0; offset < values.size(); offset += 4) {
          changeInput(director, values.get(forward ? offset : values.size() - 1 - offset));
        }
      }
      resetCounters(solution);
      director.updateShadowVariables();
      for (var route : solution.routes) {
        // Each changed seed changes by two; its successor observes the same parity and stops.
        assertOperations(route, storage, 16, 61, 1);
        assertThat(route.values.stream().mapToInt(value -> value.calls).sum()).isEqualTo(32);
      }
      assertReplay(solution, forward);

      // A notified no-op seed must not seek into a sequential list.
      var unchanged = solution.values.get(31);
      director.beforeVariableChanged(unchanged, "input");
      director.afterVariableChanged(unchanged, "input");
      resetCounters(solution);
      director.updateShadowVariables();
      for (var route : solution.routes) {
        assertOperations(route, storage, 0, 0, 0);
      }
      assertReplay(solution, forward);

      var first = solution.routes.get(0);
      var second = solution.routes.get(1);
      director.beforeListVariableChanged(first, "values", 0, first.values.size());
      Collections.reverse(first.values);
      director.afterListVariableChanged(first, "values", 0, first.values.size());
      director.updateShadowVariables();
      assertReplay(solution, forward);

      // Structural changes invalidate LinkedList iterators from the preceding pass.
      director.beforeListVariableChanged(first, "values", 10, first.values.size());
      director.beforeListVariableChanged(second, "values", 1, second.values.size());
      var moved = first.values.remove(10);
      second.values.add(1, moved);
      director.afterListVariableChanged(first, "values", 10, first.values.size());
      director.afterListVariableChanged(second, "values", 1, second.values.size());
      director.updateShadowVariables();
      assertReplay(solution, forward);

      var listDescriptor =
          director.getSolutionDescriptor().getListVariableDescriptorList().getFirst();
      director.beforeListVariableElementUnassigned(listDescriptor, moved);
      director.beforeListVariableChanged(second, "values", 1, second.values.size());
      second.values.remove(1);
      director.afterListVariableChanged(second, "values", 1, second.values.size());
      director.afterListVariableElementUnassigned(listDescriptor, moved);
      director.updateShadowVariables();
      assertThat(moved.route).isNull();
      assertThat(moved.getTotal()).isNull();
      assertReplay(solution, forward);
    }
  }

  @ParameterizedTest
  @MethodSource("scenarios")
  void boundariesAndOverlappingSeedsDoNotRepeatWork(boolean forward, Storage storage) {
    for (var size : new int[] {0, 1, 8}) {
      var solution = TraversalSolution.create(forward, storage, 1, size, false);
      try (var director = director(forward, storage)) {
        director.setWorkingSolution(solution);
        if (size == 0) {
          resetCounters(solution);
          director.updateShadowVariables();
          assertOperations(solution.routes.getFirst(), storage, 0, 0, 0);
          continue;
        }
        var end = solution.values.get(forward ? size - 1 : 0);
        changeInput(director, end);
        resetCounters(solution);
        director.updateShadowVariables();
        assertOperations(solution.routes.getFirst(), storage, 0, 0, 0);
        assertThat(end.calls).isOne();
        assertReplay(solution, forward);

        changeInput(director, solution.values.get(forward ? 0 : size - 1));
        if (size > 1) {
          changeInput(director, solution.values.get(size / 2));
          changeInput(director, end);
        }
        resetCounters(solution);
        director.updateShadowVariables();
        assertOperations(solution.routes.getFirst(), storage, size - 1, size - 1, size > 1 ? 1 : 0);
        assertThat(solution.values).allSatisfy(value -> assertThat(value.calls).isOne());
        assertReplay(solution, forward);
      }
    }
  }

  @ParameterizedTest
  @MethodSource("scenarios")
  void failureRecoveryDiscardsSequentialCursors(boolean forward, Storage storage) {
    var solution = TraversalSolution.create(forward, storage, 1, 8, false);
    try (var director = director(forward, storage)) {
      director.setWorkingSolution(solution);
      var root = solution.values.get(forward ? 0 : 7);
      var failed = solution.values.get(forward ? 2 : 5);
      failed.fail = true;
      changeInput(director, root);
      assertThatThrownBy(director::updateShadowVariables)
          .hasRootCauseMessage("expected supplier failure");
      assertThat(director.isLastVariableUpdateSuccessful()).isFalse();
      failed.fail = false;
      director.forceUpdateShadowVariables();
      assertReplay(solution, forward);

      var route = solution.routes.getFirst();
      director.beforeListVariableChanged(route, "values", 0, 8);
      route.values.add(route.values.removeFirst());
      director.afterListVariableChanged(route, "values", 0, 8);
      director.updateShadowVariables();
      assertReplay(solution, forward);
    }
  }

  private static void changeInput(
      InnerScoreDirector<TraversalSolution, SimpleScore> director, Value value) {
    director.beforeVariableChanged(value, "input");
    value.input = value.input == 1 ? 3 : 1;
    director.afterVariableChanged(value, "input");
  }

  private static void resetCounters(TraversalSolution solution) {
    solution.values.forEach(value -> value.calls = 0);
    solution.routes.forEach(route -> route.operations.clear());
  }

  private static void assertOperations(
      Route route, Storage storage, int indexedReads, int iteratorSteps, int iteratorCreations) {
    assertThat(route.operations.indexedReads)
        .isEqualTo(storage == Storage.ARRAY ? indexedReads : 0);
    assertThat(route.operations.iteratorCreations)
        .isEqualTo(storage == Storage.LINKED_ITERATOR ? iteratorCreations : 0);
    assertThat(route.operations.iteratorSteps)
        .isEqualTo(storage == Storage.LINKED_ITERATOR ? iteratorSteps : 0);
    assertThat(route.operations.iteratorSeekSteps).isLessThanOrEqualTo(iteratorCreations);
  }

  private static void assertReplay(TraversalSolution solution, boolean forward) {
    for (var route : solution.routes) {
      var values = List.copyOf(route.values);
      var total = 0;
      Value parent = null;
      for (var offset = 0; offset < values.size(); offset++) {
        var index = forward ? offset : values.size() - 1 - offset;
        var value = values.get(index);
        total = value.input + (value.sparse ? total % 2 : total);
        assertThat(value.route).isSameAs(route);
        assertThat(value.index).isEqualTo(index);
        assertThat(value.parent()).isSameAs(parent);
        assertThat(value.getTotal()).isEqualTo(total);
        parent = value;
      }
    }
  }

  private static InnerScoreDirector<TraversalSolution, SimpleScore> director(
      boolean forward, Storage storage) {
    var classes = new ArrayList<Class<?>>();
    classes.add(forward ? ForwardRoute.class : BackwardRoute.class);
    classes.add(forward ? ForwardValue.class : BackwardValue.class);
    if (storage == Storage.LINKED_EXTERNALIZED) {
      classes.add(forward ? ForwardLinkedValue.class : BackwardLinkedValue.class);
    }
    var descriptor =
        SolutionDescriptor.buildSolutionDescriptor(
            TraversalSolution.class, classes.toArray(Class<?>[]::new));
    return new EasyScoreDirectorFactory<>(
            descriptor, ignored -> SimpleScore.ZERO, EnvironmentMode.NO_ASSERT)
        .buildScoreDirector();
  }

  @PlanningSolution
  public static class TraversalSolution {
    @PlanningEntityCollectionProperty public List<Route> routes = new ArrayList<>();
    @PlanningEntityCollectionProperty public List<Value> values = new ArrayList<>();

    @ValueRangeProvider(id = "inputs")
    public List<Integer> inputs = List.of(1, 3);

    @PlanningScore public SimpleScore score;

    @ValueRangeProvider(id = "forwardValues")
    public List<ForwardValue> getForwardValues() {
      return values.stream()
          .filter(ForwardValue.class::isInstance)
          .map(ForwardValue.class::cast)
          .toList();
    }

    @ValueRangeProvider(id = "backwardValues")
    public List<BackwardValue> getBackwardValues() {
      return values.stream()
          .filter(BackwardValue.class::isInstance)
          .map(BackwardValue.class::cast)
          .toList();
    }

    static TraversalSolution create(
        boolean forward, Storage storage, int ownerCount, int size, boolean sparse) {
      var solution = new TraversalSolution();
      for (var ownerIndex = 0; ownerIndex < ownerCount; ownerIndex++) {
        Route route = forward ? new ForwardRoute() : new BackwardRoute();
        route.values =
            storage == Storage.ARRAY
                ? new CountingArrayList(route.operations)
                : new CountingLinkedList(route.operations);
        for (var i = 0; i < size; i++) {
          Value value;
          if (forward) {
            value =
                storage == Storage.LINKED_EXTERNALIZED
                    ? new ForwardLinkedValue()
                    : new ForwardValue();
          } else {
            value =
                storage == Storage.LINKED_EXTERNALIZED
                    ? new BackwardLinkedValue()
                    : new BackwardValue();
          }
          value.sparse = sparse;
          route.values.add(value);
          solution.values.add(value);
        }
        solution.routes.add(route);
      }
      return solution;
    }
  }

  public abstract static class Route {
    public List<Value> values;
    final Operations operations = new Operations();

    @Override
    public boolean equals(Object other) {
      return other instanceof Route;
    }

    @Override
    public int hashCode() {
      return 1;
    }
  }

  @PlanningEntity
  public static class ForwardRoute extends Route {
    @SuppressWarnings({"unchecked", "rawtypes"})
    @PlanningListVariable(valueRangeProviderRefs = "forwardValues", allowsUnassignedValues = true)
    public List<ForwardValue> getValues() {
      return (List) values;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public void setValues(List<ForwardValue> values) {
      this.values = (List) values;
    }
  }

  @PlanningEntity
  public static class BackwardRoute extends Route {
    @SuppressWarnings({"unchecked", "rawtypes"})
    @PlanningListVariable(valueRangeProviderRefs = "backwardValues", allowsUnassignedValues = true)
    public List<BackwardValue> getValues() {
      return (List) values;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public void setValues(List<BackwardValue> values) {
      this.values = (List) values;
    }
  }

  public abstract static class Value {
    public Integer input = 1;
    public Route route;
    public Integer index;
    public boolean sparse;
    public boolean fail;
    public int calls;

    public abstract Integer getTotal();

    public abstract Value parent();

    Integer calculate() {
      calls++;
      if (fail) {
        throw new IllegalStateException("expected supplier failure");
      }
      if (route == null) {
        return null;
      }
      var parent = parent();
      var parentTotal = parent == null ? 0 : parent.getTotal();
      return input + (sparse ? parentTotal % 2 : parentTotal);
    }
  }

  @PlanningEntity
  public static class ForwardValue extends Value {
    @PreviousElementShadowVariable(sourceVariableName = "values")
    public ForwardValue previous;

    private Integer total;

    @PlanningVariable(valueRangeProviderRefs = "inputs")
    public Integer getInput() {
      return input;
    }

    public void setInput(Integer input) {
      this.input = input;
    }

    @IndexShadowVariable(sourceVariableName = "values")
    public Integer getIndex() {
      return index;
    }

    public void setIndex(Integer index) {
      this.index = index;
    }

    @InverseRelationShadowVariable(sourceVariableName = "values")
    public ForwardRoute getRoute() {
      return (ForwardRoute) route;
    }

    public void setRoute(ForwardRoute route) {
      this.route = route;
    }

    @Override
    @ShadowVariable(supplierName = "totalSupplier")
    public Integer getTotal() {
      return total;
    }

    public void setTotal(Integer total) {
      this.total = total;
    }

    @Override
    public Value parent() {
      return previous;
    }

    @ShadowSources({"input", "route", "previous.total"})
    public Integer totalSupplier() {
      return calculate();
    }
  }

  @PlanningEntity
  public static class ForwardLinkedValue extends ForwardValue {
    @NextElementShadowVariable(sourceVariableName = "values")
    public ForwardValue next;
  }

  @PlanningEntity
  public static class BackwardValue extends Value {
    @NextElementShadowVariable(sourceVariableName = "values")
    public BackwardValue next;

    private Integer total;

    @PlanningVariable(valueRangeProviderRefs = "inputs")
    public Integer getInput() {
      return input;
    }

    public void setInput(Integer input) {
      this.input = input;
    }

    @IndexShadowVariable(sourceVariableName = "values")
    public Integer getIndex() {
      return index;
    }

    public void setIndex(Integer index) {
      this.index = index;
    }

    @InverseRelationShadowVariable(sourceVariableName = "values")
    public BackwardRoute getRoute() {
      return (BackwardRoute) route;
    }

    public void setRoute(BackwardRoute route) {
      this.route = route;
    }

    @Override
    @ShadowVariable(supplierName = "totalSupplier")
    public Integer getTotal() {
      return total;
    }

    public void setTotal(Integer total) {
      this.total = total;
    }

    @Override
    public Value parent() {
      return next;
    }

    @ShadowSources({"input", "route", "next.total"})
    public Integer totalSupplier() {
      return calculate();
    }
  }

  @PlanningEntity
  public static class BackwardLinkedValue extends BackwardValue {
    @PreviousElementShadowVariable(sourceVariableName = "values")
    public BackwardValue previous;
  }

  private static final class Operations {
    int indexedReads;
    int iteratorCreations;
    int iteratorSeekSteps;
    int iteratorSteps;

    void clear() {
      indexedReads = iteratorCreations = iteratorSeekSteps = iteratorSteps = 0;
    }
  }

  private static final class CountingArrayList extends ArrayList<Value> {
    private final Operations operations;

    CountingArrayList(Operations operations) {
      this.operations = operations;
    }

    @Override
    public Value get(int index) {
      operations.indexedReads++;
      return super.get(index);
    }
  }

  private static final class CountingLinkedList extends LinkedList<Value> {
    private final Operations operations;

    CountingLinkedList(Operations operations) {
      this.operations = operations;
    }

    @Override
    public Value get(int index) {
      operations.indexedReads++;
      return super.get(index);
    }

    @Override
    public ListIterator<Value> listIterator(int index) {
      operations.iteratorCreations++;
      operations.iteratorSeekSteps += index == size() ? 0 : Math.min(index, size() - 1 - index);
      var delegate = super.listIterator(index);
      return new ListIterator<>() {
        @Override
        public boolean hasNext() {
          return delegate.hasNext();
        }

        @Override
        public Value next() {
          operations.iteratorSteps++;
          return delegate.next();
        }

        @Override
        public boolean hasPrevious() {
          return delegate.hasPrevious();
        }

        @Override
        public Value previous() {
          operations.iteratorSteps++;
          return delegate.previous();
        }

        @Override
        public int nextIndex() {
          return delegate.nextIndex();
        }

        @Override
        public int previousIndex() {
          return delegate.previousIndex();
        }

        @Override
        public void remove() {
          delegate.remove();
        }

        @Override
        public void set(Value value) {
          delegate.set(value);
        }

        @Override
        public void add(Value value) {
          delegate.add(value);
        }
      };
    }
  }
}
