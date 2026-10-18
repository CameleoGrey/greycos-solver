package greycos.solver.core.impl.score.director.incremental;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.calculator.AnalyzableIncrementalScoreCalculator;
import greycos.solver.core.api.score.stream.ConstraintJustification;
import greycos.solver.core.api.score.stream.ConstraintRef;
import greycos.solver.core.api.solver.IndictmentFetchPolicy;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import greycos.solver.core.impl.score.constraint.ConstraintMatchPolicy;
import greycos.solver.core.impl.score.definition.SimpleScoreDefinition;

import org.junit.jupiter.api.Test;

class IncrementalIndictmentTest {

  private static final ConstraintRef CONSTRAINT = ConstraintRef.of("constraint");
  private static final Reason REASON = new Reason("reason");

  private record Reason(String text) implements ConstraintJustification {}

  @Test
  void independentSelectionsSnapshotsAndOccurrenceCancellation() {
    try (var director = director(ConstraintMatchPolicy.ENABLED_WITH_ORIGINAL_INDICTMENTS)) {
      assertThat(director.indictmentFetchPolicy())
          .isEqualTo(IndictmentFetchPolicy.TERMINAL_AND_ORIGINAL);
      var terminal = new ArrayList<Object>(List.of("A", "A"));
      var original = new ArrayList<Object>(List.of("source"));
      var first =
          director.registerConstraintMatchWithIndictments(
              CONSTRAINT, SimpleScore.of(-2), REASON, terminal, original);
      terminal.clear();
      original.clear();
      var second =
          director.registerConstraintMatchWithIndictments(
              CONSTRAINT, SimpleScore.of(-3), REASON, List.of("B"), List.of("source", "source"));
      var before = director.getIndictmentMaps();
      assertThat(before.terminal().get("A").score()).isEqualTo(SimpleScore.of(-2));
      assertThat(before.terminal().get("B").score()).isEqualTo(SimpleScore.of(-3));
      assertThat(before.original().get("source").score()).isEqualTo(SimpleScore.of(-5));
      assertThat(before.original().get("source").matchCount()).isEqualTo(2);
      assertThat(first.indictedObjects()).containsExactly("A");
      assertThat(first.originalIndictedObjects()).containsExactly("source");
      assertThatThrownBy(() -> first.indictedObjects().clear())
          .isInstanceOf(UnsupportedOperationException.class);

      first.cancel();
      assertThat(director.getIndictmentMaps().terminal()).containsOnlyKeys("B");
      assertThat(director.getIndictmentMaps().original().get("source").matchCount()).isEqualTo(1);
      assertThat(before.terminal()).containsOnlyKeys("A", "B");
      assertThat(before.original().get("source").matchCount()).isEqualTo(2);
      second.cancel();
      assertThat(director.totalScore()).isEqualTo(SimpleScore.ZERO);
      assertThat(director.getIndictmentMaps().terminal()).isEmpty();
      assertThat(director.getIndictmentMaps().original()).isEmpty();
      assertThatThrownBy(first::cancel)
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("canceled once");
    }
  }

  @Test
  void legacyFactRegistrationCanSupplyOnlyTerminalObjects() {
    try (var director = director(ConstraintMatchPolicy.ENABLED_WITH_INDICTMENTS)) {
      var registration =
          director.registerConstraintMatch(CONSTRAINT, SimpleScore.ONE, "A", null, "A");
      assertThat(registration.indictedObjects()).containsExactly("A");
      assertThat(registration.originalIndictedObjects()).isNull();
      assertThat(director.getIndictmentMaps().terminal()).containsOnlyKeys("A");
      assertThat(director.getIndictmentMaps().original()).isNull();
    }
  }

  @Test
  void customJustificationDoesNotInventTerminalObjects() {
    try (var director = director(ConstraintMatchPolicy.ENABLED_WITH_INDICTMENTS)) {
      var registration = director.registerConstraintMatch(CONSTRAINT, SimpleScore.ONE, REASON);
      assertThat(registration.indictedObjects()).isNull();
      assertThatThrownBy(director::getIndictmentMaps)
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("terminal")
          .hasMessageContaining("registerConstraintMatchWithIndictments");
      registration.cancel();
      assertThat(director.getIndictmentMaps().terminal()).isEmpty();
    }
  }

  @Test
  void originalMetadataMustBeSuppliedExplicitlyEvenForDefaultJustification() {
    try (var director = director(ConstraintMatchPolicy.ENABLED_WITH_ORIGINAL_INDICTMENTS)) {
      var legacy = director.registerConstraintMatch(CONSTRAINT, SimpleScore.ONE, "A");
      assertThatThrownBy(director::getIndictmentMaps)
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("original");
      legacy.cancel();
      var terminalOnly =
          director.registerConstraintMatchWithIndictments(
              CONSTRAINT, SimpleScore.ONE, REASON, List.of("A"));
      assertThatThrownBy(director::getIndictmentMaps)
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("original");
      terminalOnly.cancel();
      director.registerConstraintMatchWithIndictments(
          CONSTRAINT, SimpleScore.ONE, REASON, List.of(), List.of());
      assertThat(director.getIndictmentMaps().terminal()).isEmpty();
      assertThat(director.getIndictmentMaps().original()).isEmpty();
      assertThat(director.totalScore()).isEqualTo(SimpleScore.ONE);
    }
  }

  @Test
  void absentViewsDoNotReadSelections() {
    var unreadable =
        new AbstractCollection<Object>() {
          @Override
          public Iterator<Object> iterator() {
            throw new AssertionError("Unrequested objects must not be read.");
          }

          @Override
          public int size() {
            throw new AssertionError("Unrequested objects must not be read.");
          }
        };
    try (var director = director(ConstraintMatchPolicy.ENABLED)) {
      var registration =
          director.registerConstraintMatchWithIndictments(
              CONSTRAINT, SimpleScore.ONE, REASON, unreadable, unreadable);
      assertThat(registration.indictedObjects()).isNull();
      assertThat(registration.originalIndictedObjects()).isNull();
      assertThat(director.getIndictmentMaps().terminal()).isNull();
      assertThat(director.getIndictmentMaps().original()).isNull();
      assertThat(director.totalScore()).isEqualTo(SimpleScore.ONE);
    }
    try (var director = director(ConstraintMatchPolicy.ENABLED_WITH_INDICTMENTS)) {
      director.registerConstraintMatchWithIndictments(
          CONSTRAINT, SimpleScore.ONE, REASON, List.of("A"), unreadable);
      assertThat(director.getIndictmentMaps().terminal()).containsOnlyKeys("A");
    }
  }

  @Test
  void invalidSelectionIsRejectedBeforeScoreOrRegistryMutation() {
    try (var director = director(ConstraintMatchPolicy.ENABLED_WITH_ORIGINAL_INDICTMENTS)) {
      assertThatThrownBy(
              () ->
                  director.registerConstraintMatchWithIndictments(
                      CONSTRAINT,
                      SimpleScore.ONE,
                      REASON,
                      List.of("A"),
                      Arrays.asList("source", null)))
          .isInstanceOf(NullPointerException.class);
      assertThat(director.totalScore()).isEqualTo(SimpleScore.ZERO);
      assertThat(director.getConstraintMatchTotalMap()).isEmpty();
      assertThat(director.getIndictmentMaps().terminal()).isEmpty();
    }
  }

  @Test
  void resetInvalidatesOldRegistrationsWithoutCorruptingCurrentScore() {
    try (var director = director(ConstraintMatchPolicy.ENABLED_WITH_ORIGINAL_INDICTMENTS)) {
      var old =
          director.registerConstraintMatchWithIndictments(
              CONSTRAINT, SimpleScore.of(-2), REASON, List.of("old"), List.of("old source"));
      var snapshot = director.getIndictmentMaps();
      director.setWorkingSolution(new Object());
      director.registerConstraintMatchWithIndictments(
          CONSTRAINT, SimpleScore.of(-3), REASON, List.of("new"), List.of("new source"));
      assertThatThrownBy(old::cancel)
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("resetting");
      assertThat(director.totalScore()).isEqualTo(SimpleScore.of(-3));
      assertThat(director.getIndictmentMaps().terminal()).containsOnlyKeys("new");
      assertThat(director.getIndictmentMaps().original()).containsOnlyKeys("new source");
      assertThat(snapshot.terminal()).containsOnlyKeys("old");
      assertThat(snapshot.original()).containsOnlyKeys("old source");
    }
  }

  private record EqualObject(String value) {}

  @Test
  void rawSelectionsPreserveMultiplicityAndIdentityWithoutChangingRegistrationGetters() {
    try (var director = director(ConstraintMatchPolicy.ENABLED_WITH_RAW_ORIGINAL_INDICTMENTS)) {
      var terminal = new EqualObject("terminal");
      var equalTerminal = new EqualObject("terminal");
      var source = new EqualObject("source");
      var equalSource = new EqualObject("source");
      var terminals = new ArrayList<>(List.of(terminal, terminal, equalTerminal));
      var originals = new ArrayList<>(List.of(source, source, equalSource));
      var registration =
          director.registerConstraintMatchWithIndictments(
              CONSTRAINT, SimpleScore.of(-2), REASON, terminals, originals);
      terminals.clear();
      originals.clear();
      assertThat(registration.indictedObjects()).hasSize(1);
      assertThat(registration.originalIndictedObjects()).hasSize(1);
      var details = director.getAnalysisDetails();
      var raw = details.rawAnalysis();
      var occurrence = raw.matchOccurrences().getFirst();
      assertThat(occurrence.indictedObjects()).hasSize(3);
      assertThat(occurrence.indictedObjects().get(0)).isSameAs(terminal);
      assertThat(occurrence.indictedObjects().get(1)).isSameAs(terminal);
      assertThat(occurrence.indictedObjects().get(2)).isSameAs(equalTerminal);
      assertThat(occurrence.originalIndictedObjects()).hasSize(2);
      assertThat(occurrence.originalIndictedObjects().get(0)).isSameAs(source);
      assertThat(occurrence.originalIndictedObjects().get(1)).isSameAs(equalSource);
      assertThat(raw.indictmentMap().get(terminal).getFirst()).isSameAs(occurrence);
      assertThat(raw.originalIndictmentMap().get(source).getFirst()).isSameAs(occurrence);
      assertThat(details.indictments().terminal().get(terminal).matchCount()).isOne();
      assertThat(details.indictments().original().get(source).score())
          .isEqualTo(SimpleScore.of(-2));
      registration.cancel();
      assertThat(director.getAnalysisDetails().rawAnalysis().matchOccurrences()).isEmpty();
      assertThat(raw.matchOccurrences()).hasSize(1);
    }
  }

  @Test
  void rawRegistrationResetDoesNotRetainCanceledContributors() {
    try (var director = director(ConstraintMatchPolicy.ENABLED_WITH_RAW_ORIGINAL_INDICTMENTS)) {
      var old =
          director.registerConstraintMatchWithIndictments(
              CONSTRAINT, SimpleScore.of(-2), REASON, List.of("old", "old"), List.of("old source"));
      var snapshot = director.getAnalysisDetails().rawAnalysis();
      director.setWorkingSolution(new Object());
      assertThat(director.getAnalysisDetails().rawAnalysis().matchOccurrences()).isEmpty();
      director.registerConstraintMatchWithIndictments(
          CONSTRAINT, SimpleScore.of(-3), REASON, List.of("new", "new"), List.of("new source"));
      assertThatThrownBy(old::cancel)
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("resetting");
      var current = director.getAnalysisDetails().rawAnalysis();
      assertThat(current.indictmentMap()).containsOnlyKeys("new");
      assertThat(current.originalIndictmentMap()).containsOnlyKeys("new source");
      assertThat(current.matchOccurrences().getFirst().indictedObjects())
          .containsExactly("new", "new");
      assertThat(snapshot.indictmentMap()).containsOnlyKeys("old");
    }
  }

  @Test
  void rawLegacyTerminalRegistrationOmitsNullsButKeepsDuplicateSlots() {
    try (var director = director(ConstraintMatchPolicy.ENABLED_WITH_RAW_INDICTMENTS)) {
      director.registerConstraintMatch(CONSTRAINT, SimpleScore.ONE, "A", null, "A");
      var raw = director.getAnalysisDetails().rawAnalysis();
      assertThat(raw.matchOccurrences().getFirst().indictedObjects()).containsExactly("A", "A");
      assertThat(raw.matchOccurrences().getFirst().originalIndictedObjects()).isNull();
      assertThat(raw.originalIndictmentMap()).isNull();
    }
  }

  @SuppressWarnings("unchecked")
  private static IncrementalScoreDirector<Object, SimpleScore> director(
      ConstraintMatchPolicy policy) {
    IncrementalScoreDirectorFactory<Object, SimpleScore> factory =
        mock(IncrementalScoreDirectorFactory.class);
    when(factory.getScoreDefinition()).thenReturn(new SimpleScoreDefinition());
    when(factory.getSolutionDescriptor()).thenReturn(mock(SolutionDescriptor.class));
    AnalyzableIncrementalScoreCalculator<Object, SimpleScore> calculator =
        mock(AnalyzableIncrementalScoreCalculator.class);
    var director =
        new IncrementalScoreDirector.Builder<>(factory, EnvironmentMode.PHASE_ASSERT)
            .withIncrementalScoreCalculator(calculator)
            .withConstraintMatchPolicy(policy)
            .build();
    director.setWorkingSolution(new Object());
    return director;
  }
}
