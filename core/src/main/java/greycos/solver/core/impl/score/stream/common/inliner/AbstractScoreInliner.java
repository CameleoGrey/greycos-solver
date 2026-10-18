package greycos.solver.core.impl.score.stream.common.inliner;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Supplier;

import greycos.solver.core.api.score.Score;
import greycos.solver.core.api.score.analysis.MatchOccurrenceAnalysis;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.api.score.stream.ConstraintRef;
import greycos.solver.core.api.solver.IndictmentFetchPolicy;
import greycos.solver.core.impl.bavet.common.provenance.LineageHandle;
import greycos.solver.core.impl.bavet.common.provenance.LineageResolver;
import greycos.solver.core.impl.score.analysis.AnalysisDetails;
import greycos.solver.core.impl.score.analysis.DefaultMatchOccurrenceAnalysis;
import greycos.solver.core.impl.score.analysis.DefaultRawScoreAnalysis;
import greycos.solver.core.impl.score.analysis.IndictmentAnalysisBuilder;
import greycos.solver.core.impl.score.analysis.IndictmentMaps;
import greycos.solver.core.impl.score.constraint.ConstraintMatch;
import greycos.solver.core.impl.score.constraint.ConstraintMatchPolicy;
import greycos.solver.core.impl.score.constraint.ConstraintMatchTotal;
import greycos.solver.core.impl.score.definition.BendableBigDecimalScoreDefinition;
import greycos.solver.core.impl.score.definition.BendableScoreDefinition;
import greycos.solver.core.impl.score.definition.HardMediumSoftBigDecimalScoreDefinition;
import greycos.solver.core.impl.score.definition.HardMediumSoftScoreDefinition;
import greycos.solver.core.impl.score.definition.HardSoftBigDecimalScoreDefinition;
import greycos.solver.core.impl.score.definition.HardSoftScoreDefinition;
import greycos.solver.core.impl.score.definition.ScoreDefinition;
import greycos.solver.core.impl.score.definition.SimpleBigDecimalScoreDefinition;
import greycos.solver.core.impl.score.definition.SimpleScoreDefinition;
import greycos.solver.core.impl.score.stream.common.AbstractConstraint;
import greycos.solver.core.impl.util.ElementAwareLinkedList;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Keeps track of the working score and constraint matches for a single constraint session. Every
 * time constraint weights change, a new instance needs to be created.
 *
 * @param <Score_>
 */
@NullMarked
public abstract class AbstractScoreInliner<Score_ extends Score<Score_>> {

  @SuppressWarnings({"unchecked", "rawtypes"})
  public static <Score_ extends Score<Score_>, ScoreInliner_ extends AbstractScoreInliner<Score_>>
      ScoreInliner_ buildScoreInliner(
          ScoreDefinition<Score_> scoreDefinition,
          Map<Constraint, Score_> constraintWeightMap,
          ConstraintMatchPolicy constraintMatchPolicy) {
    var numericType = scoreDefinition.getNumericType();
    if (numericType == float.class || numericType == double.class) {
      return (ScoreInliner_)
          new FloatingScoreInliner<>(
              constraintWeightMap,
              constraintMatchPolicy,
              scoreDefinition.getZeroScore(),
              numericType);
    }
    return (ScoreInliner_)
        switch (scoreDefinition) {
          case SimpleScoreDefinition simpleScoreDefinition ->
              new SimpleScoreInliner((Map) constraintWeightMap, constraintMatchPolicy);
          case SimpleBigDecimalScoreDefinition simpleBigDecimalScoreDefinition ->
              new SimpleBigDecimalScoreInliner((Map) constraintWeightMap, constraintMatchPolicy);
          case HardSoftScoreDefinition hardSoftScoreDefinition ->
              new HardSoftScoreInliner((Map) constraintWeightMap, constraintMatchPolicy);
          case HardSoftBigDecimalScoreDefinition hardSoftBigDecimalScoreDefinition ->
              new HardSoftBigDecimalScoreInliner((Map) constraintWeightMap, constraintMatchPolicy);
          case HardMediumSoftScoreDefinition hardMediumSoftScoreDefinition ->
              new HardMediumSoftScoreInliner((Map) constraintWeightMap, constraintMatchPolicy);
          case HardMediumSoftBigDecimalScoreDefinition hardMediumSoftBigDecimalScoreDefinition ->
              new HardMediumSoftBigDecimalScoreInliner(
                  (Map) constraintWeightMap, constraintMatchPolicy);
          case BendableScoreDefinition bendableScoreDefinition ->
              new BendableScoreInliner(
                  (Map) constraintWeightMap,
                  constraintMatchPolicy,
                  bendableScoreDefinition.getHardLevelsSize(),
                  bendableScoreDefinition.getSoftLevelsSize());
          case BendableBigDecimalScoreDefinition bendableScoreDefinition ->
              new BendableBigDecimalScoreInliner(
                  (Map) constraintWeightMap,
                  constraintMatchPolicy,
                  bendableScoreDefinition.getHardLevelsSize(),
                  bendableScoreDefinition.getSoftLevelsSize());
          default ->
              throw new UnsupportedOperationException(
                  "Impossible state: Unknown score definition class (%s)."
                      .formatted(scoreDefinition.getClass().getCanonicalName()));
        };
  }

  protected final ConstraintMatchPolicy constraintMatchPolicy;
  protected final Map<Constraint, Score_> constraintWeightMap;
  private final Map<Constraint, ElementAwareLinkedList<ConstraintMatchCarrier<Score_>>>
      constraintMatchMap;
  private @Nullable Map<ConstraintRef, ConstraintMatchTotal<Score_>>
      constraintIdToConstraintMatchTotalMap = null;

  protected AbstractScoreInliner(
      Map<Constraint, Score_> constraintWeightMap, ConstraintMatchPolicy constraintMatchPolicy) {
    this.constraintMatchPolicy = constraintMatchPolicy;
    constraintWeightMap.forEach(this::validateConstraintWeight);
    this.constraintWeightMap = constraintWeightMap;
    if (constraintMatchPolicy.isEnabled()) {
      this.constraintMatchMap = new IdentityHashMap<>(constraintWeightMap.size());
      for (var constraint : constraintWeightMap.keySet()) {
        // Ensure that even constraints without matches have their entry.
        this.constraintMatchMap.put(constraint, new ElementAwareLinkedList<>());
      }
    } else {
      this.constraintMatchMap = Collections.emptyMap();
    }
  }

  private void validateConstraintWeight(Constraint constraint, Score_ constraintWeight) {
    if (constraintWeight == null || constraintWeight.isZero()) {
      throw new IllegalArgumentException(
          "Impossible state: The constraintWeight ("
              + constraintWeight
              + ") cannot be zero, constraint ("
              + constraint
              + ") should have been culled during session creation.");
    }
  }

  public abstract Score_ extractScore();

  /**
   * Create a new instance of {@link WeightedScoreImpacter} for a particular constraint.
   *
   * @param constraint never null
   * @return never null
   */
  public abstract WeightedScoreImpacter<Score_, ?> buildWeightedScoreImpacter(
      AbstractConstraint<?, ?, ?> constraint);

  protected final ScoreImpact<Score_> addConstraintMatch(
      Constraint constraint,
      ConstraintMatchSupplier<Score_> constraintMatchSupplier,
      ScoreImpact<Score_> scoreImpact) {
    var constraintMatchList = getConstraintMatchList(constraint);
    /*
     * Creating a constraint match is a heavy operation which may yet be undone.
     * Defer creation of the constraint match until a later point.
     */
    var entry =
        constraintMatchList.add(
            constraintMatchPolicy.getIndictmentFetchPolicy() == IndictmentFetchPolicy.NONE
                ? new ConstraintMatchCarrier<>(constraintMatchSupplier, constraint, scoreImpact)
                : new IndictmentConstraintMatchCarrier<>(
                    constraintMatchSupplier, constraint, scoreImpact));
    clearMaps();
    return new WrappingScoreImpact<>(this, scoreImpact, entry);
  }

  private record WrappingScoreImpact<Score_ extends Score<Score_>>(
      AbstractScoreInliner<Score_> inliner,
      ScoreImpact<Score_> delegate,
      ElementAwareLinkedList.Entry<ConstraintMatchCarrier<Score_>> entry)
      implements ScoreImpact<Score_> {

    @Override
    public void undo() {
      delegate.undo();
      entry.remove();
      inliner.clearMaps();
    }

    @Override
    public Score_ toScore() {
      return delegate.toScore();
    }

    @Override
    public @Nullable Number matchWeight() {
      return delegate.matchWeight();
    }
  }

  private ElementAwareLinkedList<ConstraintMatchCarrier<Score_>> getConstraintMatchList(
      Constraint constraint) {
    // Optimization: computeIfAbsent() would have created a lambda on the hot path.
    var constraintMatchList = constraintMatchMap.get(constraint);
    if (constraintMatchList == null) {
      throw new IllegalStateException(
          "Impossible state: Unknown constraint (%s).".formatted(constraint.getConstraintRef()));
    }
    return constraintMatchList;
  }

  private void clearMaps() {
    constraintIdToConstraintMatchTotalMap = null;
  }

  public ConstraintMatchPolicy getConstraintMatchPolicy() {
    return constraintMatchPolicy;
  }

  public final Map<ConstraintRef, ConstraintMatchTotal<Score_>> getConstraintMatchTotalMap() {
    if (!constraintMatchPolicy.isEnabled()) {
      throw new IllegalStateException(
          "Impossible state: Method called while constraint matching is disabled.");
    } else if (constraintIdToConstraintMatchTotalMap == null) {
      rebuildConstraintMatchTotals();
    }
    return constraintIdToConstraintMatchTotalMap;
  }

  /** Attaches live lineage to the raw occurrence, independently of its cached justification. */
  public static void bindOriginalIndictment(ScoreImpact<?> impact, LineageHandle lineage) {
    if (!(impact instanceof WrappingScoreImpact<?> wrapping)
        || !(wrapping.entry().element() instanceof IndictmentConstraintMatchCarrier<?> carrier)) {
      throw new IllegalStateException(
          "Original-source lineage requires indictment-aware constraint matching.");
    }
    carrier.originalLineage = Objects.requireNonNull(lineage);
  }

  public final IndictmentMaps<Score_> getIndictmentMaps() {
    return extractAnalysisDetails(false).indictments();
  }

  public final AnalysisDetails<Score_> getAnalysisDetails() {
    return extractAnalysisDetails(constraintMatchPolicy.isMatchOccurrenceEnabled());
  }

  private AnalysisDetails<Score_> extractAnalysisDetails(boolean includeMatchOccurrences) {
    var policy = constraintMatchPolicy.getIndictmentFetchPolicy();
    if (policy == IndictmentFetchPolicy.NONE) {
      return AnalysisDetails.unavailable();
    }
    var zero = extractScore().zero();
    var terminal = new IndictmentAnalysisBuilder<>(zero);
    var original =
        policy == IndictmentFetchPolicy.TERMINAL_AND_ORIGINAL
            ? new IndictmentAnalysisBuilder<>(zero)
            : null;
    List<MatchOccurrenceAnalysis<Score_>> occurrences =
        includeMatchOccurrences ? new ArrayList<>() : null;
    // Provenance handles are live. Memoization must never cross extraction boundaries.
    var resolver = original == null ? null : new LineageResolver();
    var constraints = new ArrayList<>(constraintMatchMap.keySet());
    constraints.sort(Comparator.comparing(Constraint::getConstraintRef));
    for (var constraint : constraints) {
      for (var rawCarrier : constraintMatchMap.get(constraint)) {
        var carrier = (IndictmentConstraintMatchCarrier<Score_>) rawCarrier;
        var match = carrier.get();
        var terminalObjects = carrier.getTerminalObjects();
        var originalObjects =
            original == null
                ? null
                : resolver.resolve(
                    Objects.requireNonNull(
                        carrier.originalLineage,
                        "Original-source lineage was not bound for "
                            + constraint.getConstraintRef()
                            + "."));
        if (occurrences != null) {
          occurrences.add(
              new DefaultMatchOccurrenceAnalysis<>(
                  match.getConstraintRef(),
                  match.getScore(),
                  Objects.requireNonNull(match.getJustification()),
                  1,
                  List.copyOf(terminalObjects),
                  originalObjects));
        }
        terminal.add(match, terminalObjects);
        if (original != null) {
          original.add(match, originalObjects);
        }
      }
    }
    return new AnalysisDetails<>(
        new IndictmentMaps<>(terminal.build(), original == null ? null : original.build()),
        occurrences == null ? null : new DefaultRawScoreAnalysis<>(occurrences, original != null));
  }

  private void rebuildConstraintMatchTotals() {
    var constraintIdToConstraintMatchTotalMap =
        new TreeMap<ConstraintRef, ConstraintMatchTotal<Score_>>();
    for (var entry : constraintMatchMap.entrySet()) {
      var constraint = entry.getKey();
      var constraintMatchTotal =
          new ConstraintMatchTotal<>(
              constraint.getConstraintRef(), constraintWeightMap.get(constraint));
      for (var carrier : entry.getValue()) {
        // Constraint match instances are only created here when we actually need them.
        var constraintMatch = carrier.get();
        constraintMatchTotal.addConstraintMatch(constraintMatch);
      }
      constraintIdToConstraintMatchTotalMap.put(
          constraint.getConstraintRef(), constraintMatchTotal);
    }
    this.constraintIdToConstraintMatchTotalMap = constraintIdToConstraintMatchTotalMap;
  }

  public Set<Constraint> getConstraints() {
    return constraintWeightMap.keySet();
  }

  private static class ConstraintMatchCarrier<Score_ extends Score<Score_>>
      implements Supplier<ConstraintMatch<Score_>> {

    protected final Constraint constraint;
    protected final ConstraintMatchSupplier<Score_> constraintMatchSupplier;
    private final ScoreImpact<Score_> scoreImpact;
    private @Nullable ConstraintMatch<Score_> constraintMatch;

    private ConstraintMatchCarrier(
        ConstraintMatchSupplier<Score_> constraintMatchSupplier,
        Constraint constraint,
        ScoreImpact<Score_> scoreImpact) {
      this.constraint = constraint;
      this.constraintMatchSupplier = constraintMatchSupplier;
      this.scoreImpact = scoreImpact;
    }

    @Override
    public ConstraintMatch<Score_> get() {
      if (constraintMatch == null) {
        // Repeated requests for score explanation should not create the same constraint match over
        // and over.
        constraintMatch = constraintMatchSupplier.apply(constraint, scoreImpact.toScore());
        var matchWeight = scoreImpact.matchWeight();
        if (matchWeight != null) {
          constraintMatch =
              new ConstraintMatch<>(
                  constraintMatch.getConstraintRef(),
                  constraintMatch.getJustification(),
                  constraintMatch.getScore(),
                  matchWeight);
        }
      }
      return constraintMatch;
    }
  }

  private static final class IndictmentConstraintMatchCarrier<Score_ extends Score<Score_>>
      extends ConstraintMatchCarrier<Score_> {
    private @Nullable Collection<?> terminalObjects;
    private @Nullable LineageHandle originalLineage;

    private IndictmentConstraintMatchCarrier(
        ConstraintMatchSupplier<Score_> supplier,
        Constraint constraint,
        ScoreImpact<Score_> scoreImpact) {
      super(supplier, constraint, scoreImpact);
    }

    private Collection<?> getTerminalObjects() {
      if (terminalObjects == null) {
        terminalObjects = constraintMatchSupplier.getIndictedObjects(constraint);
      }
      return terminalObjects;
    }
  }
}
