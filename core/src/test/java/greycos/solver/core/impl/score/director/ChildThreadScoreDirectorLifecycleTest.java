package greycos.solver.core.impl.score.director;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.impl.score.constraint.ConstraintMatchPolicy;
import greycos.solver.core.impl.score.director.stream.BavetConstraintStreamScoreDirector;
import greycos.solver.core.impl.score.director.stream.BavetConstraintStreamScoreDirectorFactory;
import greycos.solver.core.impl.solver.thread.ChildThreadType;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ChildThreadScoreDirectorLifecycleTest {

  @ParameterizedTest
  @CsvSource({"CLONE, false", "CLONE, true", "INITIALIZATION, false", "INITIALIZATION, true"})
  void allocatedMoveDirectorClosesWhenCloneOrInitializationFails(FailureSite site, boolean error) {
    var fixture = fixture();
    Throwable failure =
        error
            ? new AssertionError("startup failure")
            : new IllegalStateException("startup failure");
    try (var parent = fixture.parent()) {
      failStartup(fixture, site, failure);

      assertThatThrownBy(() -> parent.createChildThreadScoreDirector(ChildThreadType.MOVE_THREAD))
          .isSameAs(failure)
          .satisfies(thrown -> assertThat(thrown.getSuppressed()).isEmpty());

      verify(fixture.child()).close();
      if (site == FailureSite.CLONE) {
        verify(fixture.child(), never()).setWorkingSolution(any());
      } else {
        verify(fixture.child()).setWorkingSolution(any());
      }
      assertThat(fixture.child().getSession()).isNull();
      assertThat(fixture.child().getWorkingSolution()).isNull();
      assertThat(parent.getSession()).isNotNull();
      assertThat(parent.calculateScore().raw()).isEqualTo(SimpleScore.of(-2));
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void initializationFailureRemainsPrimaryWhenCloseAlsoFails(boolean sameFailure) {
    var fixture = fixture();
    var failure = new AssertionError("initialization failure");
    Throwable closeFailure = sameFailure ? failure : new IllegalStateException("close failure");
    try (var parent = fixture.parent()) {
      failStartup(fixture, FailureSite.INITIALIZATION, failure);
      doAnswer(
              invocation -> {
                invocation.callRealMethod();
                throw closeFailure;
              })
          .when(fixture.child())
          .close();

      assertThatThrownBy(() -> parent.createChildThreadScoreDirector(ChildThreadType.MOVE_THREAD))
          .isSameAs(failure)
          .satisfies(
              thrown -> {
                if (sameFailure) assertThat(thrown.getSuppressed()).isEmpty();
                else assertThat(thrown.getSuppressed()).containsExactly(closeFailure);
              });

      verify(fixture.child()).close();
      assertThat(fixture.child().getSession()).isNull();
      assertThat(parent.calculateScore().raw()).isEqualTo(SimpleScore.of(-2));
    }
  }

  @Test
  void successfulMoveDirectorRetainsNativeChildConfigurationAndIndependentSession() {
    var factory = factory();
    var input = TestdataSolution.generateSolution(2, 2);
    try (var parent =
        factory
            .createScoreDirectorBuilder()
            .withConstraintMatchPolicy(ConstraintMatchPolicy.ENABLED_WITHOUT_JUSTIFICATIONS)
            .build()) {
      parent.setWorkingSolution(input);
      try (var child = parent.createChildThreadScoreDirector(ChildThreadType.MOVE_THREAD)) {
        assertThat(child.getEnvironmentMode()).isEqualTo(parent.getEnvironmentMode());
        assertThat(child.getConstraintMatchPolicy()).isEqualTo(parent.getConstraintMatchPolicy());
        assertThat(child.isDerived()).isTrue();
        assertThat(child.getWorkingSolution()).isNotSameAs(input);
        assertThat(child.lookUpWorkingObject(input.getEntityList().getFirst()))
            .isSameAs(child.getWorkingSolution().getEntityList().getFirst());
        assertThat(((BavetConstraintStreamScoreDirector<?, ?>) child).getSession())
            .isNotSameAs(parent.getSession());
        assertThat(child.calculateScore()).isEqualTo(parent.calculateScore());
      }
      assertThat(parent.getSession()).isNotNull();
    }
  }

  private static void failStartup(Fixture fixture, FailureSite site, Throwable failure) {
    if (site == FailureSite.CLONE) {
      doThrow(failure).when(fixture.parent()).cloneWorkingSolution();
    } else {
      doAnswer(
              invocation -> {
                invocation.callRealMethod();
                assertThat(fixture.child().getSession()).isNotNull();
                throw failure;
              })
          .when(fixture.child())
          .setWorkingSolution(any());
    }
  }

  @SuppressWarnings("unchecked")
  private static Fixture fixture() {
    var factory = spy(factory());
    var parent = spy(factory.createScoreDirectorBuilder().build());
    parent.setWorkingSolution(TestdataSolution.generateSolution(2, 2));
    var child = spy(factory.createScoreDirectorBuilder().buildDerived());
    BavetConstraintStreamScoreDirector.Builder<TestdataSolution, SimpleScore> childBuilder =
        mock(BavetConstraintStreamScoreDirector.Builder.class, RETURNS_SELF);
    doReturn(child).when(childBuilder).buildDerived();
    doReturn(childBuilder).when(factory).createScoreDirectorBuilder(EnvironmentMode.NO_ASSERT);
    return new Fixture(parent, child);
  }

  private static BavetConstraintStreamScoreDirectorFactory<TestdataSolution, SimpleScore>
      factory() {
    return new BavetConstraintStreamScoreDirectorFactory<>(
        TestdataSolution.buildSolutionDescriptor(),
        constraints ->
            new Constraint[] {
              constraints
                  .forEach(TestdataEntity.class)
                  .penalize(SimpleScore.ONE)
                  .asConstraint("Entities")
            },
        EnvironmentMode.NO_ASSERT);
  }

  private enum FailureSite {
    CLONE,
    INITIALIZATION
  }

  private record Fixture(
      BavetConstraintStreamScoreDirector<TestdataSolution, SimpleScore> parent,
      BavetConstraintStreamScoreDirector<TestdataSolution, SimpleScore> child) {}
}
