package greycos.solver.core.api.score.analysis;

import java.util.List;
import java.util.Objects;
import java.util.SequencedMap;

import greycos.solver.core.api.score.Score;
import greycos.solver.core.api.score.stream.ConstraintJustification;
import greycos.solver.core.api.score.stream.ConstraintRef;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * An explicitly requested snapshot of individual scoring occurrences and indexes over them. Every
 * index refers to the same instances held in {@link #matchOccurrences()}; distinct occurrences are
 * never merged, even when their values are equal. All collections are immutable. Objects and
 * justifications are not deep-copied and must retain stable equality and hash codes.
 *
 * <p>Indexes use object equality and contain each occurrence once per object key, even when its
 * selection contains that object more than once. Maps and lists preserve encounter order; empty
 * index entries are omitted. In a diff, these indexes may contain association changes whose grouped
 * indictment effects cancel entirely.
 *
 * @param <Score_> the score type
 */
@NullMarked
public interface RawScoreAnalysis<Score_ extends Score<Score_>> {

  List<MatchOccurrenceAnalysis<Score_>> matchOccurrences();

  SequencedMap<ConstraintRef, List<MatchOccurrenceAnalysis<Score_>>> constraintMap();

  SequencedMap<Object, List<MatchOccurrenceAnalysis<Score_>>> indictmentMap();

  @Nullable SequencedMap<Object, List<MatchOccurrenceAnalysis<Score_>>> originalIndictmentMap();

  /** Preserves availability even when the snapshot has no occurrences. */
  default boolean originalsFetched() {
    return originalIndictmentMap() != null;
  }

  /** One justification per occurrence, including duplicate justifications and both diff signs. */
  default List<ConstraintJustification> justifications() {
    return matchOccurrences().stream().map(MatchOccurrenceAnalysis::justification).toList();
  }

  /** Filters {@link #justifications()} by type, including subclasses, preserving duplicates. */
  default <Justification_ extends ConstraintJustification> List<Justification_> justifications(
      Class<Justification_> justificationClass) {
    Objects.requireNonNull(justificationClass, "justificationClass");
    return justifications().stream()
        .filter(justificationClass::isInstance)
        .map(justificationClass::cast)
        .toList();
  }
}
