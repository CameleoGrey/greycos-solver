package greycos.solver.core.impl.score.stream.common.inliner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.stream.ConstraintJustification;
import greycos.solver.core.impl.bavet.common.provenance.LineageHandle;
import greycos.solver.core.impl.score.constraint.ConstraintMatchPolicy;
import greycos.solver.core.testconstraint.TestConstraint;
import greycos.solver.core.testconstraint.TestConstraintFactory;
import greycos.solver.core.testcotwin.TestdataSolution;

import org.junit.jupiter.api.Test;

class RawScoreInlinerTest {

  private record Key(String value) {}

  private record Reason(String value) implements ConstraintJustification {}

  @Test
  void rowsRetainTerminalMultiplicityAndDistinctSourceIdentitiesBeforeGrouping() {
    var constraint = constraint();
    var inliner =
        new SimpleScoreInliner(
            Map.of(constraint, SimpleScore.ONE),
            ConstraintMatchPolicy.ENABLED_WITH_RAW_ORIGINAL_INDICTMENTS);
    var impacter = inliner.buildWeightedScoreImpacter(constraint);
    var terminal = new Key("terminal");
    var equalTerminal = new Key("terminal");
    var source = new Key("source");
    var equalSource = new Key("source");
    var sourceHandle = LineageHandle.source(source);
    var lineage =
        LineageHandle.union(sourceHandle, sourceHandle, LineageHandle.source(equalSource));
    var supplier =
        ConstraintMatchSupplier.<Object, SimpleScore>withIndictments(
            (fact, score) -> new Reason("same"),
            ignored -> List.of(terminal, terminal, equalTerminal),
            terminal);
    var firstImpact = impacter.impactScore(3L, supplier);
    var secondImpact = impacter.impactScore(5L, supplier);
    AbstractScoreInliner.bindOriginalIndictment(firstImpact, lineage);
    AbstractScoreInliner.bindOriginalIndictment(secondImpact, lineage);

    var details = inliner.getAnalysisDetails();
    var raw = details.rawAnalysis();
    assertThat(raw.matchOccurrences()).hasSize(2);
    assertThat(raw.originalsFetched()).isTrue();
    for (var row : raw.matchOccurrences()) {
      assertThat(row.occurrenceSign()).isOne();
      assertThat(row.score()).isEqualTo(row.matchScore());
      assertThat(row.indictedObjects()).hasSize(3);
      assertThat(row.indictedObjects().get(0)).isSameAs(terminal);
      assertThat(row.indictedObjects().get(1)).isSameAs(terminal);
      assertThat(row.indictedObjects().get(2)).isSameAs(equalTerminal);
      assertThat(row.originalIndictedObjects()).hasSize(2);
      assertThat(row.originalIndictedObjects().get(0)).isSameAs(source);
      assertThat(row.originalIndictedObjects().get(1)).isSameAs(equalSource);
    }
    var first = raw.matchOccurrences().getFirst();
    var second = raw.matchOccurrences().getLast();
    assertThat(first).isNotSameAs(second);
    assertThat(raw.constraintMap().get(constraint.getConstraintRef()).getFirst()).isSameAs(first);
    assertThat(raw.indictmentMap()).containsOnlyKeys(terminal);
    assertThat(raw.indictmentMap().get(terminal)).hasSize(2);
    assertThat(raw.indictmentMap().get(terminal).getFirst()).isSameAs(first);
    assertThat(raw.originalIndictmentMap()).containsOnlyKeys(source);
    assertThat(raw.originalIndictmentMap().get(source).getLast()).isSameAs(second);
    assertThat(details.indictments().terminal().get(terminal).score()).isEqualTo(SimpleScore.of(8));
    assertThat(details.indictments().terminal().get(terminal).matches()).hasSize(1);
    assertThat(details.indictments().original().get(source).matchCount()).isEqualTo(2);
    assertThatThrownBy(() -> first.indictedObjects().clear())
        .isInstanceOf(UnsupportedOperationException.class);
    firstImpact.undo();
    secondImpact.undo();
    assertThat(inliner.getAnalysisDetails().rawAnalysis().matchOccurrences()).isEmpty();
    assertThat(raw.matchOccurrences()).hasSize(2);
  }

  @Test
  void cachedConstraintMatchDoesNotFreezeOriginalSources() {
    var constraint = constraint();
    var inliner =
        new SimpleScoreInliner(
            Map.of(constraint, SimpleScore.ONE),
            ConstraintMatchPolicy.ENABLED_WITH_RAW_ORIGINAL_INDICTMENTS);
    var firstSource = new Object();
    var replacementSource = new Object();
    var oldParent = LineageHandle.source(firstSource);
    var lineage = LineageHandle.union(oldParent);
    var callbacks = new AtomicInteger();
    var impact =
        inliner
            .buildWeightedScoreImpacter(constraint)
            .impactScore(
                2L,
                ConstraintMatchSupplier.<Object, SimpleScore>withIndictments(
                    (fact, score) -> new Reason("same"),
                    fact -> {
                      callbacks.incrementAndGet();
                      return List.of(fact);
                    },
                    "terminal"));
    AbstractScoreInliner.bindOriginalIndictment(impact, lineage);
    inliner.getConstraintMatchTotalMap();
    var first = inliner.getAnalysisDetails().rawAnalysis();
    lineage.remove(oldParent);
    lineage.add(LineageHandle.source(replacementSource));
    var second = inliner.getAnalysisDetails().rawAnalysis();
    assertThat(first.matchOccurrences().getFirst().originalIndictedObjects().getFirst())
        .isSameAs(firstSource);
    assertThat(second.matchOccurrences().getFirst().originalIndictedObjects().getFirst())
        .isSameAs(replacementSource);
    assertThat(second.matchOccurrences().getFirst().matchScore()).isEqualTo(SimpleScore.of(2));
    assertThat(callbacks).hasValue(1);
  }

  @Test
  void rawViewsAreUnavailableUnlessRequested() {
    var constraint = constraint();
    for (var policy :
        List.of(ConstraintMatchPolicy.ENABLED, ConstraintMatchPolicy.ENABLED_WITH_INDICTMENTS)) {
      var inliner = new SimpleScoreInliner(Map.of(constraint, SimpleScore.ONE), policy);
      assertThat(inliner.getAnalysisDetails().rawAnalysis()).isNull();
    }
    var inliner =
        new SimpleScoreInliner(
            Map.of(constraint, SimpleScore.ONE),
            ConstraintMatchPolicy.ENABLED_WITH_RAW_INDICTMENTS);
    assertThat(inliner.getAnalysisDetails().rawAnalysis().matchOccurrences()).isEmpty();
    assertThat(inliner.getAnalysisDetails().rawAnalysis().originalIndictmentMap()).isNull();
    assertThat(inliner.getAnalysisDetails().rawAnalysis().originalsFetched()).isFalse();
  }

  private static TestConstraint<TestdataSolution, SimpleScore> constraint() {
    return new TestConstraint<>(
        new TestConstraintFactory<>(TestdataSolution.buildSolutionDescriptor()),
        "constraint",
        SimpleScore.ONE);
  }
}
