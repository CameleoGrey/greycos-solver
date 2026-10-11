package greycos.solver.math.impl.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Random;

import greycos.solver.core.api.score.HardSoftScore;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import greycos.solver.core.impl.score.director.stream.BavetConstraintStreamScoreDirector;
import greycos.solver.core.impl.score.director.stream.BavetConstraintStreamScoreDirectorFactory;
import greycos.solver.math.api.MathModel;
import greycos.solver.math.api.MathSolver;
import greycos.solver.math.config.SolveOptions;
import greycos.solver.math.impl.cotwin.MathDecision;
import greycos.solver.math.impl.cotwin.MathSolution;
import greycos.solver.math.impl.model.MathCompiler;
import greycos.solver.math.impl.score.MathConstraintProvider;

import org.junit.jupiter.api.Test;

class MathChangeMoveIteratorFactoryTest {

  @Test
  void originalIterationIsFiniteAndExcludesPinsSingletonsAndUnchangedValues() {
    var builder = MathModel.builder();
    var first = builder.intVar("first", -1, 1);
    var fixed = builder.intVar("fixed", 0, 2);
    builder.intVar("singleton", Long.MAX_VALUE, Long.MAX_VALUE);
    var endpoint = builder.intVar("endpoint", Long.MAX_VALUE - 1, Long.MAX_VALUE);
    builder.initialValue(first, 0);
    builder.fix(fixed, 1);
    builder.initialValue(endpoint, Long.MAX_VALUE);
    var solution = MathSolution.initialize(MathCompiler.compile(builder.build()), 0);
    var factory = new MathChangeMoveIteratorFactory();
    try (var director = director(solution)) {
      factory.phaseStarted(director);
      assertThat(factory.getSize(director)).isEqualTo(3);
      var iterator = factory.createOriginalMoveIterator(director);
      var targets = new ArrayList<String>();
      while (iterator.hasNext()) {
        assertThat(iterator.hasNext()).isTrue();
        var move = iterator.next();
        var entity = (MathDecision) move.getPlanningEntities().getFirst();
        targets.add(entity.getId() + "=" + move.getPlanningValues().getFirst());
      }
      assertThat(targets).containsExactly("0=-1", "0=1", "3=" + (Long.MAX_VALUE - 1));
      assertThatThrownBy(iterator::next).isInstanceOf(NoSuchElementException.class);
      factory.phaseEnded(director);
    }
  }

  @Test
  void randomIterationIsReproducibleAndUsesNotifiedUndoAndCloneRebasing() {
    var builder = MathModel.builder();
    var x = builder.intVar("x", -5, 5);
    builder.maximize(x.times(3));
    var solution = MathSolution.initialize(MathCompiler.compile(builder.build()), 0);
    var factory = new MathChangeMoveIteratorFactory();
    try (var director = director(solution)) {
      factory.phaseStarted(director);
      var first = factory.createRandomMoveIterator(director, new Random(17));
      var second = factory.createRandomMoveIterator(director, new Random(17));
      var entity = solution.getDecisions().getFirst();
      var session = director.getSession();
      for (int i = 0; i < 100; i++) {
        var move = first.next();
        var duplicate = second.next();
        assertThat(move).isEqualTo(duplicate);
        assertThat(move.hashCode()).isEqualTo(duplicate.hashCode());
        var before = entity.getValue();
        var target = (Long) move.getPlanningValues().getFirst();
        assertThat(target).isNotEqualTo(before);
        assertThat(entity.getValueRange().contains(target)).isTrue();
        var changed = director.getMoveDirector().executeTemporary(move);
        assertThat(changed.raw()).isEqualTo(HardSoftScore.ofSoft(3 * target));
        assertThat(entity.getValue()).isEqualTo(before);
        assertThat(director.getSession()).isSameAs(session);
        director.getMoveDirector().execute(move);
        assertThat(entity.getValue()).isEqualTo(target);
      }
      var clone = director.cloneSolution(solution);
      try (var cloneDirector = director(clone)) {
        var move = first.next();
        var rebased = move.rebase(cloneDirector);
        assertThat(rebased.getPlanningEntities().getFirst())
            .isSameAs(clone.getDecisions().getFirst());
        var originalValue = entity.getValue();
        cloneDirector.getMoveDirector().execute(rebased);
        assertThat(entity.getValue()).isEqualTo(originalValue);
        assertThat(clone.getDecisions().getFirst().getValue())
            .isEqualTo(rebased.getPlanningValues().getFirst());
      }
      factory.phaseEnded(director);
    }
  }

  @Test
  void largeDomainsAreIndexedLazilyAndTheirCombinedMoveCountSaturates() {
    var builder = MathModel.builder();
    var first = builder.intVar("first", 1, Long.MAX_VALUE);
    var second = builder.intVar("second", Long.MIN_VALUE, -2);
    builder.initialValue(first, Long.MAX_VALUE);
    builder.initialValue(second, Long.MIN_VALUE);
    builder.maximize(first.plus(second));
    var model = builder.build();
    var solution = MathSolution.initialize(MathCompiler.compile(model), 0);
    var factory = new MathChangeMoveIteratorFactory();
    try (var director = director(solution)) {
      factory.phaseStarted(director);
      assertThat(factory.getSize(director)).isEqualTo(Long.MAX_VALUE);
      var original = factory.createOriginalMoveIterator(director);
      assertThat(original.next().getPlanningValues()).containsExactly(1L);
      assertThat(original.next().getPlanningValues()).containsExactly(2L);
      var random = factory.createRandomMoveIterator(director, new Random(0));
      for (int i = 0; i < 100; i++) {
        var move = random.next();
        var entity = (MathDecision) move.getPlanningEntities().getFirst();
        var value = (Long) move.getPlanningValues().getFirst();
        assertThat(entity.getValueRange().contains(value)).isTrue();
        assertThat(value).isNotEqualTo(entity.getValue());
      }
      factory.phaseEnded(director);
    }
    var result =
        MathSolver.solve(
            model,
            SolveOptions.moveEvaluationLimit(40).withEnvironmentMode(EnvironmentMode.FULL_ASSERT));
    assertThat(result.value(first)).isBetween(1L, Long.MAX_VALUE);
    assertThat(result.value(second)).isBetween(Long.MIN_VALUE, -2L);
    assertThat(result.objectiveValue())
        .isEqualTo(Math.addExact(result.value(first), result.value(second)));
  }

  @Test
  void phaseReuseReplacesCachedEntitiesAndAnAllPinnedModelHasNoMoves() {
    var builder = MathModel.builder();
    builder.intVar("variable", 0, 2);
    var compiled = MathCompiler.compile(builder.build());
    var first = MathSolution.initialize(compiled, 0);
    var second = MathSolution.initialize(compiled, 1);
    var factory = new MathChangeMoveIteratorFactory();
    try (var director = director(first)) {
      factory.phaseStarted(director);
      factory.phaseEnded(director);
      director.setWorkingSolution(second);
      factory.phaseStarted(director);
      var move = factory.createRandomMoveIterator(director, new Random(0)).next();
      assertThat(move.getPlanningEntities().getFirst()).isSameAs(second.getDecisions().getFirst());
      factory.phaseEnded(director);
      var pinnedBuilder = MathModel.builder();
      pinnedBuilder.intVar("fixed", 1, 1);
      director.setWorkingSolution(
          MathSolution.initialize(MathCompiler.compile(pinnedBuilder.build()), 0));
      factory.phaseStarted(director);
      assertThat(factory.getSize(director)).isZero();
      assertThat(factory.createOriginalMoveIterator(director).hasNext()).isFalse();
      var random = factory.createRandomMoveIterator(director, new Random(0));
      assertThat(random.hasNext()).isFalse();
      assertThatThrownBy(random::next).isInstanceOf(NoSuchElementException.class);
      factory.phaseEnded(director);
    }
  }

  @Test
  void fullAssertSolvesChangingEndpointAndLongCardinalityDomains() {
    for (var bounds :
        List.of(new long[] {Long.MAX_VALUE - 2, Long.MAX_VALUE}, new long[] {1, Long.MAX_VALUE})) {
      var builder = MathModel.builder();
      var x = builder.intVar("x", bounds[0], bounds[1]);
      builder.initialValue(x, bounds[1]);
      builder.minimize(x);
      var result =
          MathSolver.solve(
              builder.build(),
              SolveOptions.moveEvaluationLimit(20)
                  .withEnvironmentMode(EnvironmentMode.FULL_ASSERT));
      assertThat(result.value(x)).isBetween(bounds[0], bounds[1]);
      assertThat(result.objectiveValue()).isEqualTo(result.value(x));
    }
  }

  private static BavetConstraintStreamScoreDirector<MathSolution, HardSoftScore> director(
      MathSolution solution) {
    var factory =
        new BavetConstraintStreamScoreDirectorFactory<MathSolution, HardSoftScore>(
            SolutionDescriptor.buildSolutionDescriptor(MathSolution.class, MathDecision.class),
            new MathConstraintProvider(),
            EnvironmentMode.FULL_ASSERT);
    var director = factory.createScoreDirectorBuilder().withLookUpEnabled(true).build();
    director.setWorkingSolution(solution);
    director.calculateScore();
    return director;
  }
}
