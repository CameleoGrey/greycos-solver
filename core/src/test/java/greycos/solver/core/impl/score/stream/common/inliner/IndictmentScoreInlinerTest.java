package greycos.solver.core.impl.score.stream.common.inliner;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.stream.DefaultConstraintJustification;
import greycos.solver.core.impl.bavet.common.provenance.LineageHandle;
import greycos.solver.core.impl.score.constraint.ConstraintMatchPolicy;
import greycos.solver.core.testconstraint.TestConstraint;
import greycos.solver.core.testconstraint.TestConstraintFactory;
import greycos.solver.core.testcotwin.TestdataSolution;

import org.junit.jupiter.api.Test;

class IndictmentScoreInlinerTest {

  @Test
  void originalLineageChangesAfterTheConstraintMatchHasBeenCached() {
    var constraint = constraint();
    var inliner =
        new SimpleScoreInliner(
            Map.of(constraint, SimpleScore.ONE),
            ConstraintMatchPolicy.ENABLED_WITH_ORIGINAL_INDICTMENTS);
    var impacter = inliner.buildWeightedScoreImpacter(constraint);
    var target = new Object();
    var firstSource = new Object();
    var secondSource = new Object();
    var firstParent = LineageHandle.source(firstSource);
    var secondParent = LineageHandle.source(secondSource);
    var lineage = LineageHandle.union(firstParent);
    var calls = new AtomicInteger();
    var impact =
        impacter.impactScore(
            3L,
            ConstraintMatchSupplier.<Object, SimpleScore>withIndictments(
                (fact, score) -> DefaultConstraintJustification.of(score, fact),
                fact -> {
                  calls.incrementAndGet();
                  return List.of(target);
                },
                new Object()));
    AbstractScoreInliner.bindOriginalIndictment(impact, lineage);

    inliner.getConstraintMatchTotalMap();
    assertThat(calls).hasValue(0);
    var first = inliner.getIndictmentMaps();
    assertThat(first.original()).containsOnlyKeys(firstSource);
    assertThat(first.terminal()).containsOnlyKeys(target);
    assertThat(calls).hasValue(1);
    lineage.remove(firstParent);
    lineage.add(secondParent);
    var second = inliner.getIndictmentMaps();
    assertThat(second.original()).containsOnlyKeys(secondSource);
    assertThat(second.original().get(secondSource).score()).isEqualTo(SimpleScore.of(3));
    assertThat(first.original()).containsOnlyKeys(firstSource);
    assertThat(calls).hasValue(1);
  }

  @Test
  void equalJustificationsPreserveEveryRawOccurrenceAndUndoneCallbacksStayLazy() {
    var constraint = constraint();
    var inliner =
        new SimpleScoreInliner(
            Map.of(constraint, SimpleScore.ONE), ConstraintMatchPolicy.ENABLED_WITH_INDICTMENTS);
    var impacter = inliner.buildWeightedScoreImpacter(constraint);
    var target = new Object();
    var calls = new AtomicInteger();
    var supplier =
        ConstraintMatchSupplier.<Object, SimpleScore>withIndictments(
            (fact, score) -> DefaultConstraintJustification.of(SimpleScore.ZERO, "same"),
            fact -> {
              calls.incrementAndGet();
              return List.of(target, target);
            },
            target);
    var first = impacter.impactScore(3L, supplier);
    impacter.impactScore(5L, supplier);
    var undone = impacter.impactScore(7L, supplier);
    undone.undo();
    assertThat(calls).hasValue(0);
    var maps = inliner.getIndictmentMaps();
    var indictment = maps.terminal().get(target);
    assertThat(indictment.score()).isEqualTo(SimpleScore.of(8));
    assertThat(indictment.matchCount()).isEqualTo(2);
    assertThat(indictment.matches()).hasSize(1);
    assertThat(indictment.matches().getFirst().matchCount()).isEqualTo(2);
    assertThat(indictment.matches().getFirst().score()).isEqualTo(SimpleScore.of(8));
    assertThat(calls).hasValue(2);
    first.undo();
    assertThat(inliner.getIndictmentMaps().terminal().get(target).score())
        .isEqualTo(SimpleScore.of(5));
    assertThat(indictment.score()).isEqualTo(SimpleScore.of(8));
    assertThat(calls).hasValue(2);
  }

  private static TestConstraint<TestdataSolution, SimpleScore> constraint() {
    return new TestConstraint<>(
        new TestConstraintFactory<>(TestdataSolution.buildSolutionDescriptor()),
        "constraint",
        SimpleScore.ONE);
  }
}
