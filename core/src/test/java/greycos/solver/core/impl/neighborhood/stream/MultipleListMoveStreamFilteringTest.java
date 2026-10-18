package greycos.solver.core.impl.neighborhood.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import java.util.List;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.impl.neighborhood.NeighborhoodsBasedMoveRepository;
import greycos.solver.core.impl.neighborhood.stream.enumerating.uni.AbstractUniEnumeratingStream;
import greycos.solver.core.impl.neighborhood.stream.enumerating.uni.UniLeftDataset;
import greycos.solver.core.impl.score.director.SessionContext;
import greycos.solver.core.impl.score.director.easy.EasyScoreDirectorFactory;
import greycos.solver.core.preview.api.neighborhood.stream.enumerating.UniEnumeratingStream;
import greycos.solver.core.testcotwin.multiplelist.TestdataMultipleListNoInverseSolution;
import greycos.solver.core.testcotwin.multiplelist.TestdataMultipleListNoInverseSolution.BaseValue;
import greycos.solver.core.testcotwin.multiplelist.TestdataMultipleListNoInverseSolution.FirstValue;
import greycos.solver.core.testcotwin.multiplelist.TestdataMultipleListNoInverseSolution.Owner;
import greycos.solver.core.testcotwin.multiplelist.TestdataMultipleListNoInverseSolution.SecondValue;
import greycos.solver.core.testcotwin.multiplelist.TestdataMultipleListNoInverseSolution.ValueMarker;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MultipleListMoveStreamFilteringTest {

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void commonTypesAndFamilyMembershipRespectPinningAndReactWithoutShadows(
      boolean registeredSuperclass) {
    var descriptor = TestdataMultipleListNoInverseSolution.buildSolutionDescriptor();
    var solution = new TestdataMultipleListNoInverseSolution();
    var pinnedOwner = new Owner();
    pinnedOwner.pinned = true;
    pinnedOwner.basicValue = 1;
    var movableOwner = new Owner();
    movableOwner.basicValue = 1;
    var firstPinned = new FirstValue();
    var firstUnassigned = new FirstValue();
    var secondMovable = new SecondValue();
    var secondPinned = new SecondValue();
    var plain = new BaseValue();
    pinnedOwner.first.add(firstPinned);
    pinnedOwner.second.add(secondPinned);
    movableOwner.second.add(secondMovable);
    solution.owners.addAll(List.of(pinnedOwner, movableOwner));
    solution.firstValues.addAll(List.of(firstPinned, firstUnassigned));
    solution.secondValues.addAll(List.of(secondMovable, secondPinned));
    solution.otherValues.add(plain);
    var factory = new DefaultMoveStreamFactory<>(descriptor, EnvironmentMode.PHASE_ASSERT);
    var sourceClass = registeredSuperclass ? BaseValue.class : ValueMarker.class;
    assertThat(factory.forEach(sourceClass, false)).isSameAs(factory.forEach(sourceClass, false));
    var common = dataset(factory.forEach(sourceClass, false));
    var commonIncludingNull = dataset(factory.forEach(sourceClass, true));
    var ownerModel = descriptor.getMetaModel().genuineEntity(Owner.class);
    var firstModel = ownerModel.listVariable("first", FirstValue.class);
    var secondModel = ownerModel.listVariable("second", SecondValue.class);
    var firstAssigned = dataset(factory.forEachAssignedValue(firstModel));
    var firstAssignedUnfiltered = dataset(factory.forEachAssignedValueUnfiltered(firstModel));
    var firstUnassignedDataset = dataset(factory.forEachUnassignedValue(firstModel));
    var secondAssigned = dataset(factory.forEachAssignedValue(secondModel));

    try (var scoreDirector =
        new EasyScoreDirectorFactory<TestdataMultipleListNoInverseSolution, SimpleScore>(
                descriptor, ignored -> SimpleScore.ZERO, EnvironmentMode.PHASE_ASSERT)
            .buildScoreDirector()) {
      scoreDirector.setWorkingSolution(solution);
      var session = factory.createSession(new SessionContext<>(scoreDirector));
      descriptor.visitAll(solution, session::insert);
      session.settle();
      // These list elements also have independent genuine variables, so only their own entity pin
      // restricts generic enumeration. List-specific enumeration still honors the owners' pins.
      assertValues(
          session, common, firstPinned, firstUnassigned, secondMovable, secondPinned, plain);
      assertValues(
          session,
          commonIncludingNull,
          firstPinned,
          firstUnassigned,
          secondMovable,
          secondPinned,
          plain,
          null);
      assertValues(session, firstAssigned);
      assertValues(session, firstAssignedUnfiltered, firstPinned);
      assertValues(session, firstUnassignedDataset, firstUnassigned);
      assertValues(session, secondAssigned, secondMovable);

      // Connect the actual state-change notifier to the session; no inverse or index shadows exist.
      NeighborhoodsBasedMoveRepository<TestdataMultipleListNoInverseSolution> repository =
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

      scoreDirector.beforeListVariableChanged(movableOwner, "first", 0, 0);
      scoreDirector.beforeListVariableElementAssigned(movableOwner, "first", firstUnassigned);
      movableOwner.first.add(firstUnassigned);
      scoreDirector.afterListVariableElementAssigned(movableOwner, "first", firstUnassigned);
      scoreDirector.afterListVariableChanged(movableOwner, "first", 0, 1);
      scoreDirector.triggerVariableListeners();
      session.settle();
      assertValues(session, firstAssigned, firstUnassigned);
      assertValues(session, firstUnassignedDataset);
      assertValues(session, secondAssigned, secondMovable);

      scoreDirector.beforeListVariableChanged(movableOwner, "second", 0, 1);
      scoreDirector.beforeListVariableElementUnassigned(movableOwner, "second", secondMovable);
      movableOwner.second.clear();
      scoreDirector.afterListVariableElementUnassigned(movableOwner, "second", secondMovable);
      scoreDirector.afterListVariableChanged(movableOwner, "second", 0, 0);
      scoreDirector.triggerVariableListeners();
      session.settle();
      assertValues(session, secondAssigned);
      assertValues(session, firstAssigned, firstUnassigned);
      assertValues(
          session, common, firstPinned, firstUnassigned, secondMovable, secondPinned, plain);
    }
  }

  private static <A> UniLeftDataset<TestdataMultipleListNoInverseSolution, A> dataset(
      UniEnumeratingStream<TestdataMultipleListNoInverseSolution, A> stream) {
    return ((AbstractUniEnumeratingStream<TestdataMultipleListNoInverseSolution, A>) stream)
        .asCachedDataset();
  }

  private static void assertValues(
      DefaultNeighborhoodSession<TestdataMultipleListNoInverseSolution> session,
      UniLeftDataset<TestdataMultipleListNoInverseSolution, ?> dataset,
      Object... values) {
    assertThat(session.getLeftDatasetInstance(dataset).iterator())
        .toIterable()
        .map(tuple -> (Object) tuple.getA())
        .containsExactlyInAnyOrder(values);
  }
}
